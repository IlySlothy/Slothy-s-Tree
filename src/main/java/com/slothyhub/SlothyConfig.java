package com.slothyhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;

public class SlothyConfig {

    /** Free GitHub Pages catalog — change in settings when you move to a paid domain. */
    public static final String DEFAULT_SERVER_URL = "https://ilyslothy.github.io/Slothy-s-Tree";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("slothyhub.json");

    private static String urlOverride = "";
    private static String serverUrl = "";
    private static String voterId = "";
    private static boolean batchedReload = true;
    private static boolean prefetchThumbnails = true;
    private static boolean animationsEnabled = true;
    private static boolean confirmBeforeRemove = false;
    private static boolean sortByStars = true;
    private static boolean verifyDownloads = true;
    private static boolean backgroundEffects = true;
    private static boolean lowResThumb = false;
    private static int maxConcurrentDownloads = 3;
    private static boolean lazyLoadCards = false;
    private static boolean reducedMotion = false;
    private static int cacheExpiry = 30;
    private static String killEffect = "totem";
    private static boolean citEnabled = true;

    public static String getServerUrl() {
        return urlOverride != null && !urlOverride.isBlank() ? urlOverride : serverUrl;
    }

    public static void setServerUrl(String url) {
        serverUrl = url.trim().replaceAll("/+$", "");
        save();
    }

    public static String getUrlOverride() {
        return urlOverride;
    }

    public static void setUrlOverride(String s) {
        urlOverride = s == null ? "" : s.trim().replaceAll("/+$", "");
        save();
    }

    public static boolean isConfigured() {
        return !getServerUrl().isBlank();
    }

    public static String getVoterId() {
        if (voterId == null || voterId.isBlank()) {
            voterId = UUID.randomUUID().toString();
            save();
        }
        return voterId;
    }

    public static void regenerateVoterId() {
        voterId = UUID.randomUUID().toString();
        save();
    }

    public static boolean isBatchedReload() { return batchedReload; }
    public static boolean isPrefetchThumbnails() { return prefetchThumbnails; }
    public static boolean isAnimationsEnabled() { return animationsEnabled; }
    public static boolean isConfirmBeforeRemove() { return confirmBeforeRemove; }
    public static boolean isSortByStars() { return sortByStars; }
    public static boolean isVerifyDownloads() { return verifyDownloads; }
    public static boolean isBackgroundEffects() { return backgroundEffects; }
    public static String getKillEffect() { return killEffect; }
    public static boolean isKillEffectEnabled() { return !"none".equals(killEffect); }
    public static boolean isLowResThumb() { return lowResThumb; }
    public static int getMaxConcurrentDownloads() { return maxConcurrentDownloads; }
    public static boolean isLazyLoadCards() { return lazyLoadCards; }
    public static boolean isReducedMotion() { return reducedMotion; }
    public static int getCacheExpiry() { return cacheExpiry; }
    public static boolean isCitEnabled() { return citEnabled; }

    public static void setBatchedReload(boolean v) { batchedReload = v; save(); }
    public static void setPrefetchThumbnails(boolean v) { prefetchThumbnails = v; save(); }
    public static void setAnimationsEnabled(boolean v) { animationsEnabled = v; save(); }
    public static void setConfirmBeforeRemove(boolean v) { confirmBeforeRemove = v; save(); }
    public static void setSortByStars(boolean v) { sortByStars = v; save(); }
    public static void setVerifyDownloads(boolean v) { verifyDownloads = v; save(); }
    public static void setBackgroundEffects(boolean v) { backgroundEffects = v; save(); }
    public static void setLowResThumb(boolean v) { lowResThumb = v; save(); }
    public static void setMaxConcurrentDownloads(int v) { maxConcurrentDownloads = Math.max(1, Math.min(8, v)); save(); }
    public static void setLazyLoadCards(boolean v) { lazyLoadCards = v; save(); }
    public static void setReducedMotion(boolean v) { reducedMotion = v; save(); }
    public static void setCacheExpiry(int v) { cacheExpiry = Math.max(5, Math.min(120, v)); save(); }
    public static void setCitEnabled(boolean v) { citEnabled = v; save(); }

    public static void setKillEffect(String v) {
        if (!"none".equals(v) && !"totem".equals(v) && !"anvil".equals(v) && !"thunder".equals(v)) {
            killEffect = "totem";
        } else {
            killEffect = v;
        }
        save();
    }

