package com.slothyhub.cit;

import com.slothyhub.compat.McVersion;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/** Companion mod: CIT atlas + render hooks for MC versions below 1.21.8. */
public final class LegacyCitMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("slothyhub-legacy-cit");

    @Override
    public void onLoad(String mixinPackage) {
        if (McVersion.below("1.21.8")) {
            LOGGER.info("Legacy CIT active for MC {}", McVersion.current());
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (McVersion.atLeast("1.21.8")) {
            return false;
        }

        if (mixinClassName.endsWith("MixinCitLegacyItemRenderer")) {
            return McVersion.below("1.21.4");
        }

        if (mixinClassName.endsWith("MixinCitItemRenderState114")) {
            return McVersion.atLeast("1.21.4");
        }

        if (mixinClassName.endsWith("MixinCitItemRenderStateClear")
            || mixinClassName.endsWith("MixinCitLayerData")) {
            return McVersion.atLeast("1.21.4");
        }

        if (mixinClassName.endsWith("MixinCitItemLayerDraw114")) {
            return McVersion.atLeast("1.21.4");
        }

        if (mixinClassName.endsWith("MixinCitItemGetQuads114")) {
            return McVersion.atLeast("1.21.4");
        }

        if (mixinClassName.endsWith("MixinCitItemLayerPrepareDraw")) {
            return McVersion.atLeast("1.21.4");
        }

        if (mixinClassName.contains("MixinCitItemRendererModern")
            || mixinClassName.contains("MixinCitItemRenderStateApply")) {
            return false;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
