package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.InputCompat;
import com.slothyhub.ui.CustomButton;
import com.slothyhub.ui.CustomButtonBase;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_437;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Pending / approved / denied pack upload submissions. */
public class UploadDashboardScreen extends class_437 {

    private static final int HEADER = 52;
    private static final int FOOTER = 44;
    private static final int ROW_H = 56;
    private static final int PAD = 16;

    private final class_437 parent;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    private List<UploadTracker.Entry> entries = new ArrayList<>();
    private String statusLine = "Loading…";
    private int statusColor = Ui.COL_MUTED;
    private double scroll, scrollTarget;

    public UploadDashboardScreen(class_437 parent) {
        super(class_2561.method_43470("My Uploads"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
        int bw = 108, bh = 26;
        method_37063(new CustomButton(field_22789 / 2 - bw / 2, field_22790 - FOOTER + 8, bw, bh,
            class_2561.method_43470("BACK"), CustomButtonBase.Style.SECONDARY, this::method_25419));
        refresh();
    }

    private void refresh() {
        statusLine = "Loading…";
        statusColor = Ui.COL_MUTED;
        executor.submit(() -> {
            List<UploadTracker.Entry> merged;
            try {
                merged = UploadTracker.mergeRemote(
                    PackSubmitClient.fetchSubmissions(SlothyConfig.getVoterId()));
                SlothyConfig.saveUploadEntries(merged);
            } catch (Exception e) {
                merged = UploadTracker.loadLocal();
            }
            List<UploadTracker.Entry> finalMerged = merged;
            class_310.method_1551().execute(() -> {
                entries = finalMerged;
                statusLine = entries.isEmpty()
                    ? "No uploads yet — use UPLOAD in My Pack Library"
                    : entries.size() + " submission(s)";
                statusColor = Ui.COL_MUTED;
            });
        });
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
        int totalH = entries.size() * ROW_H;
        scrollTarget = Math.max(0, Math.min(scrollTarget - vDelta * 24, Math.max(0, totalH - listH)));
        return true;
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);
        scroll += (scrollTarget - scroll) * Math.min(1f, delta * 0.28f);

        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);

        ctx.method_25294(0, 0, field_22789, HEADER, Ui.COL_PANEL);
        ctx.method_25294(0, 0, field_22789, 2, Ui.COL_ACCENT);
        DrawHelper.drawText(ctx, field_22793, "MY UPLOADS", PAD, (HEADER - 9) / 2, Ui.COL_ACCENT, false);
        DrawHelper.drawText(ctx, field_22793, statusLine,
            field_22789 - PAD - field_22793.method_1727(statusLine), (HEADER - 9) / 2, statusColor, false);

        int top = HEADER, bot = field_22790 - FOOTER;
        ctx.method_44379(0, top, field_22789, bot);

        if (entries.isEmpty()) {
            String msg = "Nothing here yet";
            DrawHelper.drawText(ctx, field_22793, msg,
                field_22789 / 2 - field_22793.method_1727(msg) / 2, (top + bot) / 2, Ui.COL_MUTED, false);
        } else {
            int cardW = Math.min(field_22789 - PAD * 2, 720);
            int cardX = (field_22789 - cardW) / 2;
            int y = top - (int) scroll;
            for (UploadTracker.Entry e : entries) {
                if (y + ROW_H < top) { y += ROW_H; continue; }
                if (y > bot) break;
                drawRow(ctx, e, cardX, y, cardW);
                y += ROW_H;
            }
        }
        ctx.method_44380();

        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, Ui.COL_PANEL);
        for (class_364 w : method_25396()) {
            if (w instanceof class_4068 d) d.method_25394(ctx, mx, my, delta);
        }
    }

    private void drawRow(class_332 ctx, UploadTracker.Entry e, int x, int y, int w) {
        ctx.method_25294(x, y, x + w, y + ROW_H - 4, col(Ui.COL_PANEL & 0xFFFFFF, 180));
        ctx.method_25294(x, y, x + 3, y + ROW_H - 4, UploadTracker.statusColor(e.status()));

        String label = UploadTracker.statusLabel(e.status());
        int badgeW = field_22793.method_1727(label) + 8;
        ctx.method_25294(x + 12, y + 10, x + 12 + badgeW, y + 22, col(UploadTracker.statusColor(e.status()) & 0xFFFFFF, 40));
        DrawHelper.drawText(ctx, field_22793, label, x + 16, y + 12, UploadTracker.statusColor(e.status()), false);

        DrawHelper.drawText(ctx, field_22793, e.packName(), x + 12 + badgeW + 8, y + 12, Ui.COL_TEXT, false);
        DrawHelper.drawText(ctx, field_22793, "ID: " + e.submissionId(), x + 12, y + 28, Ui.COL_MUTED, false);

        if (!e.tags().isEmpty()) {
            String tags = String.join(" · ", e.tags()).toUpperCase(Locale.ROOT);
            DrawHelper.drawText(ctx, field_22793, tags,
                x + 12 + field_22793.method_1727("ID: " + e.submissionId()) + 12, y + 28,
                col(Ui.COL_MUTED & 0xFFFFFF, 200), false);
        }

        if ("denied".equalsIgnoreCase(e.status()) && e.denyReason() != null && !e.denyReason().isBlank()) {
            String reason = e.denyReason();
            if (field_22793.method_1727(reason) > w - 24) reason = reason.substring(0, Math.min(40, reason.length())) + "…";
            DrawHelper.drawText(ctx, field_22793, reason, x + 12, y + 40, Ui.COL_DANGER, false);
        } else if ("approved".equalsIgnoreCase(e.status()) && e.catalogId() != null && !e.catalogId().isBlank()) {
            DrawHelper.drawText(ctx, field_22793, "Live as " + e.catalogId(), x + 12, y + 40, Ui.COL_ACCENT, false);
        }
    }

    private static int col(int rgb, int a) {
        return Ui.withAlpha(rgb & 0xFFFFFF, a);
    }

    public boolean method_25403(double mx, double my, double hd, double vd) { return onScrollDelta(vd); }
    public boolean method_25401(double mx, double my, double vd) { return onScrollDelta(vd); }
}
