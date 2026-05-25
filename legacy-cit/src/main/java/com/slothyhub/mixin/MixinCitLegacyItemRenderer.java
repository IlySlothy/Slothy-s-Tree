package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitLegacyItemRenderer;
import net.minecraft.class_1087;
import net.minecraft.class_1799;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_918;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Legacy CIT hook for MC 1.21.2â€“1.21.3 ({@link net.minecraft.class_918#method_23182}).
 * Disabled automatically on 1.21.4+ via {@link com.slothyhub.cit.CitMixinPlugin}.
 */
@Pseudo
@Mixin(class_918.class)
public abstract class MixinCitLegacyItemRenderer {

    @ModifyVariable(
        method = "method_23182",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private class_1087 slothyhub$wrapCitModel(
        class_1087 model,
        class_1799 stack,
        int light,
        int overlay,
        class_4587 matrices,
        class_4588 vertices
    ) {
        if (!SlothyConfig.isCitEnabled()) return model;
        return CitLegacyItemRenderer.wrapModel(model, stack);
    }
}

