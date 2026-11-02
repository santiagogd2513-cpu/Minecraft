package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.content.Context;

import androidx.preference.PreferenceManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Amatista: nombres de las dependencias de una versión de un mod (Modrinth y CurseForge).
 */
public final class DependencyResolver {
    private DependencyResolver() {}

    /** Resultado: obligatorias y opcionales (nombres legibles). */
    public static final class Result {
        public final List<String> required = new ArrayList<>();
        public final List<String> optional = new ArrayList<>();
    }

    private static final ApiHandler MODRINTH = new ApiHandler("https://api.modrinth.com/v2");
    private static final Map<String, String> sNameCache = new HashMap<>();

    public static String curseforgeKey(Context context) {
        String key = PreferenceManager.getDefaultSharedPreferences(context).getString("amatista_curseforge_key", "").trim();
        return key.isEmpty() ? context.getString(R.string.curseforge_api_key) : key;
    }

    /** Hace peticiones de red: llamar fuera del hilo principal. Devuelve null si falla. */
    public static Result resolve(Context context, ModDetail detail, int versionIndex) {
        Result result = new Result();
        if (detail.dependencies == null || versionIndex < 0 || versionIndex >= detail.dependencies.length) return result;
        ModDetail.Dependencies[] deps = detail.dependencies[versionIndex];
        if (deps == null) return result;
        try {
            if (detail.apiSource == Constants.SOURCE_MODRINTH) resolveModrinth(deps, result);
            else if (detail.apiSource == Constants.SOURCE_CURSEFORGE) resolveCurseforge(context, deps, result);
        } catch (Exception e) {
            return null;
        }
        return result;
    }

    private static void resolveModrinth(ModDetail.Dependencies[] deps, Result result) {
        Set<String> required = new LinkedHashSet<>();
        Set<String> optional = new LinkedHashSet<>();
        for (ModDetail.Dependencies dep : deps) {
            if (dep == null) continue;
            String projectId = dep.project_id;
            if (projectId == null && dep.version_id != null) {
                JsonObject v = MODRINTH.get("version/" + dep.version_id, JsonObject.class);
                if (v != null && v.has("project_id")) projectId = v.get("project_id").getAsString();
            }
            if (projectId == null) {
                if (dep.file_name != null && "required".equals(dep.dependency_type)) result.required.add(dep.file_name);
                continue;
            }
            if ("required".equals(dep.dependency_type)) required.add(projectId);
            else if ("optional".equals(dep.dependency_type)) optional.add(projectId);
        }
        Map<String, String> names = modrinthNames(required, optional);
        for (String id : required) result.required.add(names.containsKey(id) ? names.get(id) : id);
        for (String id : optional) result.optional.add(names.containsKey(id) ? names.get(id) : id);
    }

    private static Map<String, String> modrinthNames(Set<String> a, Set<String> b) {
        Map<String, String> out = new HashMap<>();
        StringBuilder ids = new StringBuilder("[");
        for (Set<String> set : new Set[]{a, b}) {
            for (String id : set) {
                String cached;
                synchronized (sNameCache) { cached = sNameCache.get("mr:" + id); }
                if (cached != null) { out.put(id, cached); continue; }
                if (ids.length() > 1) ids.append(',');
                ids.append('"').append(id).append('"');
            }
        }
        ids.append(']');
        if (ids.length() > 2) {
            HashMap<String, Object> q = new HashMap<>();
            q.put("ids", ids.toString());
            JsonArray projects = MODRINTH.get("projects", q, JsonArray.class);
            if (projects != null) {
                for (JsonElement e : projects) {
                    JsonObject p = e.getAsJsonObject();
                    String id = p.get("id").getAsString();
                    String title = p.get("title").getAsString();
                    out.put(id, title);
                    synchronized (sNameCache) { sNameCache.put("mr:" + id, title); }
                }
            }
        }
        return out;
    }

    private static void resolveCurseforge(Context context, ModDetail.Dependencies[] deps, Result result) {
        ApiHandler cf = new ApiHandler("https://api.curseforge.com/v1", curseforgeKey(context));
        Set<String> seen = new LinkedHashSet<>();
        for (ModDetail.Dependencies dep : deps) {
            if (dep == null || dep.project_id == null || !seen.add(dep.project_id)) continue;
            // relationType de CurseForge: 3 = obligatoria, 2 = opcional
            boolean required = "3".equals(dep.dependency_type);
            boolean optional = "2".equals(dep.dependency_type);
            if (!required && !optional) continue;
            String name;
            synchronized (sNameCache) { name = sNameCache.get("cf:" + dep.project_id); }
            if (name == null) {
                JsonObject response = cf.get("mods/" + dep.project_id, JsonObject.class);
                JsonObject data = response != null && response.has("data") ? response.getAsJsonObject("data") : null;
                name = data != null && data.has("name") ? data.get("name").getAsString() : "#" + dep.project_id;
                synchronized (sNameCache) { sNameCache.put("cf:" + dep.project_id, name); }
            }
            (required ? result.required : result.optional).add(name);
        }
    }
}
