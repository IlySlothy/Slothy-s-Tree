package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.builder.ResourceScanHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.class_3264;
import net.minecraft.class_3300;

import com.slothyhub.compat.McVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class CitEngine {

    private CitEngine() {}

    public static void init() {
        if (!SlothyConfig.isCitEnabled()) {
            SlothyHubMod.LOGGER.info("CIT engine disabled via config.");
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