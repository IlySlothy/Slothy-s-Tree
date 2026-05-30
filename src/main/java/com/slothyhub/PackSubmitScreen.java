package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.InputCompat;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Submit a library pack for moderator review and public catalog listing. */
public class PackSubmitScreen extends class_437 {

    private static final int FOOTER = 36;
    private static final int FIELD_H = 22;
    private static final int ROW_GAP = 12;
    private static final String[] TAG_OPTIONS = {"pvp", "cit", "swords", "armor"};

    private enum Focus { NONE, NAME, DESC, CONTACT }

    private final class_437 parent;
    private final Pack pack;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Set<String> selectedTags = new LinkedHashSet<>();

    private String nameText = "";
    private String descText = "";
    private String contactText = "";
    private Focus focus = Focus.NAME;

    private int cardX, cardW;
    private int nameBoxY, descBoxY, contactBoxY, tagsY;

    private String statusMsg = "";
    private int statusColor = Ui.COL_MUTED;
    private boolean submitting = false;
    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    public PackSubmitScreen(class_437 parent, Pack pack) {
        super(class_2561.method_43470("Request Upload"));
        this.parent = parent;
        this.pack = pack;
        this.nameText = pack.getName() != null ? pack.getName() : "";
        selectedTags.add("pvp");
    }

    private void layoutFields() {
        cardW = Math.min(420, field_22789 - 48);
        cardX = (field_22789 - cardW) / 2;
        int y = 68;
        nameBoxY = y + 12;
        y = nameBoxY + FIELD_H + ROW_GAP;
        descBoxY = y + 12;
        y = descBoxY + FIELD_H + ROW_GAP;
        contactBoxY = y + 12;
        tagsY = contactBoxY + FIELD_H + 18;
    }

