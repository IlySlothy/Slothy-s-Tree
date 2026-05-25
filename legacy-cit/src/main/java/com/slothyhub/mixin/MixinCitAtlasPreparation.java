package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import net.minecraft.class_2960;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Normalises CIT atlas sprite lookups (strip .png / textures/ prefix).
 * Adapted from CIT Resewn (MIT) â€” Bittorn/CITResewn-1.21.4 AtlasPreparationMixin.
 */
@Mixin(targets = "net.minecraft.class_4724$class_7774", remap = false)
public abstract class MixinCitAtlasPreparation {

    @ModifyVariable(method = "method_45869", at = @At("HEAD"), argsOnly = true, remap = false)
    private class_2960 slothyhub$unwrapCitSpriteId(class_2960 id) {
        if (!SlothyConfig.isCitEnabled()) {
            return id;
        }
        String path = id.method_12832();
        if (path.endsWith(".png")) {
            path = path.substring(0, path.length() - 4);
            if (path.startsWith("textures/")) {
                path = path.substring("textures/".length());
            }
            id = class_2960.method_60655(id.method_12836(), path);
        }
        return id;
    }
}
