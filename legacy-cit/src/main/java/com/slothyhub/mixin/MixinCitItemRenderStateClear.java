package com.slothyhub.mixin;

import com.slothyhub.cit.LegacyCitItemRenderer;
import net.minecraft.class_10444;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drop CIT overrides whenever an item render state is reset (prevents sword textures on other items). */
@Mixin(class_10444.class)
public abstract class MixinCitItemRenderStateClear {

    @Inject(method = "method_65605", at = @At("HEAD"))
    private void slothyhub$clearCitOnReset(CallbackInfo ci) {
        LegacyCitItemRenderer.clearRenderCache((class_10444) (Object) this);
    }
}

