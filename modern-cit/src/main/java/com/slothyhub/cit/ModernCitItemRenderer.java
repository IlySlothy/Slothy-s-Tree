package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.DrawHelper;
import net.minecraft.class_10444;
import net.minecraft.class_1921;
import net.minecraft.class_1058;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_777;
import net.minecraft.class_7923;
import net.minecraft.class_811;
import net.minecraft.class_918;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModernCitItemRenderer {

    private static final AtomicBoolean FIRST_APPLY_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean FIRST_PREPARE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean FIRST_NOTE_LOGGED = new AtomicBoolean(false);

    /**
     * Held ItemStack for the current render-state build on the current thread.
     * Set by {@link com.slothyhub.mixin.MixinCitItemRenderState} at HEAD of every
     * stack-aware entry on {@code class_10442}, consumed at draw time so we do
     * not depend on capturing the stack onto the render-state instance.
     */
    private static final ThreadLocal<class_1799> CURRENT_STACK = new ThreadLocal<>();

    private static final Field LAYERS_ARRAY = resolveLayersField();
    private static final Field LAYER_COUNT = resolveLayerCountField();
    private static final Field LAYER_QUAD_LIST = resolveLayerField("field_56964");
    private static final Field LAYER_RENDER_LAYER = resolveLayerField("field_55347");
    private static final Field LAYER_RENDER_PASS = resolveLayerField("field_55348");
    private static final Field LAYER_PARENT = resolveLayerField("field_55345");
    private static final Field RENDER_STATE_TRANSFORM = resolveRenderStateField("field_55337");
    private static final Object NORMAL_RENDER_PASS = resolveNormalRenderPass();
    private static final Method QUAD_LAYER_DRAW = resolveQuadLayerDraw();

    static {
        SlothyHubMod.LOGGER.info(
            "Modern CIT renderer: layers={} quads={} draw={} (MC {})",
            LAYERS_ARRAY != null, LAYER_QUAD_LIST != null, QUAD_LAYER_DRAW != null,
            com.slothyhub.compat.McVersion.current());
    }

    private ModernCitItemRenderer() {}

    /**
     * Records the held stack against the render state being built.
     * Called from the class_10442 mixin at HEAD of every stack→state entry; this
     * is the only thing keeping {@link #prepareDraw(class_10444)} from logging
     * {@code cachedStack=null} when the older state-builder mixins fail to bind.
     */
    public static void noteStack(class_10444 renderState, class_1799 stack) {
        if (renderState == null || stack == null) return;
        if (FIRST_NOTE_LOGGED.compareAndSet(false, true)) {
            SlothyHubMod.LOGGER.info(
                "Modern CIT: noteStack first call (stack={})",
                stack.method_7960() ? "empty" : resolveItemId(stack));
        }
        if (stack.method_7960()) return;
        CURRENT_STACK.set(stack);
        CitRenderCache.trackStack(renderState, stack);
        applyIfNeeded(renderState, stack, null);
    }

    public static void applyIfNeeded(class_10444 renderState, class_1799 stack, Object manager) {
        if (!SlothyConfig.isCitEnabled()) return;
        CitRuleSet ruleSet = CitRuleSet.active();
        if (FIRST_APPLY_LOGGED.compareAndSet(false, true)) {
            SlothyHubMod.LOGGER.info(
                "Modern CIT: applyIfNeeded first call (ruleSet empty={}, layersField={}, stack={})",
                ruleSet.isEmpty(), LAYERS_ARRAY != null,
                stack == null ? "null" : resolveItemId(stack));
        }
        if (ruleSet.isEmpty()) return;
        if (LAYERS_ARRAY == null) {
            logLayersMissingOnce();
            return;
        }
        String itemId = resolveItemId(stack);
        if (itemId == null) return;
        List<String> matchNames = CitStackNames.resolve(stack);
        CitRule rule = ruleSet.findMatch(itemId, matchNames);
        if (rule == null || rule.texture.isBlank()) {
            CitRenderCache.clear(renderState);
            logNearMiss(itemId, matchNames);
            return;
        }
        class_1058 sprite = CitVirtualTextures.spriteForRule(rule);
        if (sprite == null || !CitVirtualTextures.isAtlasSprite(sprite)) {
            CitRenderCache.clear(renderState);
            return;
        }
        class_2960 texId = CitVirtualTextures.textureForRule(rule);
        if (texId == null) {
            try { texId = sprite.method_45852(); } catch (Exception ignored) {}
        }
        CitRenderCache.remember(renderState, stack, sprite, texId);
        applySpriteToLayers(renderState, sprite, texId);
        logMatchOnce(itemId, rule, matchNames);
    }

    public static void clearRenderCache(class_10444 renderState) {
        CitRenderCache.clear(renderState);
    }

    public static void patchBeforeDraw(class_10444 renderState, class_10444.class_10446 layer) {
        if (renderState == null || layer == null) return;
        class_1058 sprite = CitRenderCache.sprite(renderState);
        class_2960 textureId = CitRenderCache.texture(renderState);
        if (sprite == null || textureId == null) return;
        patchLayer(layer, sprite, textureId);
    }

    public static void prepareDraw(class_10444 renderState) {
        class_1799 stack = renderState == null ? null : CitRenderCache.stack(renderState);
        if (stack == null || stack.method_7960()) {
            class_1799 threadStack = CURRENT_STACK.get();
            if (threadStack != null && !threadStack.method_7960()) stack = threadStack;
        }
        if (FIRST_PREPARE_LOGGED.compareAndSet(false, true)) {
            SlothyHubMod.LOGGER.info(
                "Modern CIT: prepareDraw first call (renderState={}, stack={}, source={})",
                renderState != null,
                stack == null ? "null" : resolveItemId(stack),
                stack == null ? "none" : (CitRenderCache.stack(renderState) != null ? "cache" : "threadLocal"));
        }
        if (renderState == null) return;
        if (stack != null && !stack.method_7960()) {
            CitRenderCache.trackStack(renderState, stack);
            applyIfNeeded(renderState, stack, null);
        }
        patchAllLayersBeforeDraw(renderState);
    }

    public static void patchAllLayersBeforeDraw(class_10444 renderState) {
        if (renderState == null) return;
        class_1058 sprite = CitRenderCache.sprite(renderState);
        class_2960 textureId = CitRenderCache.texture(renderState);
        if (sprite == null || textureId == null) return;
        if (LAYERS_ARRAY != null) {
            try {
                class_10444.class_10446[] layers = (class_10444.class_10446[]) LAYERS_ARRAY.get(renderState);
                if (layers != null) {
                    int count = activeLayerCount(renderState);
                    for (int i = 0; i < count && i < layers.length; i++) {
                        if (layers[i] != null) patchLayer(layers[i], sprite, textureId);
                    }
                    return;
                }
            } catch (Exception ignored) {}
        }
        try {
            class_10444.class_10446 layer = renderState.method_65601();
            if (layer != null) patchLayer(layer, sprite, textureId);
        } catch (Exception ignored) {}
    }

    public static boolean tryDrawCitLayer(
        class_10444 renderState,
        class_10444.class_10446 layer,
        class_811 transform,
        class_4587 matrices,
        class_4597 vertexConsumers,
        int light,
        int overlay
    ) {
        if (!SlothyConfig.isCitEnabled() || renderState == null || layer == null) return false;
        if (!isNormalRenderPass(layer)) return false;
        class_1058 sprite = CitRenderCache.sprite(renderState);
        class_2960 textureId = CitRenderCache.texture(renderState);
        if (sprite == null || textureId == null || !CitVirtualTextures.isAtlasSprite(sprite)) return false;
        if (QUAD_LAYER_DRAW == null) return false;
        List<class_777> quads = getQuads(layer);
        if (quads == null || quads.isEmpty()) return false;
        List<class_777> remapped = remapQuads(quads, sprite);
        class_1921 renderLayer = resolveRenderLayer(layer, textureId);
        if (renderLayer == null) return false;
        try {
            class_10444.class_10445 renderPass = null;
            if (LAYER_RENDER_PASS != null) {
                renderPass = (class_10444.class_10445) LAYER_RENDER_PASS.get(layer);
            }
            class_811 layerTransform = transform;
            if (LAYER_PARENT != null && RENDER_STATE_TRANSFORM != null) {
                class_10444 parent = (class_10444) LAYER_PARENT.get(layer);
                if (parent != null) {
                    class_811 t = (class_811) RENDER_STATE_TRANSFORM.get(parent);
                    if (t != null) layerTransform = t;
                }
            }
            int[] tints = layer.method_65613(0);
            QUAD_LAYER_DRAW.invoke(null,
                layerTransform, matrices, vertexConsumers, light, overlay,
                tints, remapped, renderLayer, renderPass);
            return true;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("Modern CIT: quad draw failed: {}", e.getMessage());
            return false;
        }
    }

    private static void patchLayer(class_10444.class_10446 layer, class_1058 sprite, class_2960 textureId) {
        if (layer == null || sprite == null) return;
        class_1921 renderLayer = resolveRenderLayer(layer, textureId);
        if (renderLayer != null) {
            try { layer.method_67992(renderLayer); } catch (Exception ignored) {}
        }
        List<class_777> quads = getQuads(layer);
        if (quads == null || quads.isEmpty()) return;
        try { layer.method_67994(sprite); } catch (Exception ignored) {}
        setQuads(layer, remapQuads(quads, sprite));
    }

    private static List<class_777> remapQuads(List<class_777> quads, class_1058 sprite) {
        class_777 first = quads.get(0);
        if (CitQuadRemapper.getSprite(first) == sprite) return quads;
        List<class_777> out = new ArrayList<>(quads.size());
        for (class_777 quad : quads) out.add(CitQuadRemapper.withSprite(quad, sprite));
        return out;
    }

    private static class_1921 resolveRenderLayer(class_10444.class_10446 layer, class_2960 textureId) {
        class_1921 cit = CitRenderLayers.forTexture(textureId);
        if (cit != null) return cit;
        cit = DrawHelper.entityCutoutLayer(textureId);
        if (cit != null) return cit;
        if (LAYER_RENDER_LAYER != null) {
            try { return (class_1921) LAYER_RENDER_LAYER.get(layer); } catch (Exception ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<class_777> getQuads(class_10444.class_10446 layer) {
        if (LAYER_QUAD_LIST != null) {
            try { return (List<class_777>) LAYER_QUAD_LIST.get(layer); } catch (Exception ignored) {}
        }
        try { return layer.method_67997(); } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void setQuads(class_10444.class_10446 layer, List<class_777> quads) {
        if (LAYER_QUAD_LIST != null) {
            try { LAYER_QUAD_LIST.set(layer, quads); return; } catch (Exception ignored) {}
        }
        try {
            List<class_777> existing = layer.method_67997();
            if (existing != null) { existing.clear(); existing.addAll(quads); }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void applySpriteToLayers(class_10444 renderState, class_1058 sprite, class_2960 textureId) {
        if (LAYERS_ARRAY == null || sprite == null) return;
        try {
            class_10444.class_10446[] layers = (class_10444.class_10446[]) LAYERS_ARRAY.get(renderState);
            if (layers == null) return;
            int count = activeLayerCount(renderState);
            for (int i = 0; i < count && i < layers.length; i++) {
                class_10444.class_10446 layer = layers[i];
                if (layer != null) patchLayer(layer, sprite, textureId);
            }
        } catch (Exception ignored) {}
    }

    private static int activeLayerCount(class_10444 renderState) {
        if (LAYER_COUNT != null) {
            try { return Math.max(0, LAYER_COUNT.getInt(renderState)); } catch (Exception ignored) {}
        }
        return renderState.method_65606() ? 0 : 1;
    }

    private static boolean isNormalRenderPass(class_10444.class_10446 layer) {
        if (LAYER_RENDER_PASS == null || NORMAL_RENDER_PASS == null) return true;
        try {
            Object pass = LAYER_RENDER_PASS.get(layer);
            return pass == null || pass == NORMAL_RENDER_PASS;
        } catch (Exception e) { return true; }
    }

    private static String resolveItemId(class_1799 stack) {
        try { return class_7923.field_41178.method_10221(stack.method_7909()).toString(); }
        catch (Exception e) { return null; }
    }

    // Match / near-miss diagnostics are silent by default - they fire on every rendered
    // frame and can produce thousands of log lines per minute. Enable with
    // -Dslothyhub.debug.cit=true when investigating issues.
    private static final boolean CIT_DEBUG_LOG =
        Boolean.parseBoolean(System.getProperty("slothyhub.debug.cit", "false"));
    private static final java.util.Map<String, Long> LAST_MATCH_LOG_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> LAST_NEAR_MISS_LOG_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long LOG_THROTTLE_MS = 8000L;
    private static boolean layersMissingLogged;

    private static void logLayersMissingOnce() {
        if (layersMissingLogged) return;
        layersMissingLogged = true;
        SlothyHubMod.LOGGER.warn("Modern CIT: item render layers field not found — CIT draw disabled on MC {}",
            com.slothyhub.compat.McVersion.current());
    }

    private static void logMatchOnce(String itemId, CitRule rule, List<String> names) {
        if (!CIT_DEBUG_LOG) return;
        String key = rule.id + "|" + itemId;
        long now = System.currentTimeMillis();
        Long last = LAST_MATCH_LOG_MS.get(key);
        if (last != null && now - last < LOG_THROTTLE_MS) return;
        LAST_MATCH_LOG_MS.put(key, now);
        SlothyHubMod.LOGGER.info("Modern CIT: matched {} for {} (names={})", rule.id, itemId, names);
    }

    private static void logNearMiss(String itemId, List<String> names) {
        if (!CIT_DEBUG_LOG) return;
        if (!itemId.contains("netherite_sword")) return;
        String key = itemId + "|" + String.join(",", names);
        long now = System.currentTimeMillis();
        Long last = LAST_NEAR_MISS_LOG_MS.get(key);
        if (last != null && now - last < LOG_THROTTLE_MS) return;
        LAST_NEAR_MISS_LOG_MS.put(key, now);
        if (names == null || names.isEmpty()) {
            SlothyHubMod.LOGGER.info("Modern CIT: no rule matched {} (no display strings)", itemId);
        } else {
            SlothyHubMod.LOGGER.info("Modern CIT: no rule matched {} names={}", itemId, names);
        }
    }

    private static Field resolveLayersField() {
        Class<?> layerType = class_10444.class_10446.class;
        for (Field f : class_10444.class.getDeclaredFields()) {
            if (!f.getType().isArray()) continue;
            Class<?> component = f.getType().getComponentType();
            if (component == null || !layerType.isAssignableFrom(component)) continue;
            f.setAccessible(true);
            return f;
        }
        try {
            Field f = class_10444.class.getDeclaredField("field_55340");
            f.setAccessible(true);
            return f;
        } catch (Exception e) { return null; }
    }

    private static Field resolveLayerCountField() {
        try {
            Field f = class_10444.class.getDeclaredField("field_55339");
            f.setAccessible(true);
            return f;
        } catch (Exception ignored) {}
        for (Field f : class_10444.class.getDeclaredFields()) {
            if (f.getType() == int.class) { f.setAccessible(true); return f; }
        }
        return null;
    }

    private static Field resolveRenderStateField(String name) {
        try {
            Field f = class_10444.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception ignored) {}
        return null;
    }

    private static Field resolveLayerField(String name) {
        try {
            Field f = class_10444.class_10446.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Exception ignored) {}
        return null;
    }

    private static Method resolveQuadLayerDraw() {
        try {
            Method m = class_918.class.getMethod("method_62476",
                class_811.class, class_4587.class, class_4597.class,
                int.class, int.class, int[].class,
                List.class, class_1921.class, class_10444.class_10445.class);
            m.setAccessible(true);
            return m;
        } catch (Exception ignored) {}
        return null;
    }

    private static Object resolveNormalRenderPass() {
        try {
            Field f = class_10444.class_10445.class.getDeclaredField("field_55341");
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception ignored) {}
        return null;
    }
}
