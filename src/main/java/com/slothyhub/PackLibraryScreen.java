package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.ui.CustomButton;
import com.slothyhub.ui.CustomButtonBase;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_437;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shows custom packs built with the Texture Builder. */
public class PackLibraryScreen extends class_437 {

    private static final int HEADER = 52;
    private static final int FOOTER = 44;
    private static final int CARD_H = 72;
    private static final int PAD = 16;

    private static final int BG = Ui.COL_BG;
    private static final int PANEL = Ui.COL_PANEL;
    private static final int SURFACE = Ui.COL_SURFACE;
    private static final int ACCENT = Ui.COL_ACCENT;
    private static final int DANGER = Ui.COL_DANGER;
    private static final int TEXT = Ui.COL_TEXT;
    private static final int MUTED = Ui.COL_MUTED;
    private static final int BORDER = Ui.COL_BORDER;

    private static int col(int rgb, int a) { return Ui.withAlpha(rgb & 0xFFFFFF, a); }

    private final class_437 parent;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private List<Pack> packs = new ArrayList<>();
    private final Map<String, String> status = new HashMap<>();
    private double scroll = 0, scrollTarget = 0;

    public PackLibraryScreen(class_437 parent) {
        super(class_2561.method_43470("My Pack Library"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
        int bw = 108, bh = 26;
        int bx = field_22789 / 2 - bw / 2;
        int by = field_22790 - FOOTER + (FOOTER - bh) / 2;
        method_37063(new CustomButton(bx, by, bw, bh,
            class_2561.method_43470("BACK"), CustomButtonBase.Style.SECONDARY, this::method_25419));
        refresh();
    }

    private void refresh() {
        packs = BuiltPackLibrary.listPacks();
        scroll = scrollTarget = 0;
    }

    @Override
    public void method_25419() {
        executor.shutdownNow();
        class_310.method_1551().method_1507(parent);
    }

    @Override
    public boolean method_25421() { return false; }

    public boolean onScrollDelta(double vDelta) {
        int listH = field_22790 - HEADER - FOOTER;
        int totalH = Math.max(0, packs.size()) * CARD_H;
        scrollTarget = Math.max(0, Math.min(scrollTarget - vDelta * 24, Math.max(0, totalH - listH)));
        return true;
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        scroll += (scrollTarget - scroll) * Math.min(1f, delta * 0.28f);
        float phase = (float)(System.currentTimeMillis() % 4000L) / 4000f;

        ctx.method_25294(0, 0, field_22789, field_22790, BG);
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);

        ctx.method_25294(0, 0, field_22789, HEADER, PANEL);
        ctx.method_25294(0, 0, field_22789, 2, ACCENT);
        ctx.method_25294(0, HEADER - 1, field_22789, HEADER, BORDER);
        Ui.drawSlothBadge(ctx, field_22793, PAD, (HEADER - 14) / 2, phase);
        DrawHelper.drawText(ctx, field_22793, "MY PACK LIBRARY", PAD + 22, (HEADER - 9) / 2, ACCENT, false);
        DrawHelper.drawText(ctx, field_22793, packs.size() + " pack(s)",
            field_22789 - PAD - field_22793.method_1727(packs.size() + " pack(s)"),
            (HEADER - 9) / 2, MUTED, false);

        int top = HEADER, bot = field_22790 - FOOTER;
        ctx.method_44379(0, top, field_22789, bot);

        if (packs.isEmpty()) {
            String msg = "No custom packs yet";
            String hint = "Build one in TEXTURES → Texture Builder";
            int cy = (top + bot) / 2;
            Ui.drawPawPrint(ctx, field_22789 / 2, cy - 24, col(ACCENT & 0xFFFFFF, 80), 1.4f);
            DrawHelper.drawText(ctx, field_22793, msg,
                field_22789 / 2 - field_22793.method_1727(msg) / 2, cy, MUTED, false);
            DrawHelper.drawText(ctx, field_22793, hint,
                field_22789 / 2 - field_22793.method_1727(hint) / 2, cy + 14,
                col(MUTED & 0xFFFFFF, 160), false);
        } else {
            int cardW = Math.min(field_22789 - PAD * 2, 720);
            int cardX = (field_22789 - cardW) / 2;
            int y = top - (int) scroll;
            for (Pack pack : packs) {
                if (y + CARD_H < top) { y += CARD_H; continue; }
                if (y > bot) break;
                drawCard(ctx, pack, cardX, y, cardW, mx, my);
                y += CARD_H;
            }
            drawScrollbar(ctx, top, bot, packs.size() * CARD_H);
        }
        ctx.method_44380();

        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, PANEL);
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790 - FOOTER + 1, BORDER);

