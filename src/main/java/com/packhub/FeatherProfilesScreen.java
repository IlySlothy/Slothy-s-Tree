package com.packhub;

import net.minecraft.class_332;
import net.minecraft.class_437;

public class FeatherProfilesScreen extends FeatherProfilesScreenBase {
   public FeatherProfilesScreen(class_437 parent) {
      super(parent);
   }

   public void method_25420(class_332 ctx, int mx, int my, float delta) {
   }

   public boolean method_25401(double mx, double my, double hScroll, double vScroll) {
      return this.onScrollDelta(vScroll);
   }
}
