package com.slothyhub.builder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Saved Texture Builder choices embedded in built pack zips for later editing. */
public final class BuilderPackState {

    public static final String STATE_ENTRY = ".slothyhub-builder-state.json";
    private static final Gson GSON = new GsonBuilder().create();

    public record SlotSelection(
        String outputBaseName,
        String textureKey,
        String builtPath,
        String citName,
        String outputName,
        String folder
    ) {}

    private final int version;
    private final String packName;
    private final String librarySafeName;
    private final List<SlotSelection> selections;

    public BuilderPackState(String packName, String librarySafeName, List<SlotSelection> selections) {
        this.version = 1;
        this.packName = packName;
        this.librarySafeName = librarySafeName;
        this.selections = selections != null ? List.copyOf(selections) : List.of();
    }

    public int version() { return version; }
    public String packName() { return packName; }
    public String librarySafeName() { return librarySafeName; }
    public List<SlotSelection> selections() { return selections; }

    public void writeToZip(ZipOutputStream zos) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", version);
        root.addProperty("packName", packName);
        root.addProperty("librarySafeName", librarySafeName);
        JsonArray arr = new JsonArray();
        for (SlotSelection s : selections) {
            JsonObject o = new JsonObject();
            o.addProperty("outputBaseName", s.outputBaseName());
            if (s.textureKey() != null) o.addProperty("textureKey", s.textureKey());
            if (s.builtPath() != null) o.addProperty("builtPath", s.builtPath());
            if (s.citName() != null) o.addProperty("citName", s.citName());
            if (s.outputName() != null) o.addProperty("outputName", s.outputName());
            if (s.folder() != null) o.addProperty("folder", s.folder());
            arr.add(o);
        }
        root.add("selections", arr);
        byte[] json = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(STATE_ENTRY);
        zos.putNextEntry(entry);
        zos.write(json);
        zos.closeEntry();
    }

    public static BuilderPackState loadFromZipBytes(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) return null;
        try {
            byte[] json = readZipEntry(zipBytes, STATE_ENTRY);
            if (json != null) return parseJson(json);
            return inferFromZipContents(zipBytes);
        } catch (Exception e) {
            return null;
        }
    }

    private static BuilderPackState parseJson(byte[] json) {
        JsonObject root = GSON.fromJson(new String(json, StandardCharsets.UTF_8), JsonObject.class);
        if (root == null) return null;
        String packName = root.has("packName") ? root.get("packName").getAsString() : "My Custom Pack";
        String safe = root.has("librarySafeName") ? root.get("librarySafeName").getAsString() : null;
        List<SlotSelection> selections = new ArrayList<>();
        if (root.has("selections")) {
            for (var el : root.getAsJsonArray("selections")) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("outputBaseName")) continue;
                selections.add(new SlotSelection(
                    o.get("outputBaseName").getAsString(),
                    o.has("textureKey") ? o.get("textureKey").getAsString() : null,
                    o.has("builtPath") ? o.get("builtPath").getAsString() : null,
                    o.has("citName") ? o.get("citName").getAsString() : null,
                    o.has("outputName") ? o.get("outputName").getAsString() : null,
                    o.has("folder") ? o.get("folder").getAsString() : null
                ));
            }
        }
        return new BuilderPackState(packName, safe, selections);
    }

    /** Best-effort restore for packs built before state files were saved. */
    private static BuilderPackState inferFromZipContents(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = readAllZipEntries(zipBytes);
        String packName = "My Custom Pack";
        byte[] mcmeta = entries.get("pack.mcmeta");
        if (mcmeta != null) {
            String text = new String(mcmeta, StandardCharsets.UTF_8);
            int desc = text.indexOf("\"description\"");
            if (desc >= 0) {
                int q1 = text.indexOf('"', desc + 13);
                int q2 = text.indexOf('"', q1 + 1);
                if (q1 >= 0 && q2 > q1) packName = text.substring(q1 + 1, q2);
            }
        }
        List<SlotSelection> selections = new ArrayList<>();
        for (var e : entries.entrySet()) {
            String path = e.getKey().toLowerCase(Locale.ROOT);
            if (!path.endsWith(".png") && !path.endsWith(".ogg")) continue;
            if (path.contains("/cit/") && path.endsWith(".png")) {
                String base = fileBase(path);
                selections.add(new SlotSelection(base, null, e.getKey(), null, null, null));
            } else if (path.startsWith("assets/minecraft/textures/") && path.endsWith(".png")) {
                String base = fileBase(path);
                selections.add(new SlotSelection(base, null, e.getKey(), null, null, null));
            } else if (path.startsWith("assets/minecraft/sounds/") && path.endsWith(".ogg")) {
                String base = fileBase(path);
                selections.add(new SlotSelection(base, null, e.getKey(), null, null, null));
            }
        }
        if (selections.isEmpty()) return null;
        return new BuilderPackState(packName, null, selections);
    }

    private static String fileBase(String path) {
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = file.lastIndexOf('.');
        return dot >= 0 ? file.substring(0, dot) : file;
    }

    public static byte[] readZipEntry(byte[] zipBytes, String name) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (name.equals(entry.getName())) return zis.readAllBytes();
            }
        }
        return null;
    }

    public static Map<String, byte[]> readAllZipEntries(byte[] zipBytes) throws IOException {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) out.put(entry.getName().replace('\\', '/'), zis.readAllBytes());
            }
        }
        return out;
    }
}
