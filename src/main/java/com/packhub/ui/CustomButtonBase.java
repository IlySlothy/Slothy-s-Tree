package com.packhub.ui;

import com.packhub.compat.DrawHelper;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_339;
import net.minecraft.class_6382;

public abstract class CustomButtonBase extends class_339 {
   private final Runnable onPress;
   private final CustomButtonBase.Style style;
   private float hoverT = 0.0F;
   private float pressT = 0.0F;

   public CustomButtonBase(int x, int y, int w, int h, class_2561 label, CustomButtonBase.Style style, Runnable onPress) {
      super(x, y, w, h, label);
      this.onPress = onPress;
      this.style = style;
   }

   public void method_25348(double mouseX, double mouseY) {
      this.doPress();
   }

   public boolean tryPress(double mx, double my) {
      if (this.field_22763
         && this.field_22764
         && mx >= (double)this.method_46426()
         && mx <= (double)(this.method_46426() + this.method_25368())
         && my >= (double)this.method_46427()
         && my <= (double)(this.method_46427() + this.method_25364())) {
         this.doPress();
         return true;
      } else {
         return false;
      }
   }

   private void doPress() {
      this.pressT = 1.0F;
      Ui.playClick();
      if (this.onPress != null) {
         this.onPress.run();
      }
   }

   protected void drawButton(class_332 ctx, int mx, int my, float delta) {
      float hoverTarget = this.method_49606() ? 1.0F : 0.0F;
      this.hoverT = this.hoverT + (hoverTarget - this.hoverT) * Math.min(1.0F, delta * 0.35F);
      this.pressT = Math.max(0.0F, this.pressT - delta * 0.12F);
      int x = this.method_46426();
      int y = this.method_46427();
      int w = this.method_25368();
      int h = this.method_25364();
      float scale = 1.0F - 0.04F * this.pressT;
      boolean animPress = this.pressT > 0.01F;
      if (animPress) {
         DrawHelper.pushMatrices(ctx);
         DrawHelper.translateMatrices(ctx, (float)x + (float)w / 2.0F, (float)y + (float)h / 2.0F, 0.0F);
         DrawHelper.scaleMatrices(ctx, scale, scale, 1.0F);
         DrawHelper.translateMatrices(ctx, -((float)x + (float)w / 2.0F), -((float)y + (float)h / 2.0F), 0.0F);
      }

      int bg;
      int fg;
      int border;
      switch (this.style) {
         case PRIMARY:
            bg = this.hoverT > 0.05F ? Ui.lerpColor(-16119286, -1, this.hoverT) : -16119286;
            fg = this.hoverT > 0.05F ? Ui.lerpColor(-1, -16777216, this.hoverT) : -1;
            border = this.hoverT > 0.05F ? -1 : Ui.lerpColor(-12961222, -1, this.hoverT);
            break;
         case DANGER:
            bg = this.hoverT > 0.05F ? Ui.lerpColor(-16119286, -42663, this.hoverT) : -16119286;
            fg = this.hoverT > 0.05F ? Ui.lerpColor(-42663, -16777216, this.hoverT) : -42663;
            border = -42663;
            break;
         default:
            bg = -16119286;
            fg = Ui.lerpColor(-7697782, -1, this.hoverT);
            border = Ui.lerpColor(-14737633, -1, this.hoverT);
      }

      ctx.method_25294(x, y, x + w, y + h, bg);
      ctx.method_25294(x, y, x + w, y + 1, border);
      ctx.method_25294(x, y + h - 1, x + w, y + h, border);
      ctx.method_25294(x, y, x + 1, y + h, border);
      ctx.method_25294(x + w - 1, y, x + w, y + h, border);
      class_327 tr = class_310.method_1551().field_1772;
      DrawHelper.drawTextWithShadow(ctx, tr, this.method_25369(), x + (w - tr.method_27525(this.method_25369())) / 2, y + (h - 8) / 2, fg);
      if (animPress) {
         DrawHelper.popMatrices(ctx);
      }
   }

   protected void method_47399(class_6382 b) {
      this.method_37021(b);
   }

   public static enum Style {
      PRIMARY,
      SECONDARY,
      DANGER;

      private Style() {
      }
   }
}
