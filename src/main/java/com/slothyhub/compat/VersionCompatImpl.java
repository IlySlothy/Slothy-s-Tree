package com.slothyhub.compat;

import com.slothyhub.SlothyHubMod;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.class_3283;

/**
 * Reflective adapter for {@link class_3283} (ResourcePackManager) across MC 1.20–1.21.11.
 *
 * Method names and signatures changed between versions.
 * We try the known variants in priority order.
 */
final class VersionCompatImpl {

    private static final Method ENABLED_NAMES;
    private static final Method PROFILE_IDS;

    static {
        // Candidate method names for "enabled pack names" across versions
        ENABLED_NAMES = resolveMethod(class_3283.class,
            new String[]{"method_29210", "getEnabledNames", "getEnabledProfiles", "getEnabledPacks"},
            Collection.class);
        // Candidate method names for "all profile IDs" across versions
        PROFILE_IDS = resolveMethod(class_3283.class,
            new String[]{"method_29206", "getProfiles", "getProfileIds", "getAllProfiles"},
            Collection.class);
        SlothyHubMod.LOGGER.info("VersionCompat: enabledNames={} profileIds={}",
            ENABLED_NAMES != null ? ENABLED_NAMES.getName() : "null",
            PROFILE_IDS   != null ? PROFILE_IDS.getName()   : "null");
    }

    private VersionCompatImpl() {}

    private static Method resolveMethod(Class<?> target, String[] names, Class<?> returnSupertype) {
        for (String name : names) {
            for (Method m : target.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    if (returnSupertype == null || returnSupertype.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
        }
        // Fallback: find any no-arg method returning Collection if none of the names match
        for (String name : names) {
            for (Method m : target.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> invokeStringCollection(Method m, class_3283 manager) {
        if (m == null) return Collections.emptyList();
        try {
            Object result = m.invoke(manager);
            if (result instanceof Collection<?> c) return (Collection<String>) c;
            return Collections.emptyList();
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("VersionCompat: invoke failed on {}: {}", m.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    static Collection<String> enabledNames(class_3283 manager) {
        if (ENABLED_NAMES != null) return invokeStringCollection(ENABLED_NAMES, manager);
        // Absolute fallback: try direct call (will only compile on the right MC version)
        try { return manager.method_29210(); } catch (Exception ignored) {}
        return List.of();
    }

    static Collection<String> profileIds(class_3283 manager) {
        if (PROFILE_IDS != null) return invokeStringCollection(PROFILE_IDS, manager);
        try { return manager.method_29206(); } catch (Exception ignored) {}
        return List.of();
    }
}
