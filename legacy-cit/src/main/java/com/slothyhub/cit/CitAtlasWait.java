package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.Identifiers;
import net.minecraft.class_1058;
import net.minecraft.class_1059;
import net.minecraft.class_310;
import net.minecraft.class_3300;

/** Waits for the blocks atlas to stitch before resolving CIT sprites (1.21.4+). */
final class CitAtlasWait {

    private static class_3300 pendingManager;
    private static int attempts;

    private CitAtlasWait() {}

    static void scheduleRebuild(class_3300 manager) {
        if (!SlothyConfig.isCitEnabled()) return;
        pendingManager = manager;
        attempts = 0;
        class_310 mc = class_310.method_1551();
        if (mc == null) {
            CitVirtualTextures.rebuild(manager, CitRuleSet.active());
            return;
        }
        mc.execute(CitAtlasWait::tick);
    }

    private static void tick() {
        class_310 mc = class_310.method_1551();
        if (mc == null || pendingManager == null) return;

        if (isBlocksAtlasReady()) {
            CitVirtualTextures.rebuild(pendingManager, CitRuleSet.active());
            pendingManager = null;
            attempts = 0;
            return;
        }

        attempts++;
        if (attempts >= 120) {
            SlothyHubMod.LOGGER.warn("CIT: blocks atlas not ready after {} ticks — rebuilding sprites anyway", attempts);
            CitVirtualTextures.rebuild(pendingManager, CitRuleSet.active());
            pendingManager = null;
            attempts = 0;
            return;
        }

        mc.execute(CitAtlasWait::tick);
    }

    private static boolean isBlocksAtlasReady() {
        try {
            class_310 mc = class_310.method_1551();
            if (mc == null || mc.method_1554() == null) return false;
            class_1059 atlas = mc.method_1554().method_24153(class_1059.field_5275);
            if (atlas == null) return false;
            class_1058 missing = atlas.method_4608(class_1059.field_17898);
            class_1058 probe = atlas.method_4608(Identifiers.of("minecraft", "item/diamond"));
            return probe != null && probe != missing;
        } catch (Exception e) {
            return false;
        }
    }
}