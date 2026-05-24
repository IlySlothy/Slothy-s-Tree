package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.builder.ResourceScanHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.class_3264;
import net.minecraft.class_3300;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class CitEngine {

    private CitEngine() {}

    public static void init() {
        CitMixinPlugin.logPipelineMode();
        if (!SlothyConfig.isCitEnabled()) {
            SlothyHubMod.LOGGER.info("CIT engine disabled via config.");
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
        CitResourceReloadListener.reloadCit(manager);
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