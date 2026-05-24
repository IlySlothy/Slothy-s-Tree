package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;

import java.lang.reflect.Method;

/**
 * Entry point for the embedded CIT engine.
 * Called from {@link com.slothyhub.SlothyHubMod} on client init.
 *
 * Uses reflection to call ResourceManagerHelper to avoid compile-time dependency
 * on the Minecraft ResourceType intermediary name (which varies by MC version).
 */
public final class CitEngine {

    private CitEngine() {}

    public static void init() {
        if (!SlothyConfig.isCitEnabled()) {
            SlothyHubMod.LOGGER.info("CIT engine disabled via config.");
            return;
        }
        try {
            registerViaReflection();
            SlothyHubMod.LOGGER.info("CIT engine initialised.");
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("CIT engine could not register reload listener: {}", e.getMessage());
        }
    }

    private static void registerViaReflection() throws Exception {
        // net.fabricmc.fabric.api.resource.ResourceManagerHelper
        Class<?> helperClass = Class.forName("net.fabricmc.fabric.api.resource.ResourceManagerHelper");
        // net.minecraft.resource.ResourceType - try both the Fabric-API exposed class and intermediary names
        Class<?> resourceTypeClass = resolveResourceTypeClass();
        Object clientResources = resolveClientResourcesConstant(resourceTypeClass);

        Method get = helperClass.getMethod("get", resourceTypeClass);
        Object helper = get.invoke(null, clientResources);

        // net.fabricmc.fabric.api.resource.ResourceManagerHelper#registerReloadListener
        Class<?> listenerClass = Class.forName("net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener");
        Method registerMethod = helperClass.getMethod("registerReloadListener", listenerClass);
        registerMethod.invoke(helper, new CitResourceReloadListener());
    }

    private static Class<?> resolveResourceTypeClass() {
        String[] names = {
            "net.minecraft.resource.ResourceType",
            "net.minecraft.class_3281"
        };
        ClassLoader cl = CitEngine.class.getClassLoader();
        for (String name : names) {
            try { return Class.forName(name, false, cl); } catch (ClassNotFoundException ignored) {}
        }
        throw new RuntimeException("Could not resolve ResourceType class");
    }

    private static Object resolveClientResourcesConstant(Class<?> resourceTypeClass) {
        // The CLIENT_RESOURCES field has varied across MC versions
        String[] fieldNames = {"CLIENT_RESOURCES", "field_17267", "field_17268"};
        for (String name : fieldNames) {
            try {
                java.lang.reflect.Field f = resourceTypeClass.getField(name);
                Object val = f.get(null);
                if (val != null) return val;
            } catch (Exception ignored) {}
        }
        // Last resort: return first enum constant that contains "client" in its name
        if (resourceTypeClass.isEnum()) {
            for (Object c : resourceTypeClass.getEnumConstants()) {
                if (c.toString().toLowerCase(java.util.Locale.ROOT).contains("client")) return c;
            }
            Object[] consts = resourceTypeClass.getEnumConstants();
            if (consts != null && consts.length > 0) return consts[0];
        }
        throw new RuntimeException("Could not resolve CLIENT_RESOURCES constant");
    }
}
