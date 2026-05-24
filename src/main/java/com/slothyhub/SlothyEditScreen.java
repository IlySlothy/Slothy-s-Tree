package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_437;

/**
 * Pack metadata editor — rename, edit tags, and map custom textures on an applied pack.
 */
public class SlothyEditScreen extends class_437 {

    private final class_437 parent;
    private final String packId;
    private final String folderName;
    private class_342 nameField;
    private String statusMsg = null;
    private long statusAt = 0;

    public SlothyEditScreen(class_437 parent, String packId, String folderName, String currentName) {
        super(class_2561.method_43470("Edit Pack"));
        this.parent = parent;
        this.packId = packId;
        this.folderName = folderName;
    }

    @Override
    protected void method_25426() {
        String current = PackEditStore.getName(packId);
        nameField = new class_342(field_22793, field_22789 / 2 - 100, 90, 200, 20,
            class_2561.method_43470("Pack name"));
        nameField.method_1880(80);
        nameField.method_1858(false);
        nameField.method_1868(Ui.COL_TEXT);
        nameField.method_47404(class_2561.method_43470("Pack display name..."));
        nameField.method_1852(current != null ? current : "");
        method_37063(nameField);
    }

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        Ui.drawSubscreenHeader(ctx, field_22793, field_22789, "EDIT PACK  //  SLOTHYHUB", delta);

        ctx.method_25294(18, 52, field_22789 - 18, 53, Ui.COL_BORDER);
        DrawHelper.drawText(ctx, field_22793, "PACK ID: " + packId.toUpperCase(), 18, 56, Ui.COL_MUTED, false);
        DrawHelper.drawText(ctx, field_22793, "Display Name", 18, 78, Ui.COL_TEXT, false);
        DrawHelper.drawText(ctx, field_22793, "Shown in-game and on the hub screen.", 18, 88, Ui.COL_MUTED, false);
        for (net.minecraft.class_364 child : method_25396()) {
            if (child instanceof net.minecraft.class_4068 d) d.method_25394(ctx, mx, my, delta);
        }

        if (statusMsg != null && System.currentTimeMillis() - statusAt < 3000L) {
            DrawHelper.drawText(ctx, field_22793, statusMsg, field_22789 / 2 - field_22793.method_1727(statusMsg) / 2, 126, Ui.COL_ACCENT, false);
        }

        // Footer
        ctx.method_25294(0, field_22790 - 36, field_22789, field_22790 - 35, Ui.COL_BORDER);
        ctx.method_25294(0, field_22790 - 35, field_22789, field_22790, Ui.COL_PANEL);
        boolean saveHov = mx >= field_22789 / 2 - 56 && mx <= field_22789 / 2 + 56 && my >= field_22790 - 27 && my <= field_22790 - 7;
        drawBtn(ctx, field_22789 / 2 - 56, field_22790 - 27, 112, 20, "SAVE", saveHov);
        boolean closeHov = mx >= field_22789 / 2 + 64 && mx <= field_22789 / 2 + 116 && my >= field_22790 - 27 && my <= field_22790 - 7;
        drawBtn(ctx, field_22789 / 2 + 64, field_22790 - 27, 52, 20, "CANCEL", closeHov);
    }

    private void drawBtn(class_332 ctx, int x, int y, int w, int h, String label, boolean hov) {
        int border = hov ? Ui.COL_ACCENT : Ui.COL_BORDER, fill = hov ? Ui.COL_ACCENT : Ui.COL_BG, fg = hov ? -16777216 : Ui.COL_MUTED;
        ctx.method_25294(x, y, x + w, y + h, fill);
        ctx.method_25294(x, y, x + w, y + 1, border); ctx.method_25294(x, y + h - 1, x + w, y + h, border);
        ctx.method_25294(x, y, x + 1, y + h, border); ctx.method_25294(x + w - 1, y, x + w, y + h, border);
        DrawHelper.drawText(ctx, field_22793, label, x + (w - field_22793.method_1727(label)) / 2, y + (h - 8) / 2, fg, false);
    }

    @Override
    public boolean method_25402(double mx, double my, int button) {
        if (button == 0) {
            if (mx >= field_22789 / 2 - 56 && mx <= field_22789 / 2 + 56 && my >= field_22790 - 27 && my <= field_22790 - 7) {
                save(); return true;
            }
            if (mx >= field_22789 / 2 + 64 && mx <= field_22789 / 2 + 116 && my >= field_22790 - 27 && my <= field_22790 - 7) {
                method_25419(); return true;
            }
        }
        return super.method_25402(mx, my, button);
    }

    private void save() {
        String name = nameField.method_1882().trim();
        if (!name.isEmpty()) PackEditStore.setName(packId, name);
        else PackEditStore.removeName(packId);
        PackEditStore.save();
        statusMsg = "SAVED."; statusAt = System.currentTimeMillis();
        method_25419();
    }

    @Override
    public void method_25419() { net.minecraft.class_310.method_1551().method_1507(parent); }
    @Override
    public boolean method_25421() { return false; }
}
