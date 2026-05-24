package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.McVersion;
import net.minecraft.class_10444;
import net.minecraft.class_1058;
import net.minecraft.class_1087;
import net.minecraft.class_1799;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_777;
import net.minecraft.class_7923;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CIT applicator for MC 1.21.4+.
 *
 * Records matched sprites after bake on each layer; swaps model quads at draw time only.
 */
public final class CitItemRenderer {

    private CitItemRenderer() {}

    private static final Field LAYERS_ARRAY = resolveRenderStateField("field_55340");
    private static final Field LAYER_COUNT = resolveRenderStateField("field_55339");
    private static final Field LAYER_PARENT = resolveLayerField("field_55345");
    private static final Field LAYER_RENDER_PASS = resolveLayerField("field_55348");
    private static final Object NORMAL_RENDER_PASS = resolveNormalRenderPass();
    /** 1.21.4–1.21.7: baked model on layer. Absent on 1.21.8+ (quad-only layers). */
    private static final Field LAYER_BAKED_MODEL = McVersion.below("1.21.8")
        ? resolveLayerField("field_55346") : null;
    private static final Field LAYER_RENDER_LAYER = resolveLayerField("field_55347");
    private static final Field LAYER_ITEM_MODEL = resolveLayerField("field_55350");
    private static final Field LAYER_ITEM_MODEL_DATA = resolveLayerField("field_55351");
    /** 1.21.8+: pre-baked quad list on layer (field_56964). */
    private static final Field LAYER_QUAD_LIST = McVersion.atLeast("1.21.8")
        ? resolveLayerField("field_56964") : null;
    private static final Method SET_LAYER_SPRITE = McVersion.atLeast("1.21.8")
        ? resolveLayerMethod("method_67994", class_1058.class) : null;
    private static final boolean USES_QUAD_LAYERS = LAYER_QUAD_LIST != null;
    private static final Set<String> NO_MATCH_LOGGED = ConcurrentHashMap.newKeySet();

    static {
        if (USES_QUAD_LAYERS) {
            SlothyHubMod.LOGGER.info("CIT: layer patch mode=quad-remap (MC {})", McVersion.current());
        } else if (LAYER_BAKED_MODEL != null) {
            SlothyHubMod.LOGGER.info("CIT: layer patch mode=model-wrap (MC {})", McVersion.current());
        } else {
            SlothyHubMod.LOGGER.warn("CIT: item render fields missing — custom textures may not apply");
        }
    }

    /** Called after vanilla bake — records sprite match on layers, does not mutate models. */
    public static void applyIfNeeded(class_10444 renderState, class_1799 stack, Object manager) {
        if (!SlothyConfig.isCitEnabled()) return;

        CitRuleSet ruleSet = CitRuleSet.active();
        if (ruleSet.isEmpty()) {
            clearLayerSprites(renderState);
            CitRenderCache.clear(renderState);
            return;
        }

        if (stack == null || stack.method_7960()) {
            clearLayerSprites(renderState);
            CitRenderCache.clear(renderState);
            return;
        }

        CitRenderCache.trackStack(renderState, stack);

        class_1058 cachedSprite = CitRenderCache.sprite(renderState);
        if (cachedSprite != null) {
            class_1799 cachedStack = CitRenderCache.stack(renderState);
            if (cachedStack != null && class_1799.method_7973(cachedStack, stack)) {
                assignLayerSprites(renderState, cachedSprite);
                return;
            }
        }

        String itemId = resolveItemId(stack);
        if (itemId == null) {
            clearLayerSprites(renderState);
            CitRenderCache.clear(renderState);
            return;
        }

        List<String> matchNames = CitStackNames.resolve(stack);
        CitRule rule = ruleSet.findMatch(itemId, matchNames, stack);
        if (rule == null || rule.texture.isBlank()) {
            clearLayerSprites(renderState);
            CitRenderCache.clear(renderState);
            if ("minecraft:netherite_sword".equals(itemId)) {
                String key = matchNames.toString();
                if (NO_MATCH_LOGGED.add(key)) {
                    SlothyHubMod.LOGGER.info("CIT: no match for netherite_sword (names={})", matchNames);
                }
            }
            return;
        }

        class_1058 sprite = CitVirtualTextures.spriteForRule(rule);
        if (sprite == null || !CitVirtualTextures.isAtlasSprite(sprite)) {
            clearLayerSprites(renderState);
            CitRenderCache.clear(renderState);
            SlothyHubMod.LOGGER.debug("CIT: no atlas sprite for '{}' ({})", rule.texture, rule.id);
            return;
        }

        assignLayerSprites(renderState, sprite);
        class_2960 texId = CitVirtualTextures.textureForRule(rule);
        if (texId == null) {
            try { texId = sprite.method_45852(); } catch (Exception ignored) {}
        }
        CitRenderCache.remember(renderState, stack, sprite, texId);
        SlothyHubMod.LOGGER.info("CIT: matched {} for {} (names={})", rule.id, itemId, matchNames);
    }

