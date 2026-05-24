package com.slothyhub.cit;

import net.minecraft.class_1058;

/** Per-layer CIT sprite set after bake, consumed at draw time. */
public interface CitLayerAccess {

    class_1058 slothyhub$getCitSprite();

    void slothyhub$setCitSprite(class_1058 sprite);

    void slothyhub$stashSpecialModel(Object model, Object data);

    Object slothyhub$peekStashedSpecialModel();

    Object slothyhub$peekStashedSpecialData();

    void slothyhub$restoreSpecialModel();
}