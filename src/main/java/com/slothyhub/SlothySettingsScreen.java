package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.InputCompat;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_437;

import java.util.ArrayList;
import java.util.List;

public class SlothySettingsScreen extends class_437 {

    private final class_437 parent;
    private class_342 serverUrlField;
    private float toggleAnim_anim = 0, toggleAnim_bg = 0, toggleAnim_stars = 0, toggleAnim_sort = 0, toggleAnim_confirm = 0, toggleAnim_cit = 0;
    private String statusMessage = null;
    private long statusAt;
    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    private record Section(String header, List<Row> rows) {}
    private record Row(String label, String key, String hint) {}

    private final List<Section> sections = List.of(
        new Section("DISPLAY", new ArrayList<>(List.of(
            new Row("Leaf Parallax Background", "bg", "Subtle leaf-fall behind the pack list."),
            new Row("Animations", "anim", "Card entry, hover, and button transitions."),
            new Row("Sort by Stars", "sort", "Show most-starred packs first.")))),
        new Section("BEHAVIOR", new ArrayList<>(List.of(
            new Row("Confirm Before Removing Pack", "confirm", "Two-click safety for dropping active packs."),
            new Row("CIT Engine (Custom Item Textures)", "cit", "Enable embedded CIT Resewn compatible engine.")))));