    /** Called from {@link com.slothyhub.mixin.MixinCitItemLayerPrepareDraw} right before each layer draws. */
    public static void prepareLayerForDraw(class_10444.class_10446 layer) {
        restoreItemModelDrawPath(layer);
        CitDrawContext.end();
        if (!SlothyConfig.isCitEnabled() || layer == null) return;
        if (!isNormalRenderPass(layer)) return;

        class_1058 sprite = ((CitLayerAccess) (Object) layer).slothyhub$getCitSprite();
        if (sprite == null) return;
        if (!CitVirtualTextures.isAtlasSprite(sprite)) return;

        if (USES_QUAD_LAYERS) {
            // 1.21.8 path — unchanged from when CIT was working
            if (SET_LAYER_SPRITE != null) {
                try { SET_LAYER_SPRITE.invoke(layer, sprite); } catch (Exception ignored) {}
            }
            prepareQuadLayer(layer, sprite);
            return;
        }

        // 1.21.4–1.21.7: ItemModel path wins over baked model — temporarily bypass it for CIT wrap
        preferBakedModelDrawPath(layer, sprite);
    }

    /** Called from {@link com.slothyhub.mixin.MixinCitItemLayerPrepareDraw} after each layer draw. */
    public static void finishLayerDraw(class_10444.class_10446 layer) {
        if (layer != null) {
            restoreItemModelDrawPath(layer);
        }
        CitDrawContext.end();
    }

