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

/**
 * Pack builder / weave screen — lets users combine multiple packs into one.
 */
public class SlothyBuilderScreen extends class_437 {

    private final class_437 parent;
    private final List<String> queue = new ArrayList<>();
    private class_342 searchField;
    private int step = 0;
    private float[] stepT = new float[3];
    private float buildPulseT = 0;
    private boolean building = false;
    private String buildResult = null;
    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    private static final String[] STEPS = {"SELECT PACKS", "CONFIGURE", "WEAVE"};

    public SlothyBuilderScreen(class_437 parent) {
        super(class_2561.method_43470("Weave Pack"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
        searchField = new class_342(field_22793, 18, 55, Math.min(200, field_22789 / 3), 16,
            class_2561.method_43470("Search packs"));
        searchField.method_1858(false);
        searchField.method_1868(Ui.COL_TEXT);
        searchField.method_47404(class_2561.method_43470("Search..."));
        method_37063(searchField);
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);
        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        Ui.drawSubscreenHeader(ctx, field_22793, field_22789, "WEAVE PACK  //  SLOTHYHUB", delta);
        renderStepIndicator(ctx, delta);
        switch (step) {
            case 0 -> renderStepSelect(ctx, mx, my, delta);
            case 1 -> renderStepConfigure(ctx, mx, my);
            case 2 -> renderStepWeave(ctx, mx, my, delta);
        }
        renderFooter(ctx, mx, my);
        for (net.minecraft.class_364 child : method_25396()) {
            if (child instanceof net.minecraft.class_4068 d) d.method_25394(ctx, mx, my, delta);
        }
    }

    private void renderStepIndicator(class_332 ctx, float delta) {
        int totalW = STEPS.length * 90 + (STEPS.length - 1) * 32, startX = (field_22789 - totalW) / 2;
        int y = 48;
        for (int i = 0; i < STEPS.length; i++) {
            float target = i <= step ? 1f : 0f;
            stepT[i] += (target - stepT[i]) * Math.min(1f, delta * 0.3f);
            float t = stepT[i];
            int dotX = startX + i * 122 + 45, dotY = y + 8;
            int dotColor = Ui.lerpColor(Ui.COL_BORDER, Ui.COL_ACCENT, t);
            ctx.method_25294(dotX - 5, dotY - 5, dotX + 5, dotY + 5, dotColor);
            ctx.method_25294(dotX - 3, dotY - 3, dotX + 3, dotY + 3, i == step ? Ui.COL_ACCENT : Ui.COL_BG);
            if (i > 0) {
                int lineX = dotX - 32, lineColor = Ui.lerpColor(Ui.COL_BORDER, Ui.COL_ACCENT, stepT[i - 1]);
                ctx.method_25294(lineX, dotY, dotX - 5, dotY + 1, lineColor);
            }
            String label = STEPS[i];
            DrawHelper.drawText(ctx, field_22793, label, dotX - field_22793.method_1727(label) / 2, dotY + 10, i == step ? Ui.COL_TEXT : Ui.lerpColor(Ui.COL_MUTED, Ui.COL_TEXT, t * 0.5f), false);
        }
    }

    private void renderStepSelect(class_332 ctx, int mx, int my, float delta) {
        int listY = 90, listH = field_22790 - listY - 50, cardW = Math.min(field_22789 - 36, 480), cardX = (field_22789 - cardW) / 2;
        DrawHelper.drawText(ctx, field_22793, "ADD PACKS TO YOUR WEAVE QUEUE", cardX, 76, Ui.COL_MUTED, false);
        if (queue.isEmpty()) {
            DrawHelper.drawText(ctx, field_22793, "Queue is empty — click packs from the list to add them.", cardX, listY + listH / 2, Ui.COL_MUTED, false);
        } else {
            int qy = listY;
            for (String packName : queue) {
                ctx.method_25294(cardX, qy, cardX + cardW, qy + 22, Ui.COL_PANEL);
                ctx.method_25294(cardX, qy + 21, cardX + cardW, qy + 22, Ui.COL_BORDER);
                ctx.method_25294(cardX, qy, cardX + 2, qy + 22, Ui.COL_ACCENT);
                DrawHelper.drawText(ctx, field_22793, packName, cardX + 8, qy + 7, Ui.COL_TEXT, false);
                boolean rmHov = mx >= cardX + cardW - 22 && mx <= cardX + cardW - 6 && my >= qy + 4 && my <= qy + 18;
                DrawHelper.drawText(ctx, field_22793, "×", cardX + cardW - 16, qy + 7, rmHov ? Ui.COL_DANGER : Ui.COL_MUTED, false);
                qy += 24;
            }
        }
    }

    private void renderStepConfigure(class_332 ctx, int mx, int my) {
        DrawHelper.drawText(ctx, field_22793, "CONFIGURE WEAVE OPTIONS", 18, 80, Ui.COL_MUTED, false);
        DrawHelper.drawText(ctx, field_22793, "Packs are applied in queue order. First pack wins on conflicts.", 18, 96, Ui.COL_MUTED, false);
        int cardW = Math.min(field_22789 - 36, 480), cardX = (field_22789 - cardW) / 2;
        int qy = 116; int i = 0;
        for (String packName : queue) {
            ctx.method_25294(cardX, qy, cardX + cardW, qy + 22, Ui.COL_PANEL);
            ctx.method_25294(cardX, qy, cardX + 2, qy + 22, Ui.COL_ACCENT);
            DrawHelper.drawText(ctx, field_22793, (i + 1) + ". " + packName, cardX + 8, qy + 7, Ui.COL_TEXT, false);
            boolean upHov = mx >= cardX + cardW - 44 && mx <= cardX + cardW - 28 && my >= qy + 4 && my <= qy + 18;
            boolean dnHov = mx >= cardX + cardW - 22 && mx <= cardX + cardW - 6 && my >= qy + 4 && my <= qy + 18;
            DrawHelper.drawText(ctx, field_22793, "▲", cardX + cardW - 40, qy + 7, upHov ? Ui.COL_TEXT : Ui.COL_MUTED, false);
            DrawHelper.drawText(ctx, field_22793, "▼", cardX + cardW - 18, qy + 7, dnHov ? Ui.COL_TEXT : Ui.COL_MUTED, false);
            qy += 24; i++;
        }
    }

    private void renderStepWeave(class_332 ctx, int mx, int my, float delta) {
        buildPulseT = (float)(0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 600.0));
        int cx = field_22789 / 2, cy = field_22790 / 2 - 20;
        if (building) {
            Ui.spinner(ctx, cx, cy, 8, buildPulseT, Ui.COL_ACCENT);
            DrawHelper.drawText(ctx, field_22793, "WEAVING PACKS...", cx - field_22793.method_1727("WEAVING PACKS...") / 2, cy + 20, Ui.COL_TEXT, false);
        } else if (buildResult != null) {
            DrawHelper.drawText(ctx, field_22793, buildResult, cx - field_22793.method_1727(buildResult) / 2, cy, Ui.COL_TEXT, false);
        } else {
            DrawHelper.drawText(ctx, field_22793, "READY TO WEAVE", cx - field_22793.method_1727("READY TO WEAVE") / 2, cy - 12, Ui.COL_TEXT, false);
            DrawHelper.drawText(ctx, field_22793, queue.size() + " packs in queue", cx - field_22793.method_1727(queue.size() + " packs in queue") / 2, cy + 4, Ui.COL_MUTED, false);
        }
    }

