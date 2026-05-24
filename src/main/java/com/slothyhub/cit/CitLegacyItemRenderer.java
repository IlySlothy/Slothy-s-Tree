package com.slothyhub.cit;

import net.minecraft.class_1058;
import net.minecraft.class_1087;
import net.minecraft.class_1799;
import net.minecraft.class_777;
import net.minecraft.class_7923;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** CIT applicator for MC 1.21.2–1.21.3 ItemRenderer (pre render-state pipeline). */
public final class CitLegacyItemRenderer {

    private CitLegacyItemRenderer() {}

    public static class_1087 wrapModel(class_1087 model, class_1799 stack) {
        if (model == null || stack == null || stack.method_7960()) return model;

        CitRuleSet ruleSet = CitRuleSet.active();
        if (ruleSet.isEmpty()) return model;

        String itemId = resolveItemId(stack);
        if (itemId == null) return model;

        List<String> matchNames = CitStackNames.resolve(stack);
        CitRule rule = ruleSet.findMatch(itemId, matchNames);
        if (rule == null || rule.texture.isBlank()) return model;

        class_1058 sprite = CitVirtualTextures.spriteForRule(rule);
        if (sprite == null) return model;

        return wrapWithSprite(model, sprite);
    }

    @SuppressWarnings("unchecked")
    private static class_1087 wrapWithSprite(class_1087 base, class_1058 sprite) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("method_4707".equals(method.getName()) && args != null && args.length == 3) {
                @SuppressWarnings("unchecked")
                List<class_777> quads = (List<class_777>) method.invoke(base, args);
                if (quads == null || quads.isEmpty()) return quads;
                List<class_777> out = new ArrayList<>(quads.size());
                for (class_777 quad : quads) {
                    out.add(CitQuadRemapper.withSprite(quad, sprite));
                }
                return out;
            }
            if ("method_4711".equals(method.getName()) && method.getParameterCount() == 0) {
                return sprite;
            }
            return method.invoke(base, args);
        };
        return (class_1087) Proxy.newProxyInstance(
            class_1087.class.getClassLoader(),
            new Class<?>[] { class_1087.class },
            handler
        );
    }

    private static String resolveItemId(class_1799 stack) {
        try {
            return class_7923.field_41178.method_10221(stack.method_7909()).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
