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

/** Loads pack card icons from pack.png, showcase URLs, or CIT preview PNGs. */
public final class PackIconLoader {

    private PackIconLoader() {}

    public static void loadIcon(Pack pack, String serverUrl, IconCallback cb) {
        SlothyHubMod.LOGGER.info(
            "PackIcon: loadIcon start id='{}' file='{}' url='{}'",
            pack.getId(), pack.getPackFilename(), pack.getPackUrl());
        loadFromPackArchive(pack, serverUrl, cb);
    }

    private static void loadFromPackArchive(Pack pack, String serverUrl, IconCallback cb) {
        String packUrl = pack.getPackUrl();
        if (packUrl != null && !packUrl.isBlank()) {
            String lower = packUrl.toLowerCase(Locale.ROOT);
            if (lower.startsWith("file:")) {
                try {
                    Path path = Path.of(URI.create(packUrl));
                    if (Files.isDirectory(path)) {
                        loadFromFolder(path, pack.getId(), cb, pack, serverUrl);
                        return;
                    }
                    if (Files.isRegularFile(path)) {
                        loadFromZipPath(path, pack.getId(), cb, pack, serverUrl);
                        return;
                    }
                } catch (Exception ignored) {}
                tryShowcaseThenFail(pack, serverUrl, cb);
                return;
            }
            if (lower.startsWith("http")) {
                tryLoadFromZip(packUrl, pack.getId(), cb, pack, serverUrl);
                return;
            }
        }
        Path rp = class_310.method_1551().field_1697.toPath()
            .resolve("resourcepacks").resolve(pack.getPackFilename());
        if (Files.isDirectory(rp)) loadFromFolder(rp, pack.getId(), cb, pack, serverUrl);
        else if (Files.isRegularFile(rp)) loadFromZipPath(rp, pack.getId(), cb, pack, serverUrl);
        else tryShowcaseThenFail(pack, serverUrl, cb);
    }

    private static void tryShowcaseThenFail(Pack pack, String serverUrl, IconCallback cb) {
        String showcase = pack.getShowcaseUrl(serverUrl);
        if (!showcase.isBlank()) {
            tryLoadUrl(showcase, pack.getId(), cb);
            return;
        }
        cb.onFailed(pack.getId());
    }

