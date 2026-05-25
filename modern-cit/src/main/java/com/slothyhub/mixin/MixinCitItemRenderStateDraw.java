package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.cit.ModernCitItemRenderer;
import net.minecraft.class_10444;
import net.minecraft.class_11659;
import net.minecraft.class_4587;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_10444.class)
public abstract class MixinCitItemRenderStateDraw {

    static {
        SlothyHubMod.LOGGER.info("Modern CIT: MixinCitItemRenderStateDraw loaded");
    }

    // 1.21.11 Loom-resolved signature (per slothyhub-cit-refmap.json):
    //   class_10444.method_65604(class_4587, class_11659, int, int, int)V
    @Inject(method = "method_65604", at = @At("HEAD"), require = 1)
    private void slothyhub$patchCitBeforeStateDraw(
        class_4587 matrices,
        class_11659 vertexConsumers,
        int light,
        int overlay,
        int seed,
        CallbackInfo ci
    ) {
        if (!SlothyConfig.isCitEnabled()) return;
        ModernCitItemRenderer.prepareDraw((class_10444) (Object) this);
    }
}
