package com.packhub.compat;

import com.packhub.ui.CustomButtonBase;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.class_310;
import net.minecraft.class_364;
import net.minecraft.class_4069;
import org.lwjgl.glfw.GLFW;

public final class InputCompat {
   private static final boolean NEEDS_POLLING;
   private static final Method OLD_CHILD_MC;
   private static final Method NEW_CHILD_MC;
   private static final Constructor<?> CLICK_CTOR;
   private static final Constructor<?> MOUSE_INPUT_CTOR;

   public static boolean needsPolling() {
      return NEEDS_POLLING;
   }

   private InputCompat() {
   }

   public static boolean isShiftDown() {
      long window = class_310.method_1551().method_22683().method_4490();
      return GLFW.glfwGetKey(window, 340) == 1 || GLFW.glfwGetKey(window, 344) == 1;
   }

   public static boolean delegateToChildren(class_4069 parent, double mx, double my, int button) {
      for (class_364 child : parent.method_25396()) {
         boolean consumed = false;
         if (NEEDS_POLLING) {
            if (child instanceof CustomButtonBase btn) {
               consumed = btn.tryPress(mx, my);
            } else {
               consumed = callNewMouseClicked(child, mx, my, button);
            }
         } else {
            consumed = callOldMouseClicked(child, mx, my, button);
         }

         if (consumed) {
            parent.method_25395(child);
            if (button == 0) {
               parent.method_25398(true);
            }

            return true;
         }
      }

      return false;
   }

   private static boolean callOldMouseClicked(class_364 child, double mx, double my, int button) {
      if (OLD_CHILD_MC == null) {
         return false;
      } else {
         try {
            return (Boolean)OLD_CHILD_MC.invoke(child, mx, my, button);
         } catch (Exception var7) {
            return false;
         }
      }
   }

   private static boolean callNewMouseClicked(class_364 child, double mx, double my, int button) {
      if (NEW_CHILD_MC != null && CLICK_CTOR != null && MOUSE_INPUT_CTOR != null) {
         try {
            Object mouseInput = MOUSE_INPUT_CTOR.newInstance(button, 0);
            Object click = CLICK_CTOR.newInstance(mx, my, mouseInput);
            return (Boolean)NEW_CHILD_MC.invoke(child, click, false);
         } catch (Exception var81) {
            return false;
         }
      } else {
         return false;
      }
   }

   static {
      boolean newApi = false;
      Method oldMc = null;
      Method newMc = null;
      Constructor<?> clickCtor = null;
      Constructor<?> miCtor = null;

      try {
         for (Method m : class_364.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 3 && p[0] == double.class && p[1] == double.class && p[2] == int.class && m.getReturnType() == boolean.class) {
               oldMc = m;
               m.setAccessible(true);
               break;
            }
         }

         if (oldMc == null) {
            newApi = true;

            label68:
            for (Method mx : class_364.class.getMethods()) {
               Class<?>[] p = mx.getParameterTypes();
               if (p.length == 2 && mx.getReturnType() == boolean.class && !p[0].isPrimitive() && p[1] == boolean.class) {
                  newMc = mx;
                  mx.setAccessible(true);
                  Class<?> clickClass = p[0];

                  for (Constructor<?> ctor : clickClass.getDeclaredConstructors()) {
                     Class<?>[] cp = ctor.getParameterTypes();
                     if (cp.length == 3 && cp[0] == double.class && cp[1] == double.class) {
                        ctor.setAccessible(true);
                        clickCtor = ctor;
                        Class<?> mouseInputClass = cp[2];

                        for (Constructor<?> miCtorCandidate : mouseInputClass.getDeclaredConstructors()) {
                           Class<?>[] mp = miCtorCandidate.getParameterTypes();
                           if (mp.length == 2 && mp[0] == int.class && mp[1] == int.class) {
                              miCtorCandidate.setAccessible(true);
                              miCtor = miCtorCandidate;
                              break label68;
                           }
                        }
                        break label68;
                     }
                  }
                  break;
               }
            }
         }
      } catch (Exception var22) {
      }

      NEEDS_POLLING = newApi;
      OLD_CHILD_MC = oldMc;
      NEW_CHILD_MC = newMc;
      CLICK_CTOR = clickCtor;
      MOUSE_INPUT_CTOR = miCtor;
   }

   public interface ClickHandler {
      boolean handleMouseClicked(double var1, double var3, int var5);
   }

   public interface KeyHandler {
      boolean handleKeyPressed(int var1, int var2, int var3);
   }

   public static final class Poller {
      private boolean prevLmb = false;
      private boolean prevRmb = false;
      private boolean prevEsc = false;

      public Poller() {
      }

      public void poll(int mx, int my, InputCompat.ClickHandler clickHandler, InputCompat.KeyHandler keyHandler) {
         if (InputCompat.NEEDS_POLLING) {
            long window = class_310.method_1551().method_22683().method_4490();
            boolean lmb = GLFW.glfwGetMouseButton(window, 0) == 1;
            boolean rmb = GLFW.glfwGetMouseButton(window, 1) == 1;
            if (lmb && !this.prevLmb) {
               clickHandler.handleMouseClicked((double)mx, (double)my, 0);
            }

            if (rmb && !this.prevRmb) {
               clickHandler.handleMouseClicked((double)mx, (double)my, 1);
            }

            this.prevLmb = lmb;
            this.prevRmb = rmb;
            if (keyHandler != null) {
               boolean esc = GLFW.glfwGetKey(window, 256) == 1;
               if (esc && !this.prevEsc) {
                  keyHandler.handleKeyPressed(256, 0, 0);
               }

               this.prevEsc = esc;
            }
         }
      }
   }
}
