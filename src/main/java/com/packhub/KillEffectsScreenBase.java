package com.packhub;

import com.packhub.compat.DrawHelper;
import com.packhub.compat.InputCompat;
import com.packhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_437;

public abstract class KillEffectsScreenBase extends class_437 {
   private static final int HEADER = 48;
   private static final int FOOTER = 44;
   private static final int COL_BG = -16119286;
   private static final int COL_SURFACE = -15461356;
   private static final int COL_TEXT = -1;
   private static final int COL_MUTED = -7697782;
   private static final int COL_DIM = -10855846;
   private static final int COL_HAIRLINE = -14737633;
   private static final int COL_ACCENT = -8470748;
   private static final int COL_GREEN = -12339839;
   private static final String[] EFFECT_IDS = new String[]{"totem", "anvil", "thunder", "none"};
   private static final String[] EFFECT_NAMES = new String[]{"Totem Pop", "Anvil Drop", "Thunder Strike", "None"};
   private static final String[] EFFECT_DESCS = new String[]{
      "Plays a totem of undying animation + sound effect",
      "Plays an anvil landing sound with grey flash",
      "Lightning bolt crack with blue-white flash",
      "No kill effects"
   };
   private static final int[] EFFECT_COLORS = new int[]{16755200, 10066329, 8965375, 5592405};
   private final class_437 parent;
   private float openT = 0.0F;
   private final InputCompat.Poller inputPoller = new InputCompat.Poller();

   protected KillEffectsScreenBase(class_437 parent) {
      super(class_2561.method_43470("Kill Effects"));
      this.parent = parent;
   }