        for (class_364 w : method_25396()) {
            if (w instanceof class_4068 d) d.method_25394(ctx, mx, my, delta);
        }
    }

    private void drawCard(class_332 ctx, Pack pack, int x, int y, int w, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + CARD_H;
        ctx.method_25294(x, y, x + w, y + CARD_H, hov ? col(SURFACE & 0xFFFFFF, 200) : col(PANEL & 0xFFFFFF, 160));
        ctx.method_25294(x, y, x + 3, y + CARD_H, ACCENT);
        Ui.drawPawPrint(ctx, x + 28, y + CARD_H / 2, col(ACCENT & 0xFFFFFF, hov ? 180 : 100), 0.9f);

        DrawHelper.drawText(ctx, field_22793, pack.getName(), x + 48, y + 16, TEXT, false);
        DrawHelper.drawText(ctx, field_22793, "Built with Texture Builder  ·  " + pack.getPackFilename(),
            x + 48, y + 32, MUTED, false);

        String st = status.get(pack.getId());
        if (st != null) {
            DrawHelper.drawText(ctx, field_22793, st, x + 48, y + 48,
                st.toLowerCase(Locale.ROOT).contains("fail") ? DANGER : ACCENT, false);
        }

        int btnW = 72, btnH = 22;
        int applyX = x + w - btnW - PAD - btnW - 8;
        int delX = x + w - btnW - PAD;
        int btnY = y + (CARD_H - btnH) / 2;
        drawMiniBtn(ctx, applyX, btnY, btnW, btnH, "APPLY", ACCENT, mx, my);
        drawMiniBtn(ctx, delX, btnY, btnW, btnH, "DELETE", DANGER, mx, my);
    }

    private void drawMiniBtn(class_332 ctx, int x, int y, int w, int h, String label, int color, int mx, int my) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        ctx.method_25294(x, y, x + w, y + h, hov ? col(color & 0xFFFFFF, 220) : col(SURFACE & 0xFFFFFF, 180));
        DrawHelper.drawText(ctx, field_22793, label,
            x + (w - field_22793.method_1727(label)) / 2, y + (h - 9) / 2,
            hov ? BG : color, false);
    }

    private void drawScrollbar(class_332 ctx, int top, int bot, int totalH) {
        int listH = bot - top;
        if (totalH <= listH) return;
        int trkX = field_22789 - 6, trkY = top + 8, trkH = listH - 16;
        int thumbH = Math.max(24, trkH * listH / totalH);
        int thumbY = trkY + (int)((double)(trkH - thumbH) * scroll / Math.max(1, totalH - listH));
        ctx.method_25294(trkX, trkY, trkX + 3, trkY + trkH, col(BORDER & 0xFFFFFF, 120));
        ctx.method_25294(trkX, thumbY, trkX + 3, thumbY + thumbH, col(ACCENT & 0xFFFFFF, 200));
    }

    @Override
    public boolean method_25402(double mx, double my, int button) {
        if (button != 0) return super.method_25402(mx, my, button);
        if (super.method_25402(mx, my, button)) return true;

        int top = HEADER, bot = field_22790 - FOOTER;
        int cardW = Math.min(field_22789 - PAD * 2, 720);
        int cardX = (field_22789 - cardW) / 2;
        int y = top - (int) scroll;
        for (Pack pack : packs) {
            if (y + CARD_H < top) { y += CARD_H; continue; }
            if (y > bot) break;
            int btnW = 72, btnH = 22;
            int applyX = cardX + cardW - btnW - PAD - btnW - 8;
            int delX = cardX + cardW - btnW - PAD;
            int btnY = y + (CARD_H - btnH) / 2;
            if (mx >= applyX && mx <= applyX + btnW && my >= btnY && my <= btnY + btnH) {
                applyPack(pack); return true;
            }
            if (mx >= delX && mx <= delX + btnW && my >= btnY && my <= btnY + btnH) {
                deletePack(pack); return true;
            }
            y += CARD_H;
        }
        return false;
    }

    private void applyPack(Pack pack) {
        status.put(pack.getId(), "Applying…");
        executor.submit(() -> {
            try {
                Path zip = BuiltPackLibrary.libraryDir().resolve(pack.getPackFilename());
                byte[] bytes = Files.readAllBytes(zip);
                PackDownloader.applyBuiltPack(bytes, pack.getName());
                class_310.method_1551().execute(() -> status.put(pack.getId(), "Applied to resource packs"));
            } catch (Exception e) {
                class_310.method_1551().execute(() ->
                    status.put(pack.getId(), "Failed: " + e.getMessage()));
            }
        });
    }

    private void deletePack(Pack pack) {
        try {
            BuiltPackLibrary.deletePack(pack);
            status.remove(pack.getId());
            refresh();
        } catch (Exception e) {
            status.put(pack.getId(), "Delete failed");
        }
    }

    public boolean method_25403(double mx, double my, double hd, double vd) { return onScrollDelta(vd); }
    public boolean method_25401(double mx, double my, double vd) { return onScrollDelta(vd); }
}
