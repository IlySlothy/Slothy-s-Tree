package com.packhub;

import com.github.junrar.Junrar;
import com.packhub.compat.VersionCompat;
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
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraft.class_310;
import net.minecraft.class_3283;

public class PackDownloader {
   private static final String MARKER_FILENAME = ".packhub-active";

   public PackDownloader() {
   }

   public static Set<String> getActivePackIds() {
      return new LinkedHashSet<>(readMarker(class_310.method_1551()).keySet());
   }

   public static String getActivePackFolder(String packId) {
      if (packId != null && !packId.isEmpty()) {
         String folder = readMarker(class_310.method_1551()).get(packId);
         return folder != null && !folder.isEmpty() ? folder : null;
      } else {
         return null;
      }
   }

   public static Path resolveResourcePackPath(String folderName) {
      if (folderName != null && !folderName.isEmpty()) {
         class_310 mc = class_310.method_1551();
         Path base = mc.field_1697.toPath().resolve("resourcepacks").normalize();
         Path p = base.resolve(folderName).normalize();
         return p.startsWith(base) ? p : null;
      } else {
         return null;
      }
   }

   public static String applyBuiltPack(byte[] zipData, String packName) throws IOException {
      class_310 mc = class_310.method_1551();
      Path resourcePacksDir = mc.field_1697.toPath().resolve("resourcepacks");
      Files.createDirectories(resourcePacksDir);
      String safeBase = packName.replaceAll("[^a-zA-Z0-9_\\-. ]", "_").trim();
      if (safeBase.isEmpty()) {
         safeBase = "CustomPack";
      }

      Path extractTarget = resourcePacksDir.resolve(safeBase);
      if (Files.exists(extractTarget)) {
         deleteRecursive(extractTarget);
      }

      Files.createDirectories(extractTarget);
      Path tmp = resourcePacksDir.resolve(".packhub-staging");
      Files.createDirectories(tmp);
      Path tmpZip = tmp.resolve(safeBase + "-" + System.currentTimeMillis() + ".zip");

      try {
         Files.write(tmpZip, zipData);
         extractZip(tmpZip, extractTarget);
      } finally {
         try {
            Files.deleteIfExists(tmpZip);
         } catch (IOException var14) {
         }
      }

      flattenSingleRoot(extractTarget);
      String folderName = extractTarget.getFileName().toString();
      String builderId = "builder-" + folderName;
      mc.execute(() -> {
         try {
            applyResourcePack(mc, folderName);
            addToMarker(mc, builderId, folderName);
            PackHubMod.LOGGER.info("Builder pack '{}' applied and activated", folderName);
         } catch (Exception var4x) {
            PackHubMod.LOGGER.error("Failed to apply builder pack '{}'", folderName, var4x);
         }
      });
      return folderName;
   }

