package com.slothyhub.cit;

import net.minecraft.class_1921;
import net.minecraft.class_2960;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CitRenderLayers {

    private static final Method ITEM_TEXTURED = resolveItemTexturedLayer();

    private CitRenderLayers() {}

    static class_1921 forTexture(class_2960 textureId) {
        if (textureId == null || ITEM_TEXTURED == null) return null;
        try {
            return (class_1921) ITEM_TEXTURED.invoke(null, textureId);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Method resolveItemTexturedLayer() {
        for (String name : new String[]{
            "getItemEntityTranslucentCull",
            "getEntityTranslucentCull",
            "getEntityCutoutNoCull",
            "getEntityTranslucent",
            "method_23689",
            "method_23583",
            "method_23031",
            "method_23028"
        }) {
            try {
                Method m = class_1921.class.getMethod(name, class_2960.class);
                if (Modifier.isStatic(m.getModifiers()) && class_1921.class.isAssignableFrom(m.getReturnType())) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (ReflectiveOperationException ignored) {}
        }

        List<Method> cands = new ArrayList<>();
        for (Method m : class_1921.class.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) continue;
            if (m.getParameterTypes()[0] != class_2960.class) continue;
            if (!class_1921.class.isAssignableFrom(m.getReturnType())) continue;
            String n = m.getName().toLowerCase();
            if (n.contains("entity") || n.contains("item")) cands.add(m);
        }
        if (cands.isEmpty()) return null;
        cands.sort(Comparator.comparing(Method::getName));
        Method pick = cands.get(0);
        pick.setAccessible(true);
        return pick;
    }
}