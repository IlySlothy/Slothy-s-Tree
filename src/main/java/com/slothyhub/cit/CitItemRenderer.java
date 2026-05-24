package com.slothyhub.cit;

import com.slothyhub.SlothyHubMod;
import net.minecraft.class_10444;
import net.minecraft.class_1799;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.function.Function;

/**
 * CIT render-time applicator for MC 1.21.8.
 *
 * Called from {@link com.slothyhub.mixin.MixinCitItemRenderState} at the TAIL of
 * class_10442.method_65597. Checks the ItemStack against active CIT rules and,
 * when a rule matches, replaces the model reference inside the render-state layers.
 *
 * Works by reflection so the same class file runs on multiple Minecraft versions.
 */
public final class CitItemRenderer {

    private CitItemRenderer() {}

    // ── Cached reflection handles ─────────────────────────────────────────

    /** ItemStack → Item  (class_1799.getItem) */
    private static volatile Method  GET_ITEM;
    /** Registry<Item> singleton */
    private static volatile Object  ITEM_REGISTRY;
    /** Registry.getId(Item) → Identifier */
    private static volatile Method  GET_ID;

    /** ItemStack.hasCustomName() → boolean */
    private static volatile Method  HAS_CUSTOM_NAME;
    /** ItemStack.getCustomName() / getName() → Text */
    private static volatile Method  GET_CUSTOM_NAME;
    /** Text.getString() → String */
    private static volatile Method  GET_STRING;

    /** Array field on class_10444 holding the layer objects */
    private static volatile Field   LAYERS_FIELD;
    /** The model field inside each layer (may be Object-typed in 1.21.8) */
    private static volatile Field   LAYER_MODEL_FIELD;

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
        if (ruleSet.isEmpty()) return;

        ensureResolved(renderState, stack, manager);

        String itemId = resolveItemId(stack);
        if (itemId == null) return;

        String displayName = resolveCustomName(stack);   // null if no custom name
        CitRule rule = ruleSet.findMatch(itemId, displayName);
        if (rule == null || rule.texture.isBlank()) return;

        Object overrideModel = findOverrideModel(manager, rule.texture, stack);
        if (overrideModel == null) {
            SlothyHubMod.LOGGER.debug("CIT: no override model found for texture '{}'", rule.texture);
            return;
        }

