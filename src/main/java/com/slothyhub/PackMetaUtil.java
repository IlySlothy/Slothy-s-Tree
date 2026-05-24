package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.ByteArrayOutputStream;
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

/** Safe pack.mcmeta read/write — avoids invalid JSON that crashes resource-pack loading. */
public final class PackMetaUtil {

    private static final Gson GSON = new Gson();

    private PackMetaUtil() {}

    public static byte[] buildMcmetaBytes(String description, int packFormat) {
        JsonObject root = new JsonObject();
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", packFormat);
        pack.add("description", new JsonPrimitive(sanitizeDescription(description)));
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
        int format = 34;
        if (Files.isRegularFile(mcmeta)) {
            String raw = Files.readString(mcmeta, StandardCharsets.UTF_8);
            if (isValidMcmeta(raw)) return;
            desc = extractDescriptionLoose(raw, desc);
            format = extractPackFormatLoose(raw, format);
        }
        Files.write(mcmeta, buildMcmetaBytes(desc, format));
    }

    /** Returns true if the zip was rewritten. */
    public static boolean repairZip(Path zipPath) {
        try {
            String desc = zipPath.getFileName().toString().replaceAll("(?i)\\.zip$", "").replace('_', ' ');
            int format = 34;
            byte[] newMcmeta;
            try (ZipFile zf = new ZipFile(zipPath.toFile())) {
                ZipEntry e = zf.getEntry("pack.mcmeta");
                if (e != null) {
                    String raw = new String(readAll(zf.getInputStream(e)), StandardCharsets.UTF_8);
                    if (isValidMcmeta(raw)) return false;
                    desc = extractDescriptionLoose(raw, desc);
                    format = extractPackFormatLoose(raw, format);
                }
            }
            newMcmeta = buildMcmetaBytes(desc, format);
            Path tmp = zipPath.resolveSibling(zipPath.getFileName() + ".slothyhub-repair.tmp");
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
            SlothyHubMod.LOGGER.info("Repaired invalid pack.mcmeta in {}", zipPath.getFileName());
            return true;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("Could not repair {}: {}", zipPath.getFileName(), e.getMessage());
            return false;
        }
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

    private static int extractPackFormatLoose(String raw, int fallback) {
        int idx = raw.indexOf("\"pack_format\"");
        if (idx < 0) return fallback;
        int colon = raw.indexOf(':', idx);
        if (colon < 0) return fallback;
        int i = colon + 1;
        while (i < raw.length() && !Character.isDigit(raw.charAt(i))) i++;
        int j = i;
        while (j < raw.length() && Character.isDigit(raw.charAt(j))) j++;
        if (j <= i) return fallback;
        try { return Integer.parseInt(raw.substring(i, j)); } catch (NumberFormatException e) { return fallback; }
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
