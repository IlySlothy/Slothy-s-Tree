package com.slothyhub.modern;

import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.McVersion;
import com.slothyhub.builder.ResourceScanHelper;
import com.slothyhub.cit.CitRuleSet;
import com.slothyhub.cit.ModernCitItemRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.class_3264;
import net.minecraft.class_3300;

public final class ModernCitMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (McVersion.below("1.21.9")) return;

        SlothyHubMod.LOGGER.info("SlothyHub CIT companion loaded for MC {}", McVersion.current());
        // Force static init (layer reflection probe + diagnostic log) before first item render.
        ModernCitItemRenderer.class.getName();

        try {
            ResourceManagerHelper.get(clientResources())
                .registerReloadListener(new ModernCitReloadListener());
            ClientLifecycleEvents.CLIENT_STARTED.register(mc ->
                mc.execute(() -> ModernCitVirtualTextures.rebuild(
                    ResourceScanHelper.resourceManager(), CitRuleSet.active())));
        } catch (Throwable e) {
            SlothyHubMod.LOGGER.warn("Modern CIT could not register reload listener: {}", e.getMessage());
        }
    }

    private static class_3264 clientResources() {
        for (class_3264 type : class_3264.values()) {
            if ("CLIENT_RESOURCES".equals(type.name())) return type;
        }
        return class_3264.values()[0];
    }
}