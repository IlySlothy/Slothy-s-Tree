package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_10442;
import net.minecraft.class_10444;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_811;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the MC 1.21.8 item render-state pipeline after layers/quads are baked.
 *
 * class_10442.method_65598 is the common update path used by first-person, GUI, and entity rendering.
 */
@Mixin(class_10442.class)
public abstract class MixinCitItemRenderState {

    @Inject(method = "method_65598", at = @At("TAIL"))
    private void slothyhub$applyCitAfterUpdate(
        class_10444 renderState,
        class_1799 stack,
        class_811 transformation,
        class_1937 world,
        class_1309 entity,
        int seed,
        CallbackInfo ci
    ) {
        if (!SlothyConfig.isCitEnabled()) return;
        CitItemRenderer.applyIfNeeded(renderState, stack, (Object) this);
    }
}
