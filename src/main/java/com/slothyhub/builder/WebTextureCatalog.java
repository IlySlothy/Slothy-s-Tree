package com.slothyhub.builder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slothyhub.SlothyHubMod;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Index of CIT / vanilla textures hosted alongside the pack catalog on
 * https://ilyslothy.github.io/Slothy-s-Tree/. Schema:
 * <pre>
 * {
 *   "version": 1,
 *   "generated_at": "ISO8601",
 *   "packs": [
 *     {
 *       "id": "summer",
 *       "name": "Summer",
 *       "textures": [
 *         {
 *           "category": "swords",
 *           "key": "perfect_sword",
 *           "label": "Perfect Sword",
 *           "png": "https://.../textures/summer/swords/perfect_sword.png",
 *           "mcmeta": "https://.../textures/summer/swords/perfect_sword.png.mcmeta"   (optional)
 *         }
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * Lifecycle: {@link #refresh()} runs once per JVM (with an automatic retry on
 * the next call if the previous attempt failed). Callers should use the
 * blocking {@link #snapshot()} which returns the most recently loaded data
 * (or an empty list if nothing has loaded yet).
 */
public final class WebTextureCatalog {

    public record WebPack(String id, String name, List<WebTexture> textures) {}

    /** A single texture entry inside a web pack. */
    public record WebTexture(
        String category,     // e.g. "swords"
        String key,          // e.g. "perfect_sword"
        String label,        // human-friendly, e.g. "Perfect Sword"
        String pngUrl,       // absolute URL
        String mcmetaUrl     // absolute URL, may be null
    ) {}

    public static final String CATALOG_URL =
        "https://ilyslothy.github.io/Slothy-s-Tree/textures.json";

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

    /** Returns the latest loaded snapshot. Never null. */
    public static List<WebPack> snapshot() {
        return CACHED.get();
    }

    public static boolean hasLoaded() { return loadedOk; }

    /**
     * Kicks off a refresh on a background thread if no successful load has
     * happened yet, or the last attempt failed at least {@value RETRY_BACKOFF_MS}ms ago.
     */
    public static void refresh() {
        long now = System.currentTimeMillis();
        if (loadedOk) return;
        if (now - lastAttempt < RETRY_BACKOFF_MS) return;
        lastAttempt = now;
        POOL.submit(WebTextureCatalog::doLoad);
    }

    private static void doLoad() {
        try {
            byte[] bytes = WebTextureCache.fetchBlocking(CATALOG_URL);
            if (bytes == null || bytes.length == 0) {
                SlothyHubMod.LOGGER.info("WebTextureCatalog: textures.json not reachable - using empty catalog");
                CACHED.set(List.of());
                return;
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
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
                    String pngUrl = to.has("png") ? to.get("png").getAsString() : null;
                    String mcmetaUrl = to.has("mcmeta") && !to.get("mcmeta").isJsonNull()
                        ? to.get("mcmeta").getAsString() : null;
                    if (key == null || pngUrl == null) continue;
                    textures.add(new WebTexture(category, key, label, pngUrl, mcmetaUrl));
                }
                packs.add(new WebPack(id, name, Collections.unmodifiableList(textures)));
            }
            CACHED.set(Collections.unmodifiableList(packs));
            loadedOk = true;
            SlothyHubMod.LOGGER.info("WebTextureCatalog: loaded {} packs ({} textures total) from {}",
                packs.size(),
                packs.stream().mapToInt(p -> p.textures().size()).sum(),
                CATALOG_URL);
        } catch (Exception ex) {
            SlothyHubMod.LOGGER.warn("WebTextureCatalog: failed to load {}: {}", CATALOG_URL, ex.getMessage());
        }
    }
}
