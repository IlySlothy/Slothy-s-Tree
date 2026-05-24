package com.slothyhub;

import com.github.junrar.Junrar;
import com.slothyhub.compat.VersionCompat;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraft.class_310;
import net.minecraft.class_3283;

public class PackDownloader {

    private static final String MARKER_FILENAME = ".slothyhub-active";

    public static Set<String> getActivePackIds() {
        return new LinkedHashSet<>(readMarker(class_310.method_1551()).keySet());
    }

    public static String getActivePackFolder(String packId) {
        if (packId == null || packId.isEmpty()) return null;
        String folder = readMarker(class_310.method_1551()).get(packId);
        return folder != null && !folder.isEmpty() ? folder : null;
    }

    public static Path resolveResourcePackPath(String folderName) {
        if (folderName == null || folderName.isEmpty()) return null;
        class_310 mc = class_310.method_1551();
        Path base = mc.field_1697.toPath().resolve("resourcepacks").normalize();
        Path p = base.resolve(folderName).normalize();
        return p.startsWith(base) ? p : null;
    }

    public static String applyBuiltPack(byte[] zipData, String packName) throws IOException {
        class_310 mc = class_310.method_1551();
        Path resourcePacksDir = mc.field_1697.toPath().resolve("resourcepacks");
        Files.createDirectories(resourcePacksDir);
        String safeBase = packName.replaceAll("[^a-zA-Z0-9_\\-. ]", "_").trim();
        if (safeBase.isEmpty()) safeBase = "CustomPack";
        Path extractTarget = resourcePacksDir.resolve(safeBase);
        if (Files.exists(extractTarget)) deleteRecursive(extractTarget);
        Files.createDirectories(extractTarget);
        Path tmp = resourcePacksDir.resolve(".slothyhub-staging");
        Files.createDirectories(tmp);
        Path tmpZip = tmp.resolve(safeBase + "-" + System.currentTimeMillis() + ".zip");
        try {
            Files.write(tmpZip, zipData);
            extractZip(tmpZip, extractTarget);
        } finally {
            try { Files.deleteIfExists(tmpZip); } catch (IOException ignored) {}
        }
        flattenSingleRoot(extractTarget);
        String folderName = extractTarget.getFileName().toString();
        String builderId = "builder-" + folderName;
        mc.execute(() -> {
            try {
                applyResourcePack(mc, folderName);
                addToMarker(mc, builderId, folderName);
                SlothyHubMod.LOGGER.info("Builder pack '{}' applied and activated", folderName);
            } catch (Exception e) {
                SlothyHubMod.LOGGER.error("Failed to apply builder pack '{}'", folderName, e);
            }
        });
        return folderName;
    }

