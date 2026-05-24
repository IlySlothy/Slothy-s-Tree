package com.slothyhub.compat;

import com.slothyhub.ui.CustomButtonBase;
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

    private InputCompat() {}

    public static boolean needsPolling() { return NEEDS_POLLING; }

    public static boolean isShiftDown() {
        long window = class_310.method_1551().method_22683().method_4490();
        return GLFW.glfwGetKey(window, 340) == 1 || GLFW.glfwGetKey(window, 344) == 1;
    }

    public static boolean delegateToChildren(class_4069 parent, double mx, double my, int button) {
        for (class_364 child : parent.method_25396()) {
            if (clickChild(child, mx, my, button)) {
                parent.method_25395(child);
                if (button == 0) parent.method_25398(true);
                return true;
            }
        }
        return false;
    }

    public static boolean clickChild(class_364 child, double mx, double my, int button) {
        if (NEEDS_POLLING) {
            if (child instanceof CustomButtonBase btn) return btn.tryPress(mx, my);
            return callNewMouseClicked(child, mx, my, button);
        }
        return callOldMouseClicked(child, mx, my, button);
    }

    private static boolean callOldMouseClicked(class_364 child, double mx, double my, int button) {
        if (OLD_CHILD_MC == null) return false;
        try { return (Boolean) OLD_CHILD_MC.invoke(child, mx, my, button); } catch (Exception e) { return false; }
    }

    private static boolean callNewMouseClicked(class_364 child, double mx, double my, int button) {
        if (NEW_CHILD_MC == null || CLICK_CTOR == null || MOUSE_INPUT_CTOR == null) return false;
        try {
            Object mouseInput = MOUSE_INPUT_CTOR.newInstance(button, 0);
            Object click = CLICK_CTOR.newInstance(mx, my, mouseInput);
            return (Boolean) NEW_CHILD_MC.invoke(child, click, false);
        } catch (Exception e) { return false; }
    }

    static {
        boolean newApi = false;
        Method oldMc = null, newMc = null;
        Constructor<?> clickCtor = null, miCtor = null;
        try {
            for (Method m : class_364.class.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 3 && p[0] == double.class && p[1] == double.class && p[2] == int.class && m.getReturnType() == boolean.class) {
                    oldMc = m; m.setAccessible(true); break;
                }
            }
            if (oldMc == null) {
                newApi = true;
                outer:
                for (Method mx : class_364.class.getMethods()) {
                    Class<?>[] p = mx.getParameterTypes();
                    if (p.length == 2 && mx.getReturnType() == boolean.class && !p[0].isPrimitive() && p[1] == boolean.class) {
                        newMc = mx; mx.setAccessible(true);
                        Class<?> clickClass = p[0];
                        for (Constructor<?> ctor : clickClass.getDeclaredConstructors()) {
                            Class<?>[] cp = ctor.getParameterTypes();
                            if (cp.length == 3 && cp[0] == double.class && cp[1] == double.class) {
                                ctor.setAccessible(true); clickCtor = ctor;
                                Class<?> miClass = cp[2];
                                for (Constructor<?> miC : miClass.getDeclaredConstructors()) {
                                    Class<?>[] mp = miC.getParameterTypes();
                                    if (mp.length == 2 && mp[0] == int.class && mp[1] == int.class) {
                                        miC.setAccessible(true); miCtor = miC; break outer;
                                    }
                                }
                                break outer;
                            }
                        }
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        NEEDS_POLLING = newApi;
        OLD_CHILD_MC = oldMc;
        NEW_CHILD_MC = newMc;
        CLICK_CTOR = clickCtor;
        MOUSE_INPUT_CTOR = miCtor;
    }

    /** Functional interface for the scroll delta callback. */
    public interface ScrollHandler { boolean handleScroll(double delta); }

    /**
     * Adapter for mouseScrolled — handles both legacy (dx, dy, vScroll, button) and
     * modern (dx, dy, vScroll) signatures by forwarding the vertical delta.
     */
    public static boolean handleMouseScrolled(ScrollHandler handler, double mx, double my, double vScroll) {
        return handler.handleScroll(vScroll);
    }

    public interface ClickHandler { boolean handleMouseClicked(double x, double y, int btn); }
    public interface KeyHandler { boolean handleKeyPressed(int key, int scan, int mods); }

    public static final class Poller {
        private boolean prevLmb, prevRmb, prevEsc;

        public void poll(int mx, int my, ClickHandler clickHandler, KeyHandler keyHandler) {
            if (!NEEDS_POLLING) return;
            long window = class_310.method_1551().method_22683().method_4490();
            boolean lmb = GLFW.glfwGetMouseButton(window, 0) == 1;
            boolean rmb = GLFW.glfwGetMouseButton(window, 1) == 1;
            if (lmb && !prevLmb) clickHandler.handleMouseClicked(mx, my, 0);
            if (rmb && !prevRmb) clickHandler.handleMouseClicked(mx, my, 1);
            prevLmb = lmb; prevRmb = rmb;
            if (keyHandler != null) {
                boolean esc = GLFW.glfwGetKey(window, 256) == 1;
                if (esc && !prevEsc) keyHandler.handleKeyPressed(256, 0, 0);
                prevEsc = esc;
            }
        }
    }
}
