package com.packhub.ui;

import com.packhub.PackHubConfig;
import com.packhub.compat.DrawHelper;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.class_1109;
import net.minecraft.class_1113;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_3414;
import net.minecraft.class_3417;

public final class Ui {
   private static volatile Method SOUND_PLAY;
   private static volatile Method SOUND_MASTER;
   private static boolean SOUND_RESOLVED;
   private static final List<float[]> stars = new ArrayList<>();
   private static float starSpawnTimer = 0.0F;
   private static final int MAX_STARS = 120;
   private static final List<float[]> killParticles = new ArrayList<>();
   private static float killVignetteAlpha = 0.0F;
   private static final List<float[]> selectionParticles = new ArrayList<>();
   private static final List<float[]> starfield = new ArrayList<>();
   private static float starfieldTimer = 0.0F;
   private static final List<float[]> ripples = new ArrayList<>();

   private static void resolveSoundMethods() {
      if (!SOUND_RESOLVED) {
         SOUND_RESOLVED = true;

         try {
            Class<?> smClass = class_310.method_1551().method_1483().getClass();

            for (Method method : smClass.getMethods()) {
               Class<?>[] p = method.getParameterTypes();
               if (p.length == 1 && class_1113.class.isAssignableFrom(p[0])) {
                  method.setAccessible(true);
                  SOUND_PLAY = method;
                  break;
               }
            }
         } catch (Exception var8) {
         }

         try {
            for (String name : new String[]{"tick", "master", "master", "ui"}) {
               try {
                  Method m = class_1109.class.getMethod(name, class_3414.class, float.class, float.class);
                  if (Modifier.isStatic(m.getModifiers())) {
                     m.setAccessible(true);
                     SOUND_MASTER = m;
                     break;
                  }
               } catch (NoSuchMethodException var6) {
               }
            }
         } catch (Exception var7) {
         }
      }
   }

   private static void playSound(class_3414 event, float pitch, float volume) {
      resolveSoundMethods();
      if (SOUND_MASTER != null && SOUND_PLAY != null) {
         try {
            Object instance = SOUND_MASTER.invoke(null, event, pitch, volume);
            SOUND_PLAY.invoke(class_310.method_1551().method_1483(), instance);
         } catch (Exception var4) {
         }
      }
   }

   private Ui() {
   }

