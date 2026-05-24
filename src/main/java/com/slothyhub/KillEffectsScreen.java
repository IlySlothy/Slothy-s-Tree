package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

import java.util.List;

public class KillEffectsScreen extends class_437 {

    private final class_437 parent;

    private static final int COL_BG     = Ui.COL_BG;
    private static final int COL_PANEL  = Ui.COL_PANEL;
    private static final int COL_TEXT   = Ui.COL_TEXT;
    private static final int COL_MUTED  = Ui.COL_MUTED;
    private static final int COL_BORDER = Ui.COL_BORDER;
    private static final int COL_ACCENT = Ui.COL_ACCENT;
    private static final int COL_DANGER = Ui.COL_DANGER;

    private record Effect(String id, String name, String glyph, String desc) {}

    private static final List<Effect> EFFECTS = List.of(
        new Effect("totem",   "TOTEM",   "♣", "Totem of Undying plays on kill — classic flex."),
        new Effect("anvil",   "ANVIL",   "▲", "Crashing anvil drop sound on each elimination."),
        new Effect("thunder", "THUNDER", "⚡", "Thunder crack — dramatic storm of death."),
        new Effect("none",    "SILENT",  "—", "No kill effect — for the silent sloth.")
    );

    private float[] hoverT = new float[EFFECTS.size()];
    private String selected;

