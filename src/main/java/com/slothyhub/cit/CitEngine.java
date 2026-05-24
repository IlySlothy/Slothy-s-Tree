package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.class_3264;

import java.util.Locale;

/**
 * Entry point for the embedded CIT engine.
 * Called from {@link com.slothyhub.SlothyHubMod} on client init.
 */
public final class CitEngine {

    private CitEngine() {}

    public static void init() {
        if (!SlothyConfig.isCitEnabled()) {
            SlothyHubMod.LOGGER.info("CIT engine disabled via config.");
            return;
        }
        try {
            class_3264 client = resolveClientResources();
            ResourceManagerHelper.get(client)
                .registerReloadListener(new CitResourceReloadListener());
            SlothyHubMod.LOGGER.info("CIT engine initialised.");
        } catch (Throwable e) {
            SlothyHubMod.LOGGER.warn("CIT engine could not register reload listener: {}", e.getMessage());
        }
    }

    private static class_3264 resolveClientResources() {
        for (class_3264 t : class_3264.values()) {
            String n = t.name().toLowerCase(Locale.ROOT);
            if (n.contains("client")) return t;
        }
        class_3264[] vals = class_3264.values();
        if (vals.length == 0) throw new IllegalStateException("No ResourceType values");
        return vals[0];
    }
}