    @Override
    protected void method_25426() {
        layoutFields();
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        layoutFields();
        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);

        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);
        Ui.drawSubscreenHeader(ctx, field_22793, field_22789, "REQUEST UPLOAD  //  SHARE WITH EVERYONE", delta);

        String sub = "Submit for review — you'll get a Discord DM or ticket.";
        DrawHelper.drawText(ctx, field_22793, sub,
            field_22789 / 2 - field_22793.method_1727(sub) / 2, 52, Ui.COL_MUTED, false);

        int cardTop = 62;
        int cardBot = tagsY + 28;
        ctx.method_25294(cardX - 8, cardTop, cardX + cardW + 8, cardBot, col(Ui.COL_PANEL & 0xFFFFFF, 200));
        ctx.method_25294(cardX - 8, cardTop, cardX + cardW + 8, cardTop + 1, Ui.COL_BORDER);
        ctx.method_25294(cardX - 8, cardBot - 1, cardX + cardW + 8, cardBot, Ui.COL_BORDER);

        drawField(ctx, "Pack name", nameText, "My Custom Pack", nameBoxY, focus == Focus.NAME);
        drawField(ctx, "Description", descText,
            "What's in this pack? Credit sources, PvP tier…", descBoxY, focus == Focus.DESC);
        drawField(ctx, "Contact (optional)", contactText, "Discord username", contactBoxY, focus == Focus.CONTACT);

        DrawHelper.drawText(ctx, field_22793, "Tags", cardX, tagsY - 11, Ui.COL_TEXT, false);
        drawTagChips(ctx, mx, my);

        String author = resolveAuthorName();
        DrawHelper.drawText(ctx, field_22793, "Minecraft: " + author, cardX, cardBot + 8, Ui.COL_MUTED, false);
        DrawHelper.drawText(ctx, field_22793, "Max 8 MB · Review usually takes a few days",
            cardX, cardBot + 20, Ui.COL_MUTED, false);

        if (!statusMsg.isBlank()) {
            int cx = field_22789 / 2;
            DrawHelper.drawText(ctx, field_22793, statusMsg,
                cx - field_22793.method_1727(statusMsg) / 2, field_22790 - FOOTER - 18,
                statusColor, false);
        }

        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790 - FOOTER + 1, Ui.COL_BORDER);
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, Ui.COL_PANEL);

        int cx = field_22789 / 2;
        boolean submitHov = !submitting && mx >= cx - 120 && mx <= cx - 8
            && my >= field_22790 - 28 && my <= field_22790 - 8;
        boolean cancelHov = mx >= cx + 8 && mx <= cx + 80
            && my >= field_22790 - 28 && my <= field_22790 - 8;
        drawBtn(ctx, cx - 120, field_22790 - 28, 112, 20,
            submitting ? "UPLOADING…" : "SUBMIT", submitHov && !submitting, Ui.COL_ACCENT);
        drawBtn(ctx, cx + 8, field_22790 - 28, 72, 20, "BACK", cancelHov, Ui.COL_SURFACE);
    }

    private void drawTagChips(class_332 ctx, int mx, int my) {
        int x = cardX;
        int y = tagsY;
        int gap = 6;
        for (String tag : TAG_OPTIONS) {
            String label = tag.toUpperCase(Locale.ROOT);
            int w = field_22793.method_1727(label) + 14;
            boolean on = selectedTags.contains(tag);
            boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + 18;
            int bg = on ? Ui.COL_ACCENT : (hov ? col(Ui.COL_SURFACE & 0xFFFFFF, 220) : col(Ui.COL_SURFACE & 0xFFFFFF, 160));
            int fg = on ? Ui.COL_BG : Ui.COL_TEXT;
            ctx.method_25294(x, y, x + w, y + 18, bg);
            DrawHelper.drawText(ctx, field_22793, label, x + 7, y + 5, fg, false);
            x += w + gap;
        }
    }

    private boolean hitTag(double mx, double my, String tag) {
        int x = cardX;
        int y = tagsY;
        int gap = 6;
        for (String t : TAG_OPTIONS) {
            String label = t.toUpperCase(Locale.ROOT);
            int w = field_22793.method_1727(label) + 14;
            if (t.equals(tag) && mx >= x && mx <= x + w && my >= y && my <= y + 18) return true;
            x += w + gap;
        }
        return false;
    }

    private static int col(int rgb, int a) {
        return Ui.withAlpha(rgb & 0xFFFFFF, a);
    }

    private void drawField(class_332 ctx, String label, String value, String placeholder,
                         int boxY, boolean focused) {
        DrawHelper.drawText(ctx, field_22793, label, cardX, boxY - 11, Ui.COL_TEXT, false);

        int x = cardX;
        int w = cardW;
        int h = FIELD_H;
        ctx.method_25294(x, boxY, x + w, boxY + h, col(Ui.COL_SURFACE & 0xFFFFFF, focused ? 220 : 160));
        int border = focused ? Ui.COL_ACCENT : Ui.COL_BORDER;
        ctx.method_25294(x, boxY + h - 1, x + w, boxY + h, border);
        if (focused) {
            ctx.method_25294(x, boxY, x + w, boxY + 1, col(Ui.COL_ACCENT & 0xFFFFFF, 180));
        }

        int textX = x + 8;
        int textY = boxY + (h - 9) / 2;
        if (value.isEmpty() && !focused) {
            DrawHelper.drawText(ctx, field_22793, placeholder, textX, textY, Ui.COL_MUTED, false);
        } else {
            String shown = clipToWidth(value, w - 16);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
            DrawHelper.drawText(ctx, field_22793, shown, textX, textY, Ui.COL_TEXT, false);
        }
    }

    private String clipToWidth(String text, int maxPx) {
        if (text.isEmpty()) return text;
        if (field_22793.method_1727(text) <= maxPx) return text;
        while (text.length() > 1 && field_22793.method_1727(text + "…") > maxPx) {
            text = text.substring(1);
        }
        return "…" + text;
    }

    private boolean hitBox(double mx, double my, int boxY) {
        return mx >= cardX && mx <= cardX + cardW && my >= boxY && my <= boxY + FIELD_H;
    }

    public boolean method_25402(double mx, double my, int button) {
        if (button != 0) return false;

        for (String tag : TAG_OPTIONS) {
            if (hitTag(mx, my, tag)) {
                if (selectedTags.contains(tag)) selectedTags.remove(tag);
                else selectedTags.add(tag);
                focus = Focus.NONE;
                return true;
            }
        }

        if (hitBox(mx, my, nameBoxY)) { focus = Focus.NAME; return true; }
        if (hitBox(mx, my, descBoxY)) { focus = Focus.DESC; return true; }
        if (hitBox(mx, my, contactBoxY)) { focus = Focus.CONTACT; return true; }

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

        focus = Focus.NONE;
        return false;
    }

    @Override
    public boolean method_25400(char chr, int modifiers) {
        if (focus == Focus.NONE || submitting) return super.method_25400(chr, modifiers);
        if (chr < 32 || chr == 127) return true;
        appendChar(chr);
        return true;
    }

    @Override
    public boolean method_25404(int key, int scan, int mods) {
        if (focus != Focus.NONE && key == 259) {
            backspaceFocused();
            return true;
        }
        if (key == 256) {
            if (focus != Focus.NONE) { focus = Focus.NONE; return true; }
        }
        if (key == 258 && focus != Focus.NONE) {
            focus = switch (focus) {
                case NAME -> Focus.DESC;
                case DESC -> Focus.CONTACT;
                case CONTACT -> Focus.NAME;
                default -> Focus.NAME;
            };
            return true;
        }
        return super.method_25404(key, scan, mods);
    }

    private void appendChar(char chr) {
        switch (focus) {
            case NAME -> {
                if (nameText.length() < 64) nameText += chr;
            }
            case DESC -> {
                if (descText.length() < 280) descText += chr;
            }
            case CONTACT -> {
                if (contactText.length() < 64) contactText += chr;
            }
            default -> {}
        }
    }

    private void backspaceFocused() {
        switch (focus) {
            case NAME -> {
                if (!nameText.isEmpty()) nameText = nameText.substring(0, nameText.length() - 1);
            }
            case DESC -> {
                if (!descText.isEmpty()) descText = descText.substring(0, descText.length() - 1);
            }
            case CONTACT -> {
                if (!contactText.isEmpty()) contactText = contactText.substring(0, contactText.length() - 1);
            }
            default -> {}
        }
    }

    private void drawBtn(class_332 ctx, int x, int y, int w, int h, String label, boolean hov, int accent) {
        int fill = hov ? accent : Ui.COL_SURFACE;
        int fg = hov ? Ui.COL_BG : Ui.COL_TEXT;
        ctx.method_25294(x, y, x + w, y + h, fill);
        DrawHelper.drawText(ctx, field_22793, label,
            x + (w - field_22793.method_1727(label)) / 2, y + (h - 9) / 2, fg, false);
    }

    private void doSubmit() {
        String name = nameText.trim();
        if (name.isEmpty()) {
            statusMsg = "Enter a pack name.";
            statusColor = Ui.COL_DANGER;
            focus = Focus.NAME;
            return;
        }

        submitting = true;
        focus = Focus.NONE;
        statusMsg = "Uploading pack…";
        statusColor = Ui.COL_MUTED;

        String description = descText.trim();
        String contact = contactText.trim();
        String author = resolveAuthorName();
        List<String> tags = new ArrayList<>(selectedTags);

        executor.submit(() -> {
            try {
                Path zip = BuiltPackLibrary.libraryDir().resolve(pack.getPackFilename());
                byte[] bytes = Files.readAllBytes(zip);
                PackSubmitClient.SubmitRequest req = new PackSubmitClient.SubmitRequest(
                    name, description, author, contact, pack.getId(),
                    SlothyConfig.getVoterId(), tags);
                PackSubmitClient.SubmitResult result =
                    PackSubmitClient.submit(bytes, pack.getPackFilename(), req);
                UploadTracker.remember(result.submissionId(), name, tags);
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
