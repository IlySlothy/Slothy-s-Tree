package com.slothyhub.cit;

import net.minecraft.class_10444;
import net.minecraft.class_1058;
import net.minecraft.class_1799;
import net.minecraft.class_2960;

import java.util.Map;
import java.util.WeakHashMap;

/** Per-frame CIT overrides keyed by item render state (cleared with the state). */
final class CitRenderCache {

    private static final Map<class_10444, class_1058> SPRITES = new WeakHashMap<>();
    private static final Map<class_10444, class_2960> TEXTURES = new WeakHashMap<>();
    private static final Map<class_10444, class_1799> STACKS = new WeakHashMap<>();

    private CitRenderCache() {}

    static void remember(class_10444 state, class_1799 stack, class_1058 sprite, class_2960 textureId) {
        if (state == null || sprite == null) return;
        SPRITES.put(state, sprite);
        if (textureId != null) TEXTURES.put(state, textureId);
        if (stack != null && !stack.method_7960()) STACKS.put(state, stack);
    }

    static class_1058 sprite(class_10444 state) {
        return SPRITES.get(state);
    }

    static class_2960 texture(class_10444 state) {
        return TEXTURES.get(state);
    }

    static class_1799 stack(class_10444 state) {
        return STACKS.get(state);
    }

    static void clear(class_10444 state) {
        SPRITES.remove(state);
        TEXTURES.remove(state);
        STACKS.remove(state);
    }
}
