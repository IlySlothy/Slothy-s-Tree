package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.InputCompat;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_437;

import java.util.List;

public class KillEffectsScreen extends class_437 {

    private final class_437 parent;

    private record Effect(String id, String name, String glyph, String desc) {}

    private static final List<Effect> EFFECTS = List.of(
        new Effect("totem",   "TOTEM",   "♣", "Totem of Undying plays on kill — classic flex."),
        new Effect("anvil",   "ANVIL",   "▲", "Crashing anvil drop sound on each elimination."),
        new Effect("thunder", "THUNDER", "⚡", "Thunder crack — dramatic storm of death."),
        new Effect("none",    "SILENT",  "—", "No kill effect — for the silent sloth.")
    );

    private float[] hoverT = new float[EFFECTS.size()];
    private float[] effectCheckAnim = new float[EFFECTS.size()];
    private String selected;
    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    public KillEffectsScreen(class_437 parent) {
        super(class_2561.method_43470("Kill Effects"));
        this.parent = parent;
        selected = SlothyConfig.getKillEffect();
        if (selected == null) selected = "none";
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);
        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        Ui.drawSubscreenHeader(ctx, field_22793, field_22789, "KILL EFFECTS  //  SLOTHYHUB", delta);

        int cardW = Math.min(field_22789 - 60, 420);
        int cardX = (field_22789 - cardW) / 2;
        int startY = 64;

        DrawHelper.drawText(ctx, field_22793, "SELECT KILL EFFECT", field_22789 / 2 - field_22793.method_1727("SELECT KILL EFFECT") / 2, startY - 14, Ui.COL_MUTED, false);

        for (int i = 0; i < EFFECTS.size(); i++) {
            Effect e = EFFECTS.get(i);
            boolean sel = e.id().equals(selected);
            boolean hov = mx >= cardX && mx <= cardX + cardW && my >= startY + i * 64 && my <= startY + i * 64 + 56;
            hoverT[i] += ((hov ? 1f : 0f) - hoverT[i]) * Math.min(1f, delta * 0.3f);
            effectCheckAnim[i] = Ui.tickCheckAnim(effectCheckAnim[i], sel, delta);
            float ht = hoverT[i];
            int rowY = startY + i * 64;
            int bg = sel ? Ui.withAlpha(Ui.COL_ACCENT & 0xFFFFFF, 30) : Ui.lerpColor(Ui.COL_BG, Ui.COL_PANEL, ht);
            ctx.method_25294(cardX, rowY, cardX + cardW, rowY + 56, bg);
            int border = sel ? Ui.COL_ACCENT : Ui.lerpColor(Ui.COL_BORDER, Ui.COL_ACCENT, ht * 0.5f);
            ctx.method_25294(cardX, rowY, cardX + cardW, rowY + 1, border);
            ctx.method_25294(cardX, rowY + 55, cardX + cardW, rowY + 56, border);
            ctx.method_25294(cardX, rowY, cardX + 1, rowY + 56, border);
            ctx.method_25294(cardX + cardW - 1, rowY, cardX + cardW, rowY + 56, border);
            // Left accent for selected
            if (sel) ctx.method_25294(cardX, rowY, cardX + 2, rowY + 56, Ui.COL_ACCENT);

            // Glyph box
            int gx = cardX + 10, gy = rowY + 16;
            ctx.method_25294(gx, gy, gx + 24, gy + 24, Ui.withAlpha(Ui.COL_ACCENT & 0xFFFFFF, sel ? 80 : 40));
            DrawHelper.drawText(ctx, field_22793, e.glyph(), gx + (24 - field_22793.method_1727(e.glyph())) / 2, gy + 8, sel ? Ui.COL_ACCENT : Ui.COL_MUTED, false);

            // Text
            DrawHelper.drawText(ctx, field_22793, e.name(), cardX + 42, rowY + 14, sel ? Ui.COL_TEXT : Ui.lerpColor(Ui.COL_MUTED, Ui.COL_TEXT, ht), false);
            DrawHelper.drawText(ctx, field_22793, e.desc(), cardX + 42, rowY + 28, Ui.COL_MUTED, false);
            Ui.drawAnimatedCheckbox(ctx, cardX + cardW - 22, rowY + 22, 12, effectCheckAnim[i], hov);
        }

        // Footer
        ctx.method_25294(0, field_22790 - 36, field_22789, field_22790 - 35, Ui.COL_BORDER);
        ctx.method_25294(0, field_22790 - 35, field_22789, field_22790, Ui.COL_PANEL);
        boolean closeHov = mx >= field_22789 / 2 - 52 && mx <= field_22789 / 2 + 52 && my >= field_22790 - 27 && my <= field_22790 - 7;
        int cb = closeHov ? Ui.COL_ACCENT : Ui.COL_BORDER, cf = closeHov ? Ui.COL_ACCENT : Ui.COL_BG, cfg = closeHov ? -16777216 : Ui.COL_MUTED;
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 27, field_22789 / 2 + 52, field_22790 - 7, cf);
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 27, field_22789 / 2 + 52, field_22790 - 26, cb);
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 8, field_22789 / 2 + 52, field_22790 - 7, cb);
        ctx.method_25294(field_22789 / 2 - 52, field_22790 - 27, field_22789 / 2 - 51, field_22790 - 7, cb);
        ctx.method_25294(field_22789 / 2 + 51, field_22790 - 27, field_22789 / 2 + 52, field_22790 - 7, cb);
        DrawHelper.drawText(ctx, field_22793, "SAVE & CLOSE", field_22789 / 2 - field_22793.method_1727("SAVE & CLOSE") / 2, field_22790 - 21, cfg, false);
    }


    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            int cardW = Math.min(field_22789 - 60, 420), cardX = (field_22789 - cardW) / 2, startY = 64;
            for (int i = 0; i < EFFECTS.size(); i++) {
                if (mx >= cardX && mx <= cardX + cardW && my >= startY + i * 64 && my <= startY + i * 64 + 56) {
                    selected = EFFECTS.get(i).id(); SlothyConfig.setKillEffect(selected);
                    Ui.spawnSelectionBurst((int) mx, (int) my, Ui.COL_ACCENT);
                    Ui.playSuccess(); return true;
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
