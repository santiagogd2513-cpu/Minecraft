package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Amatista: instala mods, paquetes de recursos y shaders (no modpacks) en la carpeta del
 * perfil que elija el usuario. Para mods de Modrinth también instala las dependencias
 * obligatorias de la misma versión de Minecraft y cargador.
 */
public final class ContentInstaller {
    private ContentInstaller() {}

    private static final ApiHandler MODRINTH = new ApiHandler("https://api.modrinth.com/v2");

    /** Muestra la lista de perfiles y descarga la versión elegida en el perfil seleccionado. */
    public static void pickProfileAndInstall(Context context, ModDetail detail, int versionIndex) {
        if (versionIndex < 0 || versionIndex >= detail.versionUrls.length) return;
        if (detail.versionUrls[versionIndex] == null) {
            Toast.makeText(context, R.string.amatista_content_no_download, Toast.LENGTH_LONG).show();
            return;
        }
        LauncherProfiles.load();
        Map<String, MinecraftProfile> profiles = LauncherProfiles.mainProfileJson.profiles;
        List<MinecraftProfile> list = new ArrayList<>(profiles.values());
        if (list.isEmpty()) {
            Toast.makeText(context, R.string.amatista_content_no_profiles, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            MinecraftProfile p = list.get(i);
            String n = (p.name == null || p.name.isEmpty()) ? p.lastVersionId : p.name;
            names[i] = n + "  ·  " + p.lastVersionId;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.amatista_content_pick_profile)
                .setItems(names, (d, which) -> install(context.getApplicationContext(), detail, versionIndex, list.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void install(Context context, ModDetail detail, int versionIndex, MinecraftProfile profile) {
        File dir = new File(Tools.getGameDirPath(profile), Constants.folderFor(detail.contentType));
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("No se pudo crear " + dir);
                String url = detail.versionUrls[versionIndex];
                download(url, detail.versionHashes == null ? null : detail.versionHashes[versionIndex], dir, detail.title);
                int deps = 0;
                if (detail.apiSource == Constants.SOURCE_MODRINTH && detail.contentType == Constants.TYPE_MOD
                        && detail.dependencies != null && detail.dependencies[versionIndex] != null) {
                    JsonObject version = MODRINTH.get("version/" + detail.versionIds[versionIndex], JsonObject.class);
                    deps = installModrinthDeps(detail.dependencies[versionIndex], version, dir, new HashSet<>());
                }
                int finalDeps = deps;
                Tools.runOnUiThread(() -> Toast.makeText(context,
                        context.getString(R.string.amatista_content_installed, detail.title, finalDeps),
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
        });
    }

    private static void download(String url, String sha1, File dir, String title) throws IOException {
        String name = FileUtils.getFileName(url);
        try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) {}
        File out = new File(dir, name);
        DownloadUtils.ensureSha1(out, sha1, () -> {
            DownloadUtils.downloadFileMonitored(url, out, new byte[8192], (curr, max) -> {
                int p = max > 0 ? (int) ((float) curr / max * 100f) : 0;
                ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, p, R.string.amatista_content_downloading, title);
            });
            return null;
        });
    }

    /** Instala las dependencias "required" (de forma recursiva) que coincidan con la versión del mod. */
    private static int installModrinthDeps(ModDetail.Dependencies[] deps, JsonObject parentVersion, File dir, Set<String> seen) throws IOException {
        if (deps == null || parentVersion == null) return 0;
        JsonArray gameVersions = parentVersion.getAsJsonArray("game_versions");
        JsonArray loaders = parentVersion.getAsJsonArray("loaders");
        int count = 0;
        for (ModDetail.Dependencies dep : deps) {
            if (dep == null || !"required".equals(dep.dependency_type)) continue;
            String key = dep.project_id != null ? dep.project_id : dep.version_id;
            if (key == null || !seen.add(key)) continue;
            JsonObject version = null;
            if (dep.version_id != null) {
                version = MODRINTH.get("version/" + dep.version_id, JsonObject.class);
            } else if (dep.project_id != null) {
                HashMap<String, Object> q = new HashMap<>();
                if (gameVersions != null) q.put("game_versions", gameVersions.toString());
                if (loaders != null) q.put("loaders", loaders.toString());
                JsonArray versions = MODRINTH.get("project/" + dep.project_id + "/version", q, JsonArray.class);
                if (versions != null && versions.size() > 0) version = versions.get(0).getAsJsonObject();
            }
            if (version == null) continue;
            JsonObject file = primaryFile(version);
            if (file == null) continue;
            String sha1 = null;
            JsonObject hashes = file.getAsJsonObject("hashes");
            if (hashes != null && hashes.has("sha1")) sha1 = hashes.get("sha1").getAsString();
            download(file.get("url").getAsString(), sha1, dir, file.get("filename").getAsString());
            count++;
            // Dependencias de la dependencia
            JsonArray sub = version.getAsJsonArray("dependencies");
            if (sub != null && sub.size() > 0) {
                ModDetail.Dependencies[] subDeps = new ModDetail.Dependencies[sub.size()];
                for (int i = 0; i < sub.size(); i++) {
                    JsonObject o = sub.get(i).getAsJsonObject();
                    subDeps[i] = new ModDetail.Dependencies(str(o, "project_id"), str(o, "version_id"), str(o, "file_name"), str(o, "dependency_type"));
                }
                count += installModrinthDeps(subDeps, version, dir, seen);
            }
        }
        return count;
    }

    private static JsonObject primaryFile(JsonObject version) {
        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.size() == 0) return null;
        for (JsonElement f : files) {
            JsonObject o = f.getAsJsonObject();
            if (o.has("primary") && o.get("primary").getAsBoolean()) return o;
        }
        return files.get(0).getAsJsonObject();
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }
}
