package com.slothyhub.mixin;

import com.slothyhub.cit.CitAtlasRoots;
import net.minecraft.class_2960;
import net.minecraft.class_3298;
import net.minecraft.class_3300;
import net.minecraft.class_7654;
import net.minecraft.class_7947;
import net.minecraft.class_7948;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Registers CIT PNGs into the blocks atlas so optifine/cit textures resolve as sprites.
 * Adapted from CIT Resewn (MIT) — Bittorn/CITResewn-1.21.4 AtlasLoaderMixin.
 */
@Mixin(class_7947.class)
public abstract class MixinCitAtlasLoader {

    @Shadow @Final private List<class_7948> field_41388;

    @Inject(method = "method_47668", at = @At("RETURN"))
    private static void slothyhub$injectCitAtlasSources(
        class_3300 resourceManager,
        class_2960 id,
        CallbackInfoReturnable<class_7947> cir
    ) {
        if (!"minecraft".equals(id.method_12836()) || !"blocks".equals(id.method_12832())) {
            return;
        }
        class_7947 loader = cir.getReturnValue();
        if (loader == null) {
            return;
        }
        ((MixinCitAtlasLoader) (Object) loader).slothyhub$addCitSources(resourceManager);
    }

    private void slothyhub$addCitSources(class_3300 resourceManager) {
        field_41388.add(new class_7948() {
            @Override
            public void method_47673(class_3300 manager, class_7948.class_7949 regions) {
                for (String root : CitAtlasRoots.ROOTS) {
                    class_7654 finder = new class_7654(root + CitAtlasRoots.CIT_FOLDER, ".png");
                    Map<class_2960, class_3298> found = finder.method_45113(manager);
                    for (Map.Entry<class_2960, class_3298> entry : found.entrySet()) {
                        class_2960 spriteId = finder.method_45115(entry.getKey())
                            .method_45138(root + CitAtlasRoots.CIT_FOLDER + "/");
                        regions.method_47674(spriteId, entry.getValue());
                    }
                }
            }

            @Override
            public com.mojang.serialization.MapCodec<? extends class_7948> method_67288() {
                return null;
            }
        });
    }
}