package com.slothyhub.modern;

import com.slothyhub.SlothyHubMod;
import com.slothyhub.cit.CitRule;
import com.slothyhub.cit.CitRuleSet;
import com.slothyhub.cit.CitVirtualTextures;
import com.slothyhub.cit.TextureAnimationUtil;
import com.slothyhub.compat.DrawHelper;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1058;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3300;

import java.io.InputStream;

public final class ModernCitVirtualTextures {

    private static int seq = 0;

    private ModernCitVirtualTextures() {}

    public static void rebuild(class_3300 manager, CitRuleSet ruleSet) {
        CitVirtualTextures.clear();
        seq = 0;
        if (ruleSet == null || ruleSet.isEmpty()) return;
        class_310 mc = class_310.method_1551();
        if (mc == null || mc.method_1531() == null) return;

        int registered = 0;
        for (CitRule rule : ruleSet.allRules()) {
            if (rule.texture.isBlank()) continue;
            class_2960 resourceId = null;
            InputStream in = null;
            for (String pngPath : CitVirtualTextures.resolvePngAssetPaths(rule)) {
                resourceId = class_2960.method_60655("minecraft", pngPath);
                in = openStream(manager, resourceId);
                if (in != null) break;
            }
            if (in == null) {
                SlothyHubMod.LOGGER.warn("Modern CIT: PNG not found for rule {} (texture={})", rule.id, rule.texture);
                continue;
            }
            final InputStream stream = in;
            final class_2960 pngId = resourceId;
            try (stream) {
                class_1011 raw = class_1011.method_4309(stream);
                // Crop animated CIT strips (Summer Perfect Sword etc.) to frame 0 so MC doesn't
                // stretch the whole vertical strip onto the 16x16 item quad.
                class_1011 img = TextureAnimationUtil.firstFrame(raw, manager, pngId);
                if (img == null) img = raw;
                String key = rule.id.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
                class_2960 texId = class_2960.method_60655("slothyhub", "cit/" + key + "_" + (seq++));
                class_1043 tex = DrawHelper.createNativeTexture("slothyhub_cit_" + key, img);
                if (tex == null) {
                    tex = new class_1043(() -> "slothyhub_cit_" + key, img);
                }
                tex.method_4524();
                mc.method_1531().method_4616(texId, tex);
                class_1058 sprite = createSprite(texId, img, tex);
                CitVirtualTextures.registerRuleAssets(rule.id, texId, sprite);
                if (sprite != null) registered++;
            } catch (Exception e) {
                SlothyHubMod.LOGGER.warn("Modern CIT virtual texture miss {}: {}", resourceId, e.getMessage());
            }
        }
        SlothyHubMod.LOGGER.info("Modern CIT: registered {} sprites", registered);
    }

    private static class_1058 createSprite(class_2960 id, class_1011 img, class_1043 uploaded) {
        try {
            class_1058 sprite = DrawHelper.createFullSprite(id, img);
            if (sprite == null) {
                SlothyHubMod.LOGGER.warn("Modern CIT sprite create failed for {}: no matching Sprite constructor", id);
                return null;
            }
            DrawHelper.bindSpriteGpu(sprite, uploaded);
            return sprite;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("Modern CIT sprite create failed for {}: {}", id, e.getMessage());
            return null;
        }
    }

    private static InputStream openStream(class_3300 manager, class_2960 id) {
        try {
            var resources = manager.method_14489(id);
            if (resources != null && !resources.isEmpty()) {
                return resources.get(0).method_14482();
            }
        } catch (Exception ignored) {}
        return null;
    }
}