    public KillEffectsScreen(class_437 parent) {
        super(class_2561.method_43470("Kill Effects"));
        this.parent = parent;
        selected = SlothyConfig.getKillEffect();
        if (selected == null) selected = "none";
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        ctx.method_25294(0, 0, field_22789, field_22790, COL_BG);
        ctx.method_25294(0, 0, field_22789, 40, COL_PANEL);
        ctx.method_25294(0, 40, field_22789, 41, COL_BORDER);
        ctx.method_25294(0, 0, field_22789, 2, COL_ACCENT);
        DrawHelper.drawText(ctx, field_22793, "KILL EFFECTS  //  SLOTHYHUB", 18, 14, COL_TEXT, false);

        int cardW = Math.min(field_22789 - 60, 420);
        int cardX = (field_22789 - cardW) / 2;
        int startY = 64;

        DrawHelper.drawText(ctx, field_22793, "SELECT KILL EFFECT", field_22789 / 2 - field_22793.method_1727("SELECT KILL EFFECT") / 2, startY - 14, COL_MUTED, false);

        for (int i = 0; i < EFFECTS.size(); i++) {
            Effect e = EFFECTS.get(i);
            boolean sel = e.id().equals(selected);
            boolean hov = mx >= cardX && mx <= cardX + cardW && my >= startY + i * 64 && my <= startY + i * 64 + 56;
            hoverT[i] += ((hov ? 1f : 0f) - hoverT[i]) * Math.min(1f, delta * 0.3f);
            float ht = hoverT[i];
            int rowY = startY + i * 64;
            int bg = sel ? Ui.withAlpha(COL_ACCENT & 0xFFFFFF, 30) : Ui.lerpColor(COL_BG, COL_PANEL, ht);
            ctx.method_25294(cardX, rowY, cardX + cardW, rowY + 56, bg);
            int border = sel ? COL_ACCENT : Ui.lerpColor(COL_BORDER, COL_ACCENT, ht * 0.5f);
            ctx.method_25294(cardX, rowY, cardX + cardW, rowY + 1, border);
            ctx.method_25294(cardX, rowY + 55, cardX + cardW, rowY + 56, border);
            ctx.method_25294(cardX, rowY, cardX + 1, rowY + 56, border);
            ctx.method_25294(cardX + cardW - 1, rowY, cardX + cardW, rowY + 56, border);
            // Left accent for selected
            if (sel) ctx.method_25294(cardX, rowY, cardX + 2, rowY + 56, COL_ACCENT);

            // Glyph box
            int gx = cardX + 10, gy = rowY + 16;
            ctx.method_25294(gx, gy, gx + 24, gy + 24, Ui.withAlpha(COL_ACCENT & 0xFFFFFF, sel ? 80 : 40));
            DrawHelper.drawText(ctx, field_22793, e.glyph(), gx + (24 - field_22793.method_1727(e.glyph())) / 2, gy + 8, sel ? COL_ACCENT : COL_MUTED, false);

            // Text
            DrawHelper.drawText(ctx, field_22793, e.name(), cardX + 42, rowY + 14, sel ? COL_TEXT : Ui.lerpColor(COL_MUTED, COL_TEXT, ht), false);
            DrawHelper.drawText(ctx, field_22793, e.desc(), cardX + 42, rowY + 28, COL_MUTED, false);

            // Select pill
            int pW = field_22793.method_1727(sel ? "SELECTED" : "SELECT") + 14, pH = 16;
            int px = cardX + cardW - pW - 10, py = rowY + (56 - pH) / 2;
            int pFill = sel ? COL_ACCENT : COL_BG;
            int pFg = sel ? -16777216 : Ui.lerpColor(COL_MUTED, COL_TEXT, ht);
            int pBorder = sel ? COL_ACCENT : Ui.lerpColor(COL_BORDER, COL_ACCENT, ht);
            ctx.method_25294(px, py, px + pW, py + pH, pFill);
            ctx.method_25294(px, py, px + pW, py + 1, pBorder);
            ctx.method_25294(px, py + pH - 1, px + pW, py + pH, pBorder);
            ctx.method_25294(px, py, px + 1, py + pH, pBorder);
            ctx.method_25294(px + pW - 1, py, px + pW, py + pH, pBorder);
            String pLabel = sel ? "SELECTED" : "SELECT";
            DrawHelper.drawText(ctx, field_22793, pLabel, px + (pW - field_22793.method_1727(pLabel)) / 2, py + 4, pFg, false);
        }

        // Footer
        ctx.method_25294(0, field_22790 - 36, field_22789, field_22790 - 35, COL_BORDER);
        ctx.method_25294(0, field_22790 - 35, field_22789, field_22790, COL_PANEL);
        boolean closeHov = mx >= field_22789 / 2 - 52 && mx <= field_22789 / 2 + 52 && my >= field_22790 - 27 && my <= field_22790 - 7;
        int cb = closeHov ? COL_ACCENT : COL_BORDER, cf = closeHov ? COL_ACCENT : COL_BG, cfg = closeHov ? -16777216 : COL_MUTED;
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 27, field_22789 / 2 + 52, field_22790 - 7, cf);
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 27, field_22789 / 2 + 52, field_22790 - 26, cb);
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 8, field_22789 / 2 + 52, field_22790 - 7, cb);
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 27, field_22789 / 2 - 51, field_22790 - 7, cb);
        ctx.method_25294(field_22789 / 2 + 51, field_22790 - 27, field_22789 / 2 + 52, field_22790 - 7, cb);
        DrawHelper.drawText(ctx, field_22793, "SAVE & CLOSE", field_22789 / 2 - field_22793.method_1727("SAVE & CLOSE") / 2, field_22790 - 21, cfg, false);
    }

    @Override
    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            int cardW = Math.min(field_22789 - 60, 420), cardX = (field_22789 - cardW) / 2, startY = 64;
            for (int i = 0; i < EFFECTS.size(); i++) {
                if (mx >= cardX && mx <= cardX + cardW && my >= startY + i * 64 && my <= startY + i * 64 + 56) {
                    selected = EFFECTS.get(i).id(); SlothyConfig.setKillEffect(selected); Ui.playClick(); return true;
                }
            }
            if (mx >= field_22789 / 2 - 52 && mx <= field_22789 / 2 + 52 && my >= field_22790 - 27 && my <= field_22790 - 7) {
                method_25419(); return true;
            }
        }
        return false;
    }

    @Override
    public void method_25419() {
        SlothyConfig.save();
        net.minecraft.class_310.method_1551().method_1507(parent);
    }

    @Override
    public boolean method_25421() { return false; }
}
