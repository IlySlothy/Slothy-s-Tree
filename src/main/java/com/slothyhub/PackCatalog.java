package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.slothyhub.SlothyHubMod;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Bundled pack catalog — used when GitHub Pages is stale or offline. */
public final class PackCatalog {

    private static final Gson GSON = new Gson();
    private static final String RESOURCE = "/assets/slothyhub/packs.json";

    private PackCatalog() {}

    public static List<Pack> loadEmbedded() {
        try (InputStream in = PackCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return List.of();
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<Pack>>(){}.getType();
            List<Pack> packs = GSON.fromJson(json, listType);
            return packs != null ? packs : List.of();
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("Could not load embedded pack catalog: {}", e.getMessage());
            return List.of();
        }
    }

    public static Set<String> embeddedFilenames() {
        return loadEmbedded().stream()
            .map(Pack::getPackFilename)
            .filter(f -> f != null && !f.isBlank())
            .map(f -> f.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }

    /** Remote wins on filename conflict; embedded fills gaps. */
    public static List<Pack> mergeWithEmbedded(List<Pack> remote) {
        if (remote == null || remote.isEmpty()) return new ArrayList<>(loadEmbedded());
        List<Pack> embedded = loadEmbedded();
        if (embedded.isEmpty()) return new ArrayList<>(remote);

        var byFile = new java.util.LinkedHashMap<String, Pack>();
        for (Pack p : remote) byFile.put(filenameKey(p), p);
        for (Pack p : embedded) byFile.putIfAbsent(filenameKey(p), p);
        return new ArrayList<>(byFile.values());
    }

    static String filenameKey(Pack p) {
        if (p.getPackFilename() != null && !p.getPackFilename().isBlank())
            return p.getPackFilename().toLowerCase(Locale.ROOT);
        return "id:" + p.getId();
    }
}
