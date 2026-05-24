package com.slothyhub.compat;

import net.minecraft.class_2960;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** {@link class_2960} factory that works on MC 1.20–1.21.8 (of vs legacy constructor). */
public final class Identifiers {

    private static final Method OF_METHOD;
    private static final Constructor<?> LEGACY_CTOR;

    private Identifiers() {}

    public static class_2960 of(String namespace, String path) {
        if (OF_METHOD != null) {
            try {
                return (class_2960) OF_METHOD.invoke(null, namespace, path);
            } catch (ReflectiveOperationException ignored) {}
        }
        if (LEGACY_CTOR != null) {
            try {
                return (class_2960) LEGACY_CTOR.newInstance(namespace, path);
            } catch (ReflectiveOperationException ignored) {}
        }
        throw new IllegalStateException("Could not create Identifier for " + namespace + ":" + path);
    }

    static {
        Method of = null;
        Constructor<?> legacy = null;
        for (String name : new String[]{"method_60655", "of"}) {
            try {
                Method m = class_2960.class.getMethod(name, String.class, String.class);
                m.setAccessible(true);
                of = m;
                break;
            } catch (ReflectiveOperationException ignored) {}
        }
        try {
            Constructor<?> c = class_2960.class.getConstructor(String.class, String.class);
            c.setAccessible(true);
            legacy = c;
        } catch (ReflectiveOperationException ignored) {}
        OF_METHOD = of;
        LEGACY_CTOR = legacy;
    }
}
