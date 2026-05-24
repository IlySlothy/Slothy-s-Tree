package com.slothyhub.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.slothyhub.Pack;
import com.slothyhub.PackCatalog;
import com.slothyhub.SlothyHubMod;
import net.minecraft.class_310;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Finds IlySlothy resource packs already sitting in {@code resourcepacks/} and
 * {@code resourcepacks/slothyhub-local/}, tagged for the ElyPVP filter.
 */
public final class InstalledPackScanner {

    private InstalledPackScanner() {}

    public static List<Pack> scan() {
        return scan(PackCatalog.embeddedFilenames());
    }

    /** @param catalogFilenames lower-case filenames already in the online catalog */
    public static List<Pack> scan(java.util.Set<String> catalogFilenames) {
        Map<String, Pack> byFile = new LinkedHashMap<>();
        Path rp = class_310.method_1551().field_1697.toPath().resolve("resourcepacks");
        scanDir(rp, byFile, false, catalogFilenames);
        scanDir(LocalPackManager.getLocalPackDir(), byFile, true, catalogFilenames);
        return new ArrayList<>(byFile.values());
    }

    private static void scanDir(Path dir, Map<String, Pack> out, boolean forceLocal,
                                java.util.Set<String> catalogFilenames) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (name.startsWith(".") || name.equals(LocalPackManager.LOCAL_DIR_NAME)) continue;
                boolean isDir = Files.isDirectory(entry);
                boolean isZip = !isDir && name.toLowerCase(Locale.ROOT).endsWith(".zip");
                if (!isDir && !isZip) continue;
                String key = name.toLowerCase(Locale.ROOT);
                if (catalogFilenames != null && catalogFilenames.contains(key)) continue;
                if (!forceLocal && !isIlySlothyPack(entry, isZip)) continue;
                if (out.containsKey(key)) continue;
                Pack p = buildPack(entry, name, isZip, true);
                if (p != null) out.put(key, p);
            }
        } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("InstalledPackScanner: {}", e.getMessage());
        }
    }

    private static boolean isIlySlothyPack(Path entry, boolean isZip) {
        String lower = entry.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.contains("ilyslothy") || lower.contains("slothy") || lower.contains("_priv")
            || lower.contains("priv_") || lower.equals("blossom.zip") || lower.equals("christmas2024.zip")
            || lower.equals("warden.zip") || lower.equals("fallensnow.zip") || lower.equals("summer.zip"))
            return true;
        if (!isZip) return false;
        try (ZipFile zf = new ZipFile(entry.toFile())) {
            ZipEntry mc = zf.getEntry("pack.mcmeta");
            if (mc == null) return false;
            try (InputStream in = zf.getInputStream(mc)) {
                String meta = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                return meta.contains("ilyslothy") || meta.contains("ily slothy") || meta.contains("ilysloth");
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static Pack buildPack(Path path, String filename, boolean isZip, boolean local) {
        try {
            JsonObject obj = new JsonObject();
            String display = nameFromFilename(filename);
            String id = "installed:" + filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
            obj.addProperty("id", id);
            obj.addProperty("name", display);
            obj.addProperty("pack_filename", filename);
            obj.addProperty("author_name", "ilyslothy");
            obj.addProperty("author_id", "ilyslothy");
            obj.addProperty("showcase_path", "");
            obj.addProperty("pack_url", path.toUri().toString());
            obj.addProperty("is_zip", isZip);
            obj.addProperty("has_local_file", true);
            obj.addProperty("star_count", 0);
            obj.addProperty("downloads", 0);
            obj.addProperty("sha256", "");
            obj.addProperty("viewer_starred", false);
            JsonArray tags = new JsonArray();
            tags.add("elypvp");
            tags.add("pvp");
            obj.add("tags", tags);
            Pack p = new com.google.gson.Gson().fromJson(obj, Pack.class);
            p.setLocal(local);
            return p;
        } catch (Exception e) {
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
}
