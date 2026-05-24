package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Custom packs built with the Texture Builder — stored in .minecraft/slothyhub-library/. */
public final class BuiltPackLibrary {

    public static final String BUILT_MARKER = ".slothyhub-built";
    private static final String LIB_DIR = "slothyhub-library";
    private static final String MANIFEST = "library.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BuiltPackLibrary() {}

    public static Path libraryDir() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve(LIB_DIR);
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    public static void savePack(String safeName, String displayName, byte[] zipData) throws IOException {
        Files.write(libraryDir().resolve(safeName + ".zip"), zipData);
        JsonObject manifest = readManifest();
        JsonArray packs = manifest.has("packs") ? manifest.getAsJsonArray("packs") : new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("id", "library:" + safeName);
        entry.addProperty("name", displayName);
        entry.addProperty("filename", safeName + ".zip");
        entry.addProperty("created", System.currentTimeMillis());
        packs.add(entry);
        manifest.add("packs", packs);
        Files.writeString(libraryDir().resolve(MANIFEST), GSON.toJson(manifest), StandardCharsets.UTF_8);
    }

    public static List<Pack> listPacks() {
        List<Pack> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        JsonObject manifest = readManifest();
        if (manifest.has("packs")) {
            for (var el : manifest.getAsJsonArray("packs")) {
                JsonObject o = el.getAsJsonObject();
                String filename = o.has("filename") ? o.get("filename").getAsString() : "";
                if (filename.isBlank()) continue;
                Path zip = libraryDir().resolve(filename);
                if (!Files.exists(zip)) continue;
                Pack p = packFromEntry(o, zip);
                if (p != null) { out.add(p); seen.add(filename.toLowerCase(Locale.ROOT)); }
            }
        }
        try (var stream = Files.list(libraryDir())) {
            for (Path p : stream.toList()) {
                String name = p.getFileName().toString();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
                if (seen.contains(name.toLowerCase(Locale.ROOT))) continue;
                Pack pack = packFromZip(p);
                if (pack != null) out.add(pack);
            }
        } catch (IOException ignored) {}
        return out;
    }

    public static void deletePack(Pack pack) throws IOException {
        String filename = pack.getPackFilename();
        Path zip = libraryDir().resolve(filename);
        Files.deleteIfExists(zip);
        JsonObject manifest = readManifest();
        if (!manifest.has("packs")) return;
        JsonArray arr = manifest.getAsJsonArray("packs");
        JsonArray kept = new JsonArray();
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            if (!pack.getId().equals(o.get("id").getAsString())) kept.add(o);
        }
        manifest.add("packs", kept);
        Files.writeString(libraryDir().resolve(MANIFEST), GSON.toJson(manifest), StandardCharsets.UTF_8);
    }

    /** Skip built packs and mod staging folders when scanning for builder textures. */
    public static boolean shouldSkipForScanner(Path packPath) {
        String fn = packPath.getFileName().toString();
        if (fn.startsWith(".")) return true;
        if (".slothyhub-staging".equals(fn)) return true;
        try {
            Path lib = libraryDir().toRealPath();
            if (packPath.toRealPath().startsWith(lib)) return true;
        } catch (IOException ignored) {}
        if (Files.isDirectory(packPath) && Files.exists(packPath.resolve(BUILT_MARKER))) return true;
        if (fn.toLowerCase(Locale.ROOT).endsWith(".zip") && zipContainsBuiltMarker(packPath)) return true;
        if (isKnownLibraryFile(fn)) return true;
        return false;
    }

    public static boolean zipContainsBuiltMarker(Path zipPath) {
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            return zf.getEntry(BUILT_MARKER) != null;
        } catch (Exception e) { return false; }
    }

    private static boolean isKnownLibraryFile(String filename) {
        JsonObject manifest = readManifest();
        if (!manifest.has("packs")) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        for (var el : manifest.getAsJsonArray("packs")) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("filename") && o.get("filename").getAsString().toLowerCase(Locale.ROOT).equals(lower))
                return true;
        }
        return false;
    }

    private static JsonObject readManifest() {
        Path path = libraryDir().resolve(MANIFEST);
        if (!Files.exists(path)) return new JsonObject();
        try {
            JsonObject obj = GSON.fromJson(Files.readString(path), JsonObject.class);
            return obj != null ? obj : new JsonObject();
        } catch (Exception e) { return new JsonObject(); }
    }

    private static Pack packFromEntry(JsonObject o, Path zip) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", o.get("id").getAsString());
            obj.addProperty("name", o.has("name") ? o.get("name").getAsString() : "Custom Pack");
            obj.addProperty("pack_filename", o.get("filename").getAsString());
            obj.addProperty("author_name", "You");
            obj.addProperty("author_id", "builder");
            obj.addProperty("showcase_path", "");
            obj.addProperty("pack_url", zip.toUri().toString());
            JsonArray tags = new JsonArray();
            tags.add("Builder");
            obj.add("tags", tags);
            obj.addProperty("is_zip", true);
            obj.addProperty("has_local_file", true);
            obj.addProperty("star_count", 0);
            obj.addProperty("downloads", 0);
            obj.addProperty("sha256", "");
            obj.addProperty("viewer_starred", false);
            Pack p = GSON.fromJson(obj, Pack.class);
            p.setLocal(true);
            return p;
        } catch (Exception e) { return null; }
    }

    private static Pack packFromZip(Path zip) {
        String filename = zip.getFileName().toString();
        String safe = filename.replaceAll("(?i)\\.zip$", "");
        JsonObject obj = new JsonObject();
        obj.addProperty("id", "library:" + safe);
        obj.addProperty("name", safe.replace('_', ' '));
        obj.addProperty("pack_filename", filename);
        obj.addProperty("author_name", "You");
        obj.addProperty("author_id", "builder");
        obj.addProperty("showcase_path", "");
        obj.addProperty("pack_url", zip.toUri().toString());
        obj.addProperty("is_zip", true);
        obj.addProperty("has_local_file", true);
        obj.addProperty("star_count", 0);
        obj.addProperty("downloads", 0);
        obj.addProperty("sha256", "");
        obj.addProperty("viewer_starred", false);
        Pack p = GSON.fromJson(obj, Pack.class);
        p.setLocal(true);
        return p;
    }

    public static String sanitizeName(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim().replace(' ', '_');
        return safe.isEmpty() ? "SlothyCustomPack" : safe;
    }
}
