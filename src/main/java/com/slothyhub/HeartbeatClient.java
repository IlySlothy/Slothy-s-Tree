package com.slothyhub;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pings the SlothyHub Discord bot's Cloudflare Worker every ~14 minutes so the
 * {@code /modstats} command can report how many clients are currently online.
 *
 * <p>The worker stores each {@code clientId} in KV with a 15-minute expiry, so a
 * 14-minute interval keeps each player active without ever expiring mid-game.
 *
 * <p>Telemetry: this only sends the per-install voter UUID. No usernames, IPs,
 * pack lists, or any other identifying info are transmitted. The endpoint is
 * configured via {@code slothyhub.json -> heartbeatUrl}; if blank, the client
 * is a no-op.
 */
public final class HeartbeatClient {

    /** 14 minutes — the worker expires entries at 15 minutes (HEARTBEAT_TTL_SEC). */
    private static final long INTERVAL_SECONDS = 14L * 60L;
    /** Short delay before the first ping so we don't block startup. */
    private static final long INITIAL_DELAY_SECONDS = 30L;
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 8_000;
    private static final String USER_AGENT = "SlothyHub-Mod/1.0 (heartbeat)";

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static ScheduledExecutorService scheduler;

    private HeartbeatClient() {}

    /** Idempotent — safe to call multiple times. Silently no-ops if no worker URL is configured. */
    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "slothyhub-heartbeat");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
            HeartbeatClient::ping,
            INITIAL_DELAY_SECONDS,
            INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        SlothyHubMod.LOGGER.info("HeartbeatClient: scheduled (every {} min)", INTERVAL_SECONDS / 60);
    }

    public static void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        STARTED.set(false);
    }

    private static void ping() {
        String base = SlothyConfig.getWorkerBaseUrl();
        if (base == null || base.isBlank()) return;

        String url = base + "/v1/heartbeat";
        String clientId = SlothyConfig.getVoterId();
        String body = "{\"clientId\":\"" + escapeJson(clientId) + "\"}";

        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                SlothyHubMod.LOGGER.debug("HeartbeatClient: ok ({})", code);
            } else {
                SlothyHubMod.LOGGER.debug("HeartbeatClient: HTTP {} from {}", code, url);
            }
        } catch (Exception e) {
            // Telemetry must never crash the mod. Debug-level on purpose.
            SlothyHubMod.LOGGER.debug("HeartbeatClient: failed: {}", e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
            else b.append(c);
        }
        return b.toString();
    }
}