   public static void downloadAndApply(Pack pack, String serverUrl, PackDownloader.ProgressCallback cb) {
      class_310 mc = class_310.method_1551();
      Path resourcePacksDir = mc.field_1697.toPath().resolve("resourcepacks");

      try {
         Files.createDirectories(resourcePacksDir);
         String safeBase = pack.getName().replaceAll("[^a-zA-Z0-9_\\-. ]", "_").trim();
         if (safeBase.isEmpty()) {
            safeBase = "pack-" + pack.getId();
         }

         String ext = pack.isZip() ? ".zip" : ".rar";
         Path stagingDir = resourcePacksDir.resolve(".packhub-staging");
         Files.createDirectories(stagingDir);
         Path archiveTmp = stagingDir.resolve(safeBase + "-" + System.currentTimeMillis() + ext);
         String downloadUrl = pack.getDirectDownloadUrl(serverUrl);
         PackHubMod.LOGGER.info("Downloading pack from {}", downloadUrl);
         HttpURLConnection conn = openWithRedirects(downloadUrl, 5);
         int status = conn.getResponseCode();
         if (status != 200) {
            throw new IOException("Server returned HTTP " + status + " — is the bot still running?");
         }

         String expectedSha = conn.getHeaderField("X-PackHub-SHA256");
         if (expectedSha == null || expectedSha.isBlank()) {
            expectedSha = pack.getSha256();
         }

         long total = conn.getContentLengthLong();
         MessageDigest digest = newSha256();

         try (
            InputStream in = conn.getInputStream();
            OutputStream out = Files.newOutputStream(archiveTmp);
         ) {
            byte[] buf = new byte[65536];
            long downloaded = 0L;

            int n;
            while ((n = in.read(buf)) != -1) {
               out.write(buf, 0, n);
               if (digest != null) {
                  digest.update(buf, 0, n);
               }

               downloaded += (long)n;
               if (total > 0L) {
                  float frac = (float)downloaded / (float)total;
                  mc.execute(() -> cb.onProgress(frac));
               }
            }
         }

         PackHubMod.LOGGER.info("Pack downloaded to {} ({} bytes)", archiveTmp, Files.size(archiveTmp));
         if (PackHubConfig.isVerifyDownloads() && digest != null && expectedSha != null && !expectedSha.isBlank()) {
            String actual = toHex(digest.digest());
            if (!actual.equalsIgnoreCase(expectedSha.trim())) {
               try {
                  Files.deleteIfExists(archiveTmp);
               } catch (IOException var36) {
               }

               throw new IOException(
                  "Download checksum did not match — file may be corrupted. Expected "
                     + expectedSha.substring(0, Math.min(12, expectedSha.length()))
                     + "…, got "
                     + actual.substring(0, 12)
                     + "…"
               );
            }
         } else if (expectedSha == null || expectedSha.isBlank()) {
            PackHubMod.LOGGER.info("No SHA-256 advertised for pack {} — skipping verify", pack.getId());
         }

         mc.execute(cb::onApplying);
         Path extractTarget = resourcePacksDir.resolve(safeBase);
         if (Files.exists(extractTarget)) {
            deleteRecursive(extractTarget);
         }

         Files.createDirectories(extractTarget);

         try {
            PackDownloader.ArchiveKind kind = sniffArchiveKind(archiveTmp, pack.isZip());
            switch (kind) {
               case ZIP:
                  extractZip(archiveTmp, extractTarget);
                  break;
               case RAR:
                  extractRar(archiveTmp, extractTarget);
                  break;
               case UNKNOWN:
                  throw new IOException("Downloaded file is neither a zip nor a rar archive.");
            }
         } finally {
            try {
               Files.deleteIfExists(archiveTmp);
            } catch (IOException var35) {
            }
         }

         flattenSingleRoot(extractTarget);
         if (!Files.exists(extractTarget.resolve("pack.mcmeta"))) {
            PackHubMod.LOGGER.warn("Extracted pack at {} has no pack.mcmeta — Minecraft may reject it", extractTarget);
         }

         String safeName = extractTarget.getFileName().toString();
         mc.execute(() -> {
            try {
               applyResourcePack(mc, safeName);
               addToMarker(mc, pack.getId(), safeName);
               cb.onDone();
            } catch (Exception var5x) {
               PackHubMod.LOGGER.error("Failed to apply resource pack", var5x);
               cb.onError("Extracted but couldn't auto-apply — enable it in Resource Packs.");
            }
         });
      } catch (IOException var42) {
         PackHubMod.LOGGER.error("Pack download failed", var42);
         mc.execute(() -> cb.onError("Download failed: " + var42.getMessage()));
      } catch (Exception var431) {
         PackHubMod.LOGGER.error("Pack extraction failed", var431);
         mc.execute(() -> cb.onError("Extraction failed: " + var431.getMessage()));
      }
   }

   private static PackDownloader.ArchiveKind sniffArchiveKind(Path file, boolean hintZip) throws IOException {
      byte[] head = new byte[8];

      try (InputStream in = Files.newInputStream(file)) {
         int n = in.read(head);
         if (n < 4) {
            return hintZip ? PackDownloader.ArchiveKind.ZIP : PackDownloader.ArchiveKind.UNKNOWN;
         }
      }

      if (head[0] == 80 && head[1] == 75) {
         return PackDownloader.ArchiveKind.ZIP;
      } else if (head[0] == 82 && head[1] == 97 && head[2] == 114 && head[3] == 33) {
         return PackDownloader.ArchiveKind.RAR;
      } else {
         return hintZip ? PackDownloader.ArchiveKind.ZIP : PackDownloader.ArchiveKind.UNKNOWN;
      }
   }

