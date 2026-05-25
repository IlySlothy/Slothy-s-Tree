package com.slothyhub.modern;

import com.slothyhub.cit.CitResourceReloadListener;
import com.slothyhub.compat.Identifiers;
import com.slothyhub.cit.CitRuleSet;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3300;

public final class ModernCitReloadListener implements SimpleSynchronousResourceReloadListener {

    private static final class_2960 ID = Identifiers.of("slothyhub", "modern_cit_listener");

    @Override
    public class_2960 getFabricId() { return ID; }

    @Override
    public void method_14491(class_3300 manager) {
        CitResourceReloadListener.reloadCitProperties(manager);
        scheduleSpriteRebuild(manager);
    }

    private static void scheduleSpriteRebuild(class_3300 manager) {
        class_310 mc = class_310.method_1551();
        CitRuleSet active = CitRuleSet.active();
        Runnable rebuild = () -> {
            class_3300 live = manager != null ? manager : com.slothyhub.builder.ResourceScanHelper.resourceManager();
            if (live != null) ModernCitVirtualTextures.rebuild(live, active);
        };
        if (mc != null) {
            mc.execute(() -> mc.execute(rebuild));
        } else {
            rebuild.run();
        }
    }
}