package com.slothyhub.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slothyhub.SlothyHubMod;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Index of textures hosted on GitHub Pages ({@value #CATALOG_URL}).
 *
 * <p>Each entry has an {@code asset} path (where it lives inside a resource pack),
 * a remote {@code png} or {@code sound} URL, and optional {@code mcmeta} /
 * {@code particle_json} sidecars.</p>
 *
 * <p>A bundled copy under {@code assets/slothyhub/textures.json} is merged in so
 * new entries (e.g. {@code amethyst_shard}) appear even before GitHub Pages updates.</p>
 */
public final class WebTextureCatalog {

    public record WebPack(String id, String name, List<WebTexture> textures) {}

    public record WebTexture(
        String category,
        String key,
        String label,
        String assetPath,
        String pngUrl,
        String mcmetaUrl,
        String particleJsonUrl,
        String soundUrl
    ) {
        /** Primary download URL (PNG or OGG). */
        public String dataUrl() {
            if (pngUrl != null && !pngUrl.isBlank()) return pngUrl;
            return soundUrl;
        }

        public boolean isSound() {
            return soundUrl != null && !soundUrl.isBlank()
                && (pngUrl == null || pngUrl.isBlank());
        }
    }

    public static final String CATALOG_URL =
        "https://ilyslothy.github.io/Slothy-s-Tree/textures.json";

    private static final String BUNDLED_CATALOG = "/assets/slothyhub/textures.json";

    private static final AtomicReference<List<WebPack>> CACHED = new AtomicReference<>(List.of());
    private static volatile boolean loadedOk = false;
    private static volatile long lastAttempt = 0L;
    private static final long RETRY_BACKOFF_MS = 60_000L;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SlothyHub-WebTextureCatalog");
        t.setDaemon(true);
        return t;
    });

    private WebTextureCatalog() {}

    public static List<WebPack> snapshot() {
        return CACHED.get();
    }

    public static boolean hasLoaded() { return loadedOk; }

    public static void refresh() {
        long now = System.currentTimeMillis();
        if (loadedOk) return;
        if (now - lastAttempt < RETRY_BACKOFF_MS) return;
        lastAttempt = now;
        POOL.submit(WebTextureCatalog::doLoad);
    }

    private static void doLoad() {
        try {
            List<WebPack> bundled = loadBundled();
            List<WebPack> remote = List.of();
            byte[] bytes = WebTextureCache.fetchBlocking(CATALOG_URL);
            if (bytes != null && bytes.length > 0) {
                remote = parseCatalogJson(new String(bytes, StandardCharsets.UTF_8));
            } else {
                SlothyHubMod.LOGGER.info("WebTextureCatalog: remote textures.json not reachable, using bundled catalog");
            }
            List<WebPack> merged = mergeCatalogs(remote, bundled);
            if (merged.isEmpty()) {
                SlothyHubMod.LOGGER.info("WebTextureCatalog: no catalog entries available");
                CACHED.set(List.of());
                return;
            }
            CACHED.set(Collections.unmodifiableList(merged));
            loadedOk = true;
            int entries = merged.stream().mapToInt(p -> p.textures().size()).sum();
            SlothyHubMod.LOGGER.info("WebTextureCatalog: loaded {} packs ({} entries, bundled={}, remote={})",
                merged.size(), entries, bundled.size(), remote.size());
        } catch (Exception ex) {
            SlothyHubMod.LOGGER.warn("WebTextureCatalog: failed to load catalog: {}", ex.getMessage());
        }
    }

    private static List<WebPack> loadBundled() {
        try (InputStream in = WebTextureCatalog.class.getResourceAsStream(BUNDLED_CATALOG)) {
            if (in == null) return List.of();
            return parseCatalogJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ex) {
            SlothyHubMod.LOGGER.warn("WebTextureCatalog: bundled catalog unreadable: {}", ex.getMessage());
            return List.of();
        }
    }

    /** Remote entries win on conflict; bundled fills gaps (e.g. before Pages deploy). */
    private static List<WebPack> mergeCatalogs(List<WebPack> remote, List<WebPack> bundled) {
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Map<String, WebTexture>> texByPack = new LinkedHashMap<>();

        for (WebPack p : remote) {
            names.put(p.id(), p.name());
            Map<String, WebTexture> map = texByPack.computeIfAbsent(p.id(), k -> new LinkedHashMap<>());
            for (WebTexture t : p.textures()) map.put(textureKey(t), t);
        }
        for (WebPack p : bundled) {
            names.putIfAbsent(p.id(), p.name());
            Map<String, WebTexture> map = texByPack.computeIfAbsent(p.id(), k -> new LinkedHashMap<>());
            for (WebTexture t : p.textures()) map.putIfAbsent(textureKey(t), t);
        }

        List<WebPack> out = new ArrayList<>();
        for (var e : texByPack.entrySet()) {
            String id = e.getKey();
            List<WebTexture> textures = List.copyOf(e.getValue().values());
            out.add(new WebPack(id, names.getOrDefault(id, id), textures));
        }
        return out;
    }

    private static String textureKey(WebTexture t) {
        String asset = t.assetPath();
        if (asset != null && !asset.isBlank())
            return asset.toLowerCase(Locale.ROOT);
        return (t.key() != null ? t.key() : "").toLowerCase(Locale.ROOT);
    }

    private static List<WebPack> parseCatalogJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        if (!json.isEmpty() && json.charAt(0) == '\uFEFF') json = json.substring(1);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<WebPack> packs = new ArrayList<>();
        JsonArray packArr = root.has("packs") ? root.getAsJsonArray("packs") : new JsonArray();
        for (var e : packArr) {
            JsonObject po = e.getAsJsonObject();
            String id = po.has("id") ? po.get("id").getAsString() : null;
            String name = po.has("name") ? po.get("name").getAsString() : id;
            if (id == null || id.isBlank()) continue;
            List<WebTexture> textures = new ArrayList<>();
            JsonArray ta = po.has("textures") ? po.getAsJsonArray("textures") : new JsonArray();
            for (var te : ta) {
                JsonObject to = te.getAsJsonObject();
                String category = to.has("category") ? to.get("category").getAsString() : "swords";
                String key = to.has("key") ? to.get("key").getAsString() : null;
                String label = to.has("label") ? to.get("label").getAsString() : key;
                String assetPath = to.has("asset") ? to.get("asset").getAsString() : null;
                String pngUrl = optString(to, "png");
                String soundUrl = optString(to, "sound");
                String mcmetaUrl = optString(to, "mcmeta");
                String particleJsonUrl = optString(to, "particle_json");
                if (key == null) continue;
                if (assetPath == null || assetPath.isBlank()) {
                    assetPath = "assets/minecraft/optifine/cit/swords/" + key + ".png";
                }
                if ((pngUrl == null || pngUrl.isBlank()) && (soundUrl == null || soundUrl.isBlank()))
                    continue;
                textures.add(new WebTexture(category, key, label, assetPath,
                    pngUrl, mcmetaUrl, particleJsonUrl, soundUrl));
            }
            packs.add(new WebPack(id, name, Collections.unmodifiableList(textures)));
        }
        return packs;
    }

    private static String optString(JsonObject o, String field) {
        if (!o.has(field) || o.get(field).isJsonNull()) return null;
        String s = o.get(field).getAsString();
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Map catalog category string to picker {@code SlotCategory}, or null. */
    public static String normalizeCategory(String category) {
        if (category == null) return "";
        return category.toLowerCase(Locale.ROOT);
    }
}
