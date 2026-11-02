package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.net.Uri;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        String projectType;
        switch (searchFilters.contentType) {
            case Constants.TYPE_MODPACK: projectType = "modpack"; break;
            case Constants.TYPE_RESOURCEPACK: projectType = "resourcepack"; break;
            case Constants.TYPE_SHADER: projectType = "shader"; break;
            default: projectType = "mod";
        }
        facetString.append(String.format("[\"project_type:%s\"]", projectType));
        if(searchFilters.loaderApplies())
            facetString.append(String.format(",[\"categories:%s\"]", searchFilters.loader));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    hit.get("project_type").getAsString().equals("modpack"),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.get("icon_url").isJsonNull() ? null : hit.get("icon_url").getAsString()
            );
            items[i].contentType = searchFilters.contentType;
            items[i].applyFilters(searchFilters);
            if(hit.has("author") && !hit.get("author").isJsonNull()) items[i].author = hit.get("author").getAsString();
            JsonArray gallery = hit.has("gallery") && hit.get("gallery").isJsonArray() ? hit.getAsJsonArray("gallery") : null;
            if(gallery != null) {
                items[i].gallery = new String[gallery.size()];
                for(int g = 0; g < gallery.size(); g++) items[i].gallery[g] = gallery.get(g).getAsString();
            }
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        fillInMissingModItemData(item);
        // Amatista: solo las versiones que cumplen los filtros de la búsqueda (versión de MC y cargador)
        HashMap<String, Object> versionQuery = new HashMap<>();
        if(item.filterMcVersion != null) versionQuery.put("game_versions", "[\"" + item.filterMcVersion + "\"]");
        if(item.filterLoader != null) versionQuery.put("loaders", "[\"" + item.filterLoader + "\"]");
        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), versionQuery, JsonArray.class);
        if(response == null) return null;
        System.out.println(response);
        String[] names = new String[response.size()];
        String[] ids = new String[response.size()];
        String[] mcNames = new String[response.size()];
        String[] urls = new String[response.size()];
        String[] hashes = new String[response.size()];
        ModDetail.Dependencies[][] dependencies = new ModDetail.Dependencies[response.size()][];

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = version.get("name").getAsString();
            if(!item.isModpack && item.contentType == Constants.TYPE_MOD) {
                JsonArray loaders = version.getAsJsonArray("loaders");
                if(loaders != null && loaders.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for(JsonElement l : loaders) { if(sb.length() > 0) sb.append(", "); sb.append(l.getAsString()); }
                    names[i] += " [" + sb + "]";
                }
            }
            ids[i] = version.get("id").getAsString();
            try {
                JsonArray dependenciesJsonArray = version.getAsJsonArray("dependencies");
                dependencies[i] = new ModDetail.Dependencies[dependenciesJsonArray.size()];
                for (int i1 = 0; i1 < dependenciesJsonArray.size(); ++i1) {
                    JsonObject obj = dependenciesJsonArray.get(i1).getAsJsonObject();
                    dependencies[i][i1] = new ModDetail.Dependencies(
                            GsonJsonUtils.getStringSafe(obj, "project_id"),
                            GsonJsonUtils.getStringSafe(obj, "version_id"),
                            GsonJsonUtils.getStringSafe(obj, "file_name"),
                            GsonJsonUtils.getStringSafe(obj, "dependency_type")
                    );
                }
            } catch (Exception ignored) {}

            mcNames[i] = item.filterMcVersion != null ? item.filterMcVersion
                    : version.get("game_versions").getAsJsonArray().get(0).getAsString();
            urls[i] = version.get("files").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString();
            // Assume there may not be hashes, in case the API changes
            JsonObject hashesMap = version.getAsJsonArray("files").get(0).getAsJsonObject()
                    .get("hashes").getAsJsonObject();
            if(hashesMap == null || hashesMap.get("sha1") == null){
                hashes[i] = null;
                continue;
            }

            hashes[i] = hashesMap.get("sha1").getAsString();
        }

        return new ModDetail(item, names, ids, mcNames, urls, hashes, dependencies);
    }

    private void fillInMissingModItemData(ModItem item) {
        if (!(item.title == null || item.description == null || item.imageUrl == null)) return;
        JsonObject projectResponse = mApiHandler.get(String.format("project/%s", item.id), JsonObject.class);
        if (projectResponse == null) return;
        if (item.title == null) {
            JsonElement title = projectResponse.get("title");
            item.title = title != null ? title.getAsString() : "";
        }
        if (item.description == null) {
            JsonElement description = projectResponse.get("description");
            item.description = description != null ? description.getAsString() : "";
        }
        if (item.imageUrl == null) {
            JsonElement imageUrl = projectResponse.get("icon_url");
            item.imageUrl = imageUrl != null ? imageUrl.getAsString() : null;
        }
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installMrpack);
    }

    @Override
    public ModLoader importModpack(File modpackFile) throws IOException, NoSuchAlgorithmException {
        return ModpackInstaller.importModpack(modpackFile, Constants.SOURCE_MODRINTH, this::installMrpack);
    }

    private static ModLoader createInfo(ModrinthIndex modrinthIndex) {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_NEOFORGE, modLoaderVersion, mcVersion);
        }
        return null;
    }

    private ModLoader installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            
            ModDownloader modDownloader = new ModDownloader(instanceDestination);
            for(ModrinthIndex.ModrinthIndexFile indexFile : modrinthIndex.files) {
                modDownloader.submitDownload(indexFile.fileSize, indexFile.path, indexFile.hashes.sha1, indexFile.downloads);
            }
            modDownloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }
}
