package com.slothyhub.mixin;

import com.slothyhub.cit.CitLayerAccess;
import net.minecraft.class_10444;
import net.minecraft.class_1058;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_10444.class_10446.class)
public abstract class MixinCitLayerData implements CitLayerAccess {

    @Unique
    private class_1058 slothyhub$citSprite;

    @Unique
    private Object slothyhub$stashedSpecialModel;

    @Unique
    private Object slothyhub$stashedSpecialData;

    @Override
    public class_1058 slothyhub$getCitSprite() {
        return slothyhub$citSprite;
    }

    @Override
    public void slothyhub$setCitSprite(class_1058 sprite) {
        slothyhub$citSprite = sprite;
    }

    @Override
    public void slothyhub$stashSpecialModel(Object model, Object data) {
        slothyhub$stashedSpecialModel = model;
        slothyhub$stashedSpecialData = data;
    }

    @Override
    public Object slothyhub$peekStashedSpecialModel() {
        return slothyhub$stashedSpecialModel;
    }

    @Override
    public Object slothyhub$peekStashedSpecialData() {
        return slothyhub$stashedSpecialData;
    }

    @Override
    public void slothyhub$restoreSpecialModel() {
        slothyhub$stashedSpecialModel = null;
        slothyhub$stashedSpecialData = null;
    }

    @Inject(method = "method_65612", at = @At("RETURN"))
    private void slothyhub$clearCitSprite(CallbackInfo ci) {
        slothyhub$citSprite = null;
        slothyhub$stashedSpecialModel = null;
        slothyhub$stashedSpecialData = null;
    }
}