    private static void tryLoadUrl(String url, String packId, IconCallback cb) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
            if (conn.getResponseCode() != 200) {
                cb.onFailed(packId);
                return;
            }
            byte[] data;
            try (InputStream in = conn.getInputStream()) {
                data = in.readAllBytes();
            }
            if (!isValidPng(data)) {
                SlothyHubMod.LOGGER.debug("PackIcon: showcase URL for '{}' is not PNG ({} bytes)",
                    packId, data.length);
                cb.onFailed(packId);
                return;
            }
            SlothyHubMod.LOGGER.info("PackIcon: '{}' loaded from showcase URL", packId);
            registerImage(packId, data, null, cb);
        } catch (Exception e) {
            cb.onFailed(packId);
        }
    }

    private static void loadFromZipPath(Path zipPath, String packId, IconCallback cb, Pack pack, String serverUrl) {
        try (InputStream in = Files.newInputStream(zipPath); ZipInputStream zin = new ZipInputStream(in)) {
            extractBestPng(zin, packId, cb, pack, serverUrl);
        } catch (Exception e) {
            tryShowcaseThenFail(pack, serverUrl, cb);
        }
    }

    private static void loadFromFolder(Path folder, String packId, IconCallback cb, Pack pack, String serverUrl) {
        try {
            Path packPng = folder.resolve("pack.png");
            if (Files.isRegularFile(packPng)) {
                byte[] png = Files.readAllBytes(packPng);
                if (isValidPng(png)) {
                    SlothyHubMod.LOGGER.info("PackIcon: '{}' loaded from pack.png (folder)", packId);
                    registerImage(packId, png, null, cb);
                    return;
                }
                SlothyHubMod.LOGGER.info("PackIcon: '{}' has invalid pack.png in folder '{}' — falling back to CIT preview",
                    packId, folder);
            }
            PngCandidate cit = findCitPreviewInFolder(folder);
            if (cit != null) {
                SlothyHubMod.LOGGER.info("PackIcon: '{}' loaded from CIT preview {}", packId, cit.name());
                registerImage(packId, cit.png(), cit.mcmeta(), cb);
                return;
            }
            PngCandidate anyCit = findAnyCitPngInFolder(folder);
            if (anyCit != null) {
                SlothyHubMod.LOGGER.info("PackIcon: '{}' loaded from generic CIT PNG {}", packId, anyCit.name());
                registerImage(packId, anyCit.png(), anyCit.mcmeta(), cb);
                return;
            }
            tryShowcaseThenFail(pack, serverUrl, cb);
        } catch (Exception e) {
            tryShowcaseThenFail(pack, serverUrl, cb);
        }
    }

    private static PngCandidate findAnyCitPngInFolder(Path root) throws Exception {
        try (var walk = Files.walk(root)) {
            for (Path p : walk.filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList()) {
                String name = root.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (!name.contains("/cit/") && !name.contains("optifine/cit")) continue;
                byte[] png = Files.readAllBytes(p);
                if (!isValidPng(png)) continue;
                byte[] mcmeta = readSiblingMcmeta(p);
                return new PngCandidate(png, mcmeta, name);
            }
        }
        return null;
    }

    private static PngCandidate findCitPreviewInFolder(Path root) throws Exception {
        PngCandidate cit = null;
        try (var walk = Files.walk(root)) {
            for (Path p : walk.filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList()) {
                String name = root.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                byte[] png = Files.readAllBytes(p);
                if (!isValidPng(png)) continue;
                if (!isCitPreview(name)) continue;
                byte[] mcmeta = readSiblingMcmeta(p);
                PngCandidate cand = new PngCandidate(png, mcmeta, name);
                if (cit == null || citScore(name) > citScore(cit.name())) cit = cand;
            }
        }
        return cit;
    }

    private static void tryLoadFromZip(String url, String packId, IconCallback cb, Pack pack, String serverUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
            if (conn.getResponseCode() != 200) {
                tryShowcaseThenFail(pack, serverUrl, cb);
                return;
            }
            try (InputStream in = conn.getInputStream(); ZipInputStream zin = new ZipInputStream(in)) {
                extractBestPng(zin, packId, cb, pack, serverUrl);
            }
        } catch (Exception ex) {
            tryShowcaseThenFail(pack, serverUrl, cb);
        }
    }

    /** Only the pack root icon — not nested pack.png files inside asset folders. */
    private static boolean isRootPackIcon(String name) {
        return "pack.png".equals(name);
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

    private static void extractBestPng(ZipInputStream zin, String packId, IconCallback cb,
                                         Pack pack, String serverUrl) throws Exception {
        byte[] packPng = null;
        PngCandidate bestCit = null;
        PngCandidate anyCit = null;
        boolean sawInvalidPackPng = false;
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
            if (!isValidPng(png)) {
                if (isRootPackIcon(name)) {
                    sawInvalidPackPng = true;
                    SlothyHubMod.LOGGER.info(
                        "PackIcon: '{}' pack.png in zip entry '{}' has bad PNG signature — will use CIT fallback",
                        packId, name);
                }
                zin.closeEntry();
                continue;
            }
            if (isRootPackIcon(name)) {
                packPng = png;
                // Do NOT break: keep scanning so we still collect a CIT fallback in case
                // we later decide to ignore packPng (e.g. extremely tiny / corrupt image).
                zin.closeEntry();
                continue;
            }
            if (name.contains("/cit/") || name.contains("optifine/cit")) {
                PngCandidate cand = new PngCandidate(png, mcmetaByPng.get(name), name);
                if (anyCit == null) anyCit = cand;
                if (isCitPreview(name)) {
                    if (bestCit == null || citScore(name) > citScore(bestCit.name())) bestCit = cand;
                }
            }
            zin.closeEntry();
        }

        if (packPng != null) {
            SlothyHubMod.LOGGER.info("PackIcon: '{}' loaded from pack.png (zip)", packId);
            registerImage(packId, packPng, null, cb);
        } else if (bestCit != null) {
            SlothyHubMod.LOGGER.info("PackIcon: '{}' loaded from CIT preview {}{}",
                packId, bestCit.name(), sawInvalidPackPng ? " (pack.png was invalid)" : "");
            registerImage(packId, bestCit.png(), bestCit.mcmeta(), cb);
        } else if (anyCit != null) {
            SlothyHubMod.LOGGER.info("PackIcon: '{}' loaded from generic CIT PNG {}{}",
                packId, anyCit.name(), sawInvalidPackPng ? " (pack.png was invalid)" : "");
            registerImage(packId, anyCit.png(), anyCit.mcmeta(), cb);
        } else {
            SlothyHubMod.LOGGER.info("PackIcon: '{}' has no usable icon{} — trying showcase URL",
                packId, sawInvalidPackPng ? " (pack.png invalid, no CIT PNGs)" : "");
            tryShowcaseThenFail(pack, serverUrl, cb);
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
        if (!isPng(png)) {
            SlothyHubMod.LOGGER.debug("PackIcon: invalid PNG for '{}'", packId);
            cb.onFailed(packId);
            return;
        }
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

    private record PngCandidate(byte[] png, byte[] mcmeta, String name) {}

    private static boolean isPng(byte[] data) {
        return isValidPng(data);
    }

    /** Validates PNG magic bytes before NativeImage.read. */
    public static boolean isValidPng(byte[] data) {
        return data != null && data.length >= 8
            && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47
            && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A;
    }

    public interface IconCallback {
        void onLoaded(String packId, class_2960 id, int w, int h);
        void onFailed(String packId);
    }
}
