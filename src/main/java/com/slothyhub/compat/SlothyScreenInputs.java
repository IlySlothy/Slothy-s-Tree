package com.slothyhub.compat;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.class_437;

/**
 * Bridges Fabric allowMouseClick to legacy method_25402 on MC 1.21.8 and earlier.
 * On MC 1.21.9+ use InputCompat.Poller in screen render (Fabric API signature changed).
 */
public final class SlothyScreenInputs {

    private static final Method LEGACY_SCREEN_CLICK = resolveLegacyScreenClick(class_437.class);

    private SlothyScreenInputs() {}

    public static void register(class_437 screen) {
        if (InputCompat.needsPolling()) {
            return;
        }
        ScreenMouseEvents.allowMouseClick(screen).register((scr, mx, my, button) -> {
            if (dispatchLegacyClick(scr, mx, my, button)) {
                return false;
            }
            return true;
        });
    }

    public static boolean dispatchLegacyClick(Object screen, double mx, double my, int button) {
        Class<?> type = screen.getClass();
        while (type != null && class_437.class.isAssignableFrom(type)) {
            try {
                Method m = type.getDeclaredMethod("method_25402", double.class, double.class, int.class);
                if (m.getReturnType() == boolean.class) {
                    m.setAccessible(true);
                    return (Boolean) m.invoke(screen, mx, my, button);
                }
            } catch (ReflectiveOperationException ignored) {
            }
            type = type.getSuperclass();
        }
        if (LEGACY_SCREEN_CLICK != null) {
            try {
                return (Boolean) LEGACY_SCREEN_CLICK.invoke(screen, mx, my, button);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    private static Method resolveLegacyScreenClick(Class<?> root) {
        for (Method m : root.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 3 && p[0] == double.class && p[1] == double.class && p[2] == int.class
                && m.getReturnType() == boolean.class) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}