   public static float easeOutCubic(float t) {
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   public static float easeOutBack(float t) {
      float c1 = 1.70158F;
      float c3 = c1 + 1.0F;
      float v = t - 1.0F;
      return 1.0F + c3 * v * v * v + c1 * v * v;
   }

   public static float lerp(float a, float b, float t) {
      return a + (b - a) * Math.max(0.0F, Math.min(1.0F, t));
   }

   public static int lerpColor(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int aa = a >>> 24 & 0xFF;
      int ar = a >>> 16 & 0xFF;
      int ag = a >>> 8 & 0xFF;
      int ab = a & 0xFF;
      int ba = b >>> 24 & 0xFF;
      int br = b >>> 16 & 0xFF;
      int bg = b >>> 8 & 0xFF;
      int bb = b & 0xFF;
      int oa = (int)((float)aa + (float)(ba - aa) * t);
      int or = (int)((float)ar + (float)(br - ar) * t);
      int og = (int)((float)ag + (float)(bg - ag) * t);
      int ob = (int)((float)ab + (float)(bb - ab) * t);
      return oa << 24 | or << 16 | og << 8 | ob;
   }

   public static int withAlpha(int rgb, int alpha) {
      return (alpha & 0xFF) << 24 | rgb & 16777215;
   }

   public static void roundRect(class_332 ctx, int x, int y, int w, int h, int r, int color) {
      if (w > 0 && h > 0) {
         ctx.method_25294(x, y, x + w, y + h, color);
      }
   }

   public static void roundOutline(class_332 ctx, int x, int y, int w, int h, int r, int color) {
      if (w > 0 && h > 0) {
         ctx.method_25294(x, y, x + w, y + 1, color);
         ctx.method_25294(x, y + h - 1, x + w, y + h, color);
         ctx.method_25294(x, y + 1, x + 1, y + h - 1, color);
         ctx.method_25294(x + w - 1, y + 1, x + w, y + h - 1, color);
      }
   }

   public static void gradientRoundRect(class_332 ctx, int x, int y, int w, int h, int r, int top, int bottom) {
      if (w > 0 && h > 0) {
         ctx.method_25296(x, y, x + w, y + h, top, bottom);
      }
   }

   public static void shadow(class_332 ctx, int x, int y, int w, int h, int r) {
      ctx.method_25294(x - 1, y + 1, x + w + 1, y + h + 1, 1426063360);
   }

   public static void shimmer(class_332 ctx, int x, int y, int w, int h, float phase, int highlight) {
      int band = Math.max(8, w / 4);
      int center = (int)((float)(x - band) + (float)(w + band * 2) * phase);
      int alphaMax = highlight >>> 24 & 0xFF;
      if (alphaMax >= 4) {
         int rgb = highlight & 16777215;
         int lo = Math.max(x, center - band);
         int hi = Math.min(x + w, center + band + 1);
         float invBand = 1.0F / (float)band;

         for (int px = lo; px < hi; px += 3) {
            float dist = (float)(px - center) * invBand;
            float bell = (float)Math.exp((double)(-(dist * dist)) * 6.0);
            int a = (int)((float)alphaMax * bell);
            if (a > 8) {
               ctx.method_25294(px, y, px + 3, y + h, a << 24 | rgb);
            }
         }
      }
   }

   public static void spinner(class_332 ctx, int cx, int cy, int radius, float phase, int color) {
      int dots = 8;

      for (int i = 0; i < dots; i++) {
         double ang = (double)i / (double)dots * Math.PI * 2.0 + (double)phase * Math.PI * 2.0;
         int dx = (int)(Math.cos(ang) * (double)radius);
         int dy = (int)(Math.sin(ang) * (double)radius);
         float t = ((float)i + (1.0F - phase) * (float)dots) % (float)dots / (float)dots;
         int alpha = (int)(40.0F + 215.0F * (1.0F - t));
         int c = withAlpha(color & 16777215, Math.max(40, Math.min(255, alpha)));
         ctx.method_25294(cx + dx - 1, cy + dy - 1, cx + dx + 1, cy + dy + 1, c);
      }
   }

   public static void renderShootingStars(class_332 ctx, int screenW, int screenH, float delta) {
      starSpawnTimer += delta;
      float spawnInterval = 0.02F;

      while (starSpawnTimer > spawnInterval && stars.size() < 120) {
         starSpawnTimer -= spawnInterval;
         spawnStar(screenW, screenH);
      }

      Iterator<float[]> it = stars.iterator();

      while (it.hasNext()) {
         float[] s = it.next();
         float drift = (float)Math.sin((double)s[4] * 1.5 + (double)s[0] * 0.01) * s[3];
         s[0] += drift * delta * 25.0F;
         s[1] += s[2] * delta * 60.0F;
         s[4] += delta;
         if (!(s[1] > (float)(screenH + 5)) && !(s[4] > s[5])) {
            float progress = s[4] / s[5];
            float alpha;
            if (progress < 0.15F) {
               alpha = progress / 0.15F;
            } else if (progress > 0.8F) {
               alpha = (1.0F - progress) / 0.2F;
            } else {
               alpha = 1.0F;
            }

            alpha = Math.max(0.0F, Math.min(1.0F, alpha));
            int a = (int)(alpha * s[7]);
            if (a >= 2) {
               int sz = (int)s[6];
               int px = (int)s[0];
               int py = (int)s[1];
               ctx.method_25294(px, py, px + sz, py + sz, a << 24 | 16777215);
            }
         } else {
            it.remove();
         }
      }
   }

   private static void spawnStar(int w, int h) {
      float x = (float)(Math.random() * (double)w);
      float y = -3.0F;
      float speedY = 0.2F + (float)(Math.random() * 0.6F);
      float driftX = 0.15F + (float)(Math.random() * 0.5);
      float maxLife = (float)h / (speedY * 60.0F) + 2.0F;
      double r = Math.random();
      float size = r < 0.5 ? 1.0F : (r < 0.85 ? 2.0F : 3.0F);
      float baseAlpha = 40.0F + (float)(Math.random() * 80.0);
      stars.add(new float[]{x, y, speedY, driftX, 0.0F, maxLife, size, baseAlpha});
   }

   public static void onKill() {
      String effect = PackHubConfig.getKillEffect();
      if (!"none".equals(effect)) {
         float cx = 0.45F + (float)(Math.random() * 0.1F);
         float cy = 0.38F + (float)(Math.random() * 0.06F);
         killParticles.add(new float[]{cx, cy, -40.0F, 1.0F, 0.0F, 1.2F});
         killVignetteAlpha = 1.0F;
         if ("totem".equals(effect)) {
            playTotemKill();
         } else if ("anvil".equals(effect)) {
            playAnvilKill();
         } else if ("thunder".equals(effect)) {
            playThunderKill();
         } else {
            playKill();
         }
      }
   }

   public static void renderKillEffect(class_332 ctx, int screenW, int screenH, float delta) {
      String effect = PackHubConfig.getKillEffect();
      int vignetteRgb;
      int textRgb;
      if ("anvil".equals(effect)) {
         vignetteRgb = 6710886;
         textRgb = 10066329;
      } else if ("thunder".equals(effect)) {
         vignetteRgb = 8961023;
         textRgb = 8965375;
      } else {
         vignetteRgb = 16755200;
         textRgb = 16755200;
      }

      if (killVignetteAlpha > 0.0F) {
         killVignetteAlpha = Math.max(0.0F, killVignetteAlpha - delta * 3.3F);
         int a = (int)(killVignetteAlpha * 80.0F);
         if (a > 0) {
            int edgeColor = a << 24 | vignetteRgb;
            int edgeW = Math.max(6, screenW / 12);
            int edgeH = Math.max(6, screenH / 12);
            int transparentRgb = 0 | vignetteRgb;
            ctx.method_25296(0, 0, screenW, edgeH, edgeColor, transparentRgb);
            ctx.method_25296(0, screenH - edgeH, screenW, screenH, transparentRgb, edgeColor);
            ctx.method_25296(0, 0, edgeW, screenH, edgeColor, a / 2 << 24 | vignetteRgb);
            ctx.method_25296(screenW - edgeW, 0, screenW, screenH, a / 2 << 24 | vignetteRgb, edgeColor);
         }
      }

      Iterator<float[]> it = killParticles.iterator();
      class_310 mc = class_310.method_1551();

      while (it.hasNext()) {
         float[] p = it.next();
         p[4] += delta;
         float progress = p[4] / p[5];
         if (progress >= 1.0F) {
            it.remove();
         } else {
            p[1] += p[2] / (float)screenH * delta;
            p[3] = 1.0F - easeOutCubic(progress);
            int alpha = (int)(p[3] * 255.0F);
            if (alpha >= 4) {
               int px = (int)(p[0] * (float)screenW);
               int py = (int)(p[1] * (float)screenH);
               String text = "+KILL";
               int tw = mc.field_1772.method_1727(text);
               int color = alpha << 24 | textRgb;
               DrawHelper.drawText(ctx, mc.field_1772, text, px - tw / 2, py, color, true);
            }
         }
      }
   }

   public static void tickKillEffect(float delta) {
      if (killVignetteAlpha > 0.0F) {
         killVignetteAlpha = Math.max(0.0F, killVignetteAlpha - delta * 3.3F);
      }

      Iterator<float[]> it = killParticles.iterator();

      while (it.hasNext()) {
         float[] p = it.next();
         p[4] += delta;
         if (p[4] / p[5] >= 1.0F) {
            it.remove();
         }
      }
   }

   public static void playClick() {
      playSound((class_3414)class_3417.field_15015.comp_349(), 1.0F, 0.6F);
   }

   public static void playOpen() {
      playSound((class_3414)class_3417.field_14622.comp_349(), 1.0F, 0.5F);
   }

   public static void playClose() {
      playSound((class_3414)class_3417.field_15015.comp_349(), 0.7F, 0.4F);
   }

   public static void playStar() {
      playSound((class_3414)class_3417.field_14725.comp_349(), 1.2F, 0.5F);
   }

   public static void playSuccess() {
      playSound((class_3414)class_3417.field_14622.comp_349(), 1.5F, 0.7F);
   }

   public static void playError() {
      playSound((class_3414)class_3417.field_14624.comp_349(), 0.5F, 0.5F);
   }

   public static void playKill() {
      playSound(class_3417.field_14627, 1.2F, 0.7F);
   }

   public static void playTotemKill() {
      playSound(class_3417.field_14931, 1.0F, 0.8F);
   }

   public static void playAnvilKill() {
      playSound(class_3417.field_14833, 0.8F, 0.7F);
   }

   public static void playThunderKill() {
      playSound(class_3417.field_14865, 0.6F, 1.2F);
   }

   public static void pulseGlow(class_332 ctx, int x, int y, int w, int h, float phase, int baseColor, int glowColor) {
      float pulse = (float)Math.sin((double)phase * Math.PI * 2.0) * 0.5F + 0.5F;
      int glowAlpha = (int)(pulse * 60.0F);
      int glow = withAlpha(glowColor & 16777215, glowAlpha);
      ctx.method_25294(x - 2, y - 2, x + w + 2, y, glow);
      ctx.method_25294(x - 2, y + h, x + w + 2, y + h + 2, glow);
      ctx.method_25294(x - 2, y, x, y + h, glow);
      ctx.method_25294(x + w, y, x + w + 2, y + h, glow);
   }

   public static void animatedBorder(class_332 ctx, int x, int y, int w, int h, float phase, int color1, int color2) {
      float t = (float)Math.sin((double)phase * Math.PI * 2.0) * 0.5F + 0.5F;
      int c = lerpColor(color1, color2, t);
      ctx.method_25294(x, y, x + w, y + 2, c);
      ctx.method_25294(x, y + h - 2, x + w, y + h, c);
      ctx.method_25294(x, y + 2, x + 2, y + h - 2, c);
      ctx.method_25294(x + w - 2, y + 2, x + w, y + h - 2, c);
   }

   public static void spawnSelectionBurst(int cx, int cy, int color) {
      for (int i = 0; i < 12; i++) {
         float angle = (float)i / 12.0F * (float) Math.PI * 2.0F;
         float speed = 2.0F + (float)Math.random() * 3.0F;
         selectionParticles.add(
            new float[]{
               (float)cx,
               (float)cy,
               (float)Math.cos((double)angle) * speed,
               (float)Math.sin((double)angle) * speed,
               0.0F,
               0.6F + (float)Math.random() * 0.4F,
               (float)(color >> 16 & 0xFF),
               (float)(color >> 8 & 0xFF),
               (float)(color & 0xFF)
            }
         );
      }
   }

   public static void renderSelectionParticles(class_332 ctx, float delta) {
      Iterator<float[]> it = selectionParticles.iterator();

      while (it.hasNext()) {
         float[] p = it.next();
         p[0] += p[2] * delta * 60.0F;
         p[1] += p[3] * delta * 60.0F;
         p[3] += 0.15F * delta * 60.0F;
         p[4] += delta;
         float progress = p[4] / p[5];
         if (progress >= 1.0F) {
            it.remove();
         } else {
            int alpha = (int)((1.0F - progress) * 200.0F);
            if (alpha >= 5) {
               int color = alpha << 24 | (int)p[6] << 16 | (int)p[7] << 8 | (int)p[8];
               int px = (int)p[0];
               int py = (int)p[1];
               int size = (int)(3.0F * (1.0F - progress * 0.7F));
               ctx.method_25294(px - size, py - size, px + size, py + size, color);
            }
         }
      }
   }

   public static void waveShimmer(class_332 ctx, int x, int y, int w, int h, float phase, int color) {
      for (int i = 0; i < w; i += 4) {
         float wave = (float)Math.sin((double)((float)i / (float)w) * Math.PI * 3.0 + (double)phase * Math.PI * 4.0);
         float alpha = (wave * 0.5F + 0.5F) * (float)(color >>> 24 & 0xFF) / 255.0F;
         int a = (int)(alpha * 60.0F);
         int c = a << 24 | color & 16777215;
         ctx.method_25294(x + i, y, x + i + 4, y + h, c);
      }
   }

   public static int getFloatOffset(float phase, float amplitude) {
      return (int)(Math.sin((double)phase * Math.PI * 2.0) * (double)amplitude);
   }

   public static float bounceScale(float t) {
      if (t < 0.3F) {
         return 1.0F + t * 0.5F;
      } else {
         return t < 0.6F ? 1.15F - (t - 0.3F) * 0.3F : 1.0F + (1.0F - t) * 0.1F;
      }
   }

   public static float easeOutElastic(float t) {
      float c4 = (float) (Math.PI * 2.0 / 3.0);
      if (t <= 0.0F) {
         return 0.0F;
      } else {
         return t >= 1.0F ? 1.0F : (float)Math.pow(2.0, (double)(-10.0F * t)) * (float)Math.sin((double)((t * 10.0F - 0.75F) * c4)) + 1.0F;
      }
   }

   public static void progressRing(class_332 ctx, int cx, int cy, int radius, float progress, int color, int bgColor) {
      for (int i = 0; i < 360; i += 5) {
         double ang = Math.toRadians((double)i);
         int x = cx + (int)(Math.cos(ang) * (double)radius);
         int y = cy + (int)(Math.sin(ang) * (double)radius);
         ctx.method_25294(x - 1, y - 1, x + 2, y + 2, bgColor);
      }

      int endAngle = (int)(360.0F * progress);

      for (int i = 0; i < endAngle; i += 3) {
         double ang = Math.toRadians((double)(i - 90));
         int x = cx + (int)(Math.cos(ang) * (double)radius);
         int y = cy + (int)(Math.sin(ang) * (double)radius);
         int alpha = (int)(180.0 + 75.0 * Math.sin((double)((float)i / 360.0F) * Math.PI));
         int c = withAlpha(color & 16777215, alpha);
         ctx.method_25294(x - 1, y - 1, x + 2, y + 2, c);
      }
   }

   public static void neonTrail(class_332 ctx, int x, int y, int w, int h, float phase, int accentColor) {
      int layers = 4;

      for (int i = 0; i < layers; i++) {
         float offset = (phase * (float)layers + (float)i) % (float)layers;
         float alpha = (1.0F - offset / (float)layers) * 40.0F;
         int expand = (int)(offset * 2.0F);
         int a = (int)alpha;
         int c = withAlpha(accentColor & 16777215, a);
         ctx.method_25294(x - expand, y - expand, x + w + expand, y + h + expand, c);
      }
   }

   public static void renderStarfield(class_332 ctx, int screenW, int screenH, float delta) {
      starfieldTimer += delta;

      while (starfield.size() < 80 && Math.random() < 0.1) {
         float layer = (float)Math.random();
         starfield.add(
            new float[]{
               (float)(Math.random() * (double)screenW),
               (float)(Math.random() * (double)screenH),
               layer,
               0.2F + layer * 0.3F,
               (float)(Math.random() * Math.PI * 2.0)
            }
         );
      }

      Iterator<float[]> it = starfield.iterator();

      while (it.hasNext()) {
         float[] s = it.next();
         s[4] += delta * s[3];
         float twinkle = (float)Math.sin((double)s[4]) * 0.5F + 0.5F;
         int alpha = (int)(twinkle * (60.0F + (1.0F - s[2]) * 100.0F));
         int size = (double)s[2] < 0.3 ? 2 : 1;
         int c = alpha << 24 | 16777215;
         int px = (int)s[0];
         int py = (int)s[1];
         ctx.method_25294(px, py, px + size, py + size, c);
         if (px < -5 || px > screenW + 5 || py < -5 || py > screenH + 5) {
            it.remove();
         }
      }
   }

   public static void addRipple(int cx, int cy, int color) {
      ripples.add(new float[]{(float)cx, (float)cy, 0.0F, 1.0F, (float)(color >> 16 & 0xFF), (float)(color >> 8 & 0xFF), (float)(color & 0xFF)});
   }

   public static void renderRipples(class_332 ctx, float delta) {
      Iterator<float[]> it = ripples.iterator();

      while (it.hasNext()) {
         float[] r = it.next();
         r[2] += delta * 150.0F;
         r[3] -= delta * 1.5F;
         if (r[3] <= 0.0F) {
            it.remove();
         } else {
            int alpha = (int)(r[3] * 150.0F);
            int color = alpha << 24 | (int)r[4] << 16 | (int)r[5] << 8 | (int)r[6];
            int cx = (int)r[0];
            int cy = (int)r[1];
            int rad = (int)r[2];

            for (int a = 0; a < 360; a += 10) {
               double ang = Math.toRadians((double)a);
               int x = cx + (int)(Math.cos(ang) * (double)rad);
               int y = cy + (int)(Math.sin(ang) * (double)rad);
               ctx.method_25294(x - 1, y - 1, x + 1, y + 1, color);
            }
         }
      }
   }

   public static void scrollIndicator(class_332 ctx, int x, int y, boolean up, float phase, int color) {
      float bounce = (float)Math.sin((double)phase * Math.PI * 4.0) * 3.0F;
      int arrowY = y + (up ? -3 : 3) + (int)bounce;
      String arrow = up ? "▲" : "▼";
      int alpha = (int)(180.0 + 75.0 * Math.sin((double)phase * Math.PI * 2.0));
      int c = withAlpha(color & 16777215, alpha);
      class_310 mc = class_310.method_1551();
      DrawHelper.drawText(ctx, mc.field_1772, arrow, x, arrowY, c, true);
   }

   public static void animatedStepIndicator(class_332 ctx, int x, int y, int steps, int current, float phase, int activeColor, int inactiveColor) {
      int dotSize = 10;
      int gap = 40;
      class_310 mc = class_310.method_1551();
      int lineY = y + dotSize / 2;
      ctx.method_25294(x + dotSize, lineY - 1, x + (steps - 1) * gap, lineY + 1, inactiveColor);
      int activeWidth = (int)((float)(current - 1) / (float)(steps - 1) * (float)((steps - 1) * gap - dotSize));
      if (activeWidth > 0) {
         ctx.method_25294(x + dotSize, lineY - 1, x + dotSize + activeWidth, lineY + 1, activeColor);
      }

      for (int i = 0; i < steps; i++) {
         int cx = x + i * gap + dotSize / 2;
         int cy = y + dotSize / 2;
         boolean isActive = i == current;
         boolean isCompleted = i < current;
         int color = isActive ? activeColor : (isCompleted ? activeColor : inactiveColor);
         int alpha = isActive ? (int)(200.0 + 55.0 * Math.sin((double)phase * Math.PI * 4.0)) : (isCompleted ? 255 : 100);
         int c = withAlpha(color & 16777215, alpha);
         if (isActive) {
            int glowAlpha = (int)(40.0 + 30.0 * Math.sin((double)phase * Math.PI * 2.0));
            int glow = withAlpha(activeColor & 16777215, glowAlpha);
            ctx.method_25294(cx - 8, cy - 8, cx + 8, cy + 8, glow);
         }

         ctx.method_25294(cx - dotSize / 2, cy - dotSize / 2, cx + dotSize / 2, cy + dotSize / 2, c);
         String num = String.valueOf(i + 1);
         int tw = mc.field_1772.method_1727(num);
         DrawHelper.drawText(ctx, mc.field_1772, num, cx - tw / 2, cy - 4, -1, true);
      }
   }

   public static void cardHoverEffect(class_332 ctx, int x, int y, int w, int h, float hoverProgress, int baseColor, int accentColor) {
      float lift = hoverProgress * 4.0F;
      int shadowAlpha = (int)(hoverProgress * 60.0F);
      int shadow = withAlpha(0, shadowAlpha);
      ctx.method_25294((int)((float)x + lift), y + h, (int)((float)(x + w) + lift), (int)((float)(y + h) + lift), shadow);
      ctx.method_25294(x + w, (int)((float)y + lift), (int)((float)(x + w) + lift), (int)((float)(y + h) + lift), shadow);
      if (hoverProgress > 0.1F) {
         int glowAlpha = (int)(hoverProgress * 100.0F);
         int glow = withAlpha(accentColor & 16777215, glowAlpha);
         ctx.method_25294(x, y, x + w, y + 2, glow);
         ctx.method_25294(x, y + h - 2, x + w, y + h, glow);
      }
   }

   public static void animatedCheckmark(class_332 ctx, int x, int y, float appearProgress, int color) {
      float scale = easeOutBack(Math.min(1.0F, appearProgress * 2.0F));
      int size = (int)(8.0F * scale);
      int alpha = (int)(Math.min(1.0F, appearProgress * 3.0F) * 255.0F);
      int c = withAlpha(color & 16777215, alpha);
      int cx = x + size;
      int cy = y + size;

      for (int i = 0; i < size; i++) {
         ctx.method_25294(cx - size + i, cy + i / 2, cx - size + i + 2, cy + i / 2 + 2, c);
      }

      for (int i = 0; i < size; i++) {
         ctx.method_25294(cx + i / 2, cy - i / 2, cx + i / 2 + 2, cy - i / 2 + 2, c);
      }
   }

   public static void thumbnailFadeIn(class_332 ctx, int x, int y, int w, int h, float progress, int placeholderColor) {
      if (progress < 1.0F) {
         int alpha = (int)((1.0F - progress) * 150.0F);
         int shimmer = (int)(progress * (float)w);
         int c1 = withAlpha(placeholderColor & 16777215, alpha);
         int c2 = withAlpha(placeholderColor + 2236962 & 16777215, alpha);
         ctx.method_25296(x, y, x + shimmer, y + h, c1, c2);
         ctx.method_25296(x + shimmer, y, x + w, y + h, c2, c1);
      }
   }

   public static void tabSlideIndicator(class_332 ctx, int fromX, int toX, int y, int w, int h, float progress, int color) {
      float ease = easeOutCubic(progress);
      int currentX = (int)((float)fromX + (float)(toX - fromX) * ease);
      int alpha = (int)(progress * 255.0F);
      int c = withAlpha(color & 16777215, alpha);
      ctx.method_25294(currentX, y + h - 3, currentX + w, y + h - 1, c);
   }
}
