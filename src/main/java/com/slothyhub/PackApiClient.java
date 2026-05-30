package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PackApiClient {

    private static final Gson GSON = new Gson();
    private static final int TIMEOUT_MS = 10000;

    public static List<Pack> fetchPacks(String serverUrl) throws IOException {
        IOException last = null;
        for (String path : new String[]{"/api/packs.json", "/api/packs"}) {
            try {
                List<Pack> packs = PackCatalog.mergeWithEmbedded(fetchPacksFrom(serverUrl + path));
                applyStarData(packs);
                return packs;
            } catch (IOException e) {
                last = e;
            }
        }
        List<Pack> embedded = PackCatalog.loadEmbedded();
        if (!embedded.isEmpty()) {
            applyStarData(embedded);
            return embedded;
        }
        throw last != null ? last : new IOException("Could not load pack catalog from " + serverUrl);
    }

    /** Merge live star counts from the Cloudflare Worker (GitHub Pages catalog is static). */
    private static void applyStarData(List<Pack> packs) {
        if (packs == null || packs.isEmpty()) return;
        String worker = SlothyConfig.getWorkerBaseUrl();
        if (worker == null || worker.isBlank()) return;
        try {
            StringBuilder ids = new StringBuilder();
            for (Pack pack : packs) {
                if (pack.isLocal()) continue;
                if (ids.length() > 0) ids.append(',');
                ids.append(pack.getId());
            }
            String endpoint = worker + "/v1/pack-stars";
            if (ids.length() > 0) {
                endpoint += "?ids=" + URLEncoder.encode(ids.toString(), StandardCharsets.UTF_8);
            }
            HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
            conn.setRequestProperty("X-SlothyHub-Voter", SlothyConfig.getVoterId());
            if (conn.getResponseCode() != 200) return;
            try (InputStream in = conn.getInputStream()) {
                JsonObject root = GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
                if (root == null) return;
                JsonObject counts = root.has("counts") && root.get("counts").isJsonObject()
                    ? root.getAsJsonObject("counts") : null;
                Set<String> starred = new HashSet<>();
                if (root.has("starred") && root.get("starred").isJsonArray()) {
                    for (var el : root.getAsJsonArray("starred")) {
                        if (el.isJsonPrimitive()) starred.add(el.getAsString());
                    }
                }
                for (Pack pack : packs) {
                    if (pack.isLocal()) continue;
                    String id = pack.getId();
                    if (counts != null && counts.has(id)) {
                        int workerCount = counts.get(id).getAsInt();
                        if (workerCount > pack.getStarCount()) pack.setStarCount(workerCount);
                    }
                    pack.setViewerStarred(starred.contains(id));
                }
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("Pack stars unavailable: {}", e.getMessage());
        }
    }

    private static List<Pack> fetchPacksFrom(String endpoint) throws IOException {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
            String voter = SlothyConfig.getVoterId();
            if (voter != null && !voter.isBlank()) {
                conn.setRequestProperty("X-SlothyHub-Voter", voter);
            }
            int code = conn.getResponseCode();
            if (code == 503) throw new IOException("SlothyHub server is updating. Try again in a minute.");
            if (code != 200) throw new IOException("Server returned HTTP " + code + " for " + endpoint);
            try (InputStream in = conn.getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Type listType = new TypeToken<List<Pack>>(){}.getType();
                List<Pack> packs = GSON.fromJson(json, listType);
                return packs != null ? packs : List.of();
            }
        } catch (UnknownHostException e) {
            throw new IOException("The SlothyHub server looks offline. Press RECONNECT to retry.", e);
        } catch (SocketTimeoutException e) {
            throw new IOException("SlothyHub server didn't respond in time. Press RECONNECT to retry.", e);
        }
    }

    public static StarResult starPack(Pack pack) throws IOException {
        String worker = SlothyConfig.getWorkerBaseUrl();
        if (worker == null || worker.isBlank()) {
            throw new IOException("Star server is not configured.");
        }
        String endpoint = worker + "/v1/pack-star";
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
        conn.setRequestProperty("X-SlothyHub-Voter", SlothyConfig.getVoterId());
        conn.setDoOutput(true);
        JsonObject body = new JsonObject();
        body.addProperty("packId", pack.getId());
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(payload.length);
        try (OutputStream os = conn.getOutputStream()) { os.write(payload); }
        int code = conn.getResponseCode();
        if (code == 200 || code == 201) {
            try (InputStream in = conn.getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject obj = GSON.fromJson(json, JsonObject.class);
                int newCount = obj != null && obj.has("star_count") ? obj.get("star_count").getAsInt() : pack.getStarCount();
                boolean starred = obj == null || !obj.has("viewer_starred") || obj.get("viewer_starred").getAsBoolean();
                return new StarResult(newCount, starred);
            }
        } else if (code == 429) {
            throw new IOException("Too many stars too fast — try again in a minute.");
        } else if (code == 404) {
            throw new IOException("That pack is no longer available.");
        }
        throw new IOException("Server returned HTTP " + code + " for /v1/pack-star.");
    }

    public static BuilderResourcesResponse fetchBuilderResources(String serverUrl, String category) throws IOException {
        String endpoint = serverUrl + "/api/builder/resources";
        if (category != null && !category.isEmpty()) endpoint += "?category=" + category;
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("Server returned HTTP " + code + " for builder resources.");
        try (InputStream in = conn.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return GSON.fromJson(json, BuilderResourcesResponse.class);
        }
    }

    public static byte[] buildCustomPack(String serverUrl, String packName, List<BuilderMapping> mappings) throws IOException {
        String endpoint = serverUrl + "/api/builder/build";
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(120000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
        conn.setDoOutput(true);
        JsonObject body = new JsonObject();
        body.addProperty("name", packName);
        JsonArray arr = new JsonArray();
        for (BuilderMapping m : mappings) {
            JsonObject obj = new JsonObject();
            obj.addProperty("resource_id", m.resourceId());
            obj.addProperty("vanilla_name", m.vanillaName());
            arr.add(obj);
        }
        body.add("mappings", arr);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(payload.length);
        try (OutputStream os = conn.getOutputStream()) { os.write(payload); }
        int code = conn.getResponseCode();
        if (code != 200) {
            String errBody = "";
            try (InputStream es = conn.getErrorStream()) {
                if (es != null) errBody = new String(es.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
            throw new IOException("Build failed (HTTP " + code + "): " + errBody);
        }
        try (InputStream in = conn.getInputStream()) { return in.readAllBytes(); }
    }

    public static byte[] downloadBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
        if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode() + " fetching " + url);
        try (InputStream in = conn.getInputStream()) { return in.readAllBytes(); }
    }

    public static FeatherProfilesResponse fetchFeatherProfiles(String serverUrl) throws IOException {
        String endpoint = serverUrl + "/api/feather/profiles";
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("Server returned HTTP " + code + " for feather profiles.");
        try (InputStream in = conn.getInputStream()) {
            return GSON.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8), FeatherProfilesResponse.class);
        }
    }

    public static String downloadFeatherProfile(String serverUrl, String profileId) throws IOException {
        String endpoint = serverUrl + "/api/feather/profiles/" + profileId;
        HttpURLConnection conn = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("Server returned HTTP " + code + " for feather profile download.");
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public record BuilderMapping(String resourceId, String vanillaName) {}

    public static class BuilderResource {
        public String id;
        public String category;
        public String name;
        public String url;
        public String author;
        public int width;
        public int height;
    }

    public static class BuilderResourcesResponse {
        public List<String> categories;
        public List<BuilderResource> resources;
        public int total;
    }

    public static class FeatherProfile {
        public String id;
        public String name;
        public String description;
        public String author;
        public String download_url;
    }

    public static class FeatherProfilesResponse {
        public List<FeatherProfile> profiles;
        public int total;
    }

    public record StarResult(int starCount, boolean viewerStarred) {}
}
