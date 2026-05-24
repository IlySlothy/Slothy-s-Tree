package com.slothyhub.cit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.Identifiers;
import net.minecraft.class_1011;
import net.minecraft.class_2960;
import net.minecraft.class_3300;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/** Parses .png.mcmeta animation metadata and crops animated strips to a single frame. */
public final class TextureAnimationUtil {

    record FrameInfo(int frameWidth, int frameHeight, int frameCount) {}

    private static final Method SET_PIXEL = resolveSetPixel();

    private TextureAnimationUtil() {}

    private static Method resolveSetPixel() {
        for (String name : new String[]{"method_4305", "setColor", "setPixelAbgr"}) {
            try {
                Method m = class_1011.class.getDeclaredMethod(name, int.class, int.class, int.class);
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static void setPixel(class_1011 img, int x, int y, int color) {
        if (SET_PIXEL == null) return;
        try {
            SET_PIXEL.invoke(img, x, y, color);
        } catch (ReflectiveOperationException e) {
            SlothyHubMod.LOGGER.debug("CIT: setPixel failed: {}", e.getMessage());
        }
    }

    public static class_1011 firstFrame(class_1011 img, class_3300 manager, class_2960 pngId) {
        if (img == null) return null;
        int w = img.method_4307();
        int h = img.method_4323();
        FrameInfo info = resolveFrameInfo(manager, pngId, w, h);
        if (info.frameHeight() >= h && info.frameWidth() >= w) return img;
        return crop(img, 0, 0, info.frameWidth(), info.frameHeight());
    }

    static byte[] defaultMcmetaForSize(int frameW, int frameH) {
        return ("{\n  \"animation\": {\n    \"frametime\": 2,\n    \"width\": "
            + frameW + ",\n    \"height\": " + frameH + "\n  }\n}\n").getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] ensureMcmeta(byte[] existing, int imgW, int imgH) {
        FrameInfo info = parseMcmeta(existing, imgW, imgH);
        if (info.frameCount() <= 1 && imgH <= imgW) return existing;
        if (existing != null && existing.length > 0) {
            String raw = new String(existing, StandardCharsets.UTF_8);
            if (raw.contains("\"animation\"") && (raw.contains("\"width\"") || raw.contains("\"height\"") || imgH > imgW)) {
                return existing;
            }
        }
        return defaultMcmetaForSize(info.frameWidth(), info.frameHeight());
    }

    private static FrameInfo resolveFrameInfo(class_3300 manager, class_2960 pngId, int w, int h) {
        byte[] mcmeta = readMcmetaBytes(manager, pngId);
        return parseMcmeta(mcmeta, w, h);
    }

    static FrameInfo parseMcmeta(byte[] mcmeta, int w, int h) {
        int frameW = w;
        int frameH = h;
        int frameCount = 1;
        if (mcmeta != null && mcmeta.length > 0) {
            try {
                JsonObject root = JsonParser.parseString(new String(mcmeta, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("animation")) {
                    JsonObject anim = root.getAsJsonObject("animation");
                    if (anim.has("width")) frameW = anim.get("width").getAsInt();
                    if (anim.has("height")) frameH = anim.get("height").getAsInt();
                    if (anim.has("frames") && anim.get("frames").isJsonArray()) {
                        frameCount = anim.getAsJsonArray("frames").size();
                    } else if (frameH > 0 && h > frameH) {
                        frameCount = h / frameH;
                    } else if (frameW > 0 && w > frameW && h == frameH) {
                        frameCount = w / frameW;
                        frameW = w / Math.max(1, frameCount);
                    }
                }
            } catch (Exception e) {
                SlothyHubMod.LOGGER.debug("CIT: mcmeta parse failed: {}", e.getMessage());
            }
        }
        if (frameCount <= 1 && h > w && w > 0 && h % w == 0) {
            frameW = w;
            frameH = w;
            frameCount = h / w;
        }
        if (frameW <= 0) frameW = w;
        if (frameH <= 0) frameH = h;
        if (frameCount <= 0) frameCount = 1;
        return new FrameInfo(frameW, frameH, frameCount);
    }

    private static byte[] readMcmetaBytes(class_3300 manager, class_2960 pngId) {
        if (manager == null || pngId == null) return null;
        class_2960 metaId = Identifiers.of(pngId.method_12836(), pngId.method_12832() + ".mcmeta");
        try {
            var resources = manager.method_14489(metaId);
            if (resources != null && !resources.isEmpty()) {
                try (InputStream in = resources.get(0).method_14482()) {
                    return in.readAllBytes();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Crop animated CIT strips to the first frame for GUI thumbnails (no resource manager needed). */
    public static class_1011 firstFrameFromImage(class_1011 img, byte[] mcmeta) {
        if (img == null) return null;
        int w = img.method_4307();
        int h = img.method_4323();
        FrameInfo info = parseMcmeta(mcmeta, w, h);
        if (info.frameHeight() >= h && info.frameWidth() >= w) return img;
        return crop(img, 0, 0, info.frameWidth(), info.frameHeight());
    }

    private static class_1011 crop(class_1011 src, int x, int y, int w, int h) {
        w = Math.min(w, src.method_4307() - x);
        h = Math.min(h, src.method_4323() - y);
        if (w <= 0 || h <= 0) return src;
        class_1011 out = new class_1011(w, h, false);
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                setPixel(out, col, row, src.method_61940(x + col, y + row));
            }
        }
        return out;
    }
}
