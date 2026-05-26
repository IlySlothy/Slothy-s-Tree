package com.slothyhub.builder;

import com.slothyhub.SlothyHubMod;
import net.minecraft.class_310;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Tiny on-disk cache for SlothyHub web assets.
 *
 * <p>Stores files under {@code .minecraft/cache/slothyhub-textures/<sha256>.bin}.
 * Successful fetches are remembered in-memory for the lifetime of the JVM so
 * repeat lookups never even {@code stat()} the disk twice.</p>
 *
 * <p>Cache entries are content-addressed (sha256 of the URL), which means a
 * URL with a different query string or path gets its own slot, but the same
 * URL re-fetches the same file. Existing cached files are returned as-is and
 * never re-validated against the server - they're meant for static GitHub
 * Pages assets, so a "delete and refetch" only happens if the user wipes the
 * cache dir manually.</p>
 */
public final class WebTextureCache {

    private static final ConcurrentHashMap<String, byte[]> MEMORY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Future<byte[]>> INFLIGHT = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "SlothyHub-WebTextureCache");
        t.setDaemon(true);
        return t;
    });

    private WebTextureCache() {}

    public static Path cacheDir() {
        Path dir = class_310.method_1551().field_1697.toPath()
            .resolve("cache").resolve("slothyhub-textures");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    /** Quick lookup - returns null if not cached in memory or on disk. */
    public static byte[] peek(String url) {
        byte[] mem = MEMORY.get(url);
        if (mem != null) return mem;
        Path file = pathFor(url);
        if (Files.exists(file)) {
            try {
                byte[] bytes = Files.readAllBytes(file);
                MEMORY.put(url, bytes);
                return bytes;
            } catch (IOException ignored) {}
        }
        return null;
    }

    /**
     * Synchronous fetch with disk + memory caching. Returns null on transport
     * error (network down, 404, etc.) - callers should treat null as "missing".
     */
    public static byte[] fetchBlocking(String url) {
        byte[] cached = peek(url);
        if (cached != null) return cached;
        try {
            byte[] bytes = downloadTo(url);
            if (bytes != null) MEMORY.put(url, bytes);
            return bytes;
        } catch (IOException e) {
            SlothyHubMod.LOGGER.debug("WebTextureCache: fetch failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Async fetch - returns a future that resolves to the bytes, or null on
     * error. Multiple callers for the same URL share a single in-flight task.
     */
    public static Future<byte[]> fetchAsync(String url) {
        byte[] cached = peek(url);
        if (cached != null) return java.util.concurrent.CompletableFuture.completedFuture(cached);
        return INFLIGHT.computeIfAbsent(url, u -> POOL.submit(() -> {
            try {
                byte[] bytes = downloadTo(u);
                if (bytes != null) MEMORY.put(u, bytes);
                return bytes;
            } catch (IOException e) {
                SlothyHubMod.LOGGER.debug("WebTextureCache: async fetch failed for {}: {}", u, e.getMessage());
                return null;
            } finally {
                INFLIGHT.remove(u);
            }
        }));
    }

    private static Path pathFor(String url) {
        return cacheDir().resolve(sha256(url) + ".bin");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static byte[] downloadTo(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "SlothyHub/1.0.3 (texture-picker)");
        conn.setRequestProperty("Accept", "*/*");
        int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_NOT_FOUND) return null;
        if (code >= 400) throw new IOException("HTTP " + code + " for " + url);
        byte[] bytes;
        try (var in = conn.getInputStream()) {
            bytes = in.readAllBytes();
        }
        // Write to disk so subsequent JVM starts skip the network round-trip.
        Path file = pathFor(url);
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Cache is best-effort; in-memory copy is still useful for this session.
        }
        return bytes;
    }
}
