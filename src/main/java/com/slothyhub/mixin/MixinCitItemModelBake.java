package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_10430;
import net.minecraft.class_10432;
import net.minecraft.class_10435;
import net.minecraft.class_10437;
import net.minecraft.class_10442;
import net.minecraft.class_10444;
import net.minecraft.class_10447;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_638;
import net.minecraft.class_811;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies CIT after each baked-model item layer writes its model (MC 1.21.4+). */
@Mixin({
    class_10430.class,
    class_10432.class,
    class_10435.class,
    class_10437.class,
    class_10447.class
})
public abstract class MixinCitItemModelBake {

    @Inject(method = "method_65584", at = @At("TAIL"))
    private void slothyhub$applyCitAfterModelBake(
        class_10444 renderState,
        class_1799 stack,
        class_10442 manager,
        class_811 transformation,
        class_638 world,
        class_1309 entity,
        int seed,
        CallbackInfo ci
    ) {
        if (!SlothyConfig.isCitEnabled()) return;
        CitItemRenderer.applyIfNeeded(renderState, stack, manager);
    }
}
