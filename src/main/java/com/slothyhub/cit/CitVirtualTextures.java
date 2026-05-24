package com.slothyhub.cit;

import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.DrawHelper;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1044;
import net.minecraft.class_1058;
import net.minecraft.class_1059;
import net.minecraft.class_1092;
import com.slothyhub.compat.Identifiers;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3300;
import net.minecraft.class_7368;
import net.minecraft.class_7764;
import net.minecraft.class_7771;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Maps CIT rules to dynamically registered item textures loaded from optifine/cit/ PNGs.
 * Allows Summer-style packs (texture PNG next to .properties) to render without OptiFine.
 */
public final class CitVirtualTextures {

    private static final Map<String, class_2960> RULE_TEXTURES = new HashMap<>();
    private static final Map<String, class_1058> RULE_SPRITES = new HashMap<>();
    private static int seq = 0;
    private static Constructor<class_1058> SPRITE_CTOR;
    private static final Method UPLOADED_GPU_TEXTURE = resolveUploadedGpuTexture();
    private static final Method SPRITE_BIND_GPU = resolveSpriteBindGpu();
    private static final Method SPRITE_BIND_LEGACY = resolveSpriteBindLegacy();

    private CitVirtualTextures() {}

    public static void rebuild(class_3300 manager, CitRuleSet ruleSet) {
        clear();
        if (ruleSet.isEmpty()) return;
        class_310 mc = class_310.method_1551();
        if (mc == null || mc.method_1531() == null) return;

        for (CitRule rule : ruleSet.allRules()) {
            if (rule.texture.isBlank()) continue;
            class_2960 resourceId = null;
            InputStream in = null;
            for (String pngPath : resolvePngAssetPaths(rule)) {
                resourceId = Identifiers.of("minecraft", pngPath);
                in = openStream(manager, resourceId);
                if (in != null) break;
            }
            if (in == null) {
                SlothyHubMod.LOGGER.warn("CIT: PNG not found for rule {} (texture={})", rule.id, rule.texture);
                continue;
            }
            final InputStream stream = in;
            try (stream) {
                class_1011 img = class_1011.method_4309(stream);
                img = TextureAnimationUtil.firstFrame(img, manager, resourceId);
                if (img == null) continue;
                class_1058 atlas = lookupAtlasSprite(rule);
                if (atlas != null) {
                    RULE_SPRITES.put(rule.id, atlas);
                    SlothyHubMod.LOGGER.debug("CIT: atlas sprite for {} -> {}", rule.id, atlas.method_45852());
                    continue;
                }

                SlothyHubMod.LOGGER.debug("CIT: no atlas sprite for {} — skipping rule", rule.id);
                continue;
            } catch (Exception e) {
                SlothyHubMod.LOGGER.debug("CIT virtual texture miss {}: {}", resourceId, e.getMessage());
            }
        }
        SlothyHubMod.LOGGER.info("CIT: resolved {} sprites ({} atlas, {} dynamic) for {} rules",
            RULE_SPRITES.size(),
            RULE_SPRITES.values().stream().filter(CitVirtualTextures::isAtlasSprite).count(),
            RULE_SPRITES.size() - RULE_SPRITES.values().stream().filter(CitVirtualTextures::isAtlasSprite).count(),
            ruleSet.allRules().size());
    }

    public static class_2960 textureForRule(CitRule rule) {
        return RULE_TEXTURES.get(rule.id);
    }

    public static class_1058 spriteForRule(CitRule rule) {
        if (rule == null) return null;

        class_1058 atlas = lookupAtlasSprite(rule);
        if (atlas != null) {
            RULE_SPRITES.put(rule.id, atlas);
            return atlas;
        }

        class_1058 cached = RULE_SPRITES.get(rule.id);
        return cached;
    }

    /** Atlas sprites work with the vanilla item render layer; dynamic sprites do not. */
    public static boolean isAtlasSprite(class_1058 sprite) {
        if (sprite == null) return false;
        try {
            class_2960 id = sprite.method_45852();
            return id != null && !"slothyhub".equals(id.method_12836());
        } catch (Exception e) {
            return false;
        }
    }

