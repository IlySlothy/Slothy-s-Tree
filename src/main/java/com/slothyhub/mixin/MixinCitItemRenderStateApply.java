package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_10444;
import net.minecraft.class_1799;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_10444.class)
public abstract class MixinCitItemRenderStateApply {

    @Inject(method = "method_65604", at = @At("HEAD"))
    private void slothyhub$applyCitBeforeLayerRender(
        class_4587 matrices,
        class_4597 vertexConsumers,
        int light,
        int overlay,
        CallbackInfo ci
    ) {
        CitItemRenderer.endDrawContext();
        if (!SlothyConfig.isCitEnabled()) return;
        class_10444 state = (class_10444) (Object) this;
        class_1799 stack = CitItemRenderer.trackedStack(state);
        if (stack != null && !stack.method_7960()) {
            CitItemRenderer.applyIfNeeded(state, stack, null);
        }
    }
}