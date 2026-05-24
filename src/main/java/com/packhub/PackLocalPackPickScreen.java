package com.packhub;

import com.packhub.compat.DrawHelper;
import com.packhub.compat.InputCompat;
import com.packhub.ui.Ui;
import java.util.List;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;

public class PackLocalPackPickScreen extends class_437 {
   private final class_437 parent;
   private List<String> folders = List.of();
   private float scroll;
   private float scrollTarget;
   private static final int ROW = 28;

   public PackLocalPackPickScreen(class_437 parent) {
      super(class_2561.method_43470("Local packs"));
      this.parent = parent;
   }

   protected void method_25426() {
      super.method_25426();
      this.folders = PackDownloader.listLocalPackFolderNames();
      this.scroll = this.scrollTarget = 0.0F;
   }

   private int maxScr() {
      int view = this.field_22790 - 120;
      int total = this.folders.size() * 28;
      return Math.max(0, total - view);
   }

   public boolean onScrollDelta(double d) {
      this.scrollTarget = Math.max(0.0F, Math.min((float)this.maxScr(), this.scrollTarget - (float)d * 24.0F));
      return true;
   }

   public boolean method_25401(double mx, double my, double hScroll, double vScroll) {
      return this.onScrollDelta(vScroll);
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      this.scroll = this.scroll + (this.scrollTarget - this.scroll) * Math.min(1.0F, delta * 0.35F);
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, -16119286);
      ctx.method_25294(0, 0, this.field_22789, 44, -15461356);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "<", 14, 16, mx >= 8 && mx <= 40 && my >= 10 && my <= 32 ? -8470748 : -7697782);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "LOCAL RESOURCE PACKS", 36, 14, -1);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Choose a folder from resourcepacks/ (needs pack.mcmeta)", 36, 28, -7697782);
      int top = 56;
      int bot = this.field_22790 - 20;

      for (int i = 0; i < this.folders.size(); i++) {
         int y = top + i * 28 - (int)this.scroll;
         if (y + 28 >= top && y <= bot) {
            String name = this.folders.get(i);
            boolean h = mx >= 16 && mx <= this.field_22789 - 16 && my >= y && my <= y + 28 - 2 && my >= top && my <= bot;
            ctx.method_25294(16, y, this.field_22789 - 16, y + 28 - 2, h ? -10968080 : -15066598);
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, name, 24, y + 8, -1);
         }
      }

      if (this.folders.isEmpty()) {
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "No pack folders found.", 24, top + 12, -7697782);
      }
   }

   public boolean method_25402(double mx, double my, int button) {
      if (button == 0 && mx >= 8.0 && mx <= 40.0 && my >= 10.0 && my <= 32.0) {
         class_310.method_1551().method_1507(this.parent);
         return true;
      } else {
         if (button == 0) {
            int top = 56;
            int bot = this.field_22790 - 20;
            if (my >= (double)top && my <= (double)bot) {
               int idx = (int)((my - (double)top + (double)this.scroll) / 28.0);
               if (idx >= 0 && idx < this.folders.size()) {
                  String folder = this.folders.get(idx);
                  class_310.method_1551().method_1507(new PackEditScreen(this, null, folder, folder));
                  Ui.playClick();
                  return true;
               }
            }
         }

         return InputCompat.delegateToChildren(this, mx, my, button);
      }
   }

   public boolean method_25421() {
      return false;
   }
}
