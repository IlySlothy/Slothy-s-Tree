package com.slothyhub.compat;

import com.slothyhub.SlothyHubMod;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.class_3283;

/**
 * Reflective adapter for {@link class_3283} (ResourcePackManager) across MC 1.20–1.21.8.
 */
final class VersionCompatImpl {

    private static final Method ENABLED_NAMES;
    private static final Method PROFILE_IDS;
    private static final Method REFRESH;
    private static final Method SET_ENABLED;
    private static final Method GET_PROFILE;

    static {
        ENABLED_NAMES = resolveNoArg(class_3283.class,
            new String[]{"method_29210", "getEnabledNames", "getEnabledProfiles", "getEnabledPacks", "method_14448"},
            Collection.class);
        PROFILE_IDS = resolveNoArg(class_3283.class,
            new String[]{"method_29206", "getProfiles", "getProfileIds", "getAllProfiles", "method_14446"},
            Collection.class);
        REFRESH = resolveNoArg(class_3283.class,
            new String[]{"method_14445", "scanPacks", "reload", "refresh"},
            void.class);
        SET_ENABLED = resolveOneArg(class_3283.class,
            new String[]{"method_14447", "setEnabledProfiles", "setEnabled", "enableProfiles"},
            Collection.class, void.class);
        GET_PROFILE = resolveOneArg(class_3283.class,
            new String[]{"method_14449", "getProfile", "getPackProfile"},
            String.class, Object.class);
        SlothyHubMod.LOGGER.info("VersionCompat: enabled={} profiles={} refresh={} setEnabled={} getProfile={}",
            nameOf(ENABLED_NAMES), nameOf(PROFILE_IDS), nameOf(REFRESH),
            nameOf(SET_ENABLED), nameOf(GET_PROFILE));
    }

    private VersionCompatImpl() {}

    private static String nameOf(Method m) {
        return m != null ? m.getName() : "null";
    }

    private static Method resolveNoArg(Class<?> target, String[] names, Class<?> returnType) {
        for (String name : names) {
            for (Method m : target.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    if (returnType == void.class) {
                        if (m.getReturnType() == void.class) {
                            m.setAccessible(true);
                            return m;
                        }
                    } else if (returnType.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
        }
        for (String name : names) {
            for (Method m : target.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static Method resolveOneArg(Class<?> target, String[] names, Class<?> paramType, Class<?> returnType) {
        for (String name : names) {
            for (Method m : target.getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                if (!paramType.isAssignableFrom(p) && !Collection.class.isAssignableFrom(p)) continue;
                if (returnType == void.class && m.getReturnType() != void.class) continue;
                if (returnType != void.class && !returnType.isAssignableFrom(m.getReturnType())
                    && returnType != Object.class) continue;
                m.setAccessible(true);
                return m;
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
        try { return manager.method_29210(); } catch (Exception ignored) {}
        return List.of();
    }

    static Collection<String> profileIds(class_3283 manager) {
        if (PROFILE_IDS != null) return invokeStringCollection(PROFILE_IDS, manager);
        try { return manager.method_29206(); } catch (Exception ignored) {}
        return List.of();
    }

    static void refresh(class_3283 manager) {
        if (REFRESH != null) {
            try {
                REFRESH.invoke(manager);
                return;
            } catch (Exception e) {
                SlothyHubMod.LOGGER.warn("VersionCompat: refresh failed: {}", e.getMessage());
            }
        }
        try { manager.method_14445(); } catch (Exception ignored) {}
    }

    static void setEnabled(class_3283 manager, Collection<String> enabled) {
        if (SET_ENABLED != null) {
            try {
                SET_ENABLED.invoke(manager, enabled);
                return;
            } catch (Exception e) {
                SlothyHubMod.LOGGER.warn("VersionCompat: setEnabled failed: {}", e.getMessage());
            }
        }
        try { manager.method_14447(enabled); } catch (Exception ignored) {}
    }

    static boolean hasProfile(class_3283 manager, String id) {
        if (GET_PROFILE != null) {
            try {
                return GET_PROFILE.invoke(manager, id) != null;
            } catch (Exception e) {
                SlothyHubMod.LOGGER.warn("VersionCompat: getProfile failed for {}: {}", id, e.getMessage());
            }
        }
        try { return manager.method_14449(id) != null; } catch (Exception ignored) {}
        return false;
    }
}
