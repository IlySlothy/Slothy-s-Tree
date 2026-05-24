package com.slothyhub.cit;

import net.minecraft.class_1799;
import net.minecraft.class_2561;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Pre-1.20.5 CIT name resolution via NBT display tags (reflection — compile target is 1.21.8). */
final class CitStackNamesLegacy {

    private CitStackNamesLegacy() {}

    static List<String> resolve(class_1799 stack) {
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        java.util.function.Consumer<String> add = raw -> {
            if (raw == null) return;
            String plain = CitItemRenderer.stripFormatting(raw);
            if (plain.isBlank()) return;
            String key = plain.toLowerCase(Locale.ROOT);
            if (seen.add(key)) out.add(plain);
        };

        try {
            class_2561 display = stack.method_7964();
            if (display != null) add.accept(display.getString());

            Object nbt = invokeNoArg(stack, "method_7969", "getNbt", "getTag");
            if (nbt == null) return out;

            Object displayTag = invokeOneArg(nbt, "display", "method_10562", "getCompound");
            if (displayTag != null) {
                if (containsTag(displayTag, "Name", 8)) {
                    String name = readString(displayTag, "Name", "method_10558", "getString");
                    if (name != null) add.accept(name);
                }
                if (containsTag(displayTag, "Lore", 9)) {
                    Object lore = invokeOneArg(displayTag, "Lore", "method_10554", "getList");
                    int size = readSize(lore);
                    for (int i = 0; i < size; i++) {
                        Object line = invokeOneArgInt(lore, i, "method_10602", "get");
                        String text = readNbtString(line);
                        if (text != null) add.accept(text);
                    }
                }
            }

            if (containsTag(nbt, "CustomModelData", 99)) {
                add.accept(String.valueOf(readInt(nbt, "CustomModelData")));
            }
        } catch (Throwable ignored) {}

        return out;
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static Object invokeOneArg(Object target, String arg, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name, String.class);
                m.setAccessible(true);
                Object result = m.invoke(target, arg);
                return unwrapOptional(result);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static Object invokeOneArgInt(Object target, int index, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method m = target.getClass().getMethod(name, int.class);
                m.setAccessible(true);
                Object result = m.invoke(target, index);
                return unwrapOptional(result);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static int readSize(Object list) {
        for (String name : new String[]{"size", "method_10546"}) {
            try {
                Method m = list.getClass().getMethod(name);
                m.setAccessible(true);
                Object result = m.invoke(list);
                if (result instanceof Number n) return n.intValue();
            } catch (ReflectiveOperationException ignored) {}
        }
        return 0;
    }

    private static int readInt(Object target, String field) {
        for (String name : new String[]{"method_10550", "getInt"}) {
            try {
                Method m = target.getClass().getMethod(name, String.class);
                m.setAccessible(true);
                Object result = m.invoke(target, field);
                if (result instanceof Number n) return n.intValue();
            } catch (ReflectiveOperationException ignored) {}
        }
        return 0;
    }

    private static boolean containsTag(Object nbt, String key, int type) {
        for (String name : new String[]{"method_10573", "contains"}) {
            try {
                Method m = nbt.getClass().getMethod(name, String.class, int.class);
                m.setAccessible(true);
                Object result = m.invoke(nbt, key, type);
                if (result instanceof Boolean b) return b;
            } catch (ReflectiveOperationException ignored) {}
            try {
                Method m = nbt.getClass().getMethod(name, String.class);
                m.setAccessible(true);
                Object result = m.invoke(nbt, key);
                if (result instanceof Boolean b) return b;
            } catch (ReflectiveOperationException ignored) {}
        }
        return false;
    }

    private static String readString(Object nbt, String key, String... names) {
        for (String name : names) {
            try {
                Method m = nbt.getClass().getMethod(name, String.class);
                m.setAccessible(true);
                Object result = m.invoke(nbt, key);
                result = unwrapOptional(result);
                if (result instanceof String s) return s;
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    private static String readNbtString(Object line) {
        if (line == null) return null;
        for (String name : new String[]{"method_10714", "asString", "getAsString"}) {
            try {
                Method m = line.getClass().getMethod(name);
                m.setAccessible(true);
                Object result = m.invoke(line);
                if (result instanceof String s) return s;
            } catch (ReflectiveOperationException ignored) {}
        }
        return line.toString();
    }

    private static Object unwrapOptional(Object value) {
        if (value == null) return null;
        if (value instanceof java.util.Optional<?> opt) return opt.orElse(null);
        return value;
    }
}
