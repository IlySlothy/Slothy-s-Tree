package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_10444;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_811;
import net.minecraft.class_918;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_918.class)
public abstract class MixinCitItemRendererModern {

    @Shadow @Final private class_10444 field_55297;

    @Inject(method = "method_23178", at = @At("HEAD"))
    private void slothyhub$applyCitBeforeRender(
        class_1799 stack,
        class_811 mode,
        int light,
        int overlay,
        class_4587 matrices,
        class_4597 vertexConsumers,
        class_1937 world,
        int seed,
        CallbackInfo ci
    ) {
        slothyhub$track(stack);
    }

    @Inject(method = "method_23177", at = @At("HEAD"))
    private void slothyhub$applyCitBeforeEntityRender(
        class_1309 entity,
        class_1799 stack,
        class_811 mode,
        boolean leftHanded,
        class_4587 matrices,
        class_4597 vertexConsumers,
        class_1937 world,
        int light,
        int overlay,
        int seed,
        CallbackInfo ci
    ) {
        slothyhub$track(stack);
    }

    private void slothyhub$track(class_1799 stack) {
        if (!SlothyConfig.isCitEnabled()) return;
        CitItemRenderer.trackStackForRender(field_55297, stack);
    }
}