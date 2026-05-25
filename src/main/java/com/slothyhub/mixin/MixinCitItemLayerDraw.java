package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_10444;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-applies CIT sprite swaps immediately before quads are drawn (MC 1.21.8).
 * Some mods / render paths rebuild layers after the update hook runs.
 */
@Mixin(class_10444.class_10446.class)
public abstract class MixinCitItemLayerDraw {

    @Shadow @Final class_10444 field_55345;

    @Inject(method = "method_65614", at = @At("HEAD"))
    private void slothyhub$applyCitBeforeDraw(
        class_4587 matrices,
        class_4597 vertexConsumers,
        int light,
        int overlay,
        CallbackInfo ci
    ) {
        if (!SlothyConfig.isCitEnabled()) return;
        CitItemRenderer.patchBeforeDraw(field_55345, (class_10444.class_10446) (Object) this);
    }
}
