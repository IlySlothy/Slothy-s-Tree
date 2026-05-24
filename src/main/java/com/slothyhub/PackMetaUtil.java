package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.slothyhub.compat.Identifiers;
import com.slothyhub.compat.McVersion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Safe pack.mcmeta read/write — avoids invalid JSON and wrong pack_format for the running game. */
public final class PackMetaUtil {

    private static final Gson GSON = new Gson();
    /** Widest texture-only range this mod targets (1.20 → 1.21.8). */
    private static final int SUPPORTED_FORMAT_MIN = 15;

    private PackMetaUtil() {}

    public static int packFormatForCurrentGame() {
        return packFormatForVersion(McVersion.current());
    }

    public static int packFormatForVersion(String version) {
        if (compareVersions(version, "1.21.8") >= 0) return 64;
        if (compareVersions(version, "1.21.7") >= 0) return 64;
        if (compareVersions(version, "1.21.6") >= 0) return 63;
        if (compareVersions(version, "1.21.5") >= 0) return 55;
        if (compareVersions(version, "1.21.4") >= 0) return 46;
        if (compareVersions(version, "1.21.3") >= 0) return 42;
        if (compareVersions(version, "1.21.2") >= 0) return 42;
        if (compareVersions(version, "1.21.1") >= 0) return 34;
        if (compareVersions(version, "1.21") >= 0) return 34;
        if (compareVersions(version, "1.20.6") >= 0) return 32;
        if (compareVersions(version, "1.20.5") >= 0) return 32;
        if (compareVersions(version, "1.20.4") >= 0) return 32;
        if (compareVersions(version, "1.20.3") >= 0) return 22;
        if (compareVersions(version, "1.20.2") >= 0) return 18;
        return 15;
    }

    public static byte[] buildMcmetaBytes(String description, int packFormat) {
        return buildCompatibleMcmetaBytes(description, packFormat);
    }

    public static byte[] buildCompatibleMcmetaBytes(String description) {
        return buildCompatibleMcmetaBytes(description, packFormatForCurrentGame());
    }

