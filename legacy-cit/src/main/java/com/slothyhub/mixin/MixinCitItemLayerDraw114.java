package com.slothyhub.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slothyhub.cit.CitItemRenderer114;
import net.minecraft.class_10444;
import net.minecraft.class_10515;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_811;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** MC 1.21.4-1.21.7: optional CIT baked-model draw; vanilla ItemModel path when no CIT sprite. */
@Mixin(class_10444.class_10446.class)
public abstract class MixinCitItemLayerDraw114 {

    @WrapOperation(
        method = "method_65614",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_10515;method_65694(Ljava/lang/Object;Lnet/minecraft/class_811;Lnet/minecraft/class_4587;Lnet/minecraft/class_4597;IIZ)V"
        )
    )
    private void slothyhub$wrapCitItemModelDraw(
        class_10515<Object> itemModel,
        Object data,
        class_811 transform,
        class_4587 matrices,
        class_4597 vertexConsumers,
        int light,
        int overlay,
        boolean glint,
        Operation<Void> original
    ) {
        class_10444.class_10446 layer = (class_10444.class_10446) (Object) this;
        if (CitItemRenderer114.tryDrawCitLayer(layer, transform, matrices, vertexConsumers, light, overlay)) {
            return;
        }
        original.call(itemModel, data, transform, matrices, vertexConsumers, light, overlay, glint);
    }
}
