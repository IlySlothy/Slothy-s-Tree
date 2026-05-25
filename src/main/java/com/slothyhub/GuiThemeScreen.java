package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.InputCompat;
import com.slothyhub.ui.GuiTheme;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

/** Configure SlothyHub client GUI colors — presets or per-channel custom picker. */
public class GuiThemeScreen extends class_437 {

    private enum Tab { PRESETS, CUSTOM }

    private enum ColorSlot {
        BG("Background"), PANEL("Panel"), SURFACE("Surface"),
        ACCENT("Accent"), TEXT("Text"), MUTED("Muted"), BORDER("Border");
        final String label;
        ColorSlot(String label) { this.label = label; }
    }

    private static final ColorSlot[] SLOTS = ColorSlot.values();
    private static final int CONTENT_TOP = 68;
    private static final int FOOTER_H = 36;

    private final class_437 parent;
    private Tab activeTab = Tab.PRESETS;
    private final float[] presetHover = new float[GuiTheme.PRESETS.length];
    private final float[] presetCheckAnim = new float[GuiTheme.PRESETS.length];
    private final float[] tabHover = new float[2];
    private float customCheckAnim = 0f;
    private final float[] customCheckArr = new float[1];
    private String selectedPreset;

    private final int[] customColors = GuiTheme.currentColors();
    private ColorSlot editingSlot = ColorSlot.ACCENT;
    private float editHue, editSat, editVal;
    private boolean draggingHue, draggingSv;
    private int hueX, hueY, hueW, hueH;
    private int svX, svY, svW, svH;
    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    public GuiThemeScreen(class_437 parent) {
        super(class_2561.method_43470("GUI Theme"));
        this.parent = parent;
        this.selectedPreset = SlothyConfig.getThemePreset();
        if (selectedPreset == null || selectedPreset.isBlank()) selectedPreset = "forest";
        if ("custom".equals(selectedPreset)) activeTab = Tab.CUSTOM;
        syncEditorFromSlot();
    }

    private void syncEditorFromSlot() {
        float[] hsv = GuiTheme.rgbToHsv(customColors[editingSlot.ordinal()]);
        editHue = hsv[0];
        editSat = hsv[1];
        editVal = hsv[2];
    }

    private void applyCustomTheme() {
        GuiTheme.applyCustom(
            customColors[0], customColors[1], customColors[2],
            customColors[3], customColors[4], customColors[5], customColors[6]);
        selectedPreset = "custom";
    }

    private void applyCustomThemeLive() {
        GuiTheme.applyCustomLive(
            customColors[0], customColors[1], customColors[2],
            customColors[3], customColors[4], customColors[5], customColors[6]);
        selectedPreset = "custom";
    }

    private void commitEditingColor() {
        customColors[editingSlot.ordinal()] = GuiTheme.hsvToRgb(editHue, editSat, editVal);
        applyCustomThemeLive();
    }

    private void finishEditingColor() {
        customColors[editingSlot.ordinal()] = GuiTheme.hsvToRgb(editHue, editSat, editVal);
        applyCustomTheme();
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);
        int bg = Ui.COL_BG, panel = Ui.COL_PANEL, text = Ui.COL_TEXT, muted = Ui.COL_MUTED;
        int border = Ui.COL_BORDER, accent = Ui.COL_ACCENT, surface = Ui.COL_SURFACE;

        ctx.method_25294(0, 0, field_22789, field_22790, bg);
        Ui.drawSubscreenHeader(ctx, field_22793, field_22789, "GUI THEME  //  SLOTHYHUB", delta);
        drawTabs(ctx, mx, my, delta, text, muted, border, accent, panel);

        int cardW = Math.min(420, field_22789 - 48);
        int cardX = (field_22789 - cardW) / 2;
        if (activeTab == Tab.PRESETS) {
            drawPresetsTab(ctx, mx, my, delta, cardX, cardW, bg, panel, text, muted, border, accent, surface);
        } else {
            drawCustomTab(ctx, mx, my, delta, cardX, cardW, bg, panel, text, muted, border, accent, surface);
            drawPicker(ctx, mx, my, text, muted, border, accent);
        }

