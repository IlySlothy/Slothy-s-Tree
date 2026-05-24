package com.slothyhub.ui;

import com.slothyhub.SlothyConfig;
import com.slothyhub.compat.DrawHelper;
import com.slothyhub.kill.KillEffectAssets;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.class_1109;
import net.minecraft.class_1113;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3414;
import net.minecraft.class_3417;

/** Sloth-themed UI utilities for SlothyHub. */
public final class Ui {

    // Sloth palette — reload via {@link #reloadTheme()} when user changes GUI colors
    public static int COL_BG       = GuiTheme.DEFAULT_BG;
    public static int COL_PANEL    = GuiTheme.DEFAULT_PANEL;
    public static int COL_SURFACE  = GuiTheme.DEFAULT_SURFACE;
    public static int COL_ACCENT   = GuiTheme.DEFAULT_ACCENT;
    public static int COL_ACCENT_H = GuiTheme.DEFAULT_ACCENT_H;
    public static int COL_DANGER   = GuiTheme.DEFAULT_DANGER;
    public static int COL_TEXT     = GuiTheme.DEFAULT_TEXT;
    public static int COL_MUTED    = GuiTheme.DEFAULT_MUTED;
    public static int COL_BORDER   = GuiTheme.DEFAULT_BORDER;
    public static int COL_DIM      = GuiTheme.DEFAULT_DIM;
    public static int COL_GOLD     = GuiTheme.DEFAULT_GOLD;

    /** Sync {@link GuiTheme} / config into live color constants used by all screens. */
    public static void reloadTheme() {
        COL_BG       = GuiTheme.bg();
        COL_PANEL    = GuiTheme.panel();
        COL_SURFACE  = GuiTheme.surface();
        COL_ACCENT   = GuiTheme.accent();
        COL_ACCENT_H = GuiTheme.accentH();
        COL_DANGER   = GuiTheme.danger();
        COL_TEXT     = GuiTheme.text();
        COL_MUTED    = GuiTheme.muted();
        COL_BORDER   = GuiTheme.border();
        COL_DIM      = GuiTheme.dim();
        COL_GOLD     = GuiTheme.gold();
    }

    private static volatile Method SOUND_PLAY;
    private static volatile Method SOUND_MASTER;
    private static boolean SOUND_RESOLVED;

    // Leaf particles replacing shooting stars
    private static final List<float[]> leaves = new ArrayList<>();
    private static float leafSpawnTimer = 0f;
    private static final int MAX_LEAVES = 60;

    private static final List<float[]> killParticles = new ArrayList<>();
    private static float killVignetteAlpha = 0f;
    private static final List<float[]> selectionParticles = new ArrayList<>();
    private static final List<float[]> ripples = new ArrayList<>();

    private Ui() {}

