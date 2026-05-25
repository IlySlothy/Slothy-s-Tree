package com.slothyhub.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1058;
import net.minecraft.class_1921;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_5481;
import net.minecraft.class_7368;
import net.minecraft.class_7764;
import net.minecraft.class_7771;

public final class DrawHelper {
   private static final Method DRAW_TEXT_STRING = resolveDrawTextSixArg(String.class);
   private static final Method DRAW_TEXT_TEXT = resolveDrawTextSixArg(class_2561.class);
   private static final Method DRAW_TEXT_ORDERED = resolveDrawTextSixArg(class_5481.class);
   private static final Method DRAW_TEXTURE_METHOD;
   private static final Object DRAW_TEXTURE_FIRST_ARG;
   private static final Constructor<?> NATIVE_TEX_CTOR;
   private static final boolean NATIVE_TEX_NEEDS_NAME;
   private static final Method GET_MATRICES;
   private static final boolean MATRICES_IS_JOML;
   private static final Method MAT_PUSH;
   private static final Method MAT_POP;
   private static final Method MAT_TRANSLATE;
   private static final Method MAT_SCALE;
   private static final Method FLUSH_DRAW;
   /** 1.21.4–1.21.7: avoid pipeline blit — sprite/region blit + flush between fill and text. */
   private static final boolean LEGACY_GUI_FLUSH = McVersion.below("1.21.8");
   private static final Map<class_2960, class_1058> GUI_SPRITES = new ConcurrentHashMap<>();
   private static final Function<class_2960, Object> LEGACY_RENDER_LAYER = resolveLegacyRenderLayer();
   private static final Method LEGACY_REGION_BLIT = resolveLegacyRegionBlit();
   private static final Method LEGACY_BLIT_SPRITE = resolveBlitSpriteMethod(true);
   private static final Method MODERN_BLIT_SPRITE = resolveBlitSpriteMethod(false);
   private static final Method ENTITY_CUTOUT_LAYER = findGuiTexturedRenderLayerMethod();

   private static Constructor<class_1058> SPRITE_CTOR_6;
   private static Constructor<class_1058> SPRITE_CTOR_7;
   private static boolean spriteCtorsResolved;

   private DrawHelper() {
   }

