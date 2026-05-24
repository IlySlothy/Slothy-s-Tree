package com.slothyhub.ui;

import com.slothyhub.compat.DrawHelper;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_6382;

public abstract class CustomButtonBase extends class_339 {

    private final Runnable onPress;
    private final Style style;
    private float hoverT = 0f;
    private float pressT = 0f;

    public CustomButtonBase(int x, int y, int w, int h, class_2561 label, Style style, Runnable onPress) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        this.style = style;
    }

    public void method_25348(double mouseX, double mouseY) { doPress(); }

    public boolean tryPress(double mx, double my) {
        if (!field_22763) return false;
        if (mx >= method_46426() && mx <= method_46426() + method_25368()
            && my >= method_46427() && my <= method_46427() + method_25364()) {
            doPress(); return true;
        }
        return false;
    }

    private void doPress() {
        pressT = 1f;
        Ui.playClick();
        if (onPress != null) onPress.run();
    }

    protected void drawButton(class_332 ctx, int mx, int my, float delta) {
        hoverT += ((method_49606() ? 1f : 0f) - hoverT) * Math.min(1f, delta * 0.35f);
        pressT = Math.max(0f, pressT - delta * 0.12f);
        int x = method_46426(), y = method_46427(), w = method_25368(), h = method_25364();
        float scale = 1f - 0.04f * pressT;
        boolean animPress = pressT > 0.01f;
        if (animPress) {
            DrawHelper.pushMatrices(ctx);
            DrawHelper.translateMatrices(ctx, x + w / 2f, y + h / 2f, 0f);
            DrawHelper.scaleMatrices(ctx, scale, scale, 1f);
            DrawHelper.translateMatrices(ctx, -(x + w / 2f), -(y + h / 2f), 0f);
        }
        int bg, fg, border;
        switch (style) {
            case PRIMARY -> {
                // Mossy green on hover
                bg = hoverT > 0.05f ? Ui.lerpColor(Ui.COL_BG, Ui.COL_TEXT, hoverT) : Ui.COL_BG;
                fg = hoverT > 0.05f ? Ui.lerpColor(Ui.COL_TEXT, -16777216, hoverT) : Ui.COL_TEXT;
                border = hoverT > 0.05f ? Ui.COL_TEXT : Ui.lerpColor(Ui.COL_BORDER, Ui.COL_TEXT, hoverT);
            }
            case DANGER -> {
                bg = hoverT > 0.05f ? Ui.lerpColor(Ui.COL_BG, Ui.COL_DANGER, hoverT) : Ui.COL_BG;
                fg = hoverT > 0.05f ? Ui.lerpColor(Ui.COL_DANGER, Ui.COL_TEXT, hoverT) : Ui.COL_DANGER;
                border = Ui.COL_DANGER;
            }
            case MOSS -> {
                // Solid mossy green fill
                bg = Ui.lerpColor(Ui.COL_ACCENT, Ui.COL_ACCENT_H, hoverT);
                fg = Ui.COL_TEXT;
                border = Ui.COL_ACCENT_H;
            }
            case BARK -> {
                // Dark brown — like tree bark
                bg = hoverT > 0.05f ? Ui.lerpColor(Ui.COL_BORDER, Ui.COL_PANEL, hoverT) : Ui.COL_BORDER;
                fg = Ui.lerpColor(Ui.COL_MUTED, Ui.COL_TEXT, hoverT);
                border = Ui.lerpColor(Ui.COL_BORDER, Ui.COL_ACCENT, hoverT);
            }
            default -> { // SECONDARY
                bg = Ui.COL_BG;
                fg = Ui.lerpColor(Ui.COL_MUTED, Ui.COL_TEXT, hoverT);
                border = Ui.lerpColor(Ui.COL_BORDER, Ui.COL_TEXT, hoverT);
            }
        }
        ctx.method_25294(x, y, x + w, y + h, bg);
        ctx.method_25294(x, y, x + w, y + 1, border);
        ctx.method_25294(x, y + h - 1, x + w, y + h, border);
        ctx.method_25294(x, y, x + 1, y + h, border);
        ctx.method_25294(x + w - 1, y, x + w, y + h, border);
        class_327 tr = class_310.method_1551().field_1772;
        DrawHelper.drawTextWithShadow(ctx, tr, method_25369(), x + (w - tr.method_27525(method_25369())) / 2, y + (h - 8) / 2, fg);
        if (animPress) DrawHelper.popMatrices(ctx);
    }

    protected void method_47399(class_6382 b) { method_37021(b); }

    public enum Style { PRIMARY, SECONDARY, DANGER, MOSS, BARK }
}
