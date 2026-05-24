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

/** CIT applicator for MC 1.20–1.21.3 ItemRenderer (pre render-state pipeline). */
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

    public static class_1087 wrapWithSprite(class_1087 base, class_1058 sprite) {
        if (base == null || sprite == null) return base;
        class_1087 unwrapped = unwrap(base);
        if (Proxy.isProxyClass(base.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(base);
            if (handler instanceof SpriteSwapHandler existing
                && existing.base == unwrapped
                && existing.sprite == sprite) {
                return base;
            }
        }
        return (class_1087) Proxy.newProxyInstance(
            class_1087.class.getClassLoader(),
            new Class<?>[] { class_1087.class },
            new SpriteSwapHandler(unwrapped, sprite)
        );
    }

    public static class_1087 unwrap(class_1087 model) {
        if (model != null && Proxy.isProxyClass(model.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(model);
            if (handler instanceof SpriteSwapHandler swap) {
                return swap.base;
            }
        }
        return model;
    }

    public static boolean isWrappedWithSprite(class_1087 model, class_1058 sprite) {
        if (model == null || sprite == null || !Proxy.isProxyClass(model.getClass())) return false;
        InvocationHandler handler = Proxy.getInvocationHandler(model);
        return handler instanceof SpriteSwapHandler swap && swap.sprite == sprite;
    }

    public static boolean isProxy(class_1087 model) {
        return model != null && Proxy.isProxyClass(model.getClass());
    }

    private static String resolveItemId(class_1799 stack) {
        try {
            return class_7923.field_41178.method_10221(stack.method_7909()).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class SpriteSwapHandler implements InvocationHandler {
        final class_1087 base;
        final class_1058 sprite;

        private SpriteSwapHandler(class_1087 base, class_1058 sprite) {
            this.base = base;
            this.sprite = sprite;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (args == null) {
                args = new Object[0];
            }
            if (List.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 3) {
                @SuppressWarnings("unchecked")
                List<class_777> quads = (List<class_777>) method.invoke(base, args);
                if (quads == null || quads.isEmpty()) return quads;
                List<class_777> out = new ArrayList<>(quads.size());
                for (class_777 quad : quads) {
                    out.add(CitQuadRemapper.withSprite(quad, sprite));
                }
                return out;
            }
            if (class_1058.class.isAssignableFrom(method.getReturnType()) && method.getParameterCount() == 0) {
                return sprite;
            }
            return method.invoke(base, args);
        }
    }
}
