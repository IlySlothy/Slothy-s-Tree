package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChangelogData {

    private static final Gson GSON = new Gson();
    private static ChangelogData cached;

    private final String version;
    private final String title;
    private final List<String> items;

    private ChangelogData(String version, String title, List<String> items) {
        this.version = version;
        this.title = title;
        this.items = items;
    }

    public String version() { return version; }
    public String title() { return title; }
    public List<String> items() { return items; }

    public static ChangelogData load() {
        if (cached != null) return cached;
        try (InputStream in = ChangelogData.class.getResourceAsStream("/assets/slothyhub/changelog.json")) {
            if (in == null) return null;
            JsonObject obj = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> items = new ArrayList<>();
            if (obj.has("items")) {
                obj.getAsJsonArray("items").forEach(el -> items.add(el.getAsString()));
            }
            cached = new ChangelogData(
                obj.has("version") ? obj.get("version").getAsString() : "0",
                obj.has("title") ? obj.get("title").getAsString() : "What's new",
                Collections.unmodifiableList(items)
            );
            return cached;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("Could not load changelog.json: {}", e.getMessage());
            return null;
        }
    }
}