        drawFooter(ctx, mx, my, bg, panel, text, muted, border, accent);
        if (activeTab == Tab.CUSTOM) handleDrag(mx, my);
    }

    private void handleDrag(int mx, int my) {
        if (!draggingHue && !draggingSv) return;
        long window = net.minecraft.class_310.method_1551().method_22683().method_4490();
        if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
            != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            if (draggingHue || draggingSv) finishEditingColor();
            draggingHue = false;
            draggingSv = false;
            return;
        }
        layoutPicker();
        if (draggingHue) updateHueFromMouse(mx);
        if (draggingSv) updateSvFromMouse(mx, my);
    }

    private void drawTabs(class_332 ctx, int mx, int my, float delta,
                          int text, int muted, int border, int accent, int panel) {
        String[] labels = { "PRESETS", "CUSTOM" };
        int x = field_22789 / 2 - 80, y = 46, w = 78, h = 16;
        for (int i = 0; i < labels.length; i++) {
            Tab tab = i == 0 ? Tab.PRESETS : Tab.CUSTOM;
            boolean active = activeTab == tab;
            boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
            tabHover[i] += ((hov || active ? 1f : 0f) - tabHover[i]) * Math.min(1f, delta * 0.4f);
            int fill = active ? Ui.withAlpha(accent & 0xFFFFFF, 35)
                : Ui.lerpColor(panel, Ui.withAlpha(accent & 0xFFFFFF, 20), tabHover[i]);
            ctx.method_25294(x, y, x + w, y + h, fill);
            ctx.method_25294(x, y, x + w, y + 1, active ? accent : border);
            if (active) ctx.method_25294(x, y + h - 1, x + w, y + h, accent);
            DrawHelper.drawText(ctx, field_22793, labels[i], x + (w - field_22793.method_1727(labels[i])) / 2,
                y + 4, active ? accent : Ui.lerpColor(muted, text, tabHover[i]), false);
            x += w + 4;
        }
    }

    private void drawPresetsTab(class_332 ctx, int mx, int my, float delta,
                                int cardX, int cardW, int bg, int panel, int text, int muted, int border, int accent, int surface) {
        DrawHelper.drawText(ctx, field_22793, "CHOOSE A COLOR PRESET",
            field_22789 / 2 - field_22793.method_1727("CHOOSE A COLOR PRESET") / 2, CONTENT_TOP - 4, muted, false);

        int y = CONTENT_TOP + 12;
        for (int i = 0; i < GuiTheme.PRESETS.length; i++) {
            GuiTheme.Preset p = GuiTheme.PRESETS[i];
            boolean sel = p.id().equals(selectedPreset);
            y = drawPresetRow(ctx, mx, my, delta, cardX, cardW, y, p.label(), sel,
                new int[] { p.bg(), p.panel(), p.accent(), p.text() }, presetHover, i, presetCheckAnim, i);
        }

        boolean customSel = "custom".equals(selectedPreset);
        customCheckArr[0] = customCheckAnim;
        y = drawPresetRow(ctx, mx, my, delta, cardX, cardW, y, "Custom (your colors)", customSel,
            new int[] { customColors[0], customColors[1], customColors[3], customColors[4] },
            null, -1, customCheckArr, 0);
        customCheckAnim = customCheckArr[0];

        y += 8;
        drawPreview(ctx, cardX, cardW, y, panel, accent, surface, text, muted);
    }

    private int drawPresetRow(class_332 ctx, int mx, int my, float delta,
                              int cardX, int cardW, int y, String label, boolean sel, int[] swatches,
                              float[] hoverArr, int hoverIdx, float[] checkArr, int checkIdx) {
        boolean hov = mx >= cardX && mx <= cardX + cardW && my >= y && my <= y + 44;
        float ht = 0f;
        if (hoverArr != null && hoverIdx >= 0) {
            hoverArr[hoverIdx] += ((hov ? 1f : 0f) - hoverArr[hoverIdx]) * Math.min(1f, delta * 0.35f);
            ht = hoverArr[hoverIdx];
        } else {
            ht = hov ? 1f : 0f;
        }

        float checkT = 0f;
        if (checkArr != null && checkIdx >= 0) {
            checkArr[checkIdx] = Ui.tickCheckAnim(checkArr[checkIdx], sel, delta);
            checkT = checkArr[checkIdx];
        }

        int rowBg = sel ? Ui.withAlpha(Ui.COL_ACCENT & 0xFFFFFF, 35) : Ui.lerpColor(Ui.COL_BG, Ui.COL_PANEL, ht);
        ctx.method_25294(cardX, y, cardX + cardW, y + 44, rowBg);
        ctx.method_25294(cardX, y, cardX + cardW, y + 1, sel ? Ui.COL_ACCENT : Ui.COL_BORDER);
        if (sel) ctx.method_25294(cardX, y, cardX + 2, y + 44, Ui.COL_ACCENT);

        int sx = cardX + 12;
        for (int i = 0; i < Math.min(4, swatches.length); i++) {
            swatch(ctx, sx, y + 14, swatches[i]);
            sx += 18;
        }

        DrawHelper.drawText(ctx, field_22793, label, cardX + 90, y + 12,
            sel ? Ui.COL_TEXT : Ui.lerpColor(Ui.COL_MUTED, Ui.COL_TEXT, ht), false);
        DrawHelper.drawText(ctx, field_22793, sel ? "ACTIVE" : "Click to apply", cardX + 90, y + 26, Ui.COL_MUTED, false);
        if (checkArr != null && checkIdx >= 0) {
            Ui.drawAnimatedCheckbox(ctx, cardX + cardW - 22, y + 16, 12, checkT, hov);
        }
        return y + 52;
    }

    private void drawCustomTab(class_332 ctx, int mx, int my, float delta,
                               int cardX, int cardW, int bg, int panel, int text, int muted, int border, int accent, int surface) {
        DrawHelper.drawText(ctx, field_22793, "PICK A COLOR CHANNEL",
            field_22789 / 2 - field_22793.method_1727("PICK A COLOR CHANNEL") / 2, CONTENT_TOP - 4, muted, false);

        drawPreview(ctx, cardX, cardW, CONTENT_TOP + 10, panel, accent, surface, text, muted);

        int chipY = CONTENT_TOP + 76;
        int chipW = (cardW - 24) / 4;
        int chipH = 28;
        for (int i = 0; i < SLOTS.length; i++) {
            ColorSlot slot = SLOTS[i];
            int col = i % 4;
            int row = i / 4;
            int cx = cardX + 12 + col * (chipW + 8);
            int cy = chipY + row * (chipH + 8);
            boolean sel = slot == editingSlot;
            boolean hov = mx >= cx && mx <= cx + chipW && my >= cy && my <= cy + chipH;
            int chipBg = sel ? Ui.withAlpha(accent & 0xFFFFFF, 40) : Ui.lerpColor(bg, panel, hov ? 1f : 0f);
            ctx.method_25294(cx, cy, cx + chipW, cy + chipH, chipBg);
            ctx.method_25294(cx, cy, cx + chipW, cy + 1, sel ? accent : border);
            if (sel) ctx.method_25294(cx, cy, cx + 2, cy + chipH, accent);
            swatch(ctx, cx + 6, cy + 6, customColors[i]);
            DrawHelper.drawText(ctx, field_22793, slot.label, cx + 24, cy + 10,
                sel ? text : Ui.lerpColor(muted, text, hov ? 1f : 0.5f), false);
        }

        int editing = customColors[editingSlot.ordinal()];
        String hex = String.format("#%06X", editing & 0xFFFFFF);
        DrawHelper.drawText(ctx, field_22793, editingSlot.label + "  " + hex,
            cardX, chipY + 72, muted, false);
    }

    private void drawPicker(class_332 ctx, int mx, int my, int text, int muted, int border, int accent) {
        int cardW = Math.min(420, field_22789 - 48);
        int cardX = (field_22789 - cardW) / 2;
        int pickerTop = field_22790 - FOOTER_H - 130;

        hueW = cardW - 24;
        hueH = 14;
        hueX = cardX + 12;
        hueY = pickerTop;
        svW = cardW - 24;
        svH = 88;
        svX = cardX + 12;
        svY = hueY + hueH + 10;

        DrawHelper.drawText(ctx, field_22793, "HUE", hueX, hueY - 10, muted, false);
        for (int i = 0; i < hueW; i += 2) {
            float h = i / (float) hueW;
            ctx.method_25294(hueX + i, hueY, hueX + i + 2, hueY + hueH, GuiTheme.hsvToRgb(h, 1f, 1f));
        }
        ctx.method_25294(hueX, hueY, hueX + hueW, hueY + 1, border);
        ctx.method_25294(hueX, hueY + hueH - 1, hueX + hueW, hueY + hueH, border);
        int hueMark = hueX + (int) (editHue * hueW);
        ctx.method_25294(hueMark - 1, hueY - 2, hueMark + 2, hueY + hueH + 2, text);

        DrawHelper.drawText(ctx, field_22793, "SATURATION / BRIGHTNESS", svX, svY - 10, muted, false);
        for (int py = 0; py < svH; py += 4) {
            float v = 1f - py / (float) svH;
            for (int px = 0; px < svW; px += 4) {
                float s = px / (float) svW;
                ctx.method_25294(svX + px, svY + py, svX + px + 4, svY + py + 4,
                    GuiTheme.hsvToRgb(editHue, s, v));
            }
        }
        ctx.method_25294(svX, svY, svX + svW, svY + 1, border);
        ctx.method_25294(svX, svY + svH - 1, svX + svW, svY + svH, border);
        ctx.method_25294(svX, svY, svX + 1, svY + svH, border);
        ctx.method_25294(svX + svW - 1, svY, svX + svW, svY + svH, border);
        int svMarkX = svX + (int) (editSat * svW);
        int svMarkY = svY + (int) ((1f - editVal) * svH);
        ctx.method_25294(svMarkX - 2, svMarkY - 2, svMarkX + 3, svMarkY + 3, 0xFFFFFFFF);
        ctx.method_25294(svMarkX - 1, svMarkY - 1, svMarkX + 2, svMarkY + 2, accent);

        swatch(ctx, svX + svW + 8, svY + 28, customColors[editingSlot.ordinal()]);
    }

    private void drawPreview(class_332 ctx, int cardX, int cardW, int y,
                               int panel, int accent, int surface, int text, int muted) {
        DrawHelper.drawText(ctx, field_22793, "PREVIEW", cardX, y, muted, false);
        y += 14;
        ctx.method_25294(cardX, y, cardX + cardW, y + 56, panel);
        ctx.method_25294(cardX, y, cardX + cardW, y + 2, accent);
        ctx.method_25294(cardX + 10, y + 12, cardX + cardW - 10, y + 44, surface);
        DrawHelper.drawText(ctx, field_22793, "Slothy's Tree", cardX + 18, y + 18, accent, false);
        DrawHelper.drawText(ctx, field_22793, "Pack card · Apply · Build", cardX + 18, y + 32, text, false);
    }

    private void drawFooter(class_332 ctx, int mx, int my,
                            int bg, int panel, int text, int muted, int border, int accent) {
        ctx.method_25294(0, field_22790 - FOOTER_H, field_22789, field_22790 - FOOTER_H + 1, border);
        ctx.method_25294(0, field_22790 - FOOTER_H + 1, field_22789, field_22790, panel);
        boolean closeHov = mx >= field_22789 / 2 - 52 && mx <= field_22789 / 2 + 52
            && my >= field_22790 - 27 && my <= field_22790 - 7;
        int cb = closeHov ? accent : border, cf = closeHov ? accent : bg, cfg = closeHov ? 0xFF000000 : muted;
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 27, field_22789 / 2 + 52, field_22790 - 7, cf);
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 27, field_22789 / 2 + 52, field_22790 - 26, cb);
        DrawHelper.drawText(ctx, field_22793, "SAVE & CLOSE",
            field_22789 / 2 - field_22793.method_1727("SAVE & CLOSE") / 2, field_22790 - 21, cfg, false);
    }

    private static void swatch(class_332 ctx, int x, int y, int rgb) {
        ctx.method_25294(x, y, x + 14, y + 14, 0xFF000000 | (rgb & 0xFFFFFF));
        ctx.method_25294(x, y, x + 14, y + 1, 0x40FFFFFF);
        ctx.method_25294(x, y + 13, x + 14, y + 14, 0x40000000);
    }

    private void updateHueFromMouse(double mx) {
        editHue = (float) clamp((mx - hueX) / hueW, 0, 1);
        commitEditingColor();
    }

    private void updateSvFromMouse(double mx, double my) {
        editSat = (float) clamp((mx - svX) / svW, 0, 1);
        editVal = (float) clamp(1 - (my - svY) / svH, 0, 1);
        commitEditingColor();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }


    public boolean method_25402(double mx, double my, int button) {
        if (button != 0) return false;

        if (clickTabs(mx, my)) return true;
        if (clickFooter(mx, my)) return true;

        if (activeTab == Tab.PRESETS) return clickPresetsTab(mx, my);
        return clickCustomTab(mx, my);
    }

    private boolean clickTabs(double mx, double my) {
        int x = field_22789 / 2 - 80, y = 46, w = 78, h = 16;
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            activeTab = Tab.PRESETS;
            Ui.playClick();
            return true;
        }
        x += w + 4;
        if (mx >= x && mx <= x + w && my >= y && my <= y + h) {
            activeTab = Tab.CUSTOM;
            Ui.playClick();
            return true;
        }
        return false;
    }

    private boolean clickFooter(double mx, double my) {
        if (mx >= field_22789 / 2 - 52 && mx <= field_22789 / 2 + 52
            && my >= field_22790 - 27 && my <= field_22790 - 7) {
            method_25419();
            return true;
        }
        return false;
    }

    private boolean clickPresetsTab(double mx, double my) {
        int cardW = Math.min(420, field_22789 - 48);
        int cardX = (field_22789 - cardW) / 2;
        int y = CONTENT_TOP + 12;
        for (GuiTheme.Preset p : GuiTheme.PRESETS) {
            if (mx >= cardX && mx <= cardX + cardW && my >= y && my <= y + 44) {
                selectedPreset = p.id();
                GuiTheme.applyPreset(p.id());
                System.arraycopy(GuiTheme.currentColors(), 0, customColors, 0, customColors.length);
                Ui.spawnSelectionBurst((int) mx, (int) my, Ui.COL_ACCENT);
                Ui.playSuccess();
                return true;
            }
            y += 52;
        }
        if (mx >= cardX && mx <= cardX + cardW && my >= y && my <= y + 44) {
            activeTab = Tab.CUSTOM;
            selectedPreset = "custom";
            applyCustomTheme();
            Ui.playClick();
            return true;
        }
        return false;
    }

    private boolean clickCustomTab(double mx, double my) {
        int cardW = Math.min(420, field_22789 - 48);
        int cardX = (field_22789 - cardW) / 2;
        int chipY = CONTENT_TOP + 76;
        int chipW = (cardW - 24) / 4;
        int chipH = 28;
        for (int i = 0; i < SLOTS.length; i++) {
            int col = i % 4;
            int row = i / 4;
            int cx = cardX + 12 + col * (chipW + 8);
            int cy = chipY + row * (chipH + 8);
            if (mx >= cx && mx <= cx + chipW && my >= cy && my <= cy + chipH) {
                editingSlot = SLOTS[i];
                syncEditorFromSlot();
                Ui.playClick();
                return true;
            }
        }

        layoutPicker();
        if (mx >= hueX && mx <= hueX + hueW && my >= hueY && my <= hueY + hueH) {
            draggingHue = true;
            updateHueFromMouse(mx);
            return true;
        }
        if (mx >= svX && mx <= svX + svW && my >= svY && my <= svY + svH) {
            draggingSv = true;
            updateSvFromMouse(mx, my);
            return true;
        }
        return false;
    }

    private void layoutPicker() {
        int cardW = Math.min(420, field_22789 - 48);
        int cardX = (field_22789 - cardW) / 2;
        int pickerTop = field_22790 - FOOTER_H - 130;
        hueW = cardW - 24;
        hueH = 14;
        hueX = cardX + 12;
        hueY = pickerTop;
        svW = cardW - 24;
        svH = 88;
        svX = cardX + 12;
        svY = hueY + hueH + 10;
    }

    @Override
    public void method_25419() {
        SlothyConfig.save();
        net.minecraft.class_310.method_1551().method_1507(parent);
    }

    @Override
    public boolean method_25421() { return false; }
}