   protected void method_25426() {
      Ui.playOpen();
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25419() {
      Ui.playClose();
      class_310.method_1551().method_1507(this.parent);
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      this.inputPoller.poll(mx, my, (x, y, btn) -> this.method_25402(x, y, btn), null);
      this.openT = Math.min(1.0F, this.openT + delta * 0.1F);
      float ease = Ui.easeOutCubic(this.openT);
      float scale = 0.92F + 0.08F * ease;
      DrawHelper.pushMatrices(ctx);
      DrawHelper.translateMatrices(ctx, (float)this.field_22789 / 2.0F * (1.0F - scale), (float)this.field_22790 / 2.0F * (1.0F - scale), 0.0F);
      DrawHelper.scaleMatrices(ctx, scale, scale, 1.0F);
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, -16119286);
      if (PackHubConfig.isBackgroundEffects() && !PackHubConfig.isReducedMotion()) {
         Ui.renderShootingStars(ctx, this.field_22789, this.field_22790, delta);
      }

      ctx.method_25294(0, 0, this.field_22789, 48, -15461356);
      ctx.method_25294(0, 0, this.field_22789, 2, -8470748);
      ctx.method_25294(0, 47, this.field_22789, 48, -14737633);
      boolean backHov = mx >= 8 && mx <= 48 && my >= 10 && my <= 36;
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "<", 14, 16, backHov ? -8470748 : -7697782);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "KILL EFFECTS", 30, 16, -1);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Select one effect that plays on kill", 30, 30, -7697782);
      ctx.method_25294(0, this.field_22790 - 44, this.field_22789, this.field_22790, -15461356);
      ctx.method_25294(0, this.field_22790 - 44, this.field_22789, this.field_22790 - 44 + 1, -14737633);
      String current = "Active: " + getEffectName(PackHubConfig.getKillEffect());
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, current, 16, this.field_22790 - 44 + 18, -7697782);
      int cardW = Math.min(180, (this.field_22789 - 80) / 4);
      int cardH = 120;
      int totalW = cardW * 4 + 36;
      int startX = (this.field_22789 - totalW) / 2;
      int cardY = 48 + (this.field_22790 - 48 - 44 - cardH) / 2;
      String selected = PackHubConfig.getKillEffect();

      for (int i = 0; i < EFFECT_IDS.length; i++) {
         int cx = startX + i * (cardW + 12);
         boolean active = EFFECT_IDS[i].equals(selected);
         boolean hovered = mx >= cx && mx <= cx + cardW && my >= cardY && my <= cardY + cardH;
         ctx.method_25294(cx, cardY, cx + cardW, cardY + cardH, active ? -15066566 : (hovered ? -15066582 : -15461356));
         if (active) {
            ctx.method_25294(cx, cardY, cx + cardW, cardY + 2, -8470748);
            ctx.method_25294(cx, cardY + cardH - 2, cx + cardW, cardY + cardH, -8470748);
            ctx.method_25294(cx, cardY, cx + 2, cardY + cardH, -8470748);
            ctx.method_25294(cx + cardW - 2, cardY, cx + cardW, cardY + cardH, -8470748);
         } else {
            ctx.method_25294(cx, cardY, cx + cardW, cardY + 1, -14737633);
            ctx.method_25294(cx, cardY + cardH - 1, cx + cardW, cardY + cardH, -14737633);
            ctx.method_25294(cx, cardY, cx + 1, cardY + cardH, -14737633);
            ctx.method_25294(cx + cardW - 1, cardY, cx + cardW, cardY + cardH, -14737633);
         }

         int iconX = cx + cardW / 2;
         int iconY = cardY + 28;
         this.drawEffectIcon(ctx, i, iconX, iconY, EFFECT_COLORS[i], active);
         String name = EFFECT_NAMES[i];
         int nameW = this.field_22793.method_1727(name);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, name, cx + (cardW - nameW) / 2, cardY + 50, -1);
         String desc = EFFECT_DESCS[i];
         int descW = this.field_22793.method_1727(desc);
         if (descW > cardW - 10) {
            int mid = desc.length() / 2;
            int split = desc.lastIndexOf(32, mid);
            if (split < 0) {
               split = mid;
            }

            String l1 = desc.substring(0, split).trim();
            String l2 = desc.substring(split).trim();
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, l1, cx + (cardW - this.field_22793.method_1727(l1)) / 2, cardY + 68, -10855846);
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, l2, cx + (cardW - this.field_22793.method_1727(l2)) / 2, cardY + 80, -10855846);
         } else {
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, desc, cx + (cardW - descW) / 2, cardY + 68, -10855846);
         }

         if (active) {
            String activeLabel = "✓ ACTIVE";
            DrawHelper.drawTextWithShadow(
               ctx, this.field_22793, activeLabel, cx + (cardW - this.field_22793.method_1727(activeLabel)) / 2, cardY + cardH - 16, -12339839
            );
         }
      }

      DrawHelper.popMatrices(ctx);

      for (class_364 child : this.method_25396()) {
         if (child instanceof class_4068 d) {
            d.method_25394(ctx, mx, my, delta);
         }
      }
   }

   public boolean method_25402(double mx, double my, int button) {
      if (button != 0) {
         return InputCompat.delegateToChildren(this, mx, my, button);
      } else if (mx >= 8.0 && mx <= 48.0 && my >= 10.0 && my <= 36.0) {
         Ui.playClick();
         this.method_25419();
         return true;
      } else {
         int cardW = Math.min(180, (this.field_22789 - 80) / 4);
         int cardH = 120;
         int totalW = cardW * 4 + 36;
         int startX = (this.field_22789 - totalW) / 2;
         int cardY = 48 + (this.field_22790 - 48 - 44 - cardH) / 2;

         for (int i = 0; i < EFFECT_IDS.length; i++) {
            int cx = startX + i * (cardW + 12);
            if (mx >= (double)cx && mx <= (double)(cx + cardW) && my >= (double)cardY && my <= (double)(cardY + cardH)) {
               PackHubConfig.setKillEffect(EFFECT_IDS[i]);
               Ui.playClick();
               return true;
            }
         }

         return InputCompat.delegateToChildren(this, mx, my, button);
      }
   }

   private void drawEffectIcon(class_332 ctx, int effectIndex, int cx, int cy, int color, boolean active) {
      int c = 0xFF000000 | color & 16777215;
      int glow = active ? Ui.withAlpha(color & 16777215, 60) : 0;
      if (active && glow != 0) {
         ctx.method_25294(cx - 16, cy - 16, cx + 16, cy + 16, glow);
      }

      switch (effectIndex) {
         case 0:
            ctx.method_25294(cx - 4, cy - 8, cx + 4, cy + 8, c);
            ctx.method_25294(cx - 5, cy - 12, cx + 5, cy - 6, c);
            ctx.method_25294(cx - 3, cy - 11, cx - 1, cy - 9, -16777216);
            ctx.method_25294(cx + 1, cy - 11, cx + 3, cy - 9, -16777216);
            ctx.method_25294(cx - 8, cy - 4, cx - 4, cy - 2, c);
            ctx.method_25294(cx + 4, cy - 4, cx + 8, cy - 2, c);
            ctx.method_25294(cx - 10, cy - 6, cx - 8, cy, Ui.withAlpha(color & 16777215, 150));
            ctx.method_25294(cx + 8, cy - 6, cx + 10, cy, Ui.withAlpha(color & 16777215, 150));
            ctx.method_25294(cx - 3, cy + 8, cx + 3, cy + 10, c);
            break;
         case 1:
            ctx.method_25294(cx - 6, cy - 10, cx + 6, cy - 6, c);
            ctx.method_25294(cx - 2, cy - 6, cx + 2, cy - 2, c);
            ctx.method_25294(cx - 4, cy - 2, cx + 4, cy + 2, c);
            ctx.method_25294(cx - 8, cy + 2, cx + 8, cy + 6, c);
            ctx.method_25294(cx - 10, cy + 6, cx + 10, cy + 10, c);
            ctx.method_25294(cx - 12, cy + 12, cx - 8, cy + 13, Ui.withAlpha(color & 16777215, 120));
            ctx.method_25294(cx + 8, cy + 12, cx + 12, cy + 13, Ui.withAlpha(color & 16777215, 120));
            ctx.method_25294(cx - 3, cy + 12, cx + 3, cy + 13, Ui.withAlpha(color & 16777215, 120));
            break;
         case 2:
            ctx.method_25294(cx + 2, cy - 12, cx + 6, cy - 8, c);
            ctx.method_25294(cx, cy - 8, cx + 4, cy - 4, c);
            ctx.method_25294(cx - 2, cy - 4, cx + 6, cy - 2, c);
            ctx.method_25294(cx - 2, cy - 2, cx + 2, cy + 2, c);
            ctx.method_25294(cx - 4, cy + 2, cx + 2, cy + 4, c);
            ctx.method_25294(cx - 6, cy + 4, cx, cy + 8, c);
            ctx.method_25294(cx - 8, cy + 8, cx - 2, cy + 12, c);
            ctx.method_25294(cx - 1, cy - 10, cx + 1, cy + 10, Ui.withAlpha(16777215, 40));
            ctx.method_25294(cx + 6, cy - 6, cx + 8, cy - 4, Ui.withAlpha(color & 16777215, 180));
            ctx.method_25294(cx - 8, cy + 4, cx - 6, cy + 6, Ui.withAlpha(color & 16777215, 180));
            ctx.method_25294(cx + 4, cy + 2, cx + 6, cy + 4, Ui.withAlpha(color & 16777215, 120));
            break;
         case 3:
            int dim = Ui.withAlpha(color & 16777215, 100);

            for (int a = 0; a < 360; a += 20) {
               double ang = Math.toRadians((double)a);
               int px = cx + (int)(Math.cos(ang) * 10.0);
               int py = cy + (int)(Math.sin(ang) * 10.0);
               ctx.method_25294(px - 1, py - 1, px + 1, py + 1, dim);
            }

            for (int d = -8; d <= 8; d += 2) {
               ctx.method_25294(cx + d - 1, cy - d - 1, cx + d + 1, cy - d + 1, dim);
            }
      }
   }

   private static String getEffectName(String id) {
      for (int i = 0; i < EFFECT_IDS.length; i++) {
         if (EFFECT_IDS[i].equals(id)) {
            return EFFECT_NAMES[i];
         }
      }

      return "Unknown";
   }
}
