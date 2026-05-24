package com.packhub.ui;

import net.minecraft.class_2561;
import net.minecraft.class_332;

public class CustomButton extends CustomButtonBase {
   public CustomButton(int x, int y, int w, int h, class_2561 label, CustomButtonBase.Style style, Runnable onPress) {
      super(x, y, w, h, label, style, onPress);
   }

   public CustomButton(int x, int y, int w, int h, class_2561 label, Runnable onPress) {
      this(x, y, w, h, label, CustomButtonBase.Style.SECONDARY, onPress);
   }

   protected void method_48579(class_332 ctx, int mx, int my, float delta) {
      this.drawButton(ctx, mx, my, delta);
   }
}