        applyModelToLayers(renderState, overrideModel);
    }

    // ── One-time reflection setup ─────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void ensureResolved(class_10444 renderState, class_1799 stack, Object manager) {
        if (RESOLVED) return;
        RESOLVED = true;
        try {
            resolveItemIdMethods(stack);
            resolveCustomNameMethods(stack);
            resolveLayerFields(renderState);
            resolveManagerFunctions(manager);
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("CIT: reflection setup failed: {}", e.getMessage());
        }
    }

    private static void resolveItemIdMethods(class_1799 stack) {
        ClassLoader cl = stack.getClass().getClassLoader();

        // Find getItem() — the method with 0 params returning a net.minecraft item class
        for (Method m : stack.getClass().getMethods()) {
            if (m.getParameterCount() == 0
                    && m.getReturnType().getName().startsWith("net.minecraft.")
                    && !m.getReturnType().getName().contains("class_1799")
                    && !m.getReturnType().isPrimitive()) {
                // Heuristic: getItem() returns an Item class (not Text, not a component, etc.)
                String name = m.getName().toLowerCase(Locale.ROOT);
                if (name.equals("method_7909") || name.equals("getitem") || name.contains("item")) {
                    GET_ITEM = m; break;
                }
            }
        }
        if (GET_ITEM == null) {
            // Fallback: first MC-returning 0-arg method
            for (Method m : stack.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && m.getReturnType().getName().startsWith("net.minecraft.")
                        && !m.getReturnType().isPrimitive()
                        && !m.getReturnType().isArray()) {
                    GET_ITEM = m; break;
                }
            }
        }

        // Find Registries.ITEM registry
        String[] regClassNames = {
            "net.minecraft.registry.Registries",
            "net.minecraft.class_7923",
            "net.minecraft.class_2378"
        };
        outer:
        for (String cn : regClassNames) {
            try {
                Class<?> cls = Class.forName(cn, false, cl);
                for (Field f : cls.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object reg = f.get(null);
                    if (reg == null) continue;
                    String fn = f.getName().toLowerCase(Locale.ROOT);
                    if (!fn.contains("item") && !fn.equals("field_11142") && !fn.equals("field_29723")) continue;
                    // Verify it has a getId(Object)->Identifier method
                    for (Method m : reg.getClass().getMethods()) {
                        if (m.getParameterCount() == 1) {
                            String rn = m.getReturnType().getName();
                            if (rn.contains("class_2960") || rn.endsWith("Identifier")) {
                                ITEM_REGISTRY = reg;
                                GET_ID = m;
                                break outer;
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void resolveCustomNameMethods(class_1799 stack) {
        // Look specifically for hasCustomName() → boolean and getCustomName() → Text
        for (Method m : stack.getClass().getMethods()) {
            String mn = m.getName();
            // hasCustomName: 0 params, returns boolean
            if (HAS_CUSTOM_NAME == null && m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                if (mn.equals("method_6801") || mn.toLowerCase(Locale.ROOT).contains("hascustom")
                        || mn.equals("hasCustomName")) {
                    HAS_CUSTOM_NAME = m;
                }
            }
            // getCustomName / getName: 0 params, returns Text
            if (m.getParameterCount() == 0 && !m.getReturnType().isPrimitive()) {
                String rt = m.getReturnType().getName();
                if (rt.contains("class_2561") || rt.contains("Text") || rt.contains("MutableText")) {
                    // Prefer getCustomName (method_6835) over getName (method_11102)
                    if (mn.equals("method_6835") || mn.equalsIgnoreCase("getCustomName")) {
                        GET_CUSTOM_NAME = m;
                    } else if (GET_CUSTOM_NAME == null
                            && (mn.equals("method_11102") || mn.equalsIgnoreCase("getName"))) {
                        GET_CUSTOM_NAME = m;
                    }
                }
            }
        }
        // Resolve getString() on the Text class
        if (GET_CUSTOM_NAME != null && GET_STRING == null) {
            for (Method s : GET_CUSTOM_NAME.getReturnType().getMethods()) {
                if (s.getParameterCount() == 0 && s.getReturnType() == String.class
                        && (s.getName().equals("getString") || s.getName().equals("method_1238"))) {
                    GET_STRING = s; break;
                }
            }
        }
    }

    private static void resolveLayerFields(class_10444 renderState) {
        // Find the array field in class_10444 holding the layers
        for (Field f : renderState.getClass().getDeclaredFields()) {
            if (!f.getType().isArray()) continue;
            Class<?> layerType = f.getType().getComponentType();
            if (layerType == null || layerType.isPrimitive()) continue;
            // Must be an array of objects (the render layers)
            f.setAccessible(true);
            LAYERS_FIELD = f;

            // Find the model field inside the layer:
            // In 1.21.4 it was a net.minecraft.class_1087 typed field.
            // In 1.21.8 it may be stored as java.lang.Object (field_55351).
            // Strategy: pick the last non-primitive, non-parent, non-array field.
            Field candidate = null;
            for (Field lf : layerType.getDeclaredFields()) {
                if (lf.getType().isPrimitive()) continue;
                if (lf.getType().isArray()) continue;
                // Skip the back-reference to the parent render state
                if (lf.getType() == renderState.getClass()) continue;
                // Prefer net.minecraft typed fields (BakedModel in 1.21.4)
                String typeName = lf.getType().getName();
                if (typeName.startsWith("net.minecraft.")
                        && !typeName.contains("class_10444")
                        && !typeName.contains("Supplier")
                        && !typeName.contains("List")) {
                    candidate = lf;
                    break;
                }
            }
            if (candidate == null) {
                // Fallback: use the Object-typed field (1.21.8 layout)
                for (Field lf : layerType.getDeclaredFields()) {
                    if (lf.getType() == Object.class) {
                        candidate = lf; break;
                    }
                }
            }
            if (candidate != null) {
                candidate.setAccessible(true);
                LAYER_MODEL_FIELD = candidate;
            }
            if (LAYER_MODEL_FIELD != null) break;
        }
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
        if (GET_ITEM == null || ITEM_REGISTRY == null || GET_ID == null) return null;
        try {
            Object item = GET_ITEM.invoke(stack);
            if (item == null) return null;
            Object id = GET_ID.invoke(ITEM_REGISTRY, item);
            return id != null ? id.toString() : null;
        } catch (Exception e) { return null; }
    }

    /**
     * Returns the item's custom (anvil) display name, or null if it has none.
     * CIT name rules match against the custom name, not the default item name.
     */
    private static String resolveCustomName(class_1799 stack) {
        try {
            // Check hasCustomName first to avoid matching the default item name
            if (HAS_CUSTOM_NAME != null) {
                Boolean has = (Boolean) HAS_CUSTOM_NAME.invoke(stack);
                if (has == null || !has) return null;
            }
            if (GET_CUSTOM_NAME == null || GET_STRING == null) return null;
            Object text = GET_CUSTOM_NAME.invoke(stack);
            if (text == null) return null;
            return (String) GET_STRING.invoke(text);
        } catch (Exception e) { return null; }
    }

    /**
     * Attempts to get the override model for the given CIT texture path.
     *
     * Tries two strategies:
     * 1. Use the render state manager's own Function fields (direct model lookup).
     * 2. Construct an Identifier and look up from MinecraftClient's model manager.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object findOverrideModel(Object manager, String texturePath, class_1799 stack) {
        // Normalize texture path to a full identifier
        String norm = texturePath;
        if (!norm.contains(":")) norm = "minecraft:" + norm;

        // Strategy 1: use the manager's Function fields
        // These map Identifier → ItemModel (or similar) internally
        Object identifier = makeIdentifier(norm, stack.getClass().getClassLoader());
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
        return findViaModelManager(texturePath, stack.getClass().getClassLoader());
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

    private static void applyModelToLayers(class_10444 renderState, Object overrideModel) {
        if (LAYERS_FIELD == null || LAYER_MODEL_FIELD == null) return;
        try {
            Object[] layers = (Object[]) LAYERS_FIELD.get(renderState);
            if (layers == null) return;
            for (Object layer : layers) {
                if (layer == null) continue;
                LAYER_MODEL_FIELD.set(layer, overrideModel);
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: failed to write model to render state: {}", e.getMessage());
        }
    }
}
