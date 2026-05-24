package com.slothyhub.local;

import com.slothyhub.Pack;
import com.slothyhub.SlothyHubMod;
import net.minecraft.class_310;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Manages locally imported resource packs stored in
 * {@code .minecraft/resourcepacks/slothyhub-local/}.
 */
public final class LocalPackManager {

    private static final String LOCAL_DIR = "slothyhub-local";

    private LocalPackManager() {}

    /** Called on mod init to pre-create the local packs directory. */
    public static void init() {
        getLocalPackDir();
        SlothyHubMod.LOGGER.info("SlothyHub: local packs directory ready at {}", getLocalPackDir());
    }

    /** Returns the root directory for user-imported local packs. Creates it if absent. */
    public static Path getLocalPackDir() {
        Path base = class_310.method_1551().field_1697.toPath().resolve("resourcepacks").resolve(LOCAL_DIR);
        try { Files.createDirectories(base); } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("Could not create local pack directory: {}", e.getMessage());
        }
        return base;
    }

    /**
     * Scans the local pack directory and returns a {@link Pack} for each entry (zip or folder).
     * Returned packs have {@link Pack#isLocal()} == true.
     */
    public static List<Pack> getLocalPacks() {
        Path dir = getLocalPackDir();
        List<Pack> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                boolean isDir = Files.isDirectory(entry);
                boolean isZip = !isDir && name.toLowerCase(Locale.ROOT).endsWith(".zip");
                if (!isDir && !isZip) continue;
                String id = "local:" + name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
                Pack pack = buildLocalPack(id, name, entry, isZip);
                if (pack != null) out.add(pack);
            }
        } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("Error scanning local packs: {}", e.getMessage());
        }
        return out;
    }

    private static Pack buildLocalPack(String id, String filename, Path path, boolean isZip) {
        try {
            // Use Gson to create a minimal Pack object
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            String displayName = nameFromFilename(filename);
            obj.addProperty("id", id);
            obj.addProperty("name", displayName);
            obj.addProperty("pack_filename", filename);
            obj.addProperty("author_name", "Local");
            obj.addProperty("author_id", "local");
            obj.addProperty("showcase_path", "");
            obj.addProperty("pack_url", path.toUri().toString());
            obj.addProperty("is_zip", isZip);
            obj.addProperty("has_local_file", true);
            obj.addProperty("star_count", 0);
            obj.addProperty("downloads", 0);
            obj.addProperty("sha256", "");
            obj.addProperty("viewer_starred", false);
            Pack p = new com.google.gson.Gson().fromJson(obj, Pack.class);
            p.setLocal(true);
            return p;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("Failed to build local pack for {}: {}", filename, e.getMessage());
            return null;
        }
    }

    private static String nameFromFilename(String filename) {
        String name = filename;
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
        name = name.replace('_', ' ').replace('-', ' ');
        if (!name.isEmpty()) name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return name;
    }

    /**
     * Copies a zip or folder into the local packs directory.
     *
     * @param source the file or folder to import
     * @throws IOException on copy failure
     */
    public static void importPack(Path source) throws IOException {
        Path dir = getLocalPackDir();
        String name = source.getFileName().toString();
        Path dest = dir.resolve(name);
        if (Files.isDirectory(source)) {
            copyDirectory(source, dest);
        } else {
            Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        SlothyHubMod.LOGGER.info("SlothyHub: imported local pack '{}'", name);
    }

    /** Removes a local pack by its folder/file name. */
    public static void removePack(String filename) throws IOException {
        Path entry = getLocalPackDir().resolve(filename);
        if (!entry.startsWith(getLocalPackDir())) throw new IOException("Unsafe path: " + filename);
        if (Files.isDirectory(entry)) deleteRecursive(entry);
        else Files.deleteIfExists(entry);
    }

    private static void copyDirectory(Path src, Path dst) throws IOException {
        Files.createDirectories(dst);
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                Path rel = src.relativize(p);
                Path target = dst.resolve(rel);
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
