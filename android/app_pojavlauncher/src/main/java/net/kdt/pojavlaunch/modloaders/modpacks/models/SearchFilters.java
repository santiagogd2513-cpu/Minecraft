package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    public String name;
    @Nullable public String mcVersion;
    /** Uno de Constants.TYPE_* (isModpack se mantiene por compatibilidad). */
    public int contentType = Constants.TYPE_MODPACK;
    /** "forge", "fabric", "neoforge", "quilt" o null; solo se aplica a mods y modpacks. */
    @Nullable public String loader;

    public boolean loaderApplies() {
        return loader != null && !loader.isEmpty()
                && (contentType == Constants.TYPE_MOD || contentType == Constants.TYPE_MODPACK);
    }

}