   private static void extractZip(Path zipFile, Path destDir) throws IOException {
      ZipEntry entry;
      try (
         InputStream fin = Files.newInputStream(zipFile);
         ZipInputStream zin = new ZipInputStream(fin);
      ) {
         while ((entry = zin.getNextEntry()) != null) {
            Path out = safeResolve(destDir, entry.getName());
            if (out != null) {
               if (entry.isDirectory()) {
                  Files.createDirectories(out);
               } else {
                  Files.createDirectories(out.getParent());
                  Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
               }

               zin.closeEntry();
            }
         }
      }
   }

   private static void extractRar(Path rarFile, Path destDir) throws Exception {
      try {
         Junrar.extract(rarFile.toFile(), destDir.toFile());
      } catch (Exception var4) {
         String name = var4.getClass().getSimpleName();
         if (!name.contains("RarV5") && !name.contains("Unsupported")) {
            throw var4;
         } else {
            throw new IOException("This pack is RAR5, which the bundled extractor doesn't support. Ask the author to re-upload as .zip.", var4);
         }
      }
   }

   private static Path safeResolve(Path base, String entryName) {
      String n = entryName.replace('\\', '/').replaceAll("^/+", "");
      if (n.contains("..")) {
         return null;
      } else {
         Path resolved = base.resolve(n).normalize();
         return !resolved.startsWith(base.normalize()) ? null : resolved;
      }
   }

   private static void flattenSingleRoot(Path dir) throws IOException {
      try (Stream<Path> stream = Files.list(dir)) {
         List<Path> entries = stream.toList();
         if (entries.size() != 1) {
            return;
         }

         Path only = entries.get(0);
         if (!Files.isDirectory(only)) {
            return;
         }

         try (Stream<Path> inner = Files.list(only)) {
            for (Path p : inner.toList()) {
               Files.move(p, dir.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
         }

         Files.delete(only);
      }
   }

   private static void deleteRecursive(Path p) throws IOException {
      if (Files.exists(p)) {
         if (Files.isDirectory(p)) {
            try (Stream<Path> s = Files.list(p)) {
               for (Path c : s.toList()) {
                  deleteRecursive(c);
               }
            }
         }

         Files.delete(p);
      }
   }

   private static void applyResourcePack(class_310 mc, String fileName) {
      class_3283 manager = mc.method_1520();
      String id = resolveProfileId(manager, fileName, 4);
      if (id == null) {
         PackHubMod.LOGGER
            .error(
               "Pack '{}' not found in manager after scan — known profiles: {}, dir: {}",
               new Object[]{fileName, VersionCompat.profileIds(manager), listResourcePackDir(mc)}
            );
         throw new IllegalStateException("Manager could not find pack folder: file/" + fileName);
      } else {
         LinkedHashSet<String> enabled = new LinkedHashSet<>(VersionCompat.enabledNames(manager));
         enabled.remove(id);
         enabled.add(id);
         manager.method_14447(enabled);
         List<String> optList = mc.field_1690.field_1887;
         optList.clear();
         optList.addAll(enabled);
         mc.field_1690.method_1640();
         mc.method_1521().exceptionally(e -> {
            PackHubMod.LOGGER.error("reloadResources failed for {}", id, e);
            return null;
         });
         PackHubMod.LOGGER.info("Enabled resource pack '{}' (now active: {})", id, enabled);
      }
   }

   private static String resolveProfileId(class_3283 manager, String folderName, int retries) {
      String[] candidates = new String[]{"file/" + folderName, "file:" + folderName, folderName};

      for (int attempt = 0; attempt <= retries; attempt++) {
         manager.method_14445();

         for (String c : candidates) {
            if (manager.method_14449(c) != null) {
               return c;
            }
         }

         for (String pid : VersionCompat.profileIds(manager)) {
            if (pid.endsWith("/" + folderName) || pid.endsWith(":" + folderName)) {
               return pid;
            }
         }

         try {
            Thread.sleep(60L);
         } catch (InterruptedException var9) {
            Thread.currentThread().interrupt();
            return null;
         }
      }

      return null;
   }

   private static List<String> listResourcePackDir(class_310 mc) {
      try {
         Path dir = mc.field_1697.toPath().resolve("resourcepacks");
         List<String> out = new ArrayList<>();

         try (Stream<Path> s = Files.list(dir)) {
            s.forEach(p -> out.add(p.getFileName().toString()));
         }

         return out;
      } catch (IOException var81) {
         return List.of();
      }
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
               if (changed) {
                  manager.method_14447(enabled);
               }

               List<String> optList = mc.field_1690.field_1887;
               if (optList.removeIf(s -> s.equals(id) || s.equals("file:" + folder) || s.equals(folder))) {
                  mc.field_1690.method_1640();
               }

               if (changed) {
                  mc.method_1521().exceptionally(ex -> {
                     PackHubMod.LOGGER.error("reloadResources failed during remove {}", id, ex);
                     return null;
                  });
               }
            } catch (Exception var11) {
               PackHubMod.LOGGER.warn("Could not detach pack {} from manager: {}", id, var11.getMessage());
            }

            Path target = resourcePacksDir.resolve(folder).normalize();
            if (target.startsWith(resourcePacksDir.normalize())) {
               try {
                  if (Files.exists(target)) {
                     deleteRecursive(target);
                     PackHubMod.LOGGER.info("Removed PackHub pack on disk: {}", folder);
                  }
               } catch (IOException var101) {
                  PackHubMod.LOGGER.warn("Failed to delete pack folder {}: {}", target, var101.getMessage());
               }
            } else {
               PackHubMod.LOGGER.warn("Refusing to delete suspicious pack path: {}", folder);
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
      Path marker = mc.field_1697.toPath().resolve("resourcepacks").resolve(".packhub-active");
      if (!Files.exists(marker)) {
         return out;
      } else {
         try {
            for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
               String trimmed = line.trim();
               if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                  String id = null;
                  String folder = null;

                  for (String part : trimmed.split(";")) {
                     int eq = part.indexOf(61);
                     if (eq > 0) {
                        String k = part.substring(0, eq).trim();
                        String v = part.substring(eq + 1).trim();
                        if (k.equals("id")) {
                           id = v;
                        } else if (k.equals("folder")) {
                           folder = v;
                        }
                     }
                  }

                  if (id == null && folder == null && trimmed.contains("=")) {
                     int eq = trimmed.indexOf(61);
                     String k = trimmed.substring(0, eq).trim();
                     String v = trimmed.substring(eq + 1).trim();
                     if (k.equals("id")) {
                        id = v;
                     }

                     if (k.equals("folder")) {
                        folder = v;
                     }
                  }

                  if (id != null && !id.isEmpty() && folder != null && !folder.isEmpty()) {
                     out.put(id, folder);
                  }
               }
            }
         } catch (IOException var15) {
            PackHubMod.LOGGER.warn("Could not read PackHub marker file: {}", var15.getMessage());
         }

         return out;
      }
   }

