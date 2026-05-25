package com.slothyhub.cit;

import net.minecraft.class_10444;
import net.minecraft.class_1058;

/** Per-thread CIT state active during a single layer draw (avoids mutating shared baked models). */
final class CitDrawContext {

    private static final ThreadLocal<class_1058> ACTIVE_SPRITE = new ThreadLocal<>();
    private static final ThreadLocal<class_10444.class_10446> ACTIVE_LAYER = new ThreadLocal<>();

    private CitDrawContext() {}

    static void begin(class_10444.class_10446 layer, class_1058 sprite) {
        end();
        if (layer != null) ACTIVE_LAYER.set(layer);
        if (sprite != null) ACTIVE_SPRITE.set(sprite);
    }

    static class_1058 active() {
        return ACTIVE_SPRITE.get();
    }

    static class_10444.class_10446 activeLayer() {
        return ACTIVE_LAYER.get();
    }

    static void end() {
        ACTIVE_SPRITE.remove();
        ACTIVE_LAYER.remove();
    }
}