    public SlothySettingsScreen(class_437 parent) {
        super(class_2561.method_43470("Settings"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
        String url = SlothyConfig.getServerUrl();
        serverUrlField = new class_342(field_22793, field_22789 / 2 - 100, 120, 200, 20,
            class_2561.method_43470("Server URL"));
        serverUrlField.method_1880(256);
        serverUrlField.method_1858(false);
        serverUrlField.method_1868(Ui.COL_TEXT);
        serverUrlField.method_1852(url != null ? url : "");
        serverUrlField.method_47404(class_2561.method_43470(SlothyConfig.DEFAULT_SERVER_URL));
        method_37063(serverUrlField);
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);
        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        Ui.drawSubscreenHeader(ctx, field_22793, field_22789, "SETTINGS  //  SLOTHYHUB", delta);
        // Back pill
        boolean backHov = mx >= 4 && mx <= 14 && my >= 10 && my <= 30;
        DrawHelper.drawText(ctx, field_22793, backHov ? "<" : "‹", 8, 16, backHov ? Ui.COL_TEXT : Ui.COL_MUTED, false);

        // Server URL section
        ctx.method_25294(18, 52, field_22789 - 18, 53, Ui.COL_BORDER);
        DrawHelper.drawText(ctx, field_22793, "SERVER", 18, 56, Ui.COL_MUTED, false);
        DrawHelper.drawText(ctx, field_22793, "Server URL", 18, 108, Ui.COL_TEXT, false);
        DrawHelper.drawText(ctx, field_22793, "Where SlothyHub fetches packs from. Leave empty for local-only mode.", 18, 118, Ui.COL_MUTED, false);
        // Draw the text field manually and call super for widget rendering
        for (net.minecraft.class_364 child : method_25396()) {
            if (child instanceof net.minecraft.class_4068 d) d.method_25394(ctx, mx, my, delta);
        }

        // Toggle rows
        int rowY = 158;
        for (Section section : sections) {
            ctx.method_25294(18, rowY, field_22789 - 18, rowY + 1, Ui.COL_BORDER);
            DrawHelper.drawText(ctx, field_22793, section.header(), 18, rowY + 4, Ui.COL_MUTED, false);
            rowY += 16;
            for (Row row : section.rows()) {
                boolean val = getToggleValue(row.key());
                float t = getToggleT(row.key(), delta);
                boolean hovered = mx >= 18 && mx <= field_22789 - 18 && my >= rowY - 2 && my <= rowY + 22;
                int rowBg = hovered ? Ui.withAlpha(Ui.COL_PANEL & 0xFFFFFF, 140) : 0;
                if (rowBg != 0) ctx.method_25294(18, rowY - 2, field_22789 - 18, rowY + 22, rowBg);
                DrawHelper.drawText(ctx, field_22793, row.label(), 26, rowY + 4, hovered ? Ui.COL_TEXT : Ui.lerpColor(Ui.COL_MUTED, Ui.COL_TEXT, 0.5f), false);
                if (!row.hint().isEmpty()) DrawHelper.drawText(ctx, field_22793, row.hint(), 26, rowY + 14, Ui.COL_MUTED, false);
                renderToggle(ctx, field_22789 - 56, rowY + 5, t, val);
                rowY += 30;
            }
        }

        // Status message
        if (statusMessage != null && System.currentTimeMillis() - statusAt < 3000L) {
            int tw = field_22793.method_1727(statusMessage);
            DrawHelper.drawText(ctx, field_22793, statusMessage, field_22789 / 2 - tw / 2, field_22790 - 28, Ui.COL_ACCENT, false);
        }

        // Footer
        ctx.method_25294(0, field_22790 - 36, field_22789, field_22790 - 35, Ui.COL_BORDER);
        ctx.method_25294(0, field_22790 - 35, field_22789, field_22790, Ui.COL_PANEL);
        boolean saveHov = mx >= field_22789 / 2 - 56 && mx <= field_22789 / 2 + 56 && my >= field_22790 - 27 && my <= field_22790 - 7;
        boolean closeHov = mx >= field_22789 / 2 + 64 && mx <= field_22789 / 2 + 64 + 48 && my >= field_22790 - 27 && my <= field_22790 - 7;
        drawFooterBtn(ctx, field_22789 / 2 - 56, field_22790 - 27, 112, 20, "SAVE & CLOSE", saveHov, false);
        drawFooterBtn(ctx, field_22789 / 2 + 64, field_22790 - 27, 48, 20, "BACK", closeHov, false);
    }

    private void renderToggle(class_332 ctx, int x, int y, float t, boolean on) {
        int trackW = 32, trackH = 14;
        int trackFg = Ui.lerpColor(Ui.COL_BORDER, Ui.COL_ACCENT, t);
        ctx.method_25294(x, y, x + trackW, y + trackH, trackFg);
        ctx.method_25294(x + 1, y + 1, x + trackW - 1, y + trackH - 1, Ui.COL_BG);
        ctx.method_25294(x, y, x + trackW, y + trackH, Ui.withAlpha(Ui.COL_ACCENT & 0xFFFFFF, (int)(180 * t)));
        int thumbX = x + 2 + (int)((trackW - 14) * t);
        ctx.method_25294(thumbX, y + 2, thumbX + 10, y + trackH - 2, Ui.lerpColor(Ui.COL_BORDER, Ui.COL_TEXT, t));
        if (t > 0.08f) {
            Ui.drawAnimatedCheckmark(ctx, thumbX, y + 2, 10, t, Ui.lerpColor(Ui.COL_BG, Ui.COL_ACCENT, t));
        }
    }

    private void drawFooterBtn(class_332 ctx, int x, int y, int w, int h, String label, boolean hov, boolean danger) {
        int border = danger ? Ui.COL_DANGER : (hov ? Ui.COL_ACCENT : Ui.COL_BORDER);
        int fill = hov ? (danger ? Ui.COL_DANGER : Ui.COL_ACCENT) : Ui.COL_BG;
        int fg = hov ? (danger ? Ui.COL_TEXT : -16777216) : (danger ? Ui.COL_DANGER : Ui.COL_MUTED);
        ctx.method_25294(x, y, x + w, y + h, fill);
        ctx.method_25294(x, y, x + w, y + 1, border);
        ctx.method_25294(x, y + h - 1, x + w, y + h, border);
        ctx.method_25294(x, y, x + 1, y + h, border);
        ctx.method_25294(x + w - 1, y, x + w, y + h, border);
        DrawHelper.drawText(ctx, field_22793, label, x + (w - field_22793.method_1727(label)) / 2, y + (h - 8) / 2, fg, false);
    }

    private boolean getToggleValue(String key) {
        return switch (key) {
            case "bg"     -> SlothyConfig.isBackgroundEffects();
            case "anim"   -> SlothyConfig.isAnimationsEnabled();
            case "sort"   -> SlothyConfig.isSortByStars();
            case "confirm"-> SlothyConfig.isConfirmBeforeRemove();
            case "cit"    -> SlothyConfig.isCitEnabled();
            default -> false;
        };
    }

    private float getToggleT(String key, float delta) {
        boolean on = getToggleValue(key);
        float ease = Math.min(1f, delta * 0.35f);
        switch (key) {
            case "bg"     -> { toggleAnim_bg     += ((on ? 1f : 0f) - toggleAnim_bg) * ease; return toggleAnim_bg; }
            case "anim"   -> { toggleAnim_anim   += ((on ? 1f : 0f) - toggleAnim_anim) * ease; return toggleAnim_anim; }
            case "sort"   -> { toggleAnim_sort   += ((on ? 1f : 0f) - toggleAnim_sort) * ease; return toggleAnim_sort; }
            case "confirm"-> { toggleAnim_confirm+= ((on ? 1f : 0f) - toggleAnim_confirm) * ease; return toggleAnim_confirm; }
            case "cit"    -> { toggleAnim_cit    += ((on ? 1f : 0f) - toggleAnim_cit) * ease; return toggleAnim_cit; }
            default -> { return on ? 1f : 0f; }
        }
    }


    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            // Back button
            if (mx >= 4 && mx <= 14 && my >= 10 && my <= 30) { method_25419(); return true; }
            // Save & Close
            int sx = field_22789 / 2 - 56, sy = field_22790 - 27;
            if (mx >= sx && mx <= sx + 112 && my >= sy && my <= sy + 20) { saveAndClose(); return true; }
            // Back
            int bx = field_22789 / 2 + 64, by = field_22790 - 27;
            if (mx >= bx && mx <= bx + 48 && my >= by && my <= by + 20) { method_25419(); return true; }
            // Toggle rows (must match render loop rowY math)
            int rowY = 158;
            for (Section section : sections) {
                rowY += 16;
                for (Row row : section.rows()) {
                    if (mx >= 18 && mx <= field_22789 - 18 && my >= rowY - 2 && my <= rowY + 22) {
                        toggleValue(row.key()); Ui.playClick(); return true;
                    }
                    rowY += 30;
                }
            }
        }
        return InputCompat.delegateToChildren(this, mx, my, button);
    }

    private void toggleValue(String key) {
        switch (key) {
            case "bg"     -> SlothyConfig.setBackgroundEffects(!SlothyConfig.isBackgroundEffects());
            case "anim"   -> SlothyConfig.setAnimationsEnabled(!SlothyConfig.isAnimationsEnabled());
            case "sort"   -> SlothyConfig.setSortByStars(!SlothyConfig.isSortByStars());
            case "confirm"-> SlothyConfig.setConfirmBeforeRemove(!SlothyConfig.isConfirmBeforeRemove());
            case "cit"    -> SlothyConfig.setCitEnabled(!SlothyConfig.isCitEnabled());
        }
    }

    private void saveAndClose() {
        String url = serverUrlField.method_1882().trim();
        SlothyConfig.setServerUrl(url.isEmpty() ? null : url);
        SlothyConfig.save();
        statusMessage = "SAVED — BRANCHES SECURED."; statusAt = System.currentTimeMillis();
        method_25419();
    }

    @Override
    public void method_25419() {
        SlothyConfig.save();
        net.minecraft.class_310.method_1551().method_1507(parent);
    }

    @Override
    public boolean method_25421() { return false; }
}