    private static void resolveSoundMethods() {
        if (SOUND_RESOLVED) return;
        SOUND_RESOLVED = true;
        try {
            Class<?> smClass = class_310.method_1551().method_1483().getClass();
            for (Method m : smClass.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && class_1113.class.isAssignableFrom(p[0])) {
                    m.setAccessible(true); SOUND_PLAY = m; break;
                }
            }
        } catch (Exception ignored) {}
        try {
            for (String name : new String[]{"tick", "master", "ui"}) {
                try {
                    Method m = class_1109.class.getMethod(name, class_3414.class, float.class, float.class);
                    if (Modifier.isStatic(m.getModifiers())) { m.setAccessible(true); SOUND_MASTER = m; break; }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static void playSound(class_3414 event, float pitch, float volume) {
        resolveSoundMethods();
        if (SOUND_MASTER != null && SOUND_PLAY != null) {
            try {
                Object instance = SOUND_MASTER.invoke(null, event, pitch, volume);
                SOUND_PLAY.invoke(class_310.method_1551().method_1483(), instance);
            } catch (Exception ignored) {}
        }
    }

    public static float easeOutCubic(float t) { float u = 1f - t; return 1f - u * u * u; }
    public static float easeOutBack(float t) { float c1 = 1.70158f, c3 = c1 + 1f, v = t - 1f; return 1f + c3 * v * v * v + c1 * v * v; }
    public static float lerp(float a, float b, float t) { return a + (b - a) * Math.max(0f, Math.min(1f, t)); }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24 & 0xFF, ar = a >>> 16 & 0xFF, ag = a >>> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24 & 0xFF, br = b >>> 16 & 0xFF, bg = b >>> 8 & 0xFF, bb = b & 0xFF;
        return (int)(aa + (ba - aa) * t) << 24 | (int)(ar + (br - ar) * t) << 16
             | (int)(ag + (bg - ag) * t) << 8 | (int)(ab + (bb - ab) * t);
    }

    public static int withAlpha(int rgb, int alpha) { return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF; }

    public static void roundRect(class_332 ctx, int x, int y, int w, int h, int r, int color) {
        if (w > 0 && h > 0) ctx.method_25294(x, y, x + w, y + h, color);
    }

    public static void roundOutline(class_332 ctx, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.method_25294(x, y, x + w, y + 1, color);
        ctx.method_25294(x, y + h - 1, x + w, y + h, color);
        ctx.method_25294(x, y + 1, x + 1, y + h - 1, color);
        ctx.method_25294(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void shimmer(class_332 ctx, int x, int y, int w, int h, float phase, int highlight) {
        int band = Math.max(8, w / 4);
        int center = (int)((x - band) + (float)(w + band * 2) * phase);
        int alphaMax = highlight >>> 24 & 0xFF;
        if (alphaMax < 4) return;
        int rgb = highlight & 0xFFFFFF;
        int lo = Math.max(x, center - band), hi = Math.min(x + w, center + band + 1);
        float invBand = 1f / band;
        for (int px = lo; px < hi; px += 3) {
            float dist = (px - center) * invBand;
            float bell = (float) Math.exp(-dist * dist * 6f);
            int a = (int)(alphaMax * bell);
            if (a > 8) ctx.method_25294(px, y, px + 3, y + h, a << 24 | rgb);
        }
    }

    /** Paw-print spinner — rotates dots in a circle (sloth branding). */
    public static void spinner(class_332 ctx, int cx, int cy, int radius, float phase, int color) {
        int dots = 8;
        for (int i = 0; i < dots; i++) {
            double ang = (double) i / dots * Math.PI * 2 + phase * Math.PI * 2;
            int dx = (int)(Math.cos(ang) * radius), dy = (int)(Math.sin(ang) * radius);
            float t = ((float) i + (1f - phase) * dots) % dots / dots;
            int alpha = (int)(40f + 215f * (1f - t));
            int c = withAlpha(color & 0xFFFFFF, Math.max(40, Math.min(255, alpha)));
            ctx.method_25294(cx + dx - 1, cy + dy - 1, cx + dx + 1, cy + dy + 1, c);
        }
    }

    // ── Leaf parallax (replaces shooting stars) ────────────────────────────

    /** Falling mossy-green leaves — sloth forest ambience. */
    public static void renderLeafParallax(class_332 ctx, int w, int h, float delta) {
        leafSpawnTimer += delta;
        while (leafSpawnTimer > 0.06f && leaves.size() < MAX_LEAVES) {
            leafSpawnTimer -= 0.06f;
            spawnLeaf(w, h);
        }
        Iterator<float[]> it = leaves.iterator();
        while (it.hasNext()) {
            float[] lf = it.next();
            // x, y, speedY, swayAmp, swayOff, life, maxLife, size, alpha
            lf[1] += lf[2] * delta * 40f;
            lf[3] += delta * 1.2f; // sway accumulator
            float sway = (float) Math.sin(lf[3] * 1.4 + lf[4]) * lf[0] * 0.02f;
            lf[0] += sway * delta * 30f;
            lf[5] += delta;
            if (lf[1] > h + 8 || lf[5] > lf[6]) { it.remove(); continue; }
            float prog = lf[5] / lf[6];
            float alphaMul = prog < 0.15f ? prog / 0.15f : (prog > 0.8f ? (1f - prog) / 0.2f : 1f);
            int a = (int)(alphaMul * lf[8]);
            if (a < 2) continue;
            int sz = (int) lf[7];
            int px = (int) lf[0], py = (int) lf[1];
            // Draw a tiny diamond leaf shape
            ctx.method_25294(px, py - sz, px + 1, py + sz, withAlpha(COL_ACCENT & 0xFFFFFF, a));
            ctx.method_25294(px - sz / 2, py, px + sz / 2, py + 1, withAlpha(COL_ACCENT & 0xFFFFFF, a / 2));
        }
    }

    /** Backwards-compat alias so existing code calling renderShootingStars still works. */
    public static void renderShootingStars(class_332 ctx, int w, int h, float delta) {
        renderLeafParallax(ctx, w, h, delta);
    }

    public static void renderStarfield(class_332 ctx, int w, int h, float delta) {
        // No-op — replaced by leaf parallax
    }

    private static void spawnLeaf(int w, int h) {
        float x = (float)(Math.random() * w);
        float speedY = 0.15f + (float)(Math.random() * 0.4f);
        float maxLife = h / (speedY * 40f) + 3f;
        float size = Math.random() < 0.6 ? 2f : (Math.random() < 0.8 ? 3f : 4f);
        float baseAlpha = 30f + (float)(Math.random() * 60f);
        leaves.add(new float[]{x, -4f, speedY, 0f, (float)(Math.random() * Math.PI * 2), 0f, maxLife, size, baseAlpha});
    }

    // ── Kill effects ───────────────────────────────────────────────────────

    private static float totemPopAlpha = 0f;
    private static float totemPopScale = 0.6f;
    private static float totemPopRotation = 0f;

    private static long lastKillFxMs;

    public static void onKill() {
        long now = System.currentTimeMillis();
        if (now - lastKillFxMs < 450) return;
        lastKillFxMs = now;
        String effect = SlothyConfig.getKillEffect();
        if ("none".equals(effect)) return;
        float cx = 0.45f + (float)(Math.random() * 0.1f);
        float cy = 0.38f + (float)(Math.random() * 0.06f);
        killParticles.add(new float[]{cx, cy, -40f, 1f, 0f, 1.2f});
        killVignetteAlpha = 1f;
        if ("totem".equals(effect)) {
            totemPopAlpha = 1f;
            totemPopScale = 0.6f;
            totemPopRotation = 0f;
            playTotemKill();
        } else if ("anvil".equals(effect))   playAnvilKill();
        else if ("thunder".equals(effect)) playThunderKill();
        else playKill();
    }

    /** Totem-of-undying style pop overlay on kill. */
    public static void renderTotemPop(class_332 ctx, int screenW, int screenH, float delta) {
        if (totemPopAlpha <= 0f || !"totem".equals(SlothyConfig.getKillEffect())) return;
        totemPopAlpha = Math.max(0f, totemPopAlpha - delta * 0.55f);
        totemPopScale = Math.min(1.4f, totemPopScale + delta * 1.8f);
        totemPopRotation += delta * 6f;
        int alpha = (int)(totemPopAlpha * 255f);
        if (alpha < 8) return;
        int size = (int)(72 * totemPopScale);
        int cx = screenW / 2, cy = screenH / 2 - 20;
        class_2960 totem = KillEffectAssets.totemTextureId();
        DrawHelper.pushMatrices(ctx);
        DrawHelper.translateMatrices(ctx, cx, cy, 0);
        DrawHelper.scaleMatrices(ctx, totemPopScale, totemPopScale, 1f);
        DrawHelper.drawTexture(ctx, totem, -size / 2, -size / 2, 0f, 0f, size, size, size, size);
        DrawHelper.popMatrices(ctx);
        class_310 mc = class_310.method_1551();
        DrawHelper.drawText(ctx, mc.field_1772, "TOTEM POP", cx - mc.field_1772.method_1727("TOTEM POP") / 2,
            cy + size / 2 + 8, alpha << 24 | (COL_GOLD & 0xFFFFFF), true);
    }

    public static void renderKillEffect(class_332 ctx, int screenW, int screenH, float delta) {
        String effect = SlothyConfig.getKillEffect();
        int vignetteRgb = "anvil".equals(effect) ? 0x666666 : "thunder".equals(effect) ? 0x8899FF : 0xFF9900;
        int textRgb = vignetteRgb;
        if (killVignetteAlpha > 0f) {
            killVignetteAlpha = Math.max(0f, killVignetteAlpha - delta * 3.3f);
            int a = (int)(killVignetteAlpha * 80f);
            if (a > 0) {
                int edgeColor = a << 24 | vignetteRgb;
                int edgeW = Math.max(6, screenW / 12), edgeH = Math.max(6, screenH / 12);
                ctx.method_25296(0, 0, screenW, edgeH, edgeColor, vignetteRgb);
                ctx.method_25296(0, screenH - edgeH, screenW, screenH, vignetteRgb, edgeColor);
            }
        }
        Iterator<float[]> it = killParticles.iterator();
        class_310 mc = class_310.method_1551();
        while (it.hasNext()) {
            float[] p = it.next();
            p[4] += delta;
            float progress = p[4] / p[5];
            if (progress >= 1f) { it.remove(); continue; }
            p[1] += p[2] / screenH * delta;
            p[3] = 1f - easeOutCubic(progress);
            int alpha = (int)(p[3] * 255f);
            if (alpha >= 4) {
                int px = (int)(p[0] * screenW), py = (int)(p[1] * screenH);
                String text = "+KILL";
                int tw = mc.field_1772.method_1727(text);
                DrawHelper.drawText(ctx, mc.field_1772, text, px - tw / 2, py, alpha << 24 | textRgb, true);
            }
        }
    }

    public static void tickKillEffect(float delta) {
        if (killVignetteAlpha > 0f) killVignetteAlpha = Math.max(0f, killVignetteAlpha - delta * 3.3f);
        killParticles.removeIf(p -> { p[4] += delta; return p[4] / p[5] >= 1f; });
    }

    // ── Sounds ─────────────────────────────────────────────────────────────

    public static void playClick()   { playSound((class_3414) class_3417.field_15015.comp_349(), 1.0f, 0.6f); }
    public static void playOpen()    { playSound((class_3414) class_3417.field_14622.comp_349(), 1.0f, 0.5f); }
    public static void playClose()   { playSound((class_3414) class_3417.field_15015.comp_349(), 0.7f, 0.4f); }
    public static void playStar()    { playSound((class_3414) class_3417.field_14725.comp_349(), 1.2f, 0.5f); }
    public static void playSuccess() { playSound((class_3414) class_3417.field_14622.comp_349(), 1.5f, 0.7f); }
    public static void playError()   { playSound((class_3414) class_3417.field_14624.comp_349(), 0.5f, 0.5f); }
    public static void playKill()         { playSound(class_3417.field_14627, 1.2f, 0.7f); }
    public static void playTotemKill()    { KillEffectAssets.playTotemSound(); }
    public static void playAnvilKill()    { playSound(class_3417.field_14833, 0.8f, 0.7f); }
    public static void playThunderKill()  { playSound(class_3417.field_14865, 0.6f, 1.2f); }

    // ── Particle / ripple helpers ──────────────────────────────────────────

    public static void spawnSelectionBurst(int cx, int cy, int color) {
        for (int i = 0; i < 12; i++) {
            float angle = (float) i / 12f * (float) Math.PI * 2f;
            float speed = 2f + (float) Math.random() * 3f;
            selectionParticles.add(new float[]{cx, cy,
                (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed,
                0f, 0.6f + (float) Math.random() * 0.4f,
                color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF});
        }
    }

    public static void renderSelectionParticles(class_332 ctx, float delta) {
        Iterator<float[]> it = selectionParticles.iterator();
        while (it.hasNext()) {
            float[] p = it.next();
            p[0] += p[2] * delta * 60f; p[1] += p[3] * delta * 60f;
            p[3] += 0.15f * delta * 60f; p[4] += delta;
            float progress = p[4] / p[5];
            if (progress >= 1f) { it.remove(); continue; }
            int alpha = (int)((1f - progress) * 200f);
            if (alpha >= 5) {
                int c = alpha << 24 | (int)p[6] << 16 | (int)p[7] << 8 | (int)p[8];
                int px = (int)p[0], py = (int)p[1];
                int size = (int)(3f * (1f - progress * 0.7f));
                ctx.method_25294(px - size, py - size, px + size, py + size, c);
            }
        }
    }

    public static void addRipple(int cx, int cy, int color) {
        ripples.add(new float[]{cx, cy, 0f, 1f, color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF});
    }

    public static void renderRipples(class_332 ctx, float delta) {
        Iterator<float[]> it = ripples.iterator();
        while (it.hasNext()) {
            float[] r = it.next();
            r[2] += delta * 150f; r[3] -= delta * 1.5f;
            if (r[3] <= 0f) { it.remove(); continue; }
            int alpha = (int)(r[3] * 150f);
            int color = alpha << 24 | (int)r[4] << 16 | (int)r[5] << 8 | (int)r[6];
            int cx = (int)r[0], cy = (int)r[1], rad = (int)r[2];
            for (int a = 0; a < 360; a += 10) {
                double ang = Math.toRadians(a);
                int x = cx + (int)(Math.cos(ang) * rad), y = cy + (int)(Math.sin(ang) * rad);
                ctx.method_25294(x - 1, y - 1, x + 1, y + 1, color);
            }
        }
    }

    // ── Drawing helpers ────────────────────────────────────────────────────

    public static void pulseGlow(class_332 ctx, int x, int y, int w, int h, float phase, int baseColor, int glowColor) {
        float pulse = (float) Math.sin(phase * Math.PI * 2) * 0.5f + 0.5f;
        int ga = (int)(pulse * 60f), glow = withAlpha(glowColor & 0xFFFFFF, ga);
        ctx.method_25294(x - 2, y - 2, x + w + 2, y, glow);
        ctx.method_25294(x - 2, y + h, x + w + 2, y + h + 2, glow);
        ctx.method_25294(x - 2, y, x, y + h, glow);
        ctx.method_25294(x + w, y, x + w + 2, y + h, glow);
    }

    public static void neonTrail(class_332 ctx, int x, int y, int w, int h, float phase, int accentColor) {
        for (int i = 0; i < 4; i++) {
            float offset = (phase * 4 + i) % 4;
            int expand = (int)(offset * 2), a = (int)((1f - offset / 4) * 40f);
            ctx.method_25294(x - expand, y - expand, x + w + expand, y + h + expand, withAlpha(accentColor & 0xFFFFFF, a));
        }
    }

    public static void waveShimmer(class_332 ctx, int x, int y, int w, int h, float phase, int color) {
        for (int i = 0; i < w; i += 4) {
            float wave = (float) Math.sin((float) i / w * Math.PI * 3 + phase * Math.PI * 4);
            float alpha = (wave * 0.5f + 0.5f) * (color >>> 24 & 0xFF) / 255f;
            int a = (int)(alpha * 60f);
            ctx.method_25294(x + i, y, x + i + 4, y + h, a << 24 | color & 0xFFFFFF);
        }
    }

    public static void progressRing(class_332 ctx, int cx, int cy, int radius, float progress, int color, int bgColor) {
        for (int i = 0; i < 360; i += 5) {
            double ang = Math.toRadians(i);
            int x = cx + (int)(Math.cos(ang) * radius), y = cy + (int)(Math.sin(ang) * radius);
            ctx.method_25294(x - 1, y - 1, x + 2, y + 2, bgColor);
        }
        int end = (int)(360f * progress);
        for (int i = 0; i < end; i += 3) {
            double ang = Math.toRadians(i - 90);
            int x = cx + (int)(Math.cos(ang) * radius), y = cy + (int)(Math.sin(ang) * radius);
            int alpha = (int)(180 + 75 * Math.sin((float) i / 360f * Math.PI));
            ctx.method_25294(x - 1, y - 1, x + 2, y + 2, withAlpha(color & 0xFFFFFF, alpha));
        }
    }

    public static void scrollIndicator(class_332 ctx, int x, int y, boolean up, float phase, int color) {
        float bounce = (float) Math.sin(phase * Math.PI * 4) * 3f;
        int arrowY = y + (up ? -3 : 3) + (int) bounce;
        String arrow = up ? "▲" : "▼";
        int alpha = (int)(180 + 75 * Math.sin(phase * Math.PI * 2));
        DrawHelper.drawText(ctx, class_310.method_1551().field_1772, arrow, x, arrowY, withAlpha(color & 0xFFFFFF, alpha), true);
    }

    public static void animatedStepIndicator(class_332 ctx, int x, int y, int steps, int current, float phase, int activeColor, int inactiveColor) {
        int dotSize = 10, gap = 40;
        int lineY = y + dotSize / 2;
        ctx.method_25294(x + dotSize, lineY - 1, x + (steps - 1) * gap, lineY + 1, inactiveColor);
        int activeWidth = (int)((float)(current - 1) / (steps - 1) * ((steps - 1) * gap - dotSize));
        if (activeWidth > 0) ctx.method_25294(x + dotSize, lineY - 1, x + dotSize + activeWidth, lineY + 1, activeColor);
        class_310 mc = class_310.method_1551();
        for (int i = 0; i < steps; i++) {
            int cx = x + i * gap + dotSize / 2, cy = y + dotSize / 2;
            boolean isActive = i == current, isCompleted = i < current;
            int color = isActive ? activeColor : (isCompleted ? activeColor : inactiveColor);
            int alpha = isActive ? (int)(200 + 55 * Math.sin(phase * Math.PI * 4)) : (isCompleted ? 255 : 100);
            if (isActive) ctx.method_25294(cx - 8, cy - 8, cx + 8, cy + 8, withAlpha(activeColor & 0xFFFFFF, (int)(40 + 30 * Math.sin(phase * Math.PI * 2))));
            ctx.method_25294(cx - dotSize / 2, cy - dotSize / 2, cx + dotSize / 2, cy + dotSize / 2, withAlpha(color & 0xFFFFFF, alpha));
            String num = String.valueOf(i + 1);
            DrawHelper.drawText(ctx, mc.field_1772, num, cx - mc.field_1772.method_1727(num) / 2, cy - 4, -1, true);
        }
    }

    public static void thumbnailFadeIn(class_332 ctx, int x, int y, int w, int h, float progress, int placeholderColor) {
        if (progress >= 1f) return;
        int alpha = (int)((1f - progress) * 150f);
        int shimmer = (int)(progress * w);
        int c1 = withAlpha(placeholderColor & 0xFFFFFF, alpha), c2 = withAlpha(placeholderColor + 2236962 & 0xFFFFFF, alpha);
        ctx.method_25296(x, y, x + shimmer, y + h, c1, c2);
        ctx.method_25296(x + shimmer, y, x + w, y + h, c2, c1);
    }

    public static float easeOutElastic(float t) {
        float c4 = (float)(Math.PI * 2 / 3);
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return (float) Math.pow(2, -10f * t) * (float) Math.sin((t * 10f - 0.75f) * c4) + 1f;
    }

    public static int getFloatOffset(float phase, float amplitude) {
        return (int)(Math.sin(phase * Math.PI * 2) * amplitude);
    }

    /** Smooth 0→1 animation toward on/off. */
    public static float tickCheckAnim(float current, boolean checked, float delta) {
        float target = checked ? 1f : 0f;
        return lerp(current, target, Math.min(1f, delta * 0.32f));
    }

    /** Animated checkbox with springy checkmark stroke. */
    public static void drawAnimatedCheckbox(class_332 ctx, int x, int y, int size, float checkT, boolean hovered) {
        float pop = checkT > 0.02f
            ? 1f + 0.07f * (float) Math.sin(Math.min(1f, checkT) * Math.PI)
            : (hovered ? 1.03f : 1f);
        int cx = x + size / 2, cy = y + size / 2;
        int hs = Math.max(1, (int) (size * pop / 2f));
        int bx = cx - hs, by = cy - hs, bs = hs * 2;

        float borderMix = Math.max(checkT, hovered ? 0.35f : 0f);
        int border = lerpColor(COL_BORDER, COL_ACCENT, borderMix);
        int fill = lerpColor(withAlpha(COL_SURFACE & 0xFFFFFF, hovered ? 200 : 140), withAlpha(COL_ACCENT & 0xFFFFFF, 45), checkT);
        ctx.method_25294(bx, by, bx + bs, by + bs, fill);
        roundOutline(ctx, bx, by, bs, bs, 2, border);
        if (checkT > 0.01f) {
            drawAnimatedCheckmark(ctx, bx, by, bs, checkT, COL_ACCENT);
        }
    }

    /** Clear readable check icon for buttons and labels. */
    public static void drawCheckIcon(class_332 ctx, int x, int y, int size, int color) {
        drawAnimatedCheckmark(ctx, x, y, size, 1f, color);
    }

    /** Draws a check stroke that animates in two segments. */
    public static void drawAnimatedCheckmark(class_332 ctx, int x, int y, int size, float t, int color) {
        if (t <= 0.001f) return;
        float ease = easeOutBack(Math.min(1f, t));
        int alpha = (int) (255 * Math.min(1f, t * 1.15f));
        int c = withAlpha(color & 0xFFFFFF, alpha);
        int thick = Math.max(2, size / 3);

        float sx = x + size * 0.18f, sy = y + size * 0.55f;
        float mx = x + size * 0.40f, my = y + size * 0.78f;
        float ex = x + size * 0.84f, ey = y + size * 0.24f;
        float seg1 = 0.44f;
        if (ease <= seg1) {
            float p = ease / seg1;
            drawThickLine(ctx, sx, sy, sx + (mx - sx) * p, sy + (my - sy) * p, c, thick);
        } else {
            drawThickLine(ctx, sx, sy, mx, my, c, thick);
            float p = (ease - seg1) / (1f - seg1);
            drawThickLine(ctx, mx, my, mx + (ex - mx) * p, my + (ey - my) * p, c, thick);
        }
    }

    private static void drawThickLine(class_332 ctx, float x0, float y0, float x1, float y1, int color, int thick) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        if (len < 0.5f) return;
        int steps = Math.max(1, (int) len);
        float ux = dx / len, uy = dy / len;
        int half = thick / 2;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = (int) (x0 + dx * t), py = (int) (y0 + dy * t);
            ctx.method_25294(px - half, py - half, px + half + 1, py + half + 1, color);
        }
    }

    /** Pixel sloth face logo — top-left brand mark with gentle bob. */
    public static void drawSlothLogo(class_332 ctx, int x, int y, float phase) {
        int bob = getFloatOffset(phase, 1.2f);
        int cy = y + bob;
        int size = 16;
        ctx.method_25294(x - 2, cy - 2, x + size + 2, cy + size + 2, withAlpha(0x000000, 180));
        ctx.method_25294(x - 1, cy - 1, x + size + 1, cy + size + 1, withAlpha(0xFFFFFF, 48));
        if (GuiAssets.hasLogo()) {
            int texW = GuiAssets.logoWidth();
            int texH = GuiAssets.logoHeight();
            DrawHelper.drawTexture(ctx, GuiAssets.logoId(), x, cy, 0, 0, size, size, texW, texH);
        } else {
            drawSlothLogoProcedural(ctx, x, y, phase);
        }
    }

    /** Procedural sloth face — fallback if logo texture is not registered yet. */
    static void drawSlothLogoProcedural(class_332 ctx, int x, int y, float phase) {
        int bob = getFloatOffset(phase, 1.2f);
        int cy = y + bob;
        int glowA = 40 + (int) (20 * Math.sin(phase * Math.PI * 2));
        ctx.method_25294(x - 1, cy - 1, x + 18, cy + 17, withAlpha(COL_ACCENT & 0xFFFFFF, glowA));
        ctx.method_25294(x, cy, x + 16, cy + 16, withAlpha(0xFFFFFF, 18));

        int accentRgb = COL_ACCENT & 0xFFFFFF;
        int panelRgb = COL_PANEL & 0xFFFFFF;
        int fur = logoTone(0x7A9B5E, accentRgb, panelRgb, 0.35f);
        int furDark = lerpColor(fur, 0x2A1E10, 0.5f) & 0xFFFFFF;
        int face = lerpColor(0xD4C4A8, COL_SURFACE & 0xFFFFFF, 0.25f) & 0xFFFFFF;

        ctx.method_25294(x + 1, cy + 1, x + 4, cy + 4, fur);
        ctx.method_25294(x + 14, cy + 1, x + 17, cy + 4, fur);
        ctx.method_25294(x + 2, cy + 3, x + 16, cy + 15, fur);
        ctx.method_25294(x + 4, cy + 4, x + 14, cy + 14, face);
        ctx.method_25294(x + 4, cy + 6, x + 7, cy + 10, furDark);
        ctx.method_25294(x + 11, cy + 6, x + 14, cy + 10, furDark);
        ctx.method_25294(x + 5, cy + 7, x + 7, cy + 9, 0xFFFFFF);
        ctx.method_25294(x + 12, cy + 7, x + 14, cy + 9, 0xFFFFFF);
        ctx.method_25294(x + 6, cy + 8, x + 7, cy + 9, 0x222222);
        ctx.method_25294(x + 13, cy + 8, x + 14, cy + 9, 0x222222);
        ctx.method_25294(x + 8, cy + 10, x + 10, cy + 11, 0x553322);
        ctx.method_25294(x + 7, cy + 12, x + 11, cy + 13, furDark);
        float claw = (float) Math.sin(phase * Math.PI * 2) * 1.2f;
        ctx.method_25294(x + 8, cy + 14, x + 10, cy + 16, fur);
        ctx.method_25294((int) (x + 7 + claw), cy + 15, (int) (x + 11 + claw), cy + 16, furDark);
    }

    /** Keep logo readable even when accent matches the panel (dark custom themes). */
    private static int logoTone(int base, int accentRgb, int panelRgb, float accentMix) {
        int mixed = lerpColor(base, accentRgb, accentMix) & 0xFFFFFF;
        if (colorDistance(mixed, panelRgb) < 55) {
            mixed = lerpColor(base, 0xFFFFFF, 0.15f) & 0xFFFFFF;
        }
        return mixed;
    }

    private static int colorDistance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return (int) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    /** Compact 40px header bar with sloth logo + title. */
    public static void drawSubscreenHeader(class_332 ctx, net.minecraft.class_327 font, int screenW, String title, float delta) {
        float phase = (float) (System.currentTimeMillis() % 4000L) / 4000f;
        ctx.method_25294(0, 0, screenW, 40, COL_PANEL);
        ctx.method_25294(0, 40, screenW, 41, COL_BORDER);
        ctx.method_25294(0, 0, screenW, 2, COL_ACCENT);
        drawSlothLogo(ctx, 8, 11, phase);
        DrawHelper.drawText(ctx, font, title, 30, 14, COL_TEXT, false);
    }

    /** Small sloth face badge for headers. */
    public static void drawSlothBadge(class_332 ctx, net.minecraft.class_327 font, int x, int y, float phase) {
        drawSlothLogo(ctx, x, y, phase);
    }

    /** Paw print decoration. */
    public static void drawPawPrint(class_332 ctx, int cx, int cy, int color, float scale) {
        int s = Math.max(1, (int)(3 * scale));
        ctx.method_25294(cx - s, cy, cx + s, cy + s, color);
        ctx.method_25294(cx - s * 3, cy - s * 2, cx - s, cy - s, color);
        ctx.method_25294(cx - s, cy - s * 3, cx + s, cy - s, color);
        ctx.method_25294(cx + s, cy - s * 2, cx + s * 3, cy - s, color);
    }

    /** Hanging vine accents on panel edges. */
    public static void drawCornerVines(class_332 ctx, int w, int h, float phase) {
        int vine = withAlpha(COL_ACCENT & 0xFFFFFF, 35);
        for (int i = 0; i < 5; i++) {
            int x = 8 + i * 18;
            int len = 12 + (int)(Math.sin(phase * Math.PI * 2 + i) * 4);
            ctx.method_25294(x, 0, x + 1, len, vine);
            ctx.method_25294(x + 3, 0, x + 4, len - 4, vine);
        }
        for (int i = 0; i < 4; i++) {
            int x = w - 12 - i * 22;
            int len = 10 + (int)(Math.cos(phase * Math.PI * 2 + i * 0.7) * 3);
            ctx.method_25294(x, h - len, x + 1, h, vine);
        }
    }
}
