package com.slothyhub.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_1087;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_5819;
import net.minecraft.class_777;
import net.minecraft.class_918;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Remaps baked-model quads during item draw when a CIT sprite is active (MC 1.21.4-1.21.7). */
@Mixin(class_918.class)
public abstract class MixinCitItemGetQuads114 {

    @WrapOperation(
        method = "method_23182",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_1087;method_4707(Lnet/minecraft/class_2680;Lnet/minecraft/class_2350;Lnet/minecraft/class_5819;)Ljava/util/List;"
        )
    )
    private static List<class_777> slothyhub$remapGetQuads(
        class_1087 model,
        class_2680 state,
        class_2350 face,
        class_5819 random,
        Operation<List<class_777>> original
    ) {
        List<class_777> quads = original.call(model, state, face, random);
        if (!SlothyConfig.isCitEnabled()) return quads;
        return CitItemRenderer.remapQuadsForDraw(quads);
    }
}