    public static byte[] buildCompatibleMcmetaBytes(String description, int packFormat) {
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", packFormat);
        pack.add("description", new JsonPrimitive(sanitizeDescription(description)));
        int minFormat = Math.min(SUPPORTED_FORMAT_MIN, packFormat);
        JsonObject supported = new JsonObject();
        supported.addProperty("min_inclusive", minFormat);
        // Never claim support above the running game — 1.21.4 rejects max_inclusive: 64.
        supported.addProperty("max_inclusive", packFormat);
        pack.add("supported_formats", supported);
        root.add("pack", pack);
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    public static String sanitizeDescription(String text) {
        if (text == null || text.isBlank()) return "Resource Pack";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') sb.append(' ');
            else if (c >= 0x20 || c == ' ') sb.append(c);
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "Resource Pack" : out;
    }

    public static boolean isValidMcmeta(String raw) {
        if (raw == null || raw.isBlank()) return false;
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (!root.has("pack")) return false;
            JsonObject pack = root.getAsJsonObject("pack");
            if (!pack.has("description")) return false;
            JsonElement desc = pack.get("description");
            if (desc.isJsonPrimitive() && desc.getAsJsonPrimitive().isString()) {
                String s = desc.getAsString();
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c < 0x20 && c != ' ') return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void repairFolder(Path dir) throws IOException {
        Path mcmeta = dir.resolve("pack.mcmeta");
        String desc = dir.getFileName().toString().replace('_', ' ');
        int targetFormat = packFormatForCurrentGame();
        if (Files.isRegularFile(mcmeta)) {
            String raw = Files.readString(mcmeta, StandardCharsets.UTF_8);
            if (isValidMcmeta(raw) && !needsFormatUpgrade(raw, targetFormat)) return;
            desc = extractDescription(raw, desc);
        }
        Files.write(mcmeta, buildCompatibleMcmetaBytes(desc, targetFormat));
        SlothyHubMod.LOGGER.info("SlothyHub: set pack.mcmeta format {} for {}", targetFormat, dir.getFileName());
    }

    /** Returns true if the zip was rewritten. */
    public static boolean repairZip(Path zipPath) {
        Path tmp = zipPath.resolveSibling(zipPath.getFileName() + ".slothyhub-repair.tmp");
        try {
            String desc = zipPath.getFileName().toString().replaceAll("(?i)\\.zip$", "").replace('_', ' ');
            int targetFormat = packFormatForCurrentGame();
            try (ZipFile zf = new ZipFile(zipPath.toFile())) {
                ZipEntry e = zf.getEntry("pack.mcmeta");
                if (e != null) {
                    String raw = new String(readAll(zf.getInputStream(e)), StandardCharsets.UTF_8);
                    if (isValidMcmeta(raw) && !needsFormatUpgrade(raw, targetFormat)) return false;
                    desc = extractDescription(raw, desc);
                }
            }
            byte[] newMcmeta = buildCompatibleMcmetaBytes(desc, targetFormat);
            Files.deleteIfExists(tmp);
            try (ZipFile zf = new ZipFile(zipPath.toFile());
                 ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
                var entries = zf.entries();
                boolean wroteMcmeta = false;
                while (entries.hasMoreElements()) {
                    ZipEntry in = entries.nextElement();
                    if (in.isDirectory()) continue;
                    String name = in.getName().replace('\\', '/');
                    if ("pack.mcmeta".equalsIgnoreCase(name)) {
                        putEntry(zos, "pack.mcmeta", newMcmeta);
                        wroteMcmeta = true;
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(name));
                    try (InputStream inStream = zf.getInputStream(in)) {
                        inStream.transferTo(zos);
                    }
                    zos.closeEntry();
                }
                if (!wroteMcmeta) putEntry(zos, "pack.mcmeta", newMcmeta);
            }
            Files.move(tmp, zipPath, StandardCopyOption.REPLACE_EXISTING);
            SlothyHubMod.LOGGER.info("SlothyHub: set pack.mcmeta format {} in {}", targetFormat, zipPath.getFileName());
            return true;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("Could not repair {}: {}", zipPath.getFileName(), e.getMessage());
            return false;
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    /**
     * When a zip cannot be rewritten in place, extract readable entries to a sibling folder
     * and remove the zip so Minecraft and SlothyHub can still use the textures.
     */
    public static boolean migrateCorruptZipToFolder(Path zipPath) {
        if (!Files.isRegularFile(zipPath)) return false;
        String fileName = zipPath.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) return false;
        String folderName = fileName.substring(0, fileName.length() - 4);
        Path folder = zipPath.resolveSibling(folderName);
        try {
            if (Files.isDirectory(folder)) {
                repairFolder(folder);
                Files.deleteIfExists(zipPath);
                SlothyHubMod.LOGGER.info("Removed corrupt zip {} (using existing folder {})",
                    zipPath.getFileName(), folder.getFileName());
                return true;
            }
            int extracted = extractZipToFolder(zipPath, folder);
            if (extracted <= 0) return false;
            repairFolder(folder);
            Files.deleteIfExists(zipPath);
            SlothyHubMod.LOGGER.info("Extracted {} readable file(s) from {} → {}, removed zip",
                extracted, zipPath.getFileName(), folder.getFileName());
            return true;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("Could not migrate {} to folder: {}", zipPath.getFileName(), e.getMessage());
            return false;
        }
    }

    /** Best-effort extraction; skips individual corrupt entries. */
    static int extractZipToFolder(Path zipPath, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        int extracted = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (name.contains("..")) {
                    zis.closeEntry();
                    continue;
                }
                Path out = destDir.resolve(name);
                Files.createDirectories(out.getParent());
                try {
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                    extracted++;
                } catch (IOException e) {
                    SlothyHubMod.LOGGER.debug("Skipped corrupt entry {} in {}: {}",
                        name, zipPath.getFileName(), e.getMessage());
                }
                zis.closeEntry();
            }
        }
        return extracted;
    }

    public static boolean needsRepair(String raw) {
        if (!isValidMcmeta(raw)) return true;
        return needsFormatUpgrade(raw, packFormatForCurrentGame());
    }

    private static boolean needsFormatUpgrade(String raw, int targetFormat) {
        try {
            JsonObject pack = JsonParser.parseString(raw).getAsJsonObject().getAsJsonObject("pack");
            int packFormat = pack.has("pack_format") ? pack.get("pack_format").getAsInt() : -1;
            if (packFormat != targetFormat) return true;
            if (!pack.has("supported_formats")) return true;
            JsonElement sf = pack.get("supported_formats");
            if (!supportedFormatsInclude(sf, targetFormat)) return true;
            if (sf.isJsonObject()) {
                JsonObject range = sf.getAsJsonObject();
                int max = range.has("max_inclusive") ? range.get("max_inclusive").getAsInt() : packFormat;
                int min = range.has("min_inclusive") ? range.get("min_inclusive").getAsInt() : packFormat;
                if (max > targetFormat || min > targetFormat) return true;
                if (packFormat < min || packFormat > max) return true;
            }
            return false;
        } catch (Exception ignored) {}
        return true;
    }

    private static boolean supportedFormatsInclude(JsonElement el, int format) {
        if (el == null || el.isJsonNull()) return false;
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            int min = o.has("min_inclusive") ? o.get("min_inclusive").getAsInt() : format;
            int max = o.has("max_inclusive") ? o.get("max_inclusive").getAsInt() : format;
            return format >= min && format <= max;
        }
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                if (e.getAsInt() == format) return true;
            }
        }
        return false;
    }

    private static String extractDescription(String raw, String fallback) {
        try {
            JsonObject pack = JsonParser.parseString(raw).getAsJsonObject().getAsJsonObject("pack");
            JsonElement desc = pack.get("description");
            if (desc != null && desc.isJsonPrimitive()) {
                return sanitizeDescription(desc.getAsString());
            }
        } catch (Exception ignored) {}
        return extractDescriptionLoose(raw, fallback);
    }

    private static String extractDescriptionLoose(String raw, String fallback) {
        int idx = raw.indexOf("\"description\"");
        if (idx < 0) return sanitizeDescription(fallback);
        int start = raw.indexOf('"', idx + 13);
        if (start < 0) return sanitizeDescription(fallback);
        int end = start + 1;
        StringBuilder sb = new StringBuilder();
        while (end < raw.length()) {
            char c = raw.charAt(end);
            if (c == '"' && raw.charAt(end - 1) != '\\') break;
            if (c == '\n' || c == '\r' || c == '\t') sb.append(' ');
            else if (c >= 0x20) sb.append(c);
            end++;
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? sanitizeDescription(fallback) : sanitizeDescription(out);
    }

    private static String normalizeMcVersion(String version) {
        int dash = version.indexOf('-');
        return dash > 0 ? version.substring(0, dash) : version;
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int va = i < a.length ? parsePart(a[i]) : 0;
            int vb = i < b.length ? parsePart(b[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9].*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    private static void putEntry(ZipOutputStream zos, String path, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(data);
        zos.closeEntry();
    }

    public static String nameFromFilename(String filename) {
        String name = filename;
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) name = name.substring(0, name.length() - 4);
        name = name.replace('_', ' ').replace('-', ' ');
        if (!name.isEmpty()) name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return name;
    }
}
