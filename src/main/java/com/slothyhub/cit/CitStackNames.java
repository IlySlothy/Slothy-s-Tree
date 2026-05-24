package com.slothyhub.cit;

import com.slothyhub.compat.McVersion;
import net.minecraft.class_1799;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/** Resolves every string that should be tried against CIT name / component rules for a stack. */
final class CitStackNames {

    private static final boolean USE_MODERN = McVersion.atLeast("1.20.5");
    private static final Method MODERN_RESOLVE = loadModernResolve();

    private CitStackNames() {}

    static List<String> resolve(class_1799 stack) {
        if (USE_MODERN && MODERN_RESOLVE != null) {
            try {
                @SuppressWarnings("unchecked")
                List<String> names = (List<String>) MODERN_RESOLVE.invoke(null, stack);
                return names != null ? names : Collections.emptyList();
            } catch (Throwable ignored) {}
        }
        return CitStackNamesLegacy.resolve(stack);
    }

    private static Method loadModernResolve() {
        if (!USE_MODERN) return null;
        try {
            Method m = Class.forName("com.slothyhub.cit.CitStackNamesModern")
                .getDeclaredMethod("resolve", class_1799.class);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
