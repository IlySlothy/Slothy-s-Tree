package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;

import java.util.List;

/** Shown once per changelog version after updating the mod. */
public class WhatsNewScreen extends class_437 {

    private static final int FOOTER = 40;

    private final class_437 returnTo;

    public WhatsNewScreen(class_437 returnTo) {
        super(class_2561.method_43470("What's New"));
        this.returnTo = returnTo;
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);
        Ui.drawSubscreenHeader(ctx, field_22793, field_22789, "WHAT'S NEW", delta);

        ChangelogData data = ChangelogData.load();
        String title = data != null ? data.title() : "Updates";
        String ver = data != null ? "v" + data.version() : "";

        DrawHelper.drawText(ctx, field_22793, title,
            field_22789 / 2 - field_22793.method_1727(title) / 2, 58, Ui.COL_ACCENT, false);
        if (!ver.isBlank()) {
            DrawHelper.drawText(ctx, field_22793, ver,
                field_22789 / 2 - field_22793.method_1727(ver) / 2, 72, Ui.COL_MUTED, false);
        }

        int cardW = Math.min(400, field_22789 - 48);
        int cardX = (field_22789 - cardW) / 2;
        int y = 92;
        ctx.method_25294(cardX - 8, y - 8, cardX + cardW + 8, field_22790 - FOOTER - 12,
            col(Ui.COL_PANEL & 0xFFFFFF, 200));

        y += 4;
        List<String> items = data != null ? data.items() : List.of("Thanks for using Slothy's Tree!");
        for (String item : items) {
            DrawHelper.drawText(ctx, field_22793, "• " + item, cardX, y, Ui.COL_TEXT, false);
            y += 14;
        }

        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, Ui.COL_PANEL);
        int cx = field_22789 / 2;
        boolean hov = mx >= cx - 60 && mx <= cx + 60 && my >= field_22790 - 30 && my <= field_22790 - 10;
        ctx.method_25294(cx - 60, field_22790 - 30, cx + 60, field_22790 - 10, hov ? Ui.COL_ACCENT : Ui.COL_SURFACE);
        DrawHelper.drawText(ctx, field_22793, "GOT IT",
            cx - field_22793.method_1727("GOT IT") / 2, field_22790 - 26, hov ? Ui.COL_BG : Ui.COL_TEXT, false);
    }

    public boolean method_25402(double mx, double my, int button) {
        if (button != 0) return false;
        int cx = field_22789 / 2;
        if (mx >= cx - 60 && mx <= cx + 60 && my >= field_22790 - 30 && my <= field_22790 - 10) {
            SlothyConfig.markChangelogSeen();
            class_310.method_1551().method_1507(returnTo != null ? returnTo : new SlothyHubScreen(null));
            return true;
        }
        return false;
    }

    @Override
    public boolean method_25421() { return false; }

    private static int col(int rgb, int a) {
        return Ui.withAlpha(rgb & 0xFFFFFF, a);
    }
}
