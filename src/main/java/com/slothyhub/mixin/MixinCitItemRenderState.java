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

/** Records CIT sprite matches after the full item render-state update (MC 1.21.4+). */
@Mixin(class_10442.class)
public abstract class MixinCitItemRenderState {

    @Inject(method = "method_65598", at = @At("TAIL"))
    private void slothyhub$recordCitAfterUpdate(
        class_10444 renderState,
        class_1799 stack,
        class_811 transformation,
        boolean leftHanded,
        class_1937 world,
        class_1309 entity,
        int seed,
        CallbackInfo ci
    ) {
        slothyhub$applyCit(renderState, stack);
    }

    @Inject(method = "method_65596", at = @At("TAIL"))
    private void slothyhub$recordCitAfterUpdateAlt(
        class_10444 renderState,
        class_1799 stack,
        class_811 transformation,
        class_1937 world,
        class_1309 entity,
        int seed,
        CallbackInfo ci
    ) {
        slothyhub$applyCit(renderState, stack);
    }

    @Inject(method = "method_65597", at = @At("TAIL"))
    private void slothyhub$recordCitAfterLivingUpdate(
        class_10444 renderState,
        class_1799 stack,
        class_811 transformation,
        boolean leftHanded,
        class_1309 entity,
        CallbackInfo ci
    ) {
        slothyhub$applyCit(renderState, stack);
    }

    @Inject(method = "method_65595", at = @At("TAIL"))
    private void slothyhub$recordCitAfterNonLivingUpdate(
        class_10444 renderState,
        class_1799 stack,
        class_811 transformation,
        net.minecraft.class_1297 entity,
        CallbackInfo ci
    ) {
        slothyhub$applyCit(renderState, stack);
    }

    private void slothyhub$applyCit(class_10444 renderState, class_1799 stack) {
        if (!SlothyConfig.isCitEnabled()) return;
        CitItemRenderer.trackStackForRender(renderState, stack);
        CitItemRenderer.applyIfNeeded(renderState, stack, (Object) this);
    }
}
