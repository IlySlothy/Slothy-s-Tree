package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import net.minecraft.class_10444;
import net.minecraft.class_1058;
import net.minecraft.class_1087;
import net.minecraft.class_1921;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_811;
import net.minecraft.class_918;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** CIT draw applicator for MC 1.21.4-1.21.7 only. */
public final class CitItemRenderer114 {

    private CitItemRenderer114() {}

    private static final Field RENDER_STATE_TRANSFORM = resolveRenderStateField("field_55337");
    private static final Field LAYER_PARENT = resolveLayerField("field_55345");
    private static final Field LAYER_RENDER_PASS = resolveLayerField("field_55348");
    private static final Field LAYER_BAKED_MODEL = resolveLayerField("field_55346");
    private static final Field LAYER_RENDER_LAYER = resolveLayerField("field_55347");
    private static final Object NORMAL_RENDER_PASS = resolveNormalRenderPass();
    private static final Method BAKED_LAYER_DRAW = resolveBakedLayerDraw();

    public static boolean tryDrawCitLayer(
        class_10444.class_10446 layer,
        class_811 transform,
        class_4587 matrices,
        class_4597 vertexConsumers,
        int light,
        int overlay
    ) {
        if (!SlothyConfig.isCitEnabled() || layer == null) return false;
        if (!isNormalRenderPass(layer)) return false;

        class_1058 sprite = ((CitLayerAccess) (Object) layer).slothyhub$getCitSprite();
        if (sprite == null || !CitVirtualTextures.isAtlasSprite(sprite)) return false;
        if (LAYER_BAKED_MODEL == null || LAYER_RENDER_LAYER == null || BAKED_LAYER_DRAW == null) return false;

        try {
            class_1087 model = (class_1087) LAYER_BAKED_MODEL.get(layer);
            if (model == null) return false;

            class_1921 renderLayer = (class_1921) LAYER_RENDER_LAYER.get(layer);
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
            class_1087 drawModel = CitLegacyItemRenderer.wrapWithSprite(
                CitLegacyItemRenderer.unwrap(model), sprite);

            BAKED_LAYER_DRAW.invoke(null,
                layerTransform, matrices, vertexConsumers, light, overlay,
                tints, drawModel, renderLayer, renderPass);
            return true;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT 1.21.4: baked draw failed: {}", e.getMessage());
            return false;
        }
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

    private static Method resolveBakedLayerDraw() {
        try {
            Method m = class_918.class.getMethod("method_62476",
                class_811.class, class_4587.class, class_4597.class,
                int.class, int.class, int[].class,
                class_1087.class, class_1921.class, class_10444.class_10445.class);
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