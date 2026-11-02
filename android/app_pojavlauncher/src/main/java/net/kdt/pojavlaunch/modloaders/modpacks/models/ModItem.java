package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.NonNull;

public class ModItem extends ModSource {

    public String id;
    public String title;
    public String description;
    public String imageUrl;
    /* Amatista: creador y fotos (pueden ser null) */
    public String author;
    public String[] gallery;
    /* Amatista: filtros con los que se encontró (las versiones se limitan a ellos) */
    public String filterMcVersion;
    public String filterLoader;

    /** Copia los filtros de la búsqueda al elemento. */
    public void applyFilters(SearchFilters filters) {
        filterMcVersion = (filters.mcVersion == null || filters.mcVersion.isEmpty()) ? null : filters.mcVersion;
        filterLoader = filters.loaderApplies() ? filters.loader : null;
    }

    public ModItem(int apiSource, boolean isModpack, String id, String title, String description, String imageUrl) {
        this.apiSource = apiSource;
        this.isModpack = isModpack;
        this.contentType = isModpack ? Constants.TYPE_MODPACK : Constants.TYPE_MOD;
        this.id = id;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }

    public String getIconCacheTag() {
        return apiSource+"_"+id;
    }
}
