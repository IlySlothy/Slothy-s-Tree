package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

/**
 * Feather Profile screen — lightweight elytra / movement profile switcher.
 * Stub implementation; extend with actual profile logic as needed.
 */
public class FeatherProfilesScreen extends class_437 {

    private final class_437 parent;
    private static final int COL_BG     = Ui.COL_BG;
    private static final int COL_PANEL  = Ui.COL_PANEL;
    private static final int COL_TEXT   = Ui.COL_TEXT;
    private static final int COL_MUTED  = Ui.COL_MUTED;
    private static final int COL_BORDER = Ui.COL_BORDER;
    private static final int COL_ACCENT = Ui.COL_ACCENT;

    private static final String[] PROFILES = {"DEFAULT", "ELYTRA", "CPVP", "NETHPOT"};
    private String selected = "DEFAULT";
    private float[] hoverT = new float[PROFILES.length];

    public FeatherProfilesScreen(class_437 parent) {
        super(class_2561.method_43470("Feather Profiles"));
        this.parent = parent;
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        ctx.method_25294(0, 0, field_22789, field_22790, COL_BG);
        ctx.method_25294(0, 0, field_22789, 40, COL_PANEL);
        ctx.method_25294(0, 40, field_22789, 41, COL_BORDER);
        ctx.method_25294(0, 0, field_22789, 2, COL_ACCENT);
        DrawHelper.drawText(ctx, field_22793, "FEATHER PROFILES  //  SLOTHYHUB", 18, 14, COL_TEXT, false);
        DrawHelper.drawText(ctx, field_22793, "SELECT ACTIVE PROFILE", field_22789 / 2 - field_22793.method_1727("SELECT ACTIVE PROFILE") / 2, 56, COL_MUTED, false);

        int cardW = Math.min(field_22789 - 60, 380), cardX = (field_22789 - cardW) / 2;
        for (int i = 0; i < PROFILES.length; i++) {
            String p = PROFILES[i];
            boolean sel = p.equals(selected);
            boolean hov = mx >= cardX && mx <= cardX + cardW && my >= 76 + i * 42 && my <= 76 + i * 42 + 36;
            hoverT[i] += ((hov ? 1f : 0f) - hoverT[i]) * Math.min(1f, delta * 0.3f);
            int rowY = 76 + i * 42;
            int bg = sel ? Ui.withAlpha(COL_ACCENT & 0xFFFFFF, 30) : Ui.lerpColor(COL_BG, COL_PANEL, hoverT[i]);
            ctx.method_25294(cardX, rowY, cardX + cardW, rowY + 36, bg);
            int border = sel ? COL_ACCENT : Ui.lerpColor(COL_BORDER, COL_ACCENT, hoverT[i] * 0.6f);
            ctx.method_25294(cardX, rowY, cardX + cardW, rowY + 1, border);
            ctx.method_25294(cardX, rowY + 35, cardX + cardW, rowY + 36, border);
            ctx.method_25294(cardX, rowY, cardX + 1, rowY + 36, border);
            ctx.method_25294(cardX + cardW - 1, rowY, cardX + cardW, rowY + 36, border);
            if (sel) ctx.method_25294(cardX, rowY, cardX + 2, rowY + 36, COL_ACCENT);
            DrawHelper.drawText(ctx, field_22793, p, cardX + 12, rowY + 14, sel ? COL_TEXT : Ui.lerpColor(COL_MUTED, COL_TEXT, hoverT[i]), false);
            if (sel) DrawHelper.drawText(ctx, field_22793, "ACTIVE", cardX + cardW - field_22793.method_1727("ACTIVE") - 12, rowY + 14, COL_ACCENT, false);
        }

        // Footer
        ctx.method_25294(0, field_22790 - 36, field_22789, field_22790 - 35, COL_BORDER);
        ctx.method_25294(0, field_22790 - 35, field_22789, field_22790, COL_PANEL);
        boolean closeHov = mx >= field_22789 / 2 - 40 && mx <= field_22789 / 2 + 40 && my >= field_22790 - 27 && my <= field_22790 - 7;
        int cb = closeHov ? COL_ACCENT : COL_BORDER, cf = closeHov ? COL_ACCENT : COL_BG, cfg = closeHov ? -16777216 : COL_MUTED;
        ctx.method_25294(field_22789 / 2 - 40, field_22790 - 27, field_22789 / 2 + 40, field_22790 - 7, cf);
        ctx.method_25294(field_22789 / 2 - 40, field_22790 - 27, field_22789 / 2 + 40, field_22790 - 26, cb);
        ctx.method_25294(field_22789 / 2 - 40, field_22790 - 8, field_22789 / 2 + 40, field_22790 - 7, cb);
        ctx.method_25294(field_22789 / 2 - 40, field_22790 - 27, field_22789 / 2 - 39, field_22790 - 7, cb);
        ctx.method_25294(field_22789 / 2 + 39, field_22790 - 27, field_22789 / 2 + 40, field_22790 - 7, cb);
        DrawHelper.drawText(ctx, field_22793, "CLOSE", field_22789 / 2 - field_22793.method_1727("CLOSE") / 2, field_22790 - 21, cfg, false);
    }

    @Override
    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            int cardW = Math.min(field_22789 - 60, 380), cardX = (field_22789 - cardW) / 2;
            for (int i = 0; i < PROFILES.length; i++) {
                if (mx >= cardX && mx <= cardX + cardW && my >= 76 + i * 42 && my <= 76 + i * 42 + 36) {
                    selected = PROFILES[i]; Ui.playClick(); return true;
                }
            }
            if (mx >= field_22789 / 2 - 40 && mx <= field_22789 / 2 + 40 && my >= field_22790 - 27 && my <= field_22790 - 7) {
                method_25419(); return true;
            }
        }
        return false;
    }

    @Override
    public void method_25419() { net.minecraft.class_310.method_1551().method_1507(parent); }

    @Override
    public boolean method_25421() { return false; }
}