    /** Uses the default GitHub Pages catalog when no server URL is configured yet. */
    public static void tryAutoDiscover() {
        if (getServerUrl().isBlank()) {
            serverUrl = DEFAULT_SERVER_URL;
            save();
            SlothyHubMod.LOGGER.info("SlothyHub: using default pack catalog at {}", DEFAULT_SERVER_URL);
        }
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            getVoterId();
            save();
            return;
        }
        try {
            String raw = Files.readString(CONFIG_PATH);
            JsonObject obj = GSON.fromJson(raw, JsonObject.class);
            if (obj != null) {
                if (obj.has("serverUrl"))              serverUrl              = obj.get("serverUrl").getAsString();
                if (obj.has("urlOverride"))            urlOverride            = obj.get("urlOverride").getAsString();
                if (obj.has("voterId"))                voterId                = obj.get("voterId").getAsString();
                if (obj.has("batchedReload"))          batchedReload          = obj.get("batchedReload").getAsBoolean();
                if (obj.has("prefetchThumbnails"))     prefetchThumbnails     = obj.get("prefetchThumbnails").getAsBoolean();
                if (obj.has("animationsEnabled"))      animationsEnabled      = obj.get("animationsEnabled").getAsBoolean();
                if (obj.has("confirmBeforeRemove"))    confirmBeforeRemove    = obj.get("confirmBeforeRemove").getAsBoolean();
                if (obj.has("sortByStars"))            sortByStars            = obj.get("sortByStars").getAsBoolean();
                if (obj.has("verifyDownloads"))        verifyDownloads        = obj.get("verifyDownloads").getAsBoolean();
                if (obj.has("backgroundEffects"))      backgroundEffects      = obj.get("backgroundEffects").getAsBoolean();
                if (obj.has("killEffect"))             killEffect             = obj.get("killEffect").getAsString();
                if (obj.has("lowResThumb"))            lowResThumb            = obj.get("lowResThumb").getAsBoolean();
                if (obj.has("maxConcurrentDownloads")) maxConcurrentDownloads = Math.max(1, Math.min(8, obj.get("maxConcurrentDownloads").getAsInt()));
                if (obj.has("lazyLoadCards"))          lazyLoadCards          = obj.get("lazyLoadCards").getAsBoolean();
                if (obj.has("reducedMotion"))          reducedMotion          = obj.get("reducedMotion").getAsBoolean();
                if (obj.has("cacheExpiry"))            cacheExpiry            = Math.max(5, Math.min(120, obj.get("cacheExpiry").getAsInt()));
                if (obj.has("citEnabled"))             citEnabled             = obj.get("citEnabled").getAsBoolean();
            }
            if (voterId == null || voterId.isBlank()) {
                voterId = UUID.randomUUID().toString();
                save();
            }
        } catch (IOException e) {
            SlothyHubMod.LOGGER.error("Failed to load SlothyHub config", e);
        }
    }

    public static void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("serverUrl",              serverUrl == null ? "" : serverUrl);
        obj.addProperty("urlOverride",            urlOverride == null ? "" : urlOverride);
        obj.addProperty("voterId",                voterId == null ? "" : voterId);
        obj.addProperty("batchedReload",          batchedReload);
        obj.addProperty("prefetchThumbnails",     prefetchThumbnails);
        obj.addProperty("animationsEnabled",      animationsEnabled);
        obj.addProperty("confirmBeforeRemove",    confirmBeforeRemove);
        obj.addProperty("sortByStars",            sortByStars);
        obj.addProperty("verifyDownloads",        verifyDownloads);
        obj.addProperty("backgroundEffects",      backgroundEffects);
        obj.addProperty("killEffect",             killEffect);
        obj.addProperty("lowResThumb",            lowResThumb);
        obj.addProperty("maxConcurrentDownloads", maxConcurrentDownloads);
        obj.addProperty("lazyLoadCards",          lazyLoadCards);
        obj.addProperty("reducedMotion",          reducedMotion);
        obj.addProperty("cacheExpiry",            cacheExpiry);
        obj.addProperty("citEnabled",             citEnabled);
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(obj));
        } catch (IOException e) {
            SlothyHubMod.LOGGER.error("Failed to save SlothyHub config", e);
        }
    }
}
