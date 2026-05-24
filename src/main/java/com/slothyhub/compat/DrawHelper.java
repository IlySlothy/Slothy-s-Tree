package com.slothyhub.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_1921;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_5481;

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

   private DrawHelper() {
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
      try {
         Method exact = class_1921.class.getMethod("getGuiTextured", class_2960.class);
         if (Modifier.isStatic(exact.getModifiers()) && class_1921.class.isAssignableFrom(exact.getReturnType())) {
            exact.setAccessible(true);
            return exact;
         }
      } catch (ReflectiveOperationException var5) {
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
      invokeDrawText(DRAW_TEXT_STRING, ctx, tr, text, x, y, color, shadow);
   }

   public static void drawText(class_332 ctx, class_327 tr, class_2561 text, int x, int y, int color, boolean shadow) {
      invokeDrawText(DRAW_TEXT_TEXT, ctx, tr, text, x, y, color, shadow);
   }

   public static void drawText(class_332 ctx, class_327 tr, class_5481 text, int x, int y, int color, boolean shadow) {
      invokeDrawText(DRAW_TEXT_ORDERED, ctx, tr, text, x, y, color, shadow);
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
            if (NATIVE_TEX_NEEDS_NAME) {
               Supplier<String> nameSupplier = () -> textureName;
               return (class_1043)NATIVE_TEX_CTOR.newInstance(nameSupplier, image);
            }

            return (class_1043)NATIVE_TEX_CTOR.newInstance(image);
         } catch (ReflectiveOperationException var3) {
         }
      }

      return null;
   }

   public static void drawTexture(class_332 ctx, class_2960 id, int x, int y, float u, float v, int width, int height, int texW, int texH) {
      if (DRAW_TEXTURE_METHOD != null && DRAW_TEXTURE_FIRST_ARG != null) {
         try {
            int pc = DRAW_TEXTURE_METHOD.getParameterCount();
            if (pc == 10) {
               DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, texW, texH);
            } else if (pc == 11) {
               DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, texW, texH, -1);
            } else if (pc == 12) {
               DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, width, height, texW, texH);
            } else if (pc == 13) {
               DRAW_TEXTURE_METHOD.invoke(ctx, DRAW_TEXTURE_FIRST_ARG, id, x, y, u, v, width, height, width, height, texW, texH, -1);
            }
         } catch (ReflectiveOperationException var11) {
         }
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
   }
}

