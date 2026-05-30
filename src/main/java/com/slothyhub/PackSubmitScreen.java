package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.InputCompat;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_437;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Submit a library pack for moderator review and public catalog listing. */
public class PackSubmitScreen extends class_437 {

    private static final int FOOTER = 36;

    private final class_437 parent;
    private final Pack pack;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private class_342 nameField;
    private class_342 descField;
    private class_342 contactField;
    private String statusMsg = "";
    private int statusColor = Ui.COL_MUTED;
    private boolean submitting = false;
    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    public PackSubmitScreen(class_437 parent, Pack pack) {
        super(class_2561.method_43470("Request Upload"));
        this.parent = parent;
        this.pack = pack;
    }

    @Override
    protected void method_25426() {
        int cx = field_22789 / 2;
        int fieldW = Math.min(340, field_22789 - 36);

        nameField = new class_342(field_22793, cx - fieldW / 2, 88, fieldW, 18,
            class_2561.method_43470("Pack name"));
        nameField.method_1880(64);
        nameField.method_1858(false);
        nameField.method_1868(Ui.COL_TEXT);
        nameField.method_47404(class_2561.method_43470("Public pack name"));
        nameField.method_1852(pack.getName());
        method_37063(nameField);

        descField = new class_342(field_22793, cx - fieldW / 2, 138, fieldW, 18,
            class_2561.method_43470("Description"));
        descField.method_1880(280);
        descField.method_1858(false);
        descField.method_1868(Ui.COL_TEXT);
        descField.method_47404(class_2561.method_43470("What's in this pack? Credit sources, PvP tier, etc."));
        method_37063(descField);

        contactField = new class_342(field_22793, cx - fieldW / 2, 188, fieldW, 18,
            class_2561.method_43470("Contact"));
        contactField.method_1880(64);
        contactField.method_1858(false);
        contactField.method_1868(Ui.COL_TEXT);
        contactField.method_47404(class_2561.method_43470("Discord username (optional)"));
        method_37063(contactField);
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);
        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);
        Ui.drawSubscreenHeader(ctx, field_22793, field_22789, "REQUEST UPLOAD  //  SHARE WITH EVERYONE", delta);

        int cx = field_22789 / 2;
        DrawHelper.drawText(ctx, field_22793,
            "Submit your pack for review. You'll get a Discord DM or ticket if configured.",
            cx - field_22793.method_1727("Submit your pack for review. If approved, it will appear in the public pack browser.") / 2,
            56, Ui.COL_MUTED, false);

        drawLabel(ctx, "Pack name", 18, 78);
        drawLabel(ctx, "Description", 18, 128);
        drawLabel(ctx, "Contact (optional)", 18, 178);

        String author = resolveAuthorName();
        DrawHelper.drawText(ctx, field_22793, "Minecraft: " + author, 18, 214, Ui.COL_MUTED, false);
        DrawHelper.drawText(ctx, field_22793, "Max upload size: 8 MB  ·  Review usually takes a few days",
            18, 228, Ui.COL_MUTED, false);

        for (net.minecraft.class_364 child : method_25396()) {
            if (child instanceof net.minecraft.class_4068 d) d.method_25394(ctx, mx, my, delta);
        }

        if (!statusMsg.isBlank()) {
            DrawHelper.drawText(ctx, field_22793, statusMsg,
                cx - field_22793.method_1727(statusMsg) / 2, field_22790 - FOOTER - 18,
                statusColor, false);
        }

        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790 - FOOTER + 1, Ui.COL_BORDER);
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, Ui.COL_PANEL);

        boolean submitHov = !submitting && mx >= cx - 120 && mx <= cx - 8
            && my >= field_22790 - 28 && my <= field_22790 - 8;
        boolean cancelHov = mx >= cx + 8 && mx <= cx + 80
            && my >= field_22790 - 28 && my <= field_22790 - 8;
        drawBtn(ctx, cx - 120, field_22790 - 28, 112, 20,
            submitting ? "UPLOADING…" : "SUBMIT", submitHov && !submitting, Ui.COL_ACCENT);
        drawBtn(ctx, cx + 8, field_22790 - 28, 72, 20, "BACK", cancelHov, Ui.COL_SURFACE);
    }

    private void drawLabel(class_332 ctx, String text, int x, int y) {
        DrawHelper.drawText(ctx, field_22793, text, x, y, Ui.COL_TEXT, false);
    }

    private void drawBtn(class_332 ctx, int x, int y, int w, int h, String label, boolean hov, int accent) {
        int fill = hov ? accent : Ui.COL_SURFACE;
        int fg = hov ? Ui.COL_BG : Ui.COL_TEXT;
        ctx.method_25294(x, y, x + w, y + h, fill);
        DrawHelper.drawText(ctx, field_22793, label,
            x + (w - field_22793.method_1727(label)) / 2, y + (h - 9) / 2, fg, false);
    }

    public boolean method_25402(double mx, double my, int button) {
        if (button != 0) return InputCompat.delegateToChildren(this, mx, my, button);
        if (InputCompat.delegateToChildren(this, mx, my, button)) return true;

        int cx = field_22789 / 2;
        if (!submitting && mx >= cx - 120 && mx <= cx - 8
            && my >= field_22790 - 28 && my <= field_22790 - 8) {
            doSubmit();
            return true;
        }
        if (mx >= cx + 8 && mx <= cx + 80
            && my >= field_22790 - 28 && my <= field_22790 - 8) {
            method_25419();
            return true;
        }
        return false;
    }

    private void doSubmit() {
        String name = nameField.method_1882().trim();
        if (name.isEmpty()) {
            statusMsg = "Enter a pack name.";
            statusColor = Ui.COL_DANGER;
            return;
        }

        submitting = true;
        statusMsg = "Uploading pack…";
        statusColor = Ui.COL_MUTED;

        String description = descField.method_1882().trim();
        String contact = contactField.method_1882().trim();
        String author = resolveAuthorName();

        executor.submit(() -> {
            try {
                Path zip = BuiltPackLibrary.libraryDir().resolve(pack.getPackFilename());
                byte[] bytes = Files.readAllBytes(zip);
                PackSubmitClient.SubmitRequest req = new PackSubmitClient.SubmitRequest(
                    name, description, author, contact, pack.getId(), SlothyConfig.getVoterId());
                PackSubmitClient.SubmitResult result =
                    PackSubmitClient.submit(bytes, pack.getPackFilename(), req);
                class_310.method_1551().execute(() -> {
                    submitting = false;
                    statusMsg = result.message()
                        + (result.submissionId() != null && !result.submissionId().isBlank()
                        ? "  ID: " + result.submissionId() : "");
                    statusColor = Ui.COL_ACCENT;
                });
            } catch (Exception e) {
                class_310.method_1551().execute(() -> {
                    submitting = false;
                    statusMsg = e.getMessage() != null ? e.getMessage() : "Upload failed.";
                    statusColor = Ui.COL_DANGER;
                });
            }
        });
    }

    private static String resolveAuthorName() {
        class_310 mc = class_310.method_1551();
        if (mc != null && mc.method_1548() != null) {
            String name = mc.method_1548().method_1676();
            if (name != null && !name.isBlank()) return name;
        }
        return "Unknown";
    }

    @Override
    public void method_25419() {
        executor.shutdownNow();
        class_310.method_1551().method_1507(parent);
    }

    @Override
    public boolean method_25421() { return false; }
}