    private static class_1058 lookupAtlasSprite(CitRule rule) {
        class_310 mc = class_310.method_1551();
        if (mc == null) return null;
        try {
            class_1092 models = mc.method_1554();
            if (models == null) return null;
            class_1059 atlas = models.method_24153(class_1059.field_5275);
            if (atlas == null) return null;

            class_1058 missing = atlas.method_4608(class_1059.field_17898);
            for (String pngPath : resolvePngAssetPaths(rule)) {
                class_2960 spriteId = pngPathToSpriteId(pngPath);
                if (spriteId == null) continue;
                class_1058 sprite = atlas.method_4608(spriteId);
                if (sprite != null && sprite != missing) return sprite;
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: atlas sprite lookup failed for {}: {}", rule.id, e.getMessage());
        }
        return null;
    }

    private static class_2960 pngPathToSpriteId(String pngPath) {
        if (pngPath == null || pngPath.isBlank()) return null;
        String path = pngPath.replace('\\', '/');
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        if (path.isBlank()) return null;
        return Identifiers.of("minecraft", path);
    }

    /** Resolve candidate PNG paths for a CIT rule (assets/minecraft/... without prefix). */
    public static List<String> resolvePngAssetPaths(CitRule rule) {
        List<String> paths = new ArrayList<>();
        String primary = resolvePngAssetPath(rule);
        if (primary != null) paths.add(primary);

        String tex = rule.texture.replace('\\', '/').trim();
        if (!tex.isBlank() && !tex.contains("/")) {
            String prop = rule.id;
            int colon = prop.indexOf(':');
            if (colon >= 0) prop = prop.substring(colon + 1);
            if (prop.endsWith(".properties")) {
                String dir = prop.substring(0, prop.lastIndexOf('/') + 1);
                String adjacent = dir + tex + ".png";
                if (!paths.contains(adjacent)) paths.add(adjacent);
            }
            String itemTex = "textures/item/" + tex + ".png";
            if (!paths.contains(itemTex)) paths.add(itemTex);
            String flatTex = "textures/" + tex + ".png";
            if (!paths.contains(flatTex)) paths.add(flatTex);
        }
        return paths;
    }

    /** Primary PNG path for a CIT rule. */
    public static String resolvePngAssetPath(CitRule rule) {
        String tex = rule.texture.replace('\\', '/');
        if (tex.contains("/")) {
            if (tex.startsWith("item/") || tex.startsWith("block/"))
                return "textures/" + tex + ".png";
            return tex.endsWith(".png") ? tex : tex + ".png";
        }
        String prop = rule.id;
        int colon = prop.indexOf(':');
        if (colon >= 0) prop = prop.substring(colon + 1);
        if (!prop.endsWith(".properties")) return null;
        String dir = prop.substring(0, prop.lastIndexOf('/') + 1);
        return dir + tex + ".png";
    }

    public static void clear() {
        class_310 mc = class_310.method_1551();
        if (mc != null && mc.method_1531() != null) {
            for (class_2960 id : RULE_TEXTURES.values()) {
                try { mc.method_1531().method_4615(id); } catch (Exception ignored) {}
            }
        }
        RULE_TEXTURES.clear();
        RULE_SPRITES.clear();
        seq = 0;
    }

    private static class_1058 createSprite(class_2960 id, class_1011 img, class_1043 uploaded) {
        try {
            int w = img.method_4307();
            int h = img.method_4323();
            class_7764 contents = new class_7764(id, new class_7771(w, h), img, class_7368.field_38688);
            Constructor<class_1058> ctor = spriteCtor();
            // Params: atlasWidth, atlasHeight, x, y — full texture occupies 0..1 UV
            class_1058 sprite = ctor.newInstance(id, contents, w, h, 0, 0);
            bindSpriteTexture(sprite, uploaded);
            return sprite;
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT sprite create failed for {}: {}", id, e.getMessage());
            return null;
        }
    }

    private static void bindSpriteTexture(class_1058 sprite, class_1043 uploaded) {
        if (sprite == null) return;
        try {
            if (SPRITE_BIND_GPU != null && UPLOADED_GPU_TEXTURE != null && uploaded != null) {
                Object gpu = UPLOADED_GPU_TEXTURE.invoke(uploaded);
                if (gpu != null) {
                    SPRITE_BIND_GPU.invoke(sprite, gpu);
                    return;
                }
            }
            if (SPRITE_BIND_LEGACY != null) {
                SPRITE_BIND_LEGACY.invoke(sprite);
            }
        } catch (ReflectiveOperationException e) {
            SlothyHubMod.LOGGER.debug("CIT: sprite texture bind failed: {}", e.getMessage());
        }
    }

    private static Method resolveUploadedGpuTexture() {
        for (Method m : class_1044.class.getMethods()) {
            if (m.getParameterCount() == 0
                && "com.mojang.blaze3d.textures.GpuTexture".equals(m.getReturnType().getName())) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Method resolveSpriteBindGpu() {
        for (Method m : class_1058.class.getMethods()) {
            if ("method_4584".equals(m.getName()) && m.getParameterCount() == 1) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Method resolveSpriteBindLegacy() {
        for (Method m : class_1058.class.getMethods()) {
            if ("method_4584".equals(m.getName()) && m.getParameterCount() == 0) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Constructor<class_1058> spriteCtor() throws NoSuchMethodException {
        if (SPRITE_CTOR == null) {
            SPRITE_CTOR = class_1058.class.getDeclaredConstructor(
                class_2960.class, class_7764.class, int.class, int.class, int.class, int.class);
            SPRITE_CTOR.setAccessible(true);
        }
        return SPRITE_CTOR;
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
    }

    private static InputStream openStream(class_3300 manager, class_2960 id) {
        try {
            var resources = manager.method_14489(id);
            if (resources != null && !resources.isEmpty()) {
                return resources.get(0).method_14482();
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("CIT: open {} failed: {}", id, e.getMessage());
        }
        return null;
    }
}