   private static void addToMarker(class_310 mc, String packId, String folderName) {
      Map<String, String> current = readMarker(mc);
      current.put(packId, folderName);
      writeMarker(mc, current);
   }

   private static void removeFromMarker(class_310 mc, String packId) {
      Map<String, String> current = readMarker(mc);
      if (current.remove(packId) != null) {
         writeMarker(mc, current);
      }
   }

   private static void writeMarker(class_310 mc, Map<String, String> entries) {
      Path marker = mc.field_1697.toPath().resolve("resourcepacks").resolve(".packhub-active");

      try {
         Files.createDirectories(marker.getParent());
         StringBuilder sb = new StringBuilder();
         sb.append("# PackHub applied packs. One pack per line.\n");

         for (Entry<String, String> e : entries.entrySet()) {
            sb.append("id=").append(e.getKey()).append(";folder=").append(e.getValue()).append('\n');
         }

         Files.writeString(marker, sb.toString(), StandardCharsets.UTF_8);
      } catch (IOException var61) {
         PackHubMod.LOGGER.warn("Could not write PackHub marker file: {}", var61.getMessage());
      }
   }

   private static Set<String> markerFolders(class_310 mc) {
      return new HashSet<>(readMarker(mc).values());
   }

   public static void clearLocalCache() {
      class_310 mc = class_310.method_1551();
      Map<String, String> marker = readMarker(mc);
      if (!marker.isEmpty()) {
         mc.execute(() -> {
            class_3283 manager = mc.method_1520();

            try {
               manager.method_14445();
               LinkedHashSet<String> enabled = new LinkedHashSet<>(VersionCompat.enabledNames(manager));

               for (String folder : marker.values()) {
                  enabled.remove("file/" + folder);
                  enabled.remove("file:" + folder);
                  enabled.remove(folder);
               }

               manager.method_14447(enabled);
               List<String> optList = mc.field_1690.field_1887;
               optList.removeIf(s -> {
                  for (String folderx : marker.values()) {
                     if (s.equals("file/" + folderx) || s.equals("file:" + folderx) || s.equals(folderx)) {
                        return true;
                     }
                  }

                  return false;
               });
               mc.field_1690.method_1640();
               mc.method_1521().exceptionally(e -> {
                  PackHubMod.LOGGER.error("reloadResources failed during clear-cache", e);
                  return null;
               });
            } catch (Exception var9) {
               PackHubMod.LOGGER.warn("clearLocalCache: manager update failed: {}", var9.getMessage());
            }

            Path resourcePacksDir = mc.field_1697.toPath().resolve("resourcepacks").normalize();

            for (String folder : marker.values()) {
               Path target = resourcePacksDir.resolve(folder).normalize();
               if (target.startsWith(resourcePacksDir)) {
                  try {
                     if (Files.exists(target)) {
                        deleteRecursive(target);
                     }
                  } catch (IOException var8) {
                     PackHubMod.LOGGER.warn("clearLocalCache: could not delete {}: {}", target, var8.getMessage());
                  }
               }
            }

            writeMarker(mc, new LinkedHashMap<>());
            PackHubMod.LOGGER.info("PackHub local cache cleared ({} pack(s)).", marker.size());
         });
      }
   }

