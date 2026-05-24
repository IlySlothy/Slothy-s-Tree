package com.slothyhub.cit;

import net.minecraft.class_1058;
import net.minecraft.class_777;

/** Remaps baked-quad UVs when swapping the sprite on MC 1.21.8. */
final class CitQuadRemapper {

    private static final int INTS_PER_VERTEX = 8;
    private static final int UV_U_INDEX = 4;
    private static final int UV_V_INDEX = 5;

    private CitQuadRemapper() {}

    static class_777 withSprite(class_777 quad, class_1058 newSprite) {
        class_1058 oldSprite = quad.comp_3724();
        if (oldSprite == null || oldSprite == newSprite) {
            return new class_777(
                quad.comp_3721(), quad.comp_3722(), quad.comp_3723(),
                newSprite, quad.comp_3725(), quad.comp_3726());
        }

        int[] src = quad.comp_3721();
        int[] data = src.clone();
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

        return new class_777(
            data, quad.comp_3722(), quad.comp_3723(),
            newSprite, quad.comp_3725(), quad.comp_3726());
    }

    private static float toLocalU(class_1058 sprite, float u) {
        float min = sprite.method_4594(); // u0
        float max = sprite.method_4577(); // u1
        if (max <= min) return 0f;
        return clamp01((u - min) / (max - min));
    }

    private static float toLocalV(class_1058 sprite, float v) {
        float min = sprite.method_4593(); // v0
        float max = sprite.method_4575(); // v1
        if (max <= min) return 0f;
        return clamp01((v - min) / (max - min));
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
