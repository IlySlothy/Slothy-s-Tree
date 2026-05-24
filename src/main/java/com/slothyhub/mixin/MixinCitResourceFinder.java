package com.slothyhub.mixin;

import net.minecraft.class_2960;
import net.minecraft.class_7654;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps absolute texture identifiers for CIT PNG paths.
 * Adapted from CIT Resewn (MIT) — Bittorn/CITResewn-1.21.4 ResourceFinderMixin.
 */
@Mixin(class_7654.class)
public abstract class MixinCitResourceFinder {

    @Shadow @Final private String field_39967;

    @Inject(method = "method_45112", cancellable = true, at = @At("HEAD"))
    private void slothyhub$keepAbsolutePngPath(class_2960 id, CallbackInfoReturnable<class_2960> cir) {
        if (id.method_12832().endsWith(".png") && ".png".equals(field_39967)) {
            cir.setReturnValue(id);
        }
    }
}