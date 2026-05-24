package com.packhub;

import com.packhub.compat.DrawHelper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.class_1657;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_465;
import net.minecraft.class_490;
import net.minecraft.class_7923;

public class PackHubInventoryPeekScreen extends class_490 {
   private static Method slotAtMethod;
   private static Field slotField;
   private static boolean triedSlotAt;
   private final class_437 returnScreen;

   public PackHubInventoryPeekScreen(class_1657 player, class_437 returnScreen) {
      super(player);
      this.returnScreen = returnScreen;
   }

   private class_1735 resolveSlot(int mouseX, int mouseY) {
      if (!triedSlotAt) {
         triedSlotAt = true;

         try {
            slotAtMethod = class_465.class.getDeclaredMethod("getSlotAt", double.class, double.class);
            slotAtMethod.setAccessible(true);
         } catch (NoSuchMethodException var8) {
            slotAtMethod = null;
         }
      }

      if (slotAtMethod != null) {
         try {
            return (class_1735)slotAtMethod.invoke(this, (double)mouseX, (double)mouseY);
         } catch (Exception var11) {
         }
      }

      if (slotField == null) {
         for (String n : new String[]{"focusedSlot", "hoveredSlot"}) {
            try {
               Field f = class_465.class.getDeclaredField(n);
               f.setAccessible(true);
               slotField = f;
               break;
            } catch (NoSuchFieldException var10) {
            }
         }
      }

      if (slotField != null) {
         try {
            return (class_1735)slotField.get(this);
         } catch (IllegalAccessException var9) {
         }
      }

      return null;
   }

   public void method_25419() {
      if (this.field_22787 != null) {
         this.field_22787.method_1507(this.returnScreen);
      }
   }

   protected void method_2380(class_332 context, int mouseX, int mouseY) {
   }

   protected List<class_2561> method_51454(class_1799 stack) {
      class_2960 id = class_7923.field_41178.method_10221(stack.method_7909());
      return List.of(class_2561.method_43470(id.toString()));
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      super.method_25394(ctx, mx, my, delta);
      class_1735 slot = this.resolveSlot(mx, my);
      int pad = 10;
      int panelTop = 6;
      int panelH = slot != null && slot.method_7681() ? 58 : 40;
      int panelW = Math.min(this.field_22789 - pad * 2, 520);
      int panelX = (this.field_22789 - panelW) / 2;
      ctx.method_25294(panelX, panelTop, panelX + panelW, panelTop + panelH, -401600496);
      ctx.method_25294(panelX, panelTop, panelX + panelW, panelTop + 2, -8470748);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "PackHub builder — vanilla id (ignore item name / lore)", panelX + 8, panelTop + 6, -7697782);
      if (slot != null && slot.method_7681()) {
         class_1799 st = slot.method_7677();
         class_2960 id = class_7923.field_41178.method_10221(st.method_7909());
         String full = id.toString();
         int colon = full.indexOf(58);
         String shortId = colon >= 0 ? full.substring(colon + 1) : full;
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Type in builder: " + shortId, panelX + 8, panelTop + 22, -1);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, full, panelX + 8, panelTop + 36, -8470748);
         if (st.method_7947() != 1) {
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, "×" + st.method_7947(), panelX + panelW - 28, panelTop + 22, -10855846);
         }
      } else {
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Hover a stack in your inventory…", panelX + 8, panelTop + 24, -7697782);
      }

      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "ESC — back to Pack Builder", panelX + 8, this.field_22790 - 16, -10855846);
   }

   public boolean method_25421() {
      return false;
   }
}
