package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.builder.ResourceScanHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_3264;
import net.minecraft.class_3300;

import com.slothyhub.compat.McVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class CitEngine {

    /** External CIT mods we yield to so we don't double-handle/override their work. */
    private static final String[] EXTERNAL_CIT_MOD_IDS = {
        "citresewn", "citresewn-defaults", "chime", "cit-resewn", "more_cit",
        "moremcmeta_textures_plugin", "moremcmeta"
    };

    private CitEngine() {}

    public static void init() {
        if (!SlothyConfig.isCitEnabled()) {
            SlothyHubMod.LOGGER.info("CIT engine disabled via config.");
            return;
        }
        // External CIT mod check runs FIRST so we yield on every MC version we ship.
        // This is the contract relied on by the slothyhub-1.20-1.21.1 legacy jar:
        // if the user has CIT Resewn (or any other CIT-providing mod) installed, our
        // engine never tries to register a parallel handler, even on the older MC
        // builds where running two CIT pipelines would crash.
        String externalCit = detectExternalCitMod();
        if (externalCit != null) {
            SlothyHubMod.LOGGER.info(
                "CIT engine skipped: '{}' is installed and will handle CIT rules. "
                + "Slothy's Tree will not register a second handler.", externalCit);
            return;
        }
        // The 1.21.8-targeted jar uses GpuTexture APIs that don't exist on <1.21.4.
        // If somebody force-installs it on an older MC, refuse to register the engine
        // instead of crashing later inside the sprite rebuild.
        if (!McVersion.atLeast("1.21.4")) {
            SlothyHubMod.LOGGER.warn(
                "CIT engine skipped: this build targets MC 1.21.4+ (current = {}). "
                + "Use the slothyhub-1.0.3-mc1.20-1.21.1 jar on older versions.",
                McVersion.current());
            return;
        }
        if (McVersion.atLeast("1.21.9")) {
            SlothyHubMod.LOGGER.info(
                "CIT: install slothyhub-cit alongside Slothy's Tree for MC {} (main jar has no CIT on 1.21.9+)",
                McVersion.current());
            return;
        }
        try {
            class_3264 client = clientResources();
            ResourceManagerHelper.get(client)
                .registerReloadListener(new CitResourceReloadListener());
            SlothyHubMod.LOGGER.info("CIT engine initialised (resource type={}).", client.name());

            ClientLifecycleEvents.CLIENT_STARTED.register(mc ->
                mc.execute(() -> reloadFromManager(ResourceScanHelper.resourceManager())));
        } catch (Throwable e) {
            SlothyHubMod.LOGGER.warn("CIT engine could not register reload listener: {}", e.getMessage());
        }
    }

    /** Returns the id of an installed CIT-providing mod, or {@code null} if none are present. */
    private static String detectExternalCitMod() {
        FabricLoader loader = FabricLoader.getInstance();
        for (String id : EXTERNAL_CIT_MOD_IDS) {
            if (loader.isModLoaded(id)) return id;
        }
        return null;
    }

    public static void reloadFromManager(class_3300 manager) {
        if (!SlothyConfig.isCitEnabled() || manager == null) return;
        if (McVersion.atLeast("1.21.9")) {
            CitResourceReloadListener.reloadCitProperties(manager);
            reloadModernSprites(manager);
            return;
        }
        CitResourceReloadListener.reloadCit(manager);
    }

    private static void reloadModernSprites(class_3300 manager) {
        try {
            Class<?> textures = Class.forName("com.slothyhub.modern.ModernCitVirtualTextures");
            Method rebuild = textures.getMethod("rebuild", class_3300.class, CitRuleSet.class);
            rebuild.invoke(null, manager, CitRuleSet.active());
        } catch (ClassNotFoundException ignored) {
            SlothyHubMod.LOGGER.debug("CIT: slothyhub-cit not loaded — sprites not rebuilt");
        } catch (Throwable e) {
            SlothyHubMod.LOGGER.warn("CIT: modern sprite rebuild failed: {}", e.getMessage());
        }
    }

    private static class_3264 clientResources() {
        for (class_3264 type : class_3264.values()) {
            if ("CLIENT_RESOURCES".equals(type.name())) return type;
        }
        for (class_3264 type : class_3264.values()) {
            String dir = resourceDirectory(type);
            if ("assets".equals(dir)) return type;
        }
        for (Field field : class_3264.class.getFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            if (!class_3264.class.isAssignableFrom(field.getType())) continue;
            if (name.contains("client")) {
                try {
                    return (class_3264) field.get(null);
                } catch (ReflectiveOperationException ignored) {}
            }
        }
        class_3264[] values = class_3264.values();
        return values.length >= 2 ? values[1] : values[0];
    }

    private static String resourceDirectory(class_3264 type) {
        if (type == null) return null;
        for (Method method : class_3264.class.getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() != String.class) continue;
            try {
                String value = (String) method.invoke(type);
                if ("assets".equals(value) || "data".equals(value)) return value;
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }
}