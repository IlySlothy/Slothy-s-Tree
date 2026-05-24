package com.slothyhub.cit;

import net.minecraft.class_1058;
import net.minecraft.class_2350;
import net.minecraft.class_777;

import java.lang.reflect.Method;

/** Remaps baked-quad UVs when swapping the sprite across MC 1.21.4-1.21.8. */
final class CitQuadRemapper {

    private static final int INTS_PER_VERTEX = 8;
    private static final int UV_U_INDEX = 4;
    private static final int UV_V_INDEX = 5;

    private static final Method GET_VERTICES = resolve(class_777.class, "int[]", "comp_3721", "method_3357");
    private static final Method GET_COLOR = resolve(class_777.class, "int", "comp_3722", "method_3359");
    private static final Method GET_FACE = resolve(class_777.class, "net.minecraft.class_2350", "comp_3723", "method_3358");
    private static final Method GET_SPRITE = resolve(class_777.class, "net.minecraft.class_1058", "comp_3724", "method_35788");
    private static final Method GET_SHADE = resolve(class_777.class, "boolean", "comp_3725", "method_3360");
    private static final Method GET_LIGHT = resolve(class_777.class, "int", "comp_3726", "method_62324");

    private CitQuadRemapper() {}

    static class_777 withSprite(class_777 quad, class_1058 newSprite) {
        if (quad == null || newSprite == null) return quad;

        class_1058 oldSprite = getSpriteInternal(quad);
        int[] vertices = getVertices(quad);
        int color = getColor(quad);
        class_2350 face = getFace(quad);
        boolean shade = getShade(quad);
        int light = getLight(quad);

        if (oldSprite == null || oldSprite == newSprite) {
            return new class_777(vertices, color, face, newSprite, shade, light);
        }

        int[] data = vertices.clone();
        int verts = data.length / INTS_PER_VERTEX;

        for (int v = 0; v < verts; v++) {
            int base = v * INTS_PER_VERTEX;
            float u = Float.intBitsToFloat(data[base + UV_U_INDEX]);
            float vCoord = Float.intBitsToFloat(data[base + UV_V_INDEX]);

            float localU = toLocalU(oldSprite, u);
            float localV = toLocalV(oldSprite, vCoord);

            data[base + UV_U_INDEX] = Float.floatToRawIntBits(newSprite.method_4580(localU));
            data[base + UV_V_INDEX] = Float.floatToRawIntBits(newSprite.method_4570(localV));
        }

        return new class_777(data, color, face, newSprite, shade, light);
    }

    private static int[] getVertices(class_777 quad) {
        return (int[]) invoke(GET_VERTICES, quad);
    }

    private static int getColor(class_777 quad) {
        return (int) invoke(GET_COLOR, quad);
    }

    private static class_2350 getFace(class_777 quad) {
        return (class_2350) invoke(GET_FACE, quad);
    }

    static class_1058 getSprite(class_777 quad) {
        return getSpriteInternal(quad);
    }

    private static class_1058 getSpriteInternal(class_777 quad) {
        return (class_1058) invoke(GET_SPRITE, quad);
    }

    private static boolean getShade(class_777 quad) {
        return (boolean) invoke(GET_SHADE, quad);
    }

    private static int getLight(class_777 quad) {
        return (int) invoke(GET_LIGHT, quad);
    }

    private static Object invoke(Method method, class_777 quad) {
        if (method == null) {
            throw new IllegalStateException("CIT: BakedQuad accessor missing on this MC version");
        }
        try {
            return method.invoke(quad);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CIT: BakedQuad accessor failed", e);
        }
    }

    private static Method resolve(Class<?> owner, String returnTypeName, String... names) {
        for (String name : names) {
            for (Method m : owner.getMethods()) {
                if (!m.getName().equals(name) || m.getParameterCount() != 0) continue;
                if (!m.getReturnType().getName().equals(returnTypeName)) continue;
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static float toLocalU(class_1058 sprite, float u) {
        float min = sprite.method_4594();
        float max = sprite.method_4577();
        if (max <= min) return 0f;
        return clamp01((u - min) / (max - min));
    }

    private static float toLocalV(class_1058 sprite, float v) {
        float min = sprite.method_4593();
        float max = sprite.method_4575();
        if (max <= min) return 0f;
        return clamp01((v - min) / (max - min));
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