    private static void assignLayerSprites(class_10444 renderState, class_1058 sprite) {
        if (LAYERS_ARRAY == null || sprite == null) return;
        try {
            class_10444.class_10446[] layers = (class_10444.class_10446[]) LAYERS_ARRAY.get(renderState);
            if (layers == null) return;

            int count = layers.length;
            if (LAYER_COUNT != null) {
                count = Math.min(count, LAYER_COUNT.getInt(renderState));
            }

            for (int i = 0; i < count; i++) {
                class_10444.class_10446 layer = layers[i];
                if (layer == null || !isNormalRenderPass(layer)) continue;
                ((CitLayerAccess) (Object) layer).slothyhub$setCitSprite(sprite);
                // 1.21.4 only — bake-time wrap; 1.21.8 applies quads at draw prep (already worked)
                if (!USES_QUAD_LAYERS) {
                    wrapLayerModel114(layer, sprite);
                }
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: assign layer sprites failed: {}", e.getMessage());
        }
    }

    private static void clearLayerSprites(class_10444 renderState) {
        if (LAYERS_ARRAY == null) return;
        try {
            class_10444.class_10446[] layers = (class_10444.class_10446[]) LAYERS_ARRAY.get(renderState);
            if (layers == null) return;
            for (class_10444.class_10446 layer : layers) {
                if (layer == null) continue;
                unwrapLayerModel(layer);
                ((CitLayerAccess) (Object) layer).slothyhub$setCitSprite(null);
            }
        } catch (Exception ignored) {}
        CitDrawContext.end();
    }

    private static void unwrapLayerModel(class_10444.class_10446 layer) {
        if (LAYER_BAKED_MODEL == null) return;
        try {
            class_1087 current = (class_1087) LAYER_BAKED_MODEL.get(layer);
            if (current != null && CitLegacyItemRenderer.isProxy(current)) {
                LAYER_BAKED_MODEL.set(layer, CitLegacyItemRenderer.unwrap(current));
            }
        } catch (Exception ignored) {}
    }

    /** 1.21.4–1.21.7: wrap the layer baked model so getQuads() returns CIT sprite UVs. */
    private static void wrapLayerModel114(class_10444.class_10446 layer, class_1058 sprite) {
        if (LAYER_BAKED_MODEL == null || sprite == null) return;
        try {
            class_1087 model = (class_1087) LAYER_BAKED_MODEL.get(layer);
            if (model == null) return;
            if (CitLegacyItemRenderer.isWrappedWithSprite(model, sprite)) return;
            LAYER_BAKED_MODEL.set(layer,
                CitLegacyItemRenderer.wrapWithSprite(CitLegacyItemRenderer.unwrap(model), sprite));
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: wrap layer model failed: {}", e.getMessage());
        }
    }

    /**
     * 1.21.4–1.21.7: stash ItemModel type and use baked-model draw for this layer only.
     * field_55346 stays populated from setSpecialModel — safe unlike nulling without a model.
     */
    private static void preferBakedModelDrawPath(class_10444.class_10446 layer, class_1058 sprite) {
        if (LAYER_ITEM_MODEL == null || LAYER_BAKED_MODEL == null) return;
        try {
            Object specialModel = LAYER_ITEM_MODEL.get(layer);
            class_1087 model = (class_1087) LAYER_BAKED_MODEL.get(layer);
            if (specialModel == null || model == null) return;

            CitLayerAccess access = (CitLayerAccess) (Object) layer;
            if (access.slothyhub$peekStashedSpecialModel() == null) {
                Object data = LAYER_ITEM_MODEL_DATA != null ? LAYER_ITEM_MODEL_DATA.get(layer) : null;
                access.slothyhub$stashSpecialModel(specialModel, data);
                LAYER_ITEM_MODEL.set(layer, null);
            }
            wrapLayerModel114(layer, sprite);
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: prefer baked draw path failed: {}", e.getMessage());
        }
    }

    private static void restoreItemModelDrawPath(class_10444.class_10446 layer) {
        if (LAYER_ITEM_MODEL == null) return;
        try {
            CitLayerAccess access = (CitLayerAccess) (Object) layer;
            Object stashedModel = access.slothyhub$peekStashedSpecialModel();
            if (stashedModel == null) return;

            LAYER_ITEM_MODEL.set(layer, stashedModel);
            if (LAYER_ITEM_MODEL_DATA != null) {
                LAYER_ITEM_MODEL_DATA.set(layer, access.slothyhub$peekStashedSpecialData());
            }
            access.slothyhub$restoreSpecialModel();
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: restore item model draw path failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void prepareQuadLayer(class_10444.class_10446 layer, class_1058 sprite) {
        try {
            List<class_777> quads;
            if (LAYER_QUAD_LIST != null) {
                quads = (List<class_777>) LAYER_QUAD_LIST.get(layer);
            } else {
                Method getter = layer.getClass().getMethod("method_67997");
                quads = (List<class_777>) getter.invoke(layer);
            }
            if (quads == null || quads.isEmpty()) return;

            class_777 first = quads.get(0);
            class_1058 onQuad = CitQuadRemapper.getSprite(first);
            if (onQuad == sprite) return;

            List<class_777> remapped = new ArrayList<>(quads.size());
            for (class_777 quad : quads) {
                remapped.add(CitQuadRemapper.withSprite(quad, sprite));
            }
            if (LAYER_QUAD_LIST != null) {
                LAYER_QUAD_LIST.set(layer, remapped);
            } else {
                quads.clear();
                quads.addAll(remapped);
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: quad draw prep failed: {}", e.getMessage());
        }
    }

    private static String resolveItemId(class_1799 stack) {
        try {
            return class_7923.field_41178.method_10221(stack.method_7909()).toString();
        } catch (Exception e) { return null; }
    }

    static String resolveDisplayName(class_1799 stack) {
        List<String> names = CitStackNames.resolve(stack);
        return names.isEmpty() ? null : names.get(0);
    }

    static List<String> resolveMatchNames(class_1799 stack) {
        return CitStackNames.resolve(stack);
    }

    static String stripFormatting(String text) {
        if (text == null) return null;
        String plain = text.replaceAll("§.", "").trim();
        // Hypixel / server rank prefixes: [MVP+] Warden Sword
        plain = plain.replaceAll("(?i)\\[[^\\]]+\\]\\s*", "").trim();
        return plain;
    }

    /** Remaps quads when a CIT sprite is active for the current layer draw. */
    public static List<class_777> remapQuadsForDraw(List<class_777> quads) {
        class_1058 sprite = CitDrawContext.active();
        if (sprite == null || quads == null || quads.isEmpty()) return quads;
        if (!CitVirtualTextures.isAtlasSprite(sprite)) return quads;

        class_777 first = quads.get(0);
        class_1058 onQuad = CitQuadRemapper.getSprite(first);
        if (onQuad == sprite) return quads;

        List<class_777> out = new ArrayList<>(quads.size());
        for (class_777 quad : quads) {
            out.add(CitQuadRemapper.withSprite(quad, sprite));
        }
        return out;
    }

    public static void endDrawContext() {
        CitDrawContext.end();
    }

    public static void clearRenderCache(class_10444 renderState) {
        clearLayerSprites(renderState);
        CitRenderCache.clear(renderState);
    }

    public static void trackStackForRender(class_10444 renderState, class_1799 stack) {
        CitRenderCache.trackStack(renderState, stack);
    }

    public static class_1799 trackedStack(class_10444 renderState) {
        return CitRenderCache.stack(renderState);
    }

    private static boolean isNormalRenderPass(class_10444.class_10446 layer) {
        if (LAYER_RENDER_PASS == null || NORMAL_RENDER_PASS == null) return true;
        try {
            Object pass = LAYER_RENDER_PASS.get(layer);
            return pass == null || pass == NORMAL_RENDER_PASS;
        } catch (Exception e) {
            return true;
        }
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


    private static Method resolveLayerMethod(String name, Class<?>... params) {
        try {
            Method m = class_10444.class_10446.class.getMethod(name, params);
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
