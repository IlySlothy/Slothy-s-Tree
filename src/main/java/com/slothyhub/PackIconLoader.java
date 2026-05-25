package com.slothyhub;

import com.slothyhub.cit.TextureAnimationUtil;
import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.Identifiers;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2960;
import net.minecraft.class_310;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Loads pack card icons from showcase URLs or the pack zip itself. */
public final class PackIconLoader {

    private PackIconLoader() {}

    public static void loadIcon(Pack pack, String serverUrl, IconCallback cb) {
        String showcase = pack.getShowcaseUrl(serverUrl);
        if (!showcase.isBlank()) {
            tryLoadUrl(showcase, pack, cb, true);
            return;
        }
        loadFromPackArchive(pack, cb);
    }

    private static void tryLoadUrl(String url, Pack pack, IconCallback cb, boolean allowArchiveFallback) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
            if (conn.getResponseCode() != 200) {
                if (allowArchiveFallback) loadFromPackArchive(pack, cb);
                else cb.onFailed(pack.getId());
                return;
            }
            try (InputStream in = conn.getInputStream()) {
                registerImage(pack.getId(), in.readAllBytes(), null, cb);
            }
        } catch (Exception e) {
            if (allowArchiveFallback) loadFromPackArchive(pack, cb);
            else cb.onFailed(pack.getId());
        }
    }

    private static void loadFromPackArchive(Pack pack, IconCallback cb) {
        String packUrl = pack.getPackUrl();
        if (packUrl != null && !packUrl.isBlank()) {
            String lower = packUrl.toLowerCase(Locale.ROOT);
            if (lower.startsWith("file:")) {
                try {
                    Path path = Path.of(URI.create(packUrl));
                    if (Files.isDirectory(path)) loadFromFolder(path, pack.getId(), cb);
                    else if (Files.isRegularFile(path)) loadFromZipPath(path, pack.getId(), cb);
                    else cb.onFailed(pack.getId());
                } catch (Exception e) {
                    cb.onFailed(pack.getId());
                }
                return;
            }
            if (lower.startsWith("http")) {
                tryLoadFromZip(packUrl, pack.getId(), cb);
                return;
            }
        }
        Path rp = class_310.method_1551().field_1697.toPath()
            .resolve("resourcepacks").resolve(pack.getPackFilename());
        if (Files.isDirectory(rp)) loadFromFolder(rp, pack.getId(), cb);
        else if (Files.isRegularFile(rp)) loadFromZipPath(rp, pack.getId(), cb);
        else cb.onFailed(pack.getId());
    }

    private static void loadFromZipPath(Path zipPath, String packId, IconCallback cb) {
        try (InputStream in = Files.newInputStream(zipPath); ZipInputStream zin = new ZipInputStream(in)) {
            extractBestPng(zin, packId, cb);
        } catch (Exception e) {
            cb.onFailed(packId);
        }
    }

    private static void loadFromFolder(Path folder, String packId, IconCallback cb) {
        try {
            Path packPng = folder.resolve("pack.png");
            if (Files.isRegularFile(packPng)) {
                registerImage(packId, Files.readAllBytes(packPng), null, cb);
                return;
            }
            PngCandidate best = findFallbackPngInFolder(folder);
            if (best != null) registerImage(packId, best.png(), best.mcmeta(), cb);
            else cb.onFailed(packId);
        } catch (Exception e) {
            cb.onFailed(packId);
        }
    }

    private static PngCandidate findFallbackPngInFolder(Path root) throws Exception {
        PngCandidate cit = null;
        PngCandidate best = null;
        try (var walk = Files.walk(root)) {
            for (Path p : walk.filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList()) {
                String name = root.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                byte[] png = Files.readAllBytes(p);
                byte[] mcmeta = readSiblingMcmeta(p);
                PngCandidate cand = new PngCandidate(png, mcmeta, name);
                if (isCitPreview(name)) {
                    if (cit == null || citScore(name) > citScore(cit.name())) cit = cand.withName(name);
                } else if (best == null && name.contains("textures/") && !name.contains("particle")) {
                    best = cand;
                }
            }
        }
        return cit != null ? cit : best;
    }

    private static void tryLoadFromZip(String url, String packId, IconCallback cb) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
            if (conn.getResponseCode() != 200) { cb.onFailed(packId); return; }
            try (InputStream in = conn.getInputStream(); ZipInputStream zin = new ZipInputStream(in)) {
                extractBestPng(zin, packId, cb);
            }
        } catch (Exception ex) {
            cb.onFailed(packId);
        }
    }

    private static boolean isPackIcon(String name) {
        return name.equals("pack.png") || name.endsWith("/pack.png");
    }

    private static boolean isCitPreview(String name) {
        if (!name.endsWith(".png") || name.contains(".mcmeta")) return false;
        if (!name.contains("/cit/") && !name.contains("optifine/cit")) return false;
        return name.contains("sword") || name.contains("warden") || name.contains("perfect")
            || name.contains("pro_sword") || name.contains("mythic") || name.contains("noob")
            || name.contains("good_sword") || name.contains("summer") || name.contains("fallen");
    }

    private static int citScore(String name) {
        int score = 0;
        if (name.contains("netherite")) score += 8;
        if (name.contains("warden")) score += 6;
        if (name.contains("perfect")) score += 5;
        if (name.contains("pro_sword") || name.contains("/pro/")) score += 4;
        if (name.contains("sword")) score += 3;
        if (name.contains("optifine/cit")) score += 2;
        return score;
    }

    private static void extractBestPng(ZipInputStream zin, String packId, IconCallback cb) throws Exception {
        byte[] packPng = null;
        PngCandidate bestCit = null;
        PngCandidate bestTex = null;
        java.util.Map<String, byte[]> mcmetaByPng = new java.util.HashMap<>();

        ZipEntry e;
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            String name = e.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (name.endsWith(".png.mcmeta")) {
                String pngKey = name.substring(0, name.length() - ".mcmeta".length());
                mcmetaByPng.put(pngKey, zin.readAllBytes());
                zin.closeEntry();
                continue;
            }
            if (!name.endsWith(".png")) continue;

            byte[] png = zin.readAllBytes();
            if (isPackIcon(name)) {
                packPng = png;
                break;
            }
            PngCandidate cand = new PngCandidate(png, mcmetaByPng.get(name), name);
            if (isCitPreview(name)) {
                if (bestCit == null || citScore(name) > citScore(bestCit.name())) bestCit = cand;
            } else if (bestTex == null && name.contains("textures/") && !name.contains("particle")) {
                bestTex = cand;
            }
            zin.closeEntry();
        }

        if (packPng != null) {
            registerImage(packId, packPng, null, cb);
        } else if (bestCit != null) {
            registerImage(packId, bestCit.png(), bestCit.mcmeta(), cb);
        } else if (bestTex != null) {
            registerImage(packId, bestTex.png(), bestTex.mcmeta(), cb);
        } else {
            cb.onFailed(packId);
        }
    }

    private static byte[] readSiblingMcmeta(Path png) {
        Path meta = Path.of(png.toString() + ".mcmeta");
        if (!Files.isRegularFile(meta)) return null;
        try {
            return Files.readAllBytes(meta);
        } catch (Exception e) {
            return null;
        }
    }

    private static void registerImage(String packId, byte[] png, byte[] mcmeta, IconCallback cb) {
        class_310 mc = class_310.method_1551();
        mc.execute(() -> {
            try {
                class_1011 img = class_1011.method_4309(new ByteArrayInputStream(png));
                img = TextureAnimationUtil.firstFrameFromImage(img, mcmeta);
                if (img == null) { cb.onFailed(packId); return; }
                int texW = img.method_4307();
                int texH = img.method_4323();
                String safe = packId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                class_1043 tex = DrawHelper.createNativeTexture("slothyhub_packicon_" + safe, img);
                if (tex == null) { cb.onFailed(packId); return; }
                class_2960 id = Identifiers.of("slothyhub", "packicon/" + safe);
                DrawHelper.registerDynamicTexture(id, tex, img);
                cb.onLoaded(packId, id, texW, texH);
            } catch (Exception e) {
                cb.onFailed(packId);
            }
        });
    }

    private record PngCandidate(byte[] png, byte[] mcmeta, String name) {
        PngCandidate withName(String n) { return new PngCandidate(png, mcmeta, n); }
    }

    public interface IconCallback {
        void onLoaded(String packId, class_2960 id, int w, int h);
        void onFailed(String packId);
    }
}
