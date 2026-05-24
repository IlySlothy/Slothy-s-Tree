package com.slothyhub;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** Fixes broken pack.mcmeta files in resourcepacks/ so Minecraft can load them. */
public final class PackMcmetaRepair {

    private PackMcmetaRepair() {}

    public static void scanAndFixAsync() {
        Thread t = new Thread(PackMcmetaRepair::scanAndFix, "slothyhub-mcmeta-repair");
        t.setDaemon(true);
        t.start();
    }

    private static void scanAndFix() {
        try {
            Path rp = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
            if (!Files.isDirectory(rp)) return;
            int fixed = 0;
            try (Stream<Path> entries = Files.list(rp)) {
                for (Path entry : entries.toList()) {
                    String name = entry.getFileName().toString();
                    if (name.startsWith(".")) continue;
                    if (Files.isDirectory(entry)) {
                        try {
                            Path mcmeta = entry.resolve("pack.mcmeta");
                            if (Files.exists(mcmeta) && !PackMetaUtil.isValidMcmeta(Files.readString(mcmeta))) {
                                PackMetaUtil.repairFolder(entry);
                                fixed++;
                            }
                        } catch (Exception ignored) {}
                    } else if (name.toLowerCase().endsWith(".zip")) {
                        if (PackMetaUtil.repairZip(entry)) fixed++;
                    }
                }
            }
            if (fixed > 0) {
                SlothyHubMod.LOGGER.info("SlothyHub: repaired {} broken pack.mcmeta file(s)", fixed);
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("SlothyHub: pack.mcmeta repair scan failed: {}", e.getMessage());
        }
    }
}
