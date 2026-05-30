package com.slothyhub.cit;

import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.DrawHelper;
import net.minecraft.class_10444;
import net.minecraft.class_1921;
import net.minecraft.class_1058;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_777;
import net.minecraft.class_7923;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * CIT render-time applicator for MC 1.21.8.
 *
 * Called from {@link com.slothyhub.mixin.MixinCitItemRenderState} at the TAIL of
 * class_10442.method_65598 after item quads are baked.
 */
public final class CitItemRenderer {

    private CitItemRenderer() {}

    // ── Cached reflection handles (model override path only) ────────────────

    /** Legacy model field inside each layer (only used when rule.model is set). */
    private static volatile Field LAYER_MODEL_FIELD;

    /** ItemRenderState layer array (field name varies 1.21.4–1.21.8). */
    private static final Field LAYERS_ARRAY = resolveLayersField();
    /** Active layer count field on ItemRenderState. */
    private static final Field LAYER_COUNT = resolveLayerCountField();

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
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("CIT: could not resolve item render layer array");
            return null;
        }
    }

    private static Field resolveLayerCountField() {
        try {
            Field f = class_10444.class.getDeclaredField("field_55339");
            f.setAccessible(true);
            return f;
        } catch (Exception ignored) {}
        for (Field f : class_10444.class.getDeclaredFields()) {
            if (f.getType() == int.class) {
                f.setAccessible(true);
                return f;
            }
        }
        SlothyHubMod.LOGGER.warn("CIT: could not resolve item render layer count");
        return null;
    }

    /**
     * Function fields on class_10442 (ItemRenderStateManager) used to look up
     * item models by identifier. We store them as raw Object so we can try each.
     */
    private static volatile Function<?,?> MANAGER_FUNC_A;
    private static volatile Function<?,?> MANAGER_FUNC_B;

    private static volatile boolean RESOLVED = false;

    // ─────────────────────────────────────────────────────────────────────

    public static void applyIfNeeded(class_10444 renderState, class_1799 stack, Object manager) {
        CitRuleSet ruleSet = CitRuleSet.active();
        if (ruleSet.isEmpty() || LAYERS_ARRAY == null) return;

        ensureModelFieldResolved(renderState, manager);

        String itemId = resolveItemId(stack);
        if (itemId == null) return;

        List<String> matchNames = CitStackNames.resolve(stack);
        CitRule rule = ruleSet.findMatch(itemId, matchNames);
        if (rule == null || rule.texture.isBlank()) {
            CitRenderCache.clear(renderState);
            logNearMiss(itemId, matchNames);
            return;
        }

        logMatchOnce(itemId, rule, matchNames);

        // Only use explicit model overrides — texture-only CIT rules must use virtual sprites
        if (!rule.model.isBlank()) {
            Object overrideModel = findOverrideModel(manager, rule, stack);
            if (overrideModel != null) {
                applyModelToLayers(renderState, overrideModel);
                return;
            }
        }

        class_1058 sprite = CitVirtualTextures.spriteForRule(rule);
        if (sprite != null) {
            class_2960 texId = CitVirtualTextures.textureForRule(rule);
            CitRenderCache.remember(renderState, stack, sprite, texId);
            applySpriteToLayers(renderState, sprite, texId);
            return;
        }

        class_2960 virtualTex = CitVirtualTextures.textureForRule(rule);
        if (virtualTex != null) {
            SlothyHubMod.LOGGER.debug("CIT: sprite missing for '{}' ({})", rule.texture, rule.id);
        } else {
            SlothyHubMod.LOGGER.debug("CIT: no override for texture '{}' ({})", rule.texture, rule.id);
        }
    }

    // ── One-time reflection setup ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void ensureModelFieldResolved(class_10444 renderState, Object manager) {
        if (RESOLVED) return;
        RESOLVED = true;
        try {
            resolveLayerModelField(renderState);
            resolveManagerFunctions(manager);
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("CIT: reflection setup failed: {}", e.getMessage());
        }
    }

    private static void resolveLayerModelField(class_10444 renderState) {
        if (LAYERS_ARRAY == null) return;
        try {
            class_10444.class_10446[] layers = (class_10444.class_10446[]) LAYERS_ARRAY.get(renderState);
            Class<?> layerType = class_10444.class_10446.class;
            if (layers != null && layers.length > 0 && layers[0] != null) {
                layerType = layers[0].getClass();
            }
            for (Field lf : layerType.getDeclaredFields()) {
                if (lf.getType() == Object.class) {
                    lf.setAccessible(true);
                    LAYER_MODEL_FIELD = lf;
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void resolveManagerFunctions(Object manager) {
        if (manager == null) return;
        // class_10442 has two Function fields (field_55334, field_55553) used to
        // look up item models. Grab them so we can look up override models.
        for (Field f : manager.getClass().getDeclaredFields()) {
            if (f.getType() != java.util.function.Function.class) continue;
            f.setAccessible(true);
            try {
                Object val = f.get(manager);
                if (val instanceof Function) {
                    if (MANAGER_FUNC_A == null) MANAGER_FUNC_A = (Function) val;
                    else if (MANAGER_FUNC_B == null) { MANAGER_FUNC_B = (Function) val; break; }
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Runtime helpers ───────────────────────────────────────────────────

    private static String resolveItemId(class_1799 stack) {
        try {
            return class_7923.field_41178.method_10221(stack.method_7909()).toString();
        } catch (Exception e) { return null; }
    }

    /** Primary display string for logging / UI. */
    static String resolveDisplayName(class_1799 stack) {
        List<String> names = CitStackNames.resolve(stack);
        return names.isEmpty() ? null : names.get(0);
    }

    static List<String> resolveMatchNames(class_1799 stack) {
        return CitStackNames.resolve(stack);
    }

    // CIT match/near-miss logging is silent by default - it fires on every frame an item
    // is rendered, which spams thousands of lines per minute and can stall lower-end clients.
    // Enable diagnostics by launching with -Dslothyhub.debug.cit=true.
    private static final boolean CIT_DEBUG_LOG =
        Boolean.parseBoolean(System.getProperty("slothyhub.debug.cit", "false"));
    private static final java.util.Map<String, Long> LAST_MATCH_LOG_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> LAST_NEAR_MISS_LOG_MS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long LOG_THROTTLE_MS = 8000L;

    private static void logMatchOnce(String itemId, CitRule rule, List<String> names) {
        if (!CIT_DEBUG_LOG) return;
        String key = rule.id + "|" + itemId;
        long now = System.currentTimeMillis();
        Long last = LAST_MATCH_LOG_MS.get(key);
        if (last != null && now - last < LOG_THROTTLE_MS) return;
        LAST_MATCH_LOG_MS.put(key, now);
        SlothyHubMod.LOGGER.info("CIT: matched rule {} for {} (names={})", rule.id, itemId, names);
    }

    /** Throttled diagnostic when a netherite sword has names but no CIT rule matched. */
    private static void logNearMiss(String itemId, List<String> names) {
        if (!CIT_DEBUG_LOG) return;
        if (!itemId.contains("netherite_sword")) return;
        String key = itemId + "|" + String.join(",", names);
        long now = System.currentTimeMillis();
        Long last = LAST_NEAR_MISS_LOG_MS.get(key);
        if (last != null && now - last < LOG_THROTTLE_MS) return;
        LAST_NEAR_MISS_LOG_MS.put(key, now);
        if (names.isEmpty()) {
            SlothyHubMod.LOGGER.info("CIT: no rule matched {} (no display strings)", itemId);
        } else if (names.size() <= 2 && names.stream().allMatch(n -> n.toLowerCase(Locale.ROOT).contains("netherite"))) {
            SlothyHubMod.LOGGER.info("CIT: no rule matched {} — plain netherite sword (rename it or use a tier sword with MYTHIC/Warden tag)", itemId);
        } else {
            SlothyHubMod.LOGGER.info("CIT: no rule matched {} names={}", itemId, names);
        }
    }

    static String stripFormatting(String text) {
        if (text == null) return null;
        return text.replaceAll("§.", "").trim();
    }

    /**
     * Attempts to get the override model for the given CIT texture path.
     *
     * Tries two strategies:
     * 1. Use the render state manager's own Function fields (direct model lookup).
     * 2. Construct an Identifier and look up from MinecraftClient's model manager.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object findOverrideModel(Object manager, CitRule rule, class_1799 stack) {
        String texturePath = rule.texture;
        // Normalize texture path to a full identifier
        String norm = texturePath;
        if (!norm.contains(":")) norm = "minecraft:" + norm;

        // Strategy 1: use the manager's Function fields
        Object identifier = makeIdentifier(norm, stack.getClass().getClassLoader());
        if (identifier == null && !texturePath.contains("/")) {
            identifier = makeIdentifier("minecraft:item/" + texturePath, stack.getClass().getClassLoader());
        }
        if (identifier != null) {
            if (MANAGER_FUNC_A != null) {
                try {
                    Object model = ((Function) MANAGER_FUNC_A).apply(identifier);
                    if (model != null) return model;
                } catch (Exception ignored) {}
            }
            if (MANAGER_FUNC_B != null) {
                try {
                    Object model = ((Function) MANAGER_FUNC_B).apply(identifier);
                    if (model != null) return model;
                } catch (Exception ignored) {}
            }
        }

        // Strategy 2: BakedModelManager (works on 1.21.4; may not on 1.21.8)
        Object viaMgr = findViaModelManager(texturePath, stack.getClass().getClassLoader());
        if (viaMgr != null) return viaMgr;
        if (!texturePath.contains("/"))
            return findViaModelManager("item/" + texturePath, stack.getClass().getClassLoader());
        return null;
    }

    private static Object makeIdentifier(String path, ClassLoader cl) {
        // path already has namespace e.g. "minecraft:custom_sword"
        // Try class_2960.of(String) or new class_2960(ns, path)
        String[] ids = { "net.minecraft.class_2960", "net.minecraft.util.Identifier" };
        for (String cn : ids) {
            try {
                Class<?> cls = Class.forName(cn, false, cl);
                // Try static of(String) factory
                for (Method m : cls.getMethods()) {
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class
                            && cls.isAssignableFrom(m.getReturnType())) {
                        return m.invoke(null, path);
                    }
                }
                // Try two-arg constructor (namespace, path)
                int colon = path.indexOf(':');
                if (colon > 0) {
                    return cls.getConstructor(String.class, String.class)
                        .newInstance(path.substring(0, colon), path.substring(colon + 1));
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Object findViaModelManager(String texturePath, ClassLoader cl) {
        try {
            String norm = texturePath;
            if (!norm.contains(":")) norm = "minecraft:item/" + norm;
            else if (!norm.contains("/")) {
                int c = norm.indexOf(':');
                norm = norm.substring(0, c + 1) + "item/" + norm.substring(c + 1);
            }

            Class<?> mcClass = Class.forName("net.minecraft.class_310", false, cl);
            Method getInstance = mcClass.getMethod("method_1551");
            Object mc = getInstance.invoke(null);

            // Find BakedModelManager or equivalent
            for (Method m : mcClass.getMethods()) {
                if (m.getParameterCount() == 0 && !m.getReturnType().isPrimitive()) {
                    String rn = m.getReturnType().getName();
                    if (rn.contains("class_1092") || rn.toLowerCase(Locale.ROOT).contains("modelmanager")) {
                        Object mgr = m.invoke(mc);
                        // Find getModel(ModelIdentifier) on the manager
                        for (Method gm : mgr.getClass().getMethods()) {
                            if (gm.getParameterCount() == 1 && !gm.getReturnType().isPrimitive()) {
                                Class<?> paramType = gm.getParameterTypes()[0];
                                String paramName = paramType.getName();
                                if (paramName.contains("ModelIdentifier") || paramName.contains("class_3300")) {
                                    // Build a ModelIdentifier
                                    Object modelId = null;
                                    for (Method om : paramType.getMethods()) {
                                        if (Modifier.isStatic(om.getModifiers())
                                                && om.getParameterCount() == 1
                                                && om.getParameterTypes()[0] == String.class) {
                                            modelId = om.invoke(null, norm + "#inventory");
                                            break;
                                        }
                                    }
                                    if (modelId == null && paramType.getConstructors().length > 0) {
                                        modelId = paramType.getConstructors()[0].newInstance(norm, "inventory");
                                    }
                                    if (modelId != null) {
                                        Object result = gm.invoke(mgr, modelId);
                                        if (result != null) return result;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: model manager lookup failed for '{}': {}", texturePath, e.getMessage());
        }
        return null;
    }

    public static void clearRenderCache(class_10444 renderState) {
        CitRenderCache.clear(renderState);
    }

    /** Called from {@link com.slothyhub.mixin.MixinCitItemLayerDraw} right before quads draw. */
    public static void patchBeforeDraw(class_10444 renderState, class_10444.class_10446 layer) {
        if (renderState == null || layer == null) return;

        // Only re-bind what applyIfNeeded stored for this render pass — never re-match from a stale stack.
        class_1058 sprite = CitRenderCache.sprite(renderState);
        class_2960 textureId = CitRenderCache.texture(renderState);
        if (sprite == null || textureId == null) return;

        patchLayer(layer, sprite, textureId);
    }

    private static void patchLayer(class_10444.class_10446 layer, class_1058 sprite, class_2960 textureId) {
        if (layer == null || sprite == null) return;
        if (textureId != null) {
            try {
                class_1921 renderLayer = DrawHelper.entityCutoutLayer(textureId);
                if (renderLayer != null) layer.method_67992(renderLayer);
            } catch (Exception e) {
                SlothyHubMod.LOGGER.debug("CIT: render layer bind failed for {}: {}", textureId, e.getMessage());
            }
        }
        List<class_777> quads = layer.method_67997();
        if (quads == null || quads.isEmpty()) return;
        layer.method_67994(sprite);
        for (int i = 0; i < quads.size(); i++) {
            class_777 q = quads.get(i);
            class_777 patched = CitQuadRemapper.withSprite(q, sprite);
            if (patched != q) quads.set(i, patched);
        }
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
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: failed to write sprite to render state: {}", e.getMessage());
        }
    }

    private static int activeLayerCount(class_10444 renderState) {
        if (LAYER_COUNT != null) {
            try {
                return Math.max(0, LAYER_COUNT.getInt(renderState));
            } catch (Exception ignored) {}
        }
        return renderState.method_65606() ? 0 : 1;
    }

    private static void applyModelToLayers(class_10444 renderState, Object overrideModel) {
        if (LAYERS_ARRAY == null || LAYER_MODEL_FIELD == null) return;
        try {
            class_10444.class_10446[] layers = (class_10444.class_10446[]) LAYERS_ARRAY.get(renderState);
            if (layers == null) return;
            for (class_10444.class_10446 layer : layers) {
                if (layer == null) continue;
                LAYER_MODEL_FIELD.set(layer, overrideModel);
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: failed to write model to render state: {}", e.getMessage());
        }
    }
}