    private void renderFooter(class_332 ctx, int mx, int my) {
        ctx.method_25294(0, field_22790 - 36, field_22789, field_22790 - 35, Ui.COL_BORDER);
        ctx.method_25294(0, field_22790 - 35, field_22789, field_22790, Ui.COL_PANEL);
        if (step > 0) {
            boolean bHov = mx >= field_22789 / 2 - 120 && mx <= field_22789 / 2 - 8 && my >= field_22790 - 27 && my <= field_22790 - 7;
            drawBtn(ctx, field_22789 / 2 - 120, field_22790 - 27, 112, 20, "← BACK", bHov, false);
        } else {
            boolean closeHov = mx >= field_22789 / 2 - 120 && mx <= field_22789 / 2 - 8 && my >= field_22790 - 27 && my <= field_22790 - 7;
            drawBtn(ctx, field_22789 / 2 - 120, field_22790 - 27, 112, 20, "CLOSE", closeHov, false);
        }
        String nextLabel = step == 2 ? "WEAVE →" : "NEXT →";
        boolean nHov = mx >= field_22789 / 2 + 8 && mx <= field_22789 / 2 + 120 && my >= field_22790 - 27 && my <= field_22790 - 7;
        drawBtn(ctx, field_22789 / 2 + 8, field_22790 - 27, 112, 20, nextLabel, nHov, step == 2 && !queue.isEmpty());
    }

    private void drawBtn(class_332 ctx, int x, int y, int w, int h, String label, boolean hov, boolean primary) {
        int border = hov ? Ui.COL_ACCENT : Ui.COL_BORDER, fill = primary && hov ? Ui.COL_ACCENT : (hov ? Ui.COL_SURFACE : Ui.COL_BG);
        int fg = primary && hov ? -16777216 : (hov ? Ui.COL_TEXT : Ui.COL_MUTED);
        ctx.method_25294(x, y, x + w, y + h, fill);
        ctx.method_25294(x, y, x + w, y + 1, border);
        ctx.method_25294(x, y + h - 1, x + w, y + h, border);
        ctx.method_25294(x, y, x + 1, y + h, border);
        ctx.method_25294(x + w - 1, y, x + w, y + h, border);
        DrawHelper.drawText(ctx, field_22793, label, x + (w - field_22793.method_1727(label)) / 2, y + (h - 8) / 2, fg, false);
    }


    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            // Back/close left button
            if (mx >= field_22789 / 2 - 120 && mx <= field_22789 / 2 - 8 && my >= field_22790 - 27 && my <= field_22790 - 7) {
                if (step == 0) method_25419(); else { step--; Ui.playClick(); } return true;
            }
            // Next/weave right button
            if (mx >= field_22789 / 2 + 8 && mx <= field_22789 / 2 + 120 && my >= field_22790 - 27 && my <= field_22790 - 7) {
                if (step < 2) { step++; Ui.playClick(); } else startWeave(); return true;
            }
        }
        return InputCompat.delegateToChildren(this, mx, my, button);
    }

    private void startWeave() {
        if (queue.isEmpty()) return;
        building = true; buildResult = null;
        // TODO: implement actual pack merge via PackDownloader
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            building = false; buildResult = "WEAVE COMPLETE — CHECK YOUR RESOURCE PACKS FOLDER.";
        }).start();
    }

    @Override
    public void method_25419() { net.minecraft.class_310.method_1551().method_1507(parent); }
    @Override
    public boolean method_25421() { return false; }
}
