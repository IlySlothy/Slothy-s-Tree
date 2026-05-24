package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_10442;
import net.minecraft.class_10444;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_811;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the MC 1.21.8 item render-state pipeline to apply CIT overrides.
 *
 * class_10442  = ItemRenderStateManager
 * method_65597 = updateForTopLevel(ItemRenderState, ItemStack, Transformation, Entity)
 *
 * NOTE: In MC 1.21.4 this method had a 5th boolean (leftHand) parameter.
 *       In MC 1.21.8 that parameter was removed. We build against 1.21.8,
 *       so the 4-param signature is used here. On 1.21.4 this mixin will
 *       silently not apply (defaultRequire=0) which is acceptable.
 */
@Mixin(class_10442.class)
public abstract class MixinCitItemRenderState {

    @Inject(
        method = "method_65597",
        at = @At("TAIL")
    )
    private void slothyhub$applyCitRule(
        class_10444 renderState,
        class_1799  stack,
        class_811   transformation,
        class_1309  entity,
        CallbackInfo ci
    ) {
        if (!SlothyConfig.isCitEnabled()) return;
        CitItemRenderer.applyIfNeeded(renderState, stack, (Object) this);
    }
}
