package com.slothyhub.cit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Enables modern item-render-state CIT mixins on MC 1.21.4+ and legacy ItemRenderer
 * mixins on MC 1.21.2–1.21.3 so one jar can target 1.21.2–1.21.8.
 */
public final class CitMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("slothyhub-cit");

    private static Boolean modernPipeline;
    private static Boolean legacyPipeline;

    public static boolean isModernPipeline() {
        if (modernPipeline == null) {
            modernPipeline = detectModernPipeline();
        }
        return modernPipeline;
    }

    public static boolean isLegacyPipeline() {
        if (legacyPipeline == null) {
            legacyPipeline = !isModernPipeline() && detectLegacyPipeline();
        }
        return legacyPipeline;
    }

    private static boolean detectModernPipeline() {
        try {
            Class<?> manager = Class.forName("net.minecraft.class_10442");
            manager.getMethod(
                "method_65598",
                Class.forName("net.minecraft.class_10444"),
                Class.forName("net.minecraft.class_1799"),
                Class.forName("net.minecraft.class_811"),
                Class.forName("net.minecraft.class_1937"),
                Class.forName("net.minecraft.class_1309"),
                int.class
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean detectLegacyPipeline() {
        try {
            Class<?> itemRenderer = Class.forName("net.minecraft.class_918");
            itemRenderer.getDeclaredMethod(
                "method_23182",
                Class.forName("net.minecraft.class_1087"),
                Class.forName("net.minecraft.class_1799"),
                int.class,
                int.class,
                Class.forName("net.minecraft.class_4587"),
                Class.forName("net.minecraft.class_4588")
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
        String mode;
        if (isModernPipeline()) {
            mode = "modern (1.21.4+)";
        } else if (isLegacyPipeline()) {
            mode = "legacy (1.21.2–1.21.3)";
        } else {
            mode = "unsupported";
        }
        LOGGER.info("CIT render pipeline: {}", mode);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("MixinCitLegacyItemRenderer")) {
            return isLegacyPipeline();
        }
        if (mixinClassName.contains("MixinCitItem")) {
            return isModernPipeline();
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
