package com.slothyhub.cit;

import com.slothyhub.compat.McVersion;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Gates CIT mixins by MC version. Atlas injection is always safe; draw hooks are version-specific.
 */
public final class CitMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("slothyhub-cit");

    private static Boolean modernPipeline;
    private static Boolean legacyPipeline;

    public static boolean isModernPipeline() {
        if (modernPipeline == null) {
            modernPipeline = McVersion.atLeast("1.21.4");
        }
        return modernPipeline;
    }

    public static boolean isLegacyPipeline() {
        if (legacyPipeline == null) {
            legacyPipeline = !isModernPipeline();
        }
        return legacyPipeline;
    }

    static void logPipelineMode() {
        if (McVersion.below("1.21.4")) {
            LOGGER.info("CIT pipeline: legacy model wrap + atlas PNGs (MC {})", McVersion.current());
        } else if (McVersion.atLeast("1.21.8")) {
            LOGGER.info("CIT pipeline: render-state + layer sprite (MC {})", McVersion.current());
        } else {
            LOGGER.info("CIT pipeline: render-state + atlas PNGs (MC {}, baked-model CIT only)", McVersion.current());
        }
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("MixinCitAtlasLoader")
            || mixinClassName.endsWith("MixinCitAtlasPreparation")
            || mixinClassName.endsWith("MixinCitResourceFinder")) {
            return true;
        }

        if (mixinClassName.endsWith("MixinCitLegacyItemRenderer")) {
            return McVersion.below("1.21.4");
        }

        if (mixinClassName.endsWith("MixinCitItemRenderState")
            || mixinClassName.endsWith("MixinCitItemRenderStateClear")
            || mixinClassName.endsWith("MixinCitLayerData")
            || mixinClassName.endsWith("MixinCitItemLayerPrepareDraw")) {
            return McVersion.atLeast("1.21.4");
        }

        if (mixinClassName.endsWith("MixinCitItemDraw118")) {
            return McVersion.atLeast("1.21.8");
        }

        if (mixinClassName.endsWith("MixinCitItemGetQuads114")) {
            return McVersion.atLeast("1.21.4") && McVersion.below("1.21.8");
        }

        // Broken or experimental — keep disabled
        if (mixinClassName.contains("MixinCitItemRenderStateApply")
            || mixinClassName.contains("MixinCitItemModelBake")
            || mixinClassName.contains("MixinCitItemRendererModern")
            || mixinClassName.contains("MixinCitItemRenderQuads")) {
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
