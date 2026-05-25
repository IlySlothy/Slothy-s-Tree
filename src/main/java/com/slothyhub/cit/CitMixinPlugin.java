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
 * Main SlothyHub jar: CIT render mixins for MC 1.21.8 only.
 * Older versions use the separate {@code slothyhub-legacy-cit} companion mod.
 *
 * Must not load any Minecraft classes here — doing so breaks other mods' mixins
 * (e.g. AppleSkin ItemStackMixin) and Essential preLaunch on Feather.
 */
public final class CitMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("slothyhub-cit");

    @Override
    public void onLoad(String mixinPackage) {
        if (McVersion.atLeast("1.21.8")) {
            LOGGER.info("CIT render pipeline: 1.21.8 (main jar, MC {})", McVersion.current());
        } else {
            LOGGER.info("CIT render pipeline: install slothyhub-legacy-cit for MC {}", McVersion.current());
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("MixinCitLegacyItemRenderer")) {
            return false;
        }
        if (mixinClassName.contains("MixinCitItem")) {
            return McVersion.atLeast("1.21.8");
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