   /** Version-tolerant SpriteContents construction (1.21.4-1.21.11). */
   public static class_7764 createSpriteContents(class_2960 id, class_1011 img) {
      if (id == null || img == null) return null;
      int w = Math.max(1, img.method_4307());
      int h = Math.max(1, img.method_4323());
      class_7771 dimensions = new class_7771(w, h);
      if (McVersion.atLeast("1.21.9")) {
         for (Constructor<?> ctor : class_7764.class.getDeclaredConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 3 && p[0] == class_2960.class && p[1] == class_7771.class && p[2] == class_1011.class) {
               try {
                  ctor.setAccessible(true);
                  return (class_7764) ctor.newInstance(id, dimensions, img);
               } catch (Exception ignored) {}
            }
         }
      }
      try {
         return new class_7764(id, dimensions, img, class_7368.field_38688);
      } catch (Throwable ignored) {}
      for (Constructor<?> ctor : class_7764.class.getDeclaredConstructors()) {
         Object[] args = matchSpriteContentsArgs(ctor.getParameterTypes(), id, img, dimensions);
         if (args == null) continue;
         try {
            ctor.setAccessible(true);
            return (class_7764) ctor.newInstance(args);
         } catch (Exception ignored) {}
      }
      return null;
   }

   public static class_1058 createFullSprite(class_2960 id, class_1011 img) {
      if (id == null || img == null) return null;
      class_7764 contents = createSpriteContents(id, img);
      if (contents == null) return null;
      int w = Math.max(1, img.method_4307());
      int h = Math.max(1, img.method_4323());
      return createAtlasSprite(id, contents, w, h, 0, 0);
   }

   public static class_1058 createAtlasSprite(class_2960 id, class_7764 contents, int atlasW, int atlasH, int x, int y) {
      if (id == null || contents == null) return null;
      resolveSpriteCtors();
      try {
         if (SPRITE_CTOR_7 != null) {
            return SPRITE_CTOR_7.newInstance(id, contents, atlasW, atlasH, x, y, 0);
         }
         if (SPRITE_CTOR_6 != null) {
            return SPRITE_CTOR_6.newInstance(id, contents, atlasW, atlasH, x, y);
         }
      } catch (Exception ignored) {}
      return null;
   }

   public static void bindSpriteGpu(class_1058 sprite, class_1043 tex) {
      if (sprite == null || tex == null) return;
      Object gpu = invokeGetGpuTexture(tex);
      if (gpu == null) return;
      try {
         for (Method m : class_1058.class.getMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(gpu)) {
               m.invoke(sprite, gpu);
               return;
            }
            if (m.getParameterCount() == 2
               && m.getParameterTypes()[0].isInstance(gpu)
               && m.getParameterTypes()[1] == int.class) {
               m.invoke(sprite, gpu, 0);
               return;
            }
         }
      } catch (Exception ignored) {}
   }

   /** Reflection-safe lookup for {@code NativeImageBackedTexture.getGpuTexture()} (added in 1.21.8).
    *  Returns {@code null} on MC versions that don't expose a GpuTexture accessor instead of throwing. */
   private static Object invokeGetGpuTexture(class_1043 tex) {
      Method m = GET_GPU_TEXTURE;
      if (m == null) return null;
      try {
         return m.invoke(tex);
      } catch (ReflectiveOperationException e) {
         return null;
      }
   }

   private static Method resolveGetGpuTexture() {
      for (String name : new String[]{"method_68004", "getGpuTexture"}) {
         try {
            Method m = class_1043.class.getMethod(name);
            if (m.getParameterCount() == 0 && !m.getReturnType().isPrimitive()) {
               m.setAccessible(true);
               return m;
            }
         } catch (ReflectiveOperationException ignored) {}
      }
      for (Method m : class_1043.class.getMethods()) {
         if (m.getParameterCount() != 0) continue;
         String rt = m.getReturnType().getName();
         if (rt.contains("GpuTexture")) {
            m.setAccessible(true);
            return m;
         }
      }
      return null;
   }

   private static final Method GET_GPU_TEXTURE = resolveGetGpuTexture();

   @SuppressWarnings("unchecked")
   private static void resolveSpriteCtors() {
      if (spriteCtorsResolved) return;
      spriteCtorsResolved = true;
      for (Constructor<?> ctor : class_1058.class.getDeclaredConstructors()) {
         Class<?>[] p = ctor.getParameterTypes();
         if (p.length == 7 && p[0] == class_2960.class && p[1] == class_7764.class
            && p[2] == int.class && p[3] == int.class && p[4] == int.class
            && p[5] == int.class && p[6] == int.class) {
            ctor.setAccessible(true);
            SPRITE_CTOR_7 = (Constructor<class_1058>) ctor;
         } else if (p.length == 6 && p[0] == class_2960.class && p[1] == class_7764.class
            && p[2] == int.class && p[3] == int.class && p[4] == int.class && p[5] == int.class) {
            ctor.setAccessible(true);
            SPRITE_CTOR_6 = (Constructor<class_1058>) ctor;
         }
      }
      if (SPRITE_CTOR_7 == null) {
         try {
            SPRITE_CTOR_7 = class_1058.class.getDeclaredConstructor(
               class_2960.class, class_7764.class, int.class, int.class, int.class, int.class, int.class);
            SPRITE_CTOR_7.setAccessible(true);
         } catch (NoSuchMethodException ignored) {}
      }
      if (SPRITE_CTOR_6 == null) {
         try {
            SPRITE_CTOR_6 = class_1058.class.getDeclaredConstructor(
               class_2960.class, class_7764.class, int.class, int.class, int.class, int.class);
            SPRITE_CTOR_6.setAccessible(true);
         } catch (NoSuchMethodException ignored) {}
      }
   }

   private static Object[] matchSpriteContentsArgs(Class<?>[] params, class_2960 id, class_1011 img,
                                                   class_7771 dimensions) {
      Object[] args = new Object[params.length];
      for (int i = 0; i < params.length; i++) {
         Class<?> p = params[i];
         if (p == class_2960.class) args[i] = id;
         else if (p == class_1011.class) args[i] = img;
         else if (p == class_7771.class) args[i] = dimensions;
         else if (p.isEnum()) {
            Object[] constants = p.getEnumConstants();
            args[i] = constants != null && constants.length > 0 ? constants[0] : null;
         } else if (!p.isPrimitive()) args[i] = null;
         else return null;
      }
      return args;
   }

   /** Entity/item cutout render layer for a dynamic texture id (1.21.8–1.21.11). */
   public static class_1921 entityCutoutLayer(class_2960 textureId) {
      if (ENTITY_CUTOUT_LAYER == null || textureId == null) return null;
      try {
         return (class_1921) ENTITY_CUTOUT_LAYER.invoke(null, textureId);
      } catch (ReflectiveOperationException e) {
         return null;
      }
   }

   private static Function<class_2960, Object> resolveLegacyRenderLayer() {
      Method guiTex = findGuiTexturedRenderLayerMethod();
      if (guiTex == null) return id -> null;
      return id -> {
         try {
            return guiTex.invoke(null, id);
         } catch (ReflectiveOperationException e) {
            return null;
         }
      };
   }

   /** MC 1.21.4–1.21.7 {@code DrawContext.method_25302} — full-texture UV scaling. */
   private static Method resolveLegacyRegionBlit() {
      try {
         Method m = class_332.class.getMethod(
            "method_25302",
            Function.class,
            class_2960.class,
            int.class,
            int.class,
            float.class,
            float.class,
            int.class,
            int.class,
            int.class,
            int.class,
            int.class,
            int.class
         );
         m.setAccessible(true);
         return m;
      } catch (ReflectiveOperationException e) {
         return null;
      }
   }

   /** {@code DrawContext.method_52709} — draws via sprite UVs (works for dynamic textures). */
   private static Method resolveBlitSpriteMethod(boolean legacyFunctionFirst) {
      for (Method m : class_332.class.getMethods()) {
         if (!"method_52709".equals(m.getName()) || m.getParameterCount() != 6) continue;
         Class<?>[] p = m.getParameterTypes();
         if (p[1] != class_1058.class || p[2] != int.class) continue;
         if (legacyFunctionFirst && Function.class.isAssignableFrom(p[0])) {
            m.setAccessible(true);
            return m;
         }
         if (!legacyFunctionFirst && !Function.class.isAssignableFrom(p[0]) && p[0] != class_2960.class) {
            m.setAccessible(true);
            return m;
         }
      }
      return null;
   }

   private static void invokeLegacyRegionBlit(
      class_332 ctx,
      class_2960 id,
      int x,
      int y,
      float u,
      float v,
      int width,
      int height,
      int texW,
      int texH
   ) throws ReflectiveOperationException {
      if (LEGACY_REGION_BLIT == null || LEGACY_RENDER_LAYER == null) return;
      int tw = Math.max(1, texW);
      int th = Math.max(1, texH);
      LEGACY_REGION_BLIT.invoke(ctx, LEGACY_RENDER_LAYER, id, x, y, u, v, width, height, tw, th, tw, th);
   }

   private static void invokeSpriteBlit(class_332 ctx, class_1058 sprite, int x, int y, int width, int height)
      throws ReflectiveOperationException {
      if (sprite == null || width <= 0 || height <= 0) return;
      if (LEGACY_GUI_FLUSH) {
         if (LEGACY_BLIT_SPRITE == null || LEGACY_RENDER_LAYER == null) return;
         LEGACY_BLIT_SPRITE.invoke(ctx, LEGACY_RENDER_LAYER, sprite, x, y, width, height);
      } else if (MODERN_BLIT_SPRITE != null && DRAW_TEXTURE_FIRST_ARG != null) {
         MODERN_BLIT_SPRITE.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, sprite, x, y, width, height);
      }
   }

   private static int opaqueTextColor(int color) {
      return (color & 0xFFFFFF) | 0xFF000000;
   }

   private static boolean matchesTextureGridSignature(Class<?>[] p) {
      if (p.length < 10 || p.length > 13 || p[0] == class_2960.class) {
         return false;
      } else if (p[1] == class_2960.class && p[2] == int.class && p[3] == int.class && p[4] == float.class && p[5] == float.class) {
         for (int i = 6; i < p.length; i++) {
            if (p[i] != int.class) {
               return false;
            }
         }

         return p.length == 10 || p.length == 11 || p.length == 12 || p.length == 13;
      } else {
         return false;
      }
   }

   private static List<Method> listDrawTextureUiMethods() {
      List<Method> out = new ArrayList<>();

      for (Method m : class_332.class.getMethods()) {
         if (matchesTextureGridSignature(m.getParameterTypes())) {
            out.add(m);
         }
      }

      out.sort(Comparator.comparingInt(mx -> mx.getParameterTypes().length));
      return out;
   }

   private static Method resolveDrawTextSixArg(Class<?> textArg) {
      List<String> preferred = new ArrayList<>();
      if (textArg == String.class) {
         preferred.add("method_51433");
      } else if (textArg == class_5481.class) {
         preferred.add("method_51430");
      } else if (textArg == class_2561.class) {
         preferred.add("method_51439");
      }
      preferred.add("drawText");

      for (String name : preferred) {
         try {
            Method m = class_332.class.getMethod(name, class_327.class, textArg, int.class, int.class, int.class, boolean.class);
            m.setAccessible(true);
            return m;
         } catch (ReflectiveOperationException ignored) {
         }
      }

      List<Method> found = new ArrayList<>();

      for (Method m : class_332.class.getMethods()) {
         Class<?>[] p = m.getParameterTypes();
         if (p.length == 6
            && p[0] == class_327.class
            && p[1] == textArg
            && p[2] == int.class
            && p[3] == int.class
            && p[4] == int.class
            && p[5] == boolean.class) {
            found.add(m);
         }
      }

      if (found.isEmpty()) {
         return null;
      } else {
         found.sort(Comparator.comparing(Method::getName));
         Method pick = found.get(0);
         pick.setAccessible(true);
         return pick;
      }
   }

   private static void invokeDrawText(Method m, class_332 ctx, Object... args) {
      if (m != null) {
         try {
            m.invoke(ctx, args);
         } catch (ReflectiveOperationException var4) {
         }
      }
   }

   private static Method findGuiTexturedRenderLayerMethod() {
      // getGuiTextured is stable on 1.21.4; intermediary names vary on 1.21.8.
      for (String name : new String[]{"getGuiTextured", "method_65214", "method_65213", "method_23576"}) {
         try {
            Method exact = class_1921.class.getMethod(name, class_2960.class);
            if (Modifier.isStatic(exact.getModifiers()) && class_1921.class.isAssignableFrom(exact.getReturnType())) {
               exact.setAccessible(true);
               return exact;
            }
         } catch (ReflectiveOperationException ignored) {
         }
      }

      List<Method> cands = new ArrayList<>();

      for (Method m : class_1921.class.getMethods()) {
         if (Modifier.isStatic(m.getModifiers())
            && m.getParameterCount() == 1
            && m.getParameterTypes()[0] == class_2960.class
            && class_1921.class.isAssignableFrom(m.getReturnType())) {
            cands.add(m);
         }
      }

      if (!cands.isEmpty()) {
         cands.sort(Comparator.comparing(Method::getName));
         Method mx = cands.get(0);
         mx.setAccessible(true);
         return mx;
      } else {
         return null;
      }
   }

   private static Object tryGetVertexFormat(Object pipeline) {
      if (pipeline == null) {
         return null;
      } else {
         for (Class<?> c = pipeline.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
               if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0) {
                  Class<?> rt = m.getReturnType();
                  if (rt.getName().contains("VertexFormat")) {
                     try {
                        m.setAccessible(true);
                        Object r = m.invoke(pipeline);
                        if (r instanceof Optional<?> opt) {
                           r = opt.orElse(null);
                        }

                        if (r != null) {
                           return r;
                        }
                     } catch (ReflectiveOperationException var9) {
                     }
                  }
               }
            }
         }

         return null;
      }
   }

   private static int scorePipelineForGuiTextured(Object pipeline) {
      int score = 0;
      String ts = "";

      try {
         ts = pipeline.toString().toLowerCase();
      } catch (Throwable var11) {
      }

      if (ts.contains("gui")) {
         score += 40;
      }

      if (ts.contains("textured")) {
         score += 15;
      }

      if (ts.contains("hud")) {
         score += 10;
      }

      if (ts.contains("entity") || ts.contains("celestial") || ts.contains("armor") || ts.contains("particle")) {
         score -= 35;
      }

      if (ts.contains("weather") || ts.contains("world_border") || ts.contains("vignette") || ts.contains("sky")) {
         score -= 30;
      }

      if (ts.contains("opaque_particle") || ts.contains("translucent_particle")) {
         score -= 30;
      }

      Object vf = tryGetVertexFormat(pipeline);
      if (vf != null) {
         String vs = vf.toString().toLowerCase();
         boolean hasNormal = vs.contains("normal");
         boolean hasUv2 = vs.contains("uv2") || vs.contains("light");
         if (hasNormal) {
            score -= 100;
         }

         if (hasUv2 && hasNormal) {
            score -= 35;
         } else if (hasUv2) {
            score -= 8;
         }

         if (vs.contains("gui")) {
            score += 25;
         }
      }

      try {
         for (Method m : pipeline.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == String.class) {
               try {
                  String s = (String)m.invoke(pipeline);
                  if (s != null) {
                     String sl = s.toLowerCase();
                     if (sl.contains("gui")) {
                        score += 12;
                     }

                     if (sl.contains("textured")) {
                        score += 6;
                     }
                  }
               } catch (ReflectiveOperationException var10) {
               }
            }
         }
      } catch (Throwable var12) {
      }

      return score;
   }

   private static Object findGuiTexturedPipeline(Class<?> pipelineType) {
      String[] classNames = new String[]{"net.minecraft.client.render.RenderPipelines", "net.minecraft.client.gl.RenderPipelines", "net.minecraft.class_10799"};
      ClassLoader cl = class_332.class.getClassLoader();
      List<Field> fields = new ArrayList<>();

      for (String className : classNames) {
         fields.clear();

         try {
            Class<?> cls = Class.forName(className, false, cl);

            for (Field f : cls.getDeclaredFields()) {
               if (Modifier.isStatic(f.getModifiers()) && pipelineType.isAssignableFrom(f.getType()) && Modifier.isFinal(f.getModifiers())) {
                  fields.add(f);
               }
            }

            if (fields.isEmpty()) {
               for (Field fx : cls.getDeclaredFields()) {
                  if (Modifier.isStatic(fx.getModifiers()) && pipelineType.isAssignableFrom(fx.getType())) {
                     fields.add(fx);
                  }
               }
            }

            if (!fields.isEmpty()) {
               Object best = null;
               int bestScore = Integer.MIN_VALUE;
               String bestField = null;

               for (Field fxx : fields) {
                  try {
                     fxx.setAccessible(true);
                     Object v = fxx.get(null);
                     if (v != null) {
                        int sc = scorePipelineForGuiTextured(v);
                        String fn = fxx.getName();
                        if (fn.contains("GUI_TEXTURED") || fn.equals("field_53147")) {
                           sc += 200;
                        } else if (fn.contains("GUI") && fn.contains("TEXTURED")) {
                           sc += 120;
                        }

                        if (sc > bestScore || sc == bestScore && (bestField == null || fn.compareTo(bestField) < 0)) {
                           bestScore = sc;
                           best = v;
                           bestField = fn;
                        }
                     }
                  } catch (ReflectiveOperationException var17) {
                  }
               }

               if (best != null) {
                  return best;
               }
            }
         } catch (ReflectiveOperationException var18) {
         }
      }

      return null;
   }

   private static Object[] resolveDrawTextureInvocation() {
      List<Method> candidates = listDrawTextureUiMethods();

      for (Method m : candidates) {
         Class<?> first = m.getParameterTypes()[0];
         if (Function.class.isAssignableFrom(first)) {
            Method guiTex = findGuiTexturedRenderLayerMethod();
            if (guiTex != null) {
               m.setAccessible(true);
               Function<class_2960, Object> fn = identifier -> {
                  try {
                     return guiTex.invoke(null, identifier);
                  } catch (ReflectiveOperationException var3x) {
                     return null;
                  }
               };
               return new Object[]{m, fn};
            }
         }
      }

      for (Method mx : candidates) {
         Class<?> first = mx.getParameterTypes()[0];
         if (!Function.class.isAssignableFrom(first)) {
            Object pipeline = findGuiTexturedPipeline(first);
            if (pipeline != null) {
               mx.setAccessible(true);
               return new Object[]{mx, pipeline};
            }
         }
      }

      return new Object[]{null, null};
   }

   private static Method resolveFlushDraw() {
      List<String> names = McVersion.atLeast("1.21.8")
         ? List.of("drawDeferredElements", "method_73199", "draw", "method_51452")
         : List.of("draw", "method_51452", "drawDeferredElements", "method_73199");

      for (String name : names) {
         try {
            Method m = class_332.class.getMethod(name);
            if (m.getReturnType() == void.class && m.getParameterCount() == 0) {
               m.setAccessible(true);
               return m;
            }
         } catch (ReflectiveOperationException ignored) {
         }
      }

      return null;
   }

   public static void flushDraw(class_332 ctx) {
      if (FLUSH_DRAW != null && ctx != null) {
         try {
            FLUSH_DRAW.invoke(ctx);
         } catch (ReflectiveOperationException ignored) {
         }
      }
   }

   /** True on MC 1.21.4–1.21.7 where fill/texture/text must be flushed between draw phases. */
   public static boolean isLegacyGui() {
      return LEGACY_GUI_FLUSH;
   }

   public static void fillRect(class_332 ctx, int x1, int y1, int x2, int y2, int color) {
      if (LEGACY_GUI_FLUSH) flushDraw(ctx);
      ctx.method_25294(x1, y1, x2, y2, color);
      if (LEGACY_GUI_FLUSH) flushDraw(ctx);
   }

   public static void fillGradient(class_332 ctx, int x1, int y1, int x2, int y2, int colorStart, int colorEnd) {
      if (LEGACY_GUI_FLUSH) flushDraw(ctx);
      ctx.method_25296(x1, y1, x2, y2, colorStart, colorEnd);
      if (LEGACY_GUI_FLUSH) flushDraw(ctx);
   }

   private static boolean invokeGridBlit(
      class_332 ctx,
      class_2960 id,
      int x,
      int y,
      float u,
      float v,
      int width,
      int height,
      int texW,
      int texH
   ) {
      if (DRAW_TEXTURE_METHOD == null || DRAW_TEXTURE_FIRST_ARG == null) return false;
      try {
         int pc = DRAW_TEXTURE_METHOD.getParameterCount();
         if (pc == 10) {
            DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, texW, texH);
         } else if (pc == 11) {
            DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, texW, texH, -1);
         } else if (pc == 12) {
            if (LEGACY_GUI_FLUSH) {
               DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, width, height, texW, texH);
            } else {
               DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, texW, texH, texW, texH);
            }
         } else if (pc == 13) {
            if (LEGACY_GUI_FLUSH) {
               DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, width, height, texW, texH, -1);
            } else {
               DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, texW, texH, texW, texH, -1);
            }
         } else {
            return false;
         }
         return true;
      } catch (ReflectiveOperationException ignored) {
         return false;
      }
   }

   private static Object[] resolveNativeTextureCtor() {
      for (Constructor<?> c : class_1043.class.getDeclaredConstructors()) {
         Class<?>[] p = c.getParameterTypes();
         if (p.length == 2 && Supplier.class.isAssignableFrom(p[0]) && p[1] == class_1011.class) {
            c.setAccessible(true);
            return new Object[]{c, Boolean.TRUE};
         }
      }

      for (Constructor<?> cx : class_1043.class.getDeclaredConstructors()) {
         Class<?>[] p = cx.getParameterTypes();
         if (p.length == 2 && p[1] == class_1011.class) {
            cx.setAccessible(true);
            return new Object[]{cx, Boolean.TRUE};
         }
      }

      for (Constructor<?> lone : class_1043.class.getDeclaredConstructors()) {
         Class<?>[] p = lone.getParameterTypes();
         if (p.length == 1 && p[0] == class_1011.class) {
            lone.setAccessible(true);
            return new Object[]{lone, Boolean.FALSE};
         }
      }

      return new Object[]{null, Boolean.FALSE};
   }

   public static void drawText(class_332 ctx, class_327 tr, String text, int x, int y, int color, boolean shadow) {
      int c = LEGACY_GUI_FLUSH ? opaqueTextColor(color) : color;
      if (LEGACY_GUI_FLUSH) {
         flushDraw(ctx);
      }

      invokeDrawText(DRAW_TEXT_STRING, ctx, tr, text, x, y, c, shadow);
      if (LEGACY_GUI_FLUSH) {
         flushDraw(ctx);
      }
   }

   public static void drawText(class_332 ctx, class_327 tr, class_2561 text, int x, int y, int color, boolean shadow) {
      int c = LEGACY_GUI_FLUSH ? opaqueTextColor(color) : color;
      if (LEGACY_GUI_FLUSH) {
         flushDraw(ctx);
      }

      invokeDrawText(DRAW_TEXT_TEXT, ctx, tr, text, x, y, c, shadow);
      if (LEGACY_GUI_FLUSH) {
         flushDraw(ctx);
      }
   }

   public static void drawText(class_332 ctx, class_327 tr, class_5481 text, int x, int y, int color, boolean shadow) {
      int c = LEGACY_GUI_FLUSH ? opaqueTextColor(color) : color;
      if (LEGACY_GUI_FLUSH) {
         flushDraw(ctx);
      }

      invokeDrawText(DRAW_TEXT_ORDERED, ctx, tr, text, x, y, c, shadow);
      if (LEGACY_GUI_FLUSH) {
         flushDraw(ctx);
      }
   }

   public static void drawTextWithShadow(class_332 ctx, class_327 tr, String text, int x, int y, int color) {
      drawText(ctx, tr, text, x, y, color, true);
   }

   public static void drawTextWithShadow(class_332 ctx, class_327 tr, class_2561 text, int x, int y, int color) {
      drawText(ctx, tr, text, x, y, color, true);
   }

   public static void drawTextWithShadow(class_332 ctx, class_327 tr, class_5481 text, int x, int y, int color) {
      drawText(ctx, tr, text, x, y, color, true);
   }

   public static class_1043 createNativeTexture(String textureName, class_1011 image) {
      if (NATIVE_TEX_CTOR != null) {
         try {
            class_1043 tex;
            if (NATIVE_TEX_NEEDS_NAME) {
               Supplier<String> nameSupplier = () -> textureName;
               tex = (class_1043)NATIVE_TEX_CTOR.newInstance(nameSupplier, image);
            } else {
               tex = (class_1043)NATIVE_TEX_CTOR.newInstance(image);
            }
            uploadNativeTexture(tex);
            return tex;
         } catch (ReflectiveOperationException var3) {
         }
      }

      return null;
   }

   /** Upload NativeImage-backed texture to GPU (required before register/bind on 1.21.4). */
   public static void uploadNativeTexture(class_1043 tex) {
      if (tex != null) {
         try {
            tex.method_4524();
         } catch (Exception ignored) {
         }
      }
   }

   /** Upload and register a dynamic GUI texture with the client texture manager. */
   public static void registerDynamicTexture(class_2960 id, class_1043 tex) {
      class_1011 img = null;
      if (tex != null) {
         try {
            img = tex.method_4525();
         } catch (Exception ignored) {
         }
      }
      registerDynamicTexture(id, tex, img);
   }

   /** Registers texture + caches a full-UV sprite for {@link #drawTexture}. */
   public static void registerDynamicTexture(class_2960 id, class_1043 tex, class_1011 img) {
      if (id == null || tex == null) return;
      class_310 mc = class_310.method_1551();
      if (mc == null || mc.method_1531() == null) return;
      uploadNativeTexture(tex);
      mc.method_1531().method_4616(id, tex);
      class_1011 source = img;
      if (source == null) {
         try {
            source = tex.method_4525();
         } catch (Exception ignored) {
         }
      }
      if (source != null) {
         class_1058 sprite = createFullSprite(id, source);
         if (sprite != null) {
            bindSpriteGpu(sprite, tex);
            GUI_SPRITES.put(id, sprite);
         }
      }
   }

   public static void drawTexture(class_332 ctx, class_2960 id, int x, int y, float u, float v, int width, int height, int texW, int texH) {
      if (ctx == null || id == null || width <= 0 || height <= 0) return;
      int tw = Math.max(1, texW);
      int th = Math.max(1, texH);

      if (LEGACY_GUI_FLUSH) {
         flushDraw(ctx);
      }

      // 1.21.8+: blit by registered texture id (reliable for pack/icon thumbnails).
      if (!LEGACY_GUI_FLUSH && invokeGridBlit(ctx, id, x, y, u, v, width, height, tw, th)) {
         flushDraw(ctx);
         return;
      }

      class_1058 sprite = GUI_SPRITES.get(id);
      if (sprite != null && u == 0f && v == 0f) {
         try {
            invokeSpriteBlit(ctx, sprite, x, y, width, height);
            flushDraw(ctx);
            return;
         } catch (ReflectiveOperationException ignored) {
         }
      }

      // 1.21.4–1.21.7: RenderLayer.getGuiTextured grid blit (same path PackHub uses on 1.21.4).
      if (LEGACY_GUI_FLUSH && DRAW_TEXTURE_FIRST_ARG instanceof Function) {
         if (invokeGridBlit(ctx, id, x, y, u, v, width, height, tw, th)) {
            flushDraw(ctx);
            return;
         }
      }

      if (LEGACY_GUI_FLUSH && LEGACY_REGION_BLIT != null) {
         try {
            invokeLegacyRegionBlit(ctx, id, x, y, u, v, width, height, tw, th);
         } catch (ReflectiveOperationException ignored) {
         }
         flushDraw(ctx);
         return;
      }

      if (invokeGridBlit(ctx, id, x, y, u, v, width, height, tw, th)) {
         flushDraw(ctx);
      }
   }

   private static Object[] resolveMatrices() {
      Method getMatrices = null;

      for (Method m : class_332.class.getMethods()) {
         if (m.getParameterCount() == 0 && m.getName().equals("getMatrices")) {
            getMatrices = m;
            break;
         }
      }

      if (getMatrices == null) {
         for (Method mx : class_332.class.getMethods()) {
            if (mx.getParameterCount() == 0) {
               Class<?> rt = mx.getReturnType();
               String name = rt.getName();
               if (name.contains("MatrixStack")
                  || name.contains("Matrix3x2fStack")
                  || name.equals("net.minecraft.class_4587")
                  || name.equals("org.joml.Matrix3x2fStack")) {
                  getMatrices = mx;
                  break;
               }
            }
         }
      }

      if (getMatrices == null) {
         return new Object[]{null, Boolean.FALSE, null, null, null, null};
      } else {
         getMatrices.setAccessible(true);
         Class<?> matType = getMatrices.getReturnType();
         boolean isJoml = matType.getName().startsWith("org.joml");
         Method push = null;
         Method pop = null;
         Method translate = null;
         Method scale = null;
         if (isJoml) {
            try {
               push = matType.getMethod("pushMatrix");
            } catch (Exception var16) {
            }

            try {
               pop = matType.getMethod("popMatrix");
            } catch (Exception var15) {
            }

            try {
               translate = matType.getMethod("translate", float.class, float.class);
            } catch (Exception var14) {
            }

            try {
               scale = matType.getMethod("scale", float.class, float.class);
            } catch (Exception var13) {
            }
         } else {
            for (Method mxx : matType.getMethods()) {
               Class<?>[] p = mxx.getParameterTypes();
               if (p.length == 0) {
                  String n = mxx.getName();
                  if (n.equals("push") || n.equals("push")) {
                     push = mxx;
                  } else if (n.equals("pop") || n.equals("pop")) {
                     pop = mxx;
                  }
               }

               if (p.length == 3 && p[0] == double.class && p[1] == double.class && p[2] == double.class) {
                  String n = mxx.getName();
                  if (n.equals("translate") || n.equals("translate")) {
                     translate = mxx;
                  }
               }

               if (p.length == 3 && p[0] == float.class && p[1] == float.class && p[2] == float.class) {
                  String n = mxx.getName();
                  if (n.equals("scale") || n.equals("scale")) {
                     scale = mxx;
                  }
               }
            }
         }

         return new Object[]{getMatrices, isJoml, push, pop, translate, scale};
      }
   }

   private static Object getMatrixStack(class_332 ctx) {
      if (GET_MATRICES == null) {
         return null;
      } else {
         try {
            return GET_MATRICES.invoke(ctx);
         } catch (ReflectiveOperationException var2) {
            return null;
         }
      }
   }

   public static void pushMatrices(class_332 ctx) {
      Object mat = getMatrixStack(ctx);
      if (mat != null && MAT_PUSH != null) {
         try {
            MAT_PUSH.invoke(mat);
         } catch (ReflectiveOperationException var3) {
         }
      }
   }

   public static void popMatrices(class_332 ctx) {
      Object mat = getMatrixStack(ctx);
      if (mat != null && MAT_POP != null) {
         try {
            MAT_POP.invoke(mat);
         } catch (ReflectiveOperationException var3) {
         }
      }
   }

   public static void translateMatrices(class_332 ctx, float x, float y, float z) {
      Object mat = getMatrixStack(ctx);
      if (mat != null && MAT_TRANSLATE != null) {
         try {
            if (MATRICES_IS_JOML) {
               MAT_TRANSLATE.invoke(mat, x, y);
            } else {
               MAT_TRANSLATE.invoke(mat, (double)x, (double)y, (double)z);
            }
         } catch (ReflectiveOperationException var6) {
         }
      }
   }

   public static void scaleMatrices(class_332 ctx, float sx, float sy, float sz) {
      Object mat = getMatrixStack(ctx);
      if (mat != null && MAT_SCALE != null) {
         try {
            if (MATRICES_IS_JOML) {
               MAT_SCALE.invoke(mat, sx, sy);
            } else {
               MAT_SCALE.invoke(mat, sx, sy, sz);
            }
         } catch (ReflectiveOperationException var6) {
         }
      }
   }

   static {
      Object[] drawTex = resolveDrawTextureInvocation();
      DRAW_TEXTURE_METHOD = (Method)drawTex[0];
      DRAW_TEXTURE_FIRST_ARG = drawTex[1];
      Object[] nativeTex = resolveNativeTextureCtor();
      NATIVE_TEX_CTOR = (Constructor<?>)nativeTex[0];
      NATIVE_TEX_NEEDS_NAME = (Boolean)nativeTex[1];
      Object[] mat = resolveMatrices();
      GET_MATRICES = (Method)mat[0];
      MATRICES_IS_JOML = (Boolean)mat[1];
      MAT_PUSH = (Method)mat[2];
      MAT_POP = (Method)mat[3];
      MAT_TRANSLATE = (Method)mat[4];
      MAT_SCALE = (Method)mat[5];
      FLUSH_DRAW = resolveFlushDraw();
   }
}

