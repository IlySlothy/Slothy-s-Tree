package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
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
                registerImage(pack.getId(), in.readAllBytes(), cb);
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
                registerImage(packId, Files.readAllBytes(packPng), cb);
                return;
            }
            byte[] best = findFallbackPngInFolder(folder);
            if (best != null) registerImage(packId, best, cb);
            else cb.onFailed(packId);
        } catch (Exception e) {
            cb.onFailed(packId);
        }
    }

    private static byte[] findFallbackPngInFolder(Path root) throws Exception {
        byte[] best = null;
        try (var walk = Files.walk(root)) {
            for (Path p : walk.filter(f -> f.toString().toLowerCase(Locale.ROOT).endsWith(".png")).toList()) {
                String name = root.relativize(p).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (name.contains("/cit/") && (name.contains("sword") || name.contains("warden") || name.contains("perfect"))) {
                    return Files.readAllBytes(p);
                }
                if (best == null && name.contains("textures/") && !name.contains("particle"))
                    best = Files.readAllBytes(p);
            }
        }
        return best;
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

    private static void extractBestPng(ZipInputStream zin, String packId, IconCallback cb) throws Exception {
        ZipEntry e;
        byte[] packPng = null;
        byte[] bestData = null;
        while ((e = zin.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            String name = e.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
            if (!name.endsWith(".png")) continue;
            if (isPackIcon(name)) {
                packPng = zin.readAllBytes();
                break;
            }
            if (name.contains("/cit/") && (name.contains("sword") || name.contains("warden") || name.contains("perfect"))) {
                registerImage(packId, zin.readAllBytes(), cb);
                return;
            }
            if (bestData == null && name.contains("textures/") && !name.contains("particle"))
                bestData = zin.readAllBytes();
            zin.closeEntry();
        }
        if (packPng != null) registerImage(packId, packPng, cb);
        else if (bestData != null) registerImage(packId, bestData, cb);
        else cb.onFailed(packId);
    }

    private static void registerImage(String packId, byte[] png, IconCallback cb) {
        class_310 mc = class_310.method_1551();
        mc.execute(() -> {
            try {
                class_1011 img = class_1011.method_4309(new ByteArrayInputStream(png));
                String safe = packId.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                class_1043 tex = DrawHelper.createNativeTexture("slothyhub_packicon_" + safe, img);
                if (tex == null) { cb.onFailed(packId); return; }
                class_2960 id = class_2960.method_60655("slothyhub", "packicon/" + safe);
                mc.method_1531().method_4616(id, tex);
                cb.onLoaded(packId, id, img.method_4307(), img.method_4323());
            } catch (Exception e) {
                cb.onFailed(packId);
            }
        });
    }

    public interface IconCallback {
        void onLoaded(String packId, class_2960 id, int w, int h);
        void onFailed(String packId);
    }
}