   private static MessageDigest newSha256() {
      try {
         return MessageDigest.getInstance("SHA-256");
      } catch (NoSuchAlgorithmException var1) {
         return null;
      }
   }

   private static String toHex(byte[] bytes) {
      StringBuilder sb = new StringBuilder(bytes.length * 2);

      for (byte b : bytes) {
         sb.append(Character.forDigit(b >> 4 & 15, 16));
         sb.append(Character.forDigit(b & 15, 16));
      }

      return sb.toString();
   }

   private static HttpURLConnection openWithRedirects(String url, int maxHops) throws IOException {
      String current = url;
      int hop = 0;

      while (hop <= maxHops) {
         HttpURLConnection conn = (HttpURLConnection)URI.create(current).toURL().openConnection();
         conn.setConnectTimeout(15000);
         conn.setReadTimeout(60000);
         conn.setRequestProperty("User-Agent", "PackHub-Mod/1.0");
         conn.setInstanceFollowRedirects(false);
         int status = conn.getResponseCode();
         if (status >= 300 && status < 400) {
            String location = conn.getHeaderField("Location");
            conn.disconnect();
            if (location != null && !location.isBlank()) {
               current = URI.create(current).resolve(location).toString();
               PackHubMod.LOGGER.info("Following redirect (hop {}): {}", hop + 1, current);
               hop++;
               continue;
            }

            throw new IOException("Redirect with no Location header from " + current);
         }

         return conn;
      }

      throw new IOException("Too many redirects (>" + maxHops + ") starting at " + url);
   }

   public static List<String> listLocalPackFolderNames() {
      class_310 mc = class_310.method_1551();
      Path dir = mc.field_1697.toPath().resolve("resourcepacks");
      List<String> out = new ArrayList<>();
      if (!Files.isDirectory(dir)) {
         return out;
      } else {
         try (Stream<Path> s = Files.list(dir)) {
            for (Path p : s.toList()) {
               if (Files.isDirectory(p)) {
                  String name = p.getFileName().toString();
                  if (!name.startsWith(".") && Files.exists(p.resolve("pack.mcmeta"))) {
                     out.add(name);
                  }
               }
            }
         } catch (IOException var9) {
            PackHubMod.LOGGER.warn("listLocalPackFolderNames: {}", var9.getMessage());
         }

         out.sort(String::compareTo);
         return out;
      }
   }

   private static enum ArchiveKind {
      ZIP,
      RAR,
      UNKNOWN;

      private ArchiveKind() {
      }
   }

   public interface ProgressCallback {
      void onProgress(float var1);

      void onApplying();

      void onDone();

      void onError(String var1);
   }
}
