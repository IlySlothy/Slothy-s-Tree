package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_10444;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_10444.class_10446.class)
public abstract class MixinCitItemLayerPrepareDraw {

    @Inject(method = "method_65614", at = @At("HEAD"))
    private void slothyhub$prepareCitDraw(
        class_4587 matrices,
        class_4597 vertexConsumers,
        int light,
        int overlay,
        CallbackInfo ci
    ) {
        if (!SlothyConfig.isCitEnabled()) return;
        CitItemRenderer.prepareLayerForDraw((class_10444.class_10446) (Object) this);
    }

    @Inject(method = "method_65614", at = @At("RETURN"))
    private void slothyhub$finishCitDraw(
        class_4587 matrices,
        class_4597 vertexConsumers,
        int light,
        int overlay,
        CallbackInfo ci
    ) {
        CitItemRenderer.finishLayerDraw((class_10444.class_10446) (Object) this);
    }
}