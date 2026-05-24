package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.cit.CitItemRenderer;
import net.minecraft.class_10444;
import net.minecraft.class_1921;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_777;
import net.minecraft.class_811;
import net.minecraft.class_918;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/** Remaps pre-baked CIT quads at layer draw entry (MC 1.21.8+). */
@Mixin(class_918.class)
public abstract class MixinCitItemDraw118 {

    @ModifyVariable(
        method = "method_62476",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static List<class_777> slothyhub$remapCitQuads(
        List<class_777> quads,
        class_811 mode,
        class_4587 matrices,
        class_4597 vertexConsumers,
        int light,
        int overlay,
        int[] tints,
        class_1921 renderLayer,
        class_10444.class_10445 renderPass
    ) {
        if (!SlothyConfig.isCitEnabled()) return quads;
        return CitItemRenderer.remapQuadsForDraw(quads);
    }
}