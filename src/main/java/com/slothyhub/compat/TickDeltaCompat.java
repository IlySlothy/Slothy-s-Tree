package com.slothyhub.compat;

import java.lang.reflect.Method;

/** Resolves render tick delta without linking {@code class_9779} (1.21+ only). */
public final class TickDeltaCompat {

    private static final Method TICK_DELTA_METHOD;

    private TickDeltaCompat() {}

    public static float delta(Object tickCounter) {
        if (TICK_DELTA_METHOD != null && tickCounter != null) {
            try {
                return (Float) TICK_DELTA_METHOD.invoke(tickCounter, true);
            } catch (ReflectiveOperationException ignored) {}
        }
        if (tickCounter instanceof Number n) {
            return n.floatValue();
        }
        return 0f;
    }

    static {
        Method found = null;
        try {
            Class<?> rtc = Class.forName("net.minecraft.class_9779");
            for (Method m : rtc.getMethods()) {
                if (m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == boolean.class
                    && m.getReturnType() == float.class) {
                    m.setAccessible(true);
                    found = m;
                    break;
                }
            }
        } catch (Throwable ignored) {}
        TICK_DELTA_METHOD = found;
    }
}
