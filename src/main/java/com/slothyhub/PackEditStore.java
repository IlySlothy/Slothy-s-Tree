package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class PackEditStore {

    public static final String SIDE_CAR = "slothyhub_edit.json";
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    // Per-pack-id display name overrides stored in the config dir
    private static final Path NAMES_PATH;
    private static final Map<String, String> NAMES = new LinkedHashMap<>();
    private static boolean namesLoaded = false;

    static {
        Path configDir;
        try {
            configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("slothyhub_names.json");
        } catch (Exception e) {
            configDir = Path.of("config", "slothyhub_names.json");
        }
        NAMES_PATH = configDir;
    }

    private PackEditStore() {}

    private static void ensureNamesLoaded() {
        if (namesLoaded) return;
        namesLoaded = true;
        if (!Files.exists(NAMES_PATH)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(NAMES_PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String k : root.keySet()) {
                JsonElement v = root.get(k);
                if (v != null && v.isJsonPrimitive()) NAMES.put(k, v.getAsString());
            }
        } catch (Exception ignored) {}
    }

    public static String getName(String packId) {
        if (packId == null || packId.isBlank()) return null;
        ensureNamesLoaded();
        return NAMES.get(packId);
    }

    public static void setName(String packId, String name) {
        if (packId == null || packId.isBlank()) return;
        ensureNamesLoaded();
        if (name == null || name.isBlank()) NAMES.remove(packId);
        else NAMES.put(packId, name.trim());
    }

    public static void removeName(String packId) {
        if (packId == null || packId.isBlank()) return;
        ensureNamesLoaded();
        NAMES.remove(packId);
    }

    public static void save() {
        ensureNamesLoaded();
        JsonObject root = new JsonObject();
        NAMES.forEach((k, v) -> root.addProperty(k, v));
        try {
            Files.writeString(NAMES_PATH, PRETTY.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("Failed to save pack name overrides: {}", e.getMessage());
        }
    }

    public static String readPackMcmetaDescription(Path packRoot) {
        if (packRoot == null) return "";
        Path mcmeta = packRoot.resolve("pack.mcmeta");
        if (!Files.isRegularFile(mcmeta)) return "";
        try {
            JsonObject root = JsonParser.parseString(Files.readString(mcmeta, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has("pack")) return "";
            JsonObject pack = root.getAsJsonObject("pack");
            if (!pack.has("description")) return "";
            JsonElement d = pack.get("description");
            if (d.isJsonPrimitive()) return d.getAsString();
            if (d.isJsonArray()) {
                JsonArray arr = d.getAsJsonArray();
                return arr.size() > 0 ? arr.get(0).getAsString() : "";
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static void writePackMcmetaDescription(Path packRoot, String description) throws IOException {
        if (packRoot == null) throw new IOException("No pack folder");
        Path mcmeta = packRoot.resolve("pack.mcmeta");
        if (!Files.isRegularFile(mcmeta)) throw new IOException("pack.mcmeta not found in pack");
        String raw = Files.readString(mcmeta, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
        JsonObject packObj = root.getAsJsonObject("pack");
        if (packObj == null) { packObj = new JsonObject(); root.add("pack", packObj); }
        packObj.add("description", new JsonPrimitive(description == null ? "" : description));
        Files.writeString(mcmeta, PRETTY.toJson(root), StandardCharsets.UTF_8);
    }

    public static Map<String, String> loadHints(Path packRoot) {
        if (packRoot == null) return new LinkedHashMap<>();
        Path f = packRoot.resolve(SIDE_CAR);
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        if (!Files.isRegularFile(f)) return out;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("hints") && root.get("hints").isJsonObject()) {
                JsonObject h = root.getAsJsonObject("hints");
                for (String k : h.keySet()) {
                    JsonElement v = h.get(k);
                    if (v != null && v.isJsonPrimitive()) out.put(k, v.getAsString());
                }
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("Could not read {}: {}", SIDE_CAR, e.getMessage());
        }
        return out;
    }

    public static String loadSidecarDisplayName(Path packRoot) {
        if (packRoot == null) return "";
        Path f = packRoot.resolve(SIDE_CAR);
        if (!Files.isRegularFile(f)) return "";
        try {
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            return root.has("displayName") && root.get("displayName").isJsonPrimitive()
                ? root.get("displayName").getAsString() : "";
        } catch (Exception ignored) {}
        return "";
    }

    public static void saveSidecar(Path packRoot, String displayName, Map<String, String> hints) throws IOException {
        if (packRoot == null) throw new IOException("No pack folder");
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("displayName", displayName == null ? "" : displayName);
        JsonObject h = new JsonObject();
        for (Map.Entry<String, String> e : hints.entrySet()) {
            if (e.getKey() != null && !e.getKey().isBlank()) {
                h.addProperty(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        root.add("hints", h);
        Files.writeString(packRoot.resolve(SIDE_CAR), PRETTY.toJson(root), StandardCharsets.UTF_8);
    }

    public static List<String> scanTexturePaths(Path packRoot, String subdir, int max) throws IOException {
        if (packRoot == null) return List.of();
        Path base = packRoot.resolve("assets/minecraft/textures").resolve(subdir);
        if (!Files.isDirectory(base)) return List.of();
        List<String> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            for (Path p : walk.toList()) {
                if (Files.isRegularFile(p) && p.toString().endsWith(".png")) {
                    paths.add(packRoot.relativize(p).toString().replace('\\', '/'));
                    if (paths.size() >= max) break;
                }
            }
        }
        Collections.sort(paths);
        return paths;
    }

    public static String guessVanillaIdFromPath(String relPath) {
        int slash = relPath.lastIndexOf('/');
        String file = slash >= 0 ? relPath.substring(slash + 1) : relPath;
        if (file.endsWith(".png")) file = file.substring(0, file.length() - 4);
        return file.replace('-', '_');
    }
}