    public static void downloadAndApply(Pack pack, String serverUrl, ProgressCallback cb) {
        if (pack.isLocal()) { applyLocalPack(pack, cb); return; }
        class_310 mc = class_310.method_1551();
        Path resourcePacksDir = mc.field_1697.toPath().resolve("resourcepacks");
        try {
            Files.createDirectories(resourcePacksDir);
            String safeBase = pack.getName().replaceAll("[^a-zA-Z0-9_\\-. ]", "_").trim();
            if (safeBase.isEmpty()) safeBase = "pack-" + pack.getId();
            String ext = pack.isZip() ? ".zip" : ".rar";
            Path stagingDir = resourcePacksDir.resolve(".slothyhub-staging");
            Files.createDirectories(stagingDir);
            Path archiveTmp = stagingDir.resolve(safeBase + "-" + System.currentTimeMillis() + ext);
            String downloadUrl = resolveDownloadUrl(pack, serverUrl);
            SlothyHubMod.LOGGER.info("Downloading pack from {}", downloadUrl);
            HttpURLConnection conn = openWithRedirects(downloadUrl, 5);
            int status = conn.getResponseCode();
            if (status != 200) throw new IOException("Server returned HTTP " + status);
            String expectedSha = conn.getHeaderField("X-SlothyHub-SHA256");
            if (expectedSha == null || expectedSha.isBlank()) expectedSha = pack.getSha256();
            long total = conn.getContentLengthLong();
            MessageDigest digest = newSha256();
            try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(archiveTmp)) {
                byte[] buf = new byte[65536];
                long downloaded = 0L;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (digest != null) digest.update(buf, 0, n);
                    downloaded += n;
                    if (total > 0L) {
                        float frac = (float) downloaded / total;
                        mc.execute(() -> cb.onProgress(frac));
                    }
                }
            }
            if (SlothyConfig.isVerifyDownloads() && digest != null && expectedSha != null && !expectedSha.isBlank()) {
                String actual = toHex(digest.digest());
                if (!actual.equalsIgnoreCase(expectedSha.trim())) {
                    try { Files.deleteIfExists(archiveTmp); } catch (IOException ignored) {}
                    throw new IOException("Download checksum mismatch — file may be corrupted.");
                }
            }
            mc.execute(cb::onApplying);
            Path extractTarget = resourcePacksDir.resolve(safeBase);
            if (Files.exists(extractTarget)) deleteRecursive(extractTarget);
            Files.createDirectories(extractTarget);
            try {
                ArchiveKind kind = sniffArchiveKind(archiveTmp, pack.isZip());
                switch (kind) {
                    case ZIP -> extractZip(archiveTmp, extractTarget);
                    case RAR -> extractRar(archiveTmp, extractTarget);
                    case UNKNOWN -> throw new IOException("Downloaded file is neither zip nor rar.");
                }
            } finally {
                try { Files.deleteIfExists(archiveTmp); } catch (IOException ignored) {}
            }
            flattenSingleRoot(extractTarget);
            String safeName = extractTarget.getFileName().toString();
            mc.execute(() -> {
                try {
                    applyResourcePack(mc, safeName);
                    addToMarker(mc, pack.getId(), safeName);
                    cb.onDone();
                } catch (Exception e) {
                    SlothyHubMod.LOGGER.error("Failed to apply resource pack", e);
                    cb.onError("Extracted but couldn't auto-apply — enable it in Resource Packs.");
                }
            });
        } catch (IOException e) {
            SlothyHubMod.LOGGER.error("Pack download failed", e);
            mc.execute(() -> cb.onError("Download failed: " + e.getMessage()));
        } catch (Exception e) {
            SlothyHubMod.LOGGER.error("Pack extraction failed", e);
            mc.execute(() -> cb.onError("Extraction failed: " + e.getMessage()));
        }
    }

    private static void applyLocalPack(Pack pack, ProgressCallback cb) {
        class_310 mc = class_310.method_1551();
        Path resourcePacksDir = mc.field_1697.toPath().resolve("resourcepacks");
        try {
            // Resolve source: pack_url is stored as a file:// URI
            Path sourcePath;
            String packUrl = pack.getPackUrl();
            if (packUrl != null && packUrl.startsWith("file:")) {
                sourcePath = java.nio.file.Paths.get(URI.create(packUrl));
            } else {
                sourcePath = com.slothyhub.local.LocalPackManager.getLocalPackDir()
                    .resolve(pack.getPackFilename());
            }
            if (!Files.exists(sourcePath)) {
                mc.execute(() -> cb.onError("Local pack file not found: " + pack.getPackFilename()));
                return;
            }
            Files.createDirectories(resourcePacksDir);
            String safeBase = pack.getName().replaceAll("[^a-zA-Z0-9_\\-. ]", "_").trim();
            if (safeBase.isEmpty()) safeBase = "local-pack";

            mc.execute(cb::onApplying);

            Path extractTarget = resourcePacksDir.resolve(safeBase);
            if (Files.exists(extractTarget)) deleteRecursive(extractTarget);

            if (Files.isDirectory(sourcePath)) {
                copyDirectory(sourcePath, extractTarget);
            } else {
                Files.createDirectories(extractTarget);
                ArchiveKind kind = sniffArchiveKind(sourcePath, pack.isZip());
                switch (kind) {
                    case ZIP -> extractZip(sourcePath, extractTarget);
                    case RAR -> extractRar(sourcePath, extractTarget);
                    default  -> extractZip(sourcePath, extractTarget);
                }
                flattenSingleRoot(extractTarget);
            }

            String safeName = extractTarget.getFileName().toString();
            mc.execute(() -> {
                try {
                    applyResourcePack(mc, safeName);
                    addToMarker(mc, pack.getId(), safeName);
                    cb.onDone();
                } catch (Exception e) {
                    SlothyHubMod.LOGGER.error("Failed to auto-apply local pack '{}'", safeName, e);
                    cb.onError("Extracted but couldn't auto-apply — enable it in Resource Packs.");
                }
            });
        } catch (Exception e) {
            SlothyHubMod.LOGGER.error("Local pack apply failed for '{}'", pack.getName(), e);
            mc.execute(() -> cb.onError("Local apply failed: " + e.getMessage()));
        }
    }

    private static void copyDirectory(Path src, Path dst) throws IOException {
        Files.createDirectories(dst);
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path p : walk.toList()) {
                Path rel = src.relativize(p);
                Path target = dst.resolve(rel);
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** GitHub Releases and other static hosts put the direct URL in pack_url. */
    private static String resolveDownloadUrl(Pack pack, String serverUrl) {
        String direct = pack.getPackUrl();
        if (direct != null && !direct.isBlank()) {
            String lower = direct.toLowerCase(Locale.ROOT);
            if (lower.startsWith("http://") || lower.startsWith("https://")) return direct.trim();
        }
        return pack.getDirectDownloadUrl(serverUrl);
    }

    private static ArchiveKind sniffArchiveKind(Path file, boolean hintZip) throws IOException {
        byte[] head = new byte[8];
        try (InputStream in = Files.newInputStream(file)) {
            int n = in.read(head);
            if (n < 4) return hintZip ? ArchiveKind.ZIP : ArchiveKind.UNKNOWN;
        }
        if (head[0] == 80 && head[1] == 75) return ArchiveKind.ZIP;
        if (head[0] == 82 && head[1] == 97 && head[2] == 114 && head[3] == 33) return ArchiveKind.RAR;
        return hintZip ? ArchiveKind.ZIP : ArchiveKind.UNKNOWN;
    }

    private static void extractZip(Path zipFile, Path destDir) throws IOException {
        try (InputStream fin = Files.newInputStream(zipFile); ZipInputStream zin = new ZipInputStream(fin)) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                Path out = safeResolve(destDir, entry.getName());
                if (out != null) {
                    if (entry.isDirectory()) Files.createDirectories(out);
                    else { Files.createDirectories(out.getParent()); Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING); }
                    zin.closeEntry();
                }
            }
        }
    }

    private static void extractRar(Path rarFile, Path destDir) throws Exception {
        try {
            Junrar.extract(rarFile.toFile(), destDir.toFile());
        } catch (Exception e) {
            String name = e.getClass().getSimpleName();
            if (!name.contains("RarV5") && !name.contains("Unsupported")) throw e;
            throw new IOException("RAR5 format not supported — ask the author to re-upload as .zip.", e);
        }
    }

    private static Path safeResolve(Path base, String entryName) {
        String n = entryName.replace('\\', '/').replaceAll("^/+", "");
        if (n.contains("..")) return null;
        Path resolved = base.resolve(n).normalize();
        return resolved.startsWith(base.normalize()) ? resolved : null;
    }

    private static void flattenSingleRoot(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> entries = stream.toList();
            if (entries.size() != 1) return;
            Path only = entries.get(0);
            if (!Files.isDirectory(only)) return;
            try (Stream<Path> inner = Files.list(only)) {
                for (Path p : inner.toList()) Files.move(p, dir.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.delete(only);
        }
    }

    private static void deleteRecursive(Path p) throws IOException {
        if (Files.exists(p)) {
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.list(p)) {
                    for (Path c : s.toList()) deleteRecursive(c);
                }
            }
            Files.delete(p);
        }
    }

    private static void applyResourcePack(class_310 mc, String fileName) {
        class_3283 manager = mc.method_1520();
        String id = resolveProfileId(manager, fileName, 4);
        if (id == null) {
            SlothyHubMod.LOGGER.error("Pack '{}' not found in manager", fileName);
            throw new IllegalStateException("Manager could not find pack folder: file/" + fileName);
        }
        LinkedHashSet<String> enabled = new LinkedHashSet<>(VersionCompat.enabledNames(manager));
        enabled.remove(id);
        enabled.add(id);
        manager.method_14447(enabled);
        List<String> optList = mc.field_1690.field_1887;
        optList.clear();
        optList.addAll(enabled);
        mc.field_1690.method_1640();
        mc.method_1521().exceptionally(e -> { SlothyHubMod.LOGGER.error("reloadResources failed for {}", id, e); return null; });
        SlothyHubMod.LOGGER.info("Enabled resource pack '{}'", id);
    }

    private static String resolveProfileId(class_3283 manager, String folderName, int retries) {
        String[] candidates = {"file/" + folderName, "file:" + folderName, folderName};
        for (int attempt = 0; attempt <= retries; attempt++) {
            manager.method_14445();
            for (String c : candidates) {
                if (manager.method_14449(c) != null) return c;
            }
            for (String pid : VersionCompat.profileIds(manager)) {
                if (pid.endsWith("/" + folderName) || pid.endsWith(":" + folderName)) return pid;
            }
            try { Thread.sleep(60L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        }
        return null;
    }

    public static void removePack(Pack pack, Runnable onDone) {
        class_310 mc = class_310.method_1551();
        Map<String, String> marker = readMarker(mc);
        String folder = marker.get(pack.getId());
        if (folder != null && !folder.isEmpty()) {
            Path resourcePacksDir = mc.field_1697.toPath().resolve("resourcepacks");
            mc.execute(() -> {
                String id = "file/" + folder;
                class_3283 manager = mc.method_1520();
                try {
                    manager.method_14445();
                    LinkedHashSet<String> enabled = new LinkedHashSet<>(VersionCompat.enabledNames(manager));
                    boolean changed = enabled.remove(id) | enabled.remove("file:" + folder) | enabled.remove(folder);
                    if (changed) manager.method_14447(enabled);
                    List<String> optList = mc.field_1690.field_1887;
                    if (optList.removeIf(s -> s.equals(id) || s.equals("file:" + folder) || s.equals(folder))) mc.field_1690.method_1640();
                    if (changed) mc.method_1521().exceptionally(e -> { SlothyHubMod.LOGGER.error("reloadResources failed during remove", e); return null; });
                } catch (Exception e) {
                    SlothyHubMod.LOGGER.warn("Could not detach pack {} from manager: {}", id, e.getMessage());
                }
                Path target = resourcePacksDir.resolve(folder).normalize();
                if (target.startsWith(resourcePacksDir.normalize())) {
                    try { if (Files.exists(target)) deleteRecursive(target); } catch (IOException e) {
                        SlothyHubMod.LOGGER.warn("Failed to delete pack folder {}: {}", target, e.getMessage());
                    }
                }
                removeFromMarker(mc, pack.getId());
                onDone.run();
            });
        } else {
            mc.execute(onDone);
        }
    }

    private static Map<String, String> readMarker(class_310 mc) {
        Map<String, String> out = new LinkedHashMap<>();
        Path marker = mc.field_1697.toPath().resolve("resourcepacks").resolve(MARKER_FILENAME);
        if (!Files.exists(marker)) return out;
        try {
            for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                String id = null, folder = null;
                for (String part : trimmed.split(";")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) {
                        String k = part.substring(0, eq).trim();
                        String v = part.substring(eq + 1).trim();
                        if (k.equals("id")) id = v;
                        else if (k.equals("folder")) folder = v;
                    }
                }
                if (id != null && !id.isEmpty() && folder != null && !folder.isEmpty()) out.put(id, folder);
            }
        } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("Could not read SlothyHub marker file: {}", e.getMessage());
        }
        return out;
    }

    private static void addToMarker(class_310 mc, String packId, String folderName) {
        Map<String, String> current = readMarker(mc);
        current.put(packId, folderName);
        writeMarker(mc, current);
    }

    private static void removeFromMarker(class_310 mc, String packId) {
        Map<String, String> current = readMarker(mc);
        if (current.remove(packId) != null) writeMarker(mc, current);
    }

    private static void writeMarker(class_310 mc, Map<String, String> entries) {
        Path marker = mc.field_1697.toPath().resolve("resourcepacks").resolve(MARKER_FILENAME);
        try {
            Files.createDirectories(marker.getParent());
            StringBuilder sb = new StringBuilder("# SlothyHub applied packs. One pack per line.\n");
            for (Map.Entry<String, String> e : entries.entrySet()) {
                sb.append("id=").append(e.getKey()).append(";folder=").append(e.getValue()).append('\n');
            }
            Files.writeString(marker, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("Could not write SlothyHub marker file: {}", e.getMessage());
        }
    }

    public static void clearLocalCache() {
        class_310 mc = class_310.method_1551();
        Map<String, String> marker = readMarker(mc);
        if (marker.isEmpty()) return;
        mc.execute(() -> {
            class_3283 manager = mc.method_1520();
            try {
                manager.method_14445();
                LinkedHashSet<String> enabled = new LinkedHashSet<>(VersionCompat.enabledNames(manager));
                for (String folder : marker.values()) {
                    enabled.remove("file/" + folder); enabled.remove("file:" + folder); enabled.remove(folder);
                }
                manager.method_14447(enabled);
                List<String> optList = mc.field_1690.field_1887;
                optList.removeIf(s -> { for (String f : marker.values()) { if (s.equals("file/" + f) || s.equals("file:" + f) || s.equals(f)) return true; } return false; });
                mc.field_1690.method_1640();
                mc.method_1521().exceptionally(e -> { SlothyHubMod.LOGGER.error("reloadResources failed during clear-cache", e); return null; });
            } catch (Exception e) {
                SlothyHubMod.LOGGER.warn("clearLocalCache: manager update failed: {}", e.getMessage());
            }
            Path resourcePacksDir = mc.field_1697.toPath().resolve("resourcepacks").normalize();
            for (String folder : marker.values()) {
                Path target = resourcePacksDir.resolve(folder).normalize();
                if (target.startsWith(resourcePacksDir)) {
                    try { if (Files.exists(target)) deleteRecursive(target); } catch (IOException e) {
                        SlothyHubMod.LOGGER.warn("clearLocalCache: could not delete {}: {}", target, e.getMessage());
                    }
                }
            }
            writeMarker(mc, new LinkedHashMap<>());
            SlothyHubMod.LOGGER.info("SlothyHub local cache cleared ({} pack(s)).", marker.size());
        });
    }

    public static List<String> listLocalPackFolderNames() {
        class_310 mc = class_310.method_1551();
        Path dir = mc.field_1697.toPath().resolve("resourcepacks");
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.toList()) {
                if (Files.isDirectory(p)) {
                    String name = p.getFileName().toString();
                    if (!name.startsWith(".") && Files.exists(p.resolve("pack.mcmeta"))) out.add(name);
                }
            }
        } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("listLocalPackFolderNames: {}", e.getMessage());
        }
        out.sort(String::compareTo);
        return out;
    }

    private static MessageDigest newSha256() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { return null; }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) { sb.append(Character.forDigit(b >> 4 & 15, 16)); sb.append(Character.forDigit(b & 15, 16)); }
        return sb.toString();
    }

    private static HttpURLConnection openWithRedirects(String url, int maxHops) throws IOException {
        String current = url;
        for (int hop = 0; hop <= maxHops; hop++) {
            HttpURLConnection conn = (HttpURLConnection) URI.create(current).toURL().openConnection();
            conn.setConnectTimeout(15000); conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "SlothyHub-Mod/1.0");
            conn.setInstanceFollowRedirects(false);
            int status = conn.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location != null && !location.isBlank()) { current = URI.create(current).resolve(location).toString(); continue; }
                throw new IOException("Redirect with no Location header from " + current);
            }
            return conn;
        }
        throw new IOException("Too many redirects (>" + maxHops + ") starting at " + url);
    }

    private enum ArchiveKind { ZIP, RAR, UNKNOWN }

    public interface ProgressCallback {
        void onProgress(float f);
        void onApplying();
        void onDone();
        void onError(String msg);
    }
}
