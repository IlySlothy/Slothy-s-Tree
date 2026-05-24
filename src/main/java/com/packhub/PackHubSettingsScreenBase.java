package com.packhub;

import com.packhub.compat.DrawHelper;
import com.packhub.compat.InputCompat;
import com.packhub.ui.CustomButton;
import com.packhub.ui.CustomButtonBase;
import com.packhub.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_437;

public abstract class PackHubSettingsScreenBase extends class_437 {
   private static final int HEADER = 52;
   private static final int FOOTER = 44;
   private static final int ROW_H = 30;
   private static final int SECTION_H = 22;
   private static final int COL_BG = -16119286;
   private static final int COL_TEXT = -1;
   private static final int COL_MUTED = -7697782;
   private static final int COL_DIM = -10855846;
   private static final int COL_HAIRLINE = -14737633;
   private static final int COL_HAIRLINE_HOT = -12961222;
   private static final int COL_SECTION = -11908534;
   private final class_437 parent;
   private class_342 urlField;
   private float openT = 0.0F;
   private float scroll = 0.0F;
   private float scrollTarget = 0.0F;
   private final InputCompat.Poller inputPoller = new InputCompat.Poller();
   private final List<PackHubSettingsScreenBase.SettingRow> rows = new ArrayList<>();
   private static final int ARROW_W = 14;

   protected PackHubSettingsScreenBase(class_437 parent) {
      super(class_2561.method_43470("PackHub Settings"));
      this.parent = parent;
   }

   protected void method_25426() {
      Ui.playOpen();
      this.scroll = 0.0F;
      this.scrollTarget = 0.0F;
      this.rows.clear();
      this.rows.add(new PackHubSettingsScreenBase.SectionHeader("NETWORK"));
      this.rows
         .add(
            new PackHubSettingsScreenBase.IntRow(
               "Max concurrent downloads",
               "How many packs download in parallel.",
               PackHubConfig::getMaxConcurrentDownloads,
               PackHubConfig::setMaxConcurrentDownloads,
               1,
               8
            )
         );
      this.rows
         .add(
            new PackHubSettingsScreenBase.IntRow(
               "Cache expiry (min)",
               "How long downloaded pack data stays cached before re-checking.",
               PackHubConfig::getCacheExpiry,
               PackHubConfig::setCacheExpiry,
               5,
               120
            )
         );
      this.rows.add(new PackHubSettingsScreenBase.SectionHeader("DISPLAY"));
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Animations",
               "Hover transitions, pulses, shimmers. Disable on weak GPUs to save frames.",
               PackHubConfig::isAnimationsEnabled,
               PackHubConfig::setAnimationsEnabled
            )
         );
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Background effects",
               "Shooting stars particle effect behind the UI. Disable to save FPS.",
               PackHubConfig::isBackgroundEffects,
               PackHubConfig::setBackgroundEffects
            )
         );
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Reduced motion",
               "Disable all transitions and particle effects for accessibility.",
               PackHubConfig::isReducedMotion,
               PackHubConfig::setReducedMotion
            )
         );
      this.rows.add(new PackHubSettingsScreenBase.SectionHeader("PERFORMANCE"));
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Batch reloads",
               "Defer reloadResources() until you close PackHub instead of reloading per-pack.",
               PackHubConfig::isBatchedReload,
               PackHubConfig::setBatchedReload
            )
         );
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Prefetch showcases",
               "Load every visible thumbnail eagerly. Disable on slow connections.",
               PackHubConfig::isPrefetchThumbnails,
               PackHubConfig::setPrefetchThumbnails
            )
         );
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Low-res thumbnails", "Load thumbnails at half resolution to save VRAM.", PackHubConfig::isLowResThumb, PackHubConfig::setLowResThumb
            )
         );
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Lazy-load cards",
               "Only load pack details when scrolled into view. Faster on large libraries.",
               PackHubConfig::isLazyLoadCards,
               PackHubConfig::setLazyLoadCards
            )
         );
      this.rows.add(new PackHubSettingsScreenBase.SectionHeader("DATA"));
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Sort by stars", "Highest-voted packs always rise to the top of the list.", PackHubConfig::isSortByStars, PackHubConfig::setSortByStars
            )
         );
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Confirm before remove",
               "Require an extra click before un-applying a pack. Helps prevent fat-fingers.",
               PackHubConfig::isConfirmBeforeRemove,
               PackHubConfig::setConfirmBeforeRemove
            )
         );
      this.rows
         .add(
            new PackHubSettingsScreenBase.ToggleRow(
               "Verify downloads",
               "Check the SHA-256 hash of a downloaded pack against the server. Recommended.",
               PackHubConfig::isVerifyDownloads,
               PackHubConfig::setVerifyDownloads
            )
         );
      int fieldX = 28;
      int fieldY = 70;
      int fieldW = Math.min(this.field_22789 - 56, 520);
      this.urlField = new class_342(this.field_22793, fieldX, fieldY, fieldW, 18, class_2561.method_43470("Bot URL override"));
      this.urlField.method_1880(200);
      this.urlField.method_47404(class_2561.method_43470("Leave blank for auto-discover, or paste https://..."));
      this.urlField.method_1858(false);
      this.urlField.method_1868(16777215);
      this.urlField.method_1860(8421504);
      this.urlField.method_1852(PackHubConfig.getUrlOverride());
      this.urlField.method_1863(PackHubConfig::setUrlOverride);
      this.method_37063(this.urlField);
      int by = this.field_22790 - 44 + 9;
      int bw = 130;
      int gap = 8;
      int totalW = bw * 3 + gap * 2;
      int bx = this.field_22789 / 2 - totalW / 2;
      this.method_37063(
         new CustomButton(bx, by, bw, 26, class_2561.method_43470("REGENERATE VOTER"), CustomButtonBase.Style.SECONDARY, PackHubConfig::regenerateVoterId)
      );
      this.method_37063(
         new CustomButton(bx + bw + gap, by, bw, 26, class_2561.method_43470("CLEAR CACHE"), CustomButtonBase.Style.SECONDARY, PackDownloader::clearLocalCache)
      );
      this.method_37063(new CustomButton(bx + (bw + gap) * 2, by, bw, 26, class_2561.method_43470("BACK"), CustomButtonBase.Style.PRIMARY, this::method_25419));
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25419() {
      Ui.playClose();
      class_310.method_1551().method_1507(this.parent);
   }

   private int contentHeight() {
      int h = 0;

      for (PackHubSettingsScreenBase.SettingRow r : this.rows) {
         h += r.height();
      }

      return h;
   }

   private int scrollableArea() {
      return this.field_22790 - 52 - 44;
   }

   private int urlBlockHeight() {
      return 80;
   }

   private int maxScroll() {
      int total = this.urlBlockHeight() + this.contentHeight();
      return Math.max(0, total - this.scrollableArea());
   }

   public boolean onScrollDelta(double delta) {
      this.scrollTarget = Math.max(0.0F, Math.min((float)this.maxScroll(), this.scrollTarget - (float)delta * 28.0F));
      return true;
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

      ctx.method_25294(0, 51, this.field_22789, 52, -14737633);
      int padX = 18;
      DrawHelper.drawText(ctx, this.field_22793, "PACKHUB", padX, 18, -1, false);
      DrawHelper.drawText(ctx, this.field_22793, "//", padX + this.field_22793.method_1727("PACKHUB") + 6, 18, -10855846, false);
      DrawHelper.drawText(ctx, this.field_22793, "SETTINGS", padX + this.field_22793.method_1727("PACKHUB") + 18, 18, -7697782, false);
      ctx.method_25294(0, this.field_22790 - 44, this.field_22789, this.field_22790 - 44 + 1, -14737633);
      this.scroll = this.scroll + (this.scrollTarget - this.scroll) * Math.min(1.0F, delta * 0.35F);
      if (Math.abs(this.scroll - this.scrollTarget) < 0.5F) {
         this.scroll = this.scrollTarget;
      }

      int scissorTop = 52;
      int scissorBottom = this.field_22790 - 44;
      ctx.method_44379(0, scissorTop, this.field_22789, scissorBottom);
      int cursorY = 52 - (int)this.scroll;
      DrawHelper.drawText(ctx, this.field_22793, "BOT URL OVERRIDE", 28, cursorY + 6, -10855846, false);
      if (this.urlField != null) {
         this.urlField.method_46419(cursorY + 18);
         int sx = this.urlField.method_46426();
         int sy = this.urlField.method_46427() + this.urlField.method_25364() + 2;
         int sw = this.urlField.method_25368();
         ctx.method_25294(sx, sy, sx + sw, sy + 1, this.urlField.method_25370() ? -1 : -12961222);
      }

      DrawHelper.drawText(ctx, this.field_22793, "Leave blank to use rendezvous auto-discovery.", 28, cursorY + 50, -7697782, false);
      String vid = PackHubConfig.getVoterId();
      String shortVid = vid.length() > 8 ? vid.substring(0, 8) + "…" : vid;
      DrawHelper.drawText(ctx, this.field_22793, "VOTER ID  //  " + shortVid, 28, cursorY + 64, -10855846, false);
      cursorY += this.urlBlockHeight();
      int rowX = 28;
      int rowW = this.field_22789 - 56;

      for (PackHubSettingsScreenBase.SettingRow row : this.rows) {
         int rh = row.height();
         boolean visible = cursorY + rh > scissorTop && cursorY < scissorBottom;
         if (visible) {
            boolean hovered = mx >= rowX && mx <= rowX + rowW && my >= cursorY && my < cursorY + rh && my >= scissorTop && my < scissorBottom;
            if (row instanceof PackHubSettingsScreenBase.SectionHeader s) {
               this.renderSectionHeader(ctx, s, rowX, cursorY, rowW);
            } else if (row instanceof PackHubSettingsScreenBase.ToggleRow t) {
               this.renderToggle(ctx, t, rowX, cursorY, rowW, hovered);
            } else if (row instanceof PackHubSettingsScreenBase.IntRow r) {
               this.renderIntRow(ctx, r, rowX, cursorY, rowW, hovered);
            }
         }

         cursorY += rh;
      }

      ctx.method_44380();
      DrawHelper.popMatrices(ctx);

      for (class_364 child : this.method_25396()) {
         if (child instanceof class_4068 d) {
            d.method_25394(ctx, mx, my, delta);
         }
      }
   }

   private void renderSectionHeader(class_332 ctx, PackHubSettingsScreenBase.SectionHeader s, int x, int y, int w) {
      String title = " " + s.title() + " ";
      int tw = this.field_22793.method_1727(title);
      int lineY = y + 11;
      int cx = x + w / 2;
      int halfLine = (w - tw) / 2 - 4;
      if (halfLine > 0) {
         ctx.method_25294(x, lineY, x + halfLine, lineY + 1, -14737633);
         ctx.method_25294(cx + tw / 2 + 4, lineY, x + w, lineY + 1, -14737633);
      }

      DrawHelper.drawText(ctx, this.field_22793, title, cx - tw / 2, y + 7, -11908534, false);
   }

   private void renderToggle(class_332 ctx, PackHubSettingsScreenBase.ToggleRow t, int x, int y, int w, boolean hovered) {
      ctx.method_25294(x, y, x + w, y + 1, -14737633);
      if (hovered) {
         ctx.method_25294(x, y + 1, x + w, y + 30, 285212671);
      }

      boolean on = t.get().getAsBoolean();
      DrawHelper.drawText(ctx, this.field_22793, t.label().toUpperCase(Locale.ROOT), x + 4, y + 6, -1, false);
      DrawHelper.drawText(ctx, this.field_22793, t.hint(), x + 4, y + 18, -7697782, false);
      String pill = on ? "ON" : "OFF";
      int pw = this.field_22793.method_1727(pill) + 14;
      int ph = 14;
      int px = x + w - pw - 4;
      int py = y + (30 - ph) / 2;
      int border = on ? -1 : -12961222;
      int fill = on ? -1 : -16119286;
      int fg = on ? -16777216 : -1;
      ctx.method_25294(px, py, px + pw, py + ph, fill);
      ctx.method_25294(px, py, px + pw, py + 1, border);
      ctx.method_25294(px, py + ph - 1, px + pw, py + ph, border);
      ctx.method_25294(px, py, px + 1, py + ph, border);
      ctx.method_25294(px + pw - 1, py, px + pw, py + ph, border);
      DrawHelper.drawText(ctx, this.field_22793, pill, px + (pw - this.field_22793.method_1727(pill)) / 2, py + (ph - 8) / 2, fg, false);
   }

   private void renderIntRow(class_332 ctx, PackHubSettingsScreenBase.IntRow r, int x, int y, int w, boolean hovered) {
      ctx.method_25294(x, y, x + w, y + 1, -14737633);
      if (hovered) {
         ctx.method_25294(x, y + 1, x + w, y + 30, 285212671);
      }

      DrawHelper.drawText(ctx, this.field_22793, r.label().toUpperCase(Locale.ROOT), x + 4, y + 6, -1, false);
      DrawHelper.drawText(ctx, this.field_22793, r.hint(), x + 4, y + 18, -7697782, false);
      int val = r.get().getAsInt();
      String valStr = String.valueOf(val);
      int vw = this.field_22793.method_1727(valStr);
      int totalPillW = 14 + vw + 8 + 14;
      int ph = 14;
      int px = x + w - totalPillW - 4;
      int py = y + (30 - ph) / 2;
      ctx.method_25294(px, py, px + 14, py + ph, -16119286);
      ctx.method_25294(px, py, px + 14, py + 1, -12961222);
      ctx.method_25294(px, py + ph - 1, px + 14, py + ph, -12961222);
      ctx.method_25294(px, py, px + 1, py + ph, -12961222);
      DrawHelper.drawText(
         ctx, this.field_22793, "<", px + (14 - this.field_22793.method_1727("<")) / 2, py + (ph - 8) / 2, val > r.min() ? -1 : -10855846, false
      );
      int valueX = px + 14;
      int valueW = vw + 8;
      ctx.method_25294(valueX, py, valueX + valueW, py + ph, -16119286);
      ctx.method_25294(valueX, py, valueX + valueW, py + 1, -14737633);
      ctx.method_25294(valueX, py + ph - 1, valueX + valueW, py + ph, -14737633);
      DrawHelper.drawText(ctx, this.field_22793, valStr, valueX + (valueW - vw) / 2, py + (ph - 8) / 2, -1, false);
      int rx = valueX + valueW;
      ctx.method_25294(rx, py, rx + 14, py + ph, -16119286);
      ctx.method_25294(rx, py, rx + 14, py + 1, -12961222);
      ctx.method_25294(rx, py + ph - 1, rx + 14, py + ph, -12961222);
      ctx.method_25294(rx + 14 - 1, py, rx + 14, py + ph, -12961222);
      DrawHelper.drawText(
         ctx, this.field_22793, ">", rx + (14 - this.field_22793.method_1727(">")) / 2, py + (ph - 8) / 2, val < r.max() ? -1 : -10855846, false
      );
   }

   public boolean method_25402(double mx, double my, int button) {
      if (!(my < 52.0) && !(my >= (double)(this.field_22790 - 44))) {
         int cursorY = 52 - (int)this.scroll + this.urlBlockHeight();
         int rowX = 28;
         int rowW = this.field_22789 - 56;

         for (PackHubSettingsScreenBase.SettingRow row : this.rows) {
            int rh = row.height();
            if (mx >= (double)rowX && mx <= (double)(rowX + rowW) && my >= (double)cursorY && my < (double)(cursorY + rh)) {
               if (row instanceof PackHubSettingsScreenBase.ToggleRow t) {
                  t.set().accept(!t.get().getAsBoolean());
                  Ui.playClick();
                  return true;
               }

               if (row instanceof PackHubSettingsScreenBase.IntRow r) {
                  int val = r.get().getAsInt();
                  String valStr = String.valueOf(val);
                  int vw = this.field_22793.method_1727(valStr);
                  int totalPillW = 14 + vw + 8 + 14;
                  int px = rowX + rowW - totalPillW - 4;
                  int rightArrowX = px + 14 + vw + 8;
                  if (mx >= (double)px && mx < (double)(px + 14)) {
                     if (val > r.min()) {
                        r.set().accept(val - 1);
                        Ui.playClick();
                     }

                     return true;
                  }

                  if (mx >= (double)rightArrowX && mx < (double)(rightArrowX + 14)) {
                     if (val < r.max()) {
                        r.set().accept(val + 1);
                        Ui.playClick();
                     }

                     return true;
                  }

                  return true;
               }
            }

            cursorY += rh;
         }

         return InputCompat.delegateToChildren(this, mx, my, button);
      } else {
         return InputCompat.delegateToChildren(this, mx, my, button);
      }
   }

   private static record IntRow(String label, String hint, IntSupplier get, IntConsumer set, int min, int max) implements PackHubSettingsScreenBase.SettingRow {
      @Override
      public int height() {
         return 30;
      }
   }

   private static record SectionHeader(String title) implements PackHubSettingsScreenBase.SettingRow {
      @Override
      public int height() {
         return 22;
      }
   }

   private interface SettingRow {
      int height();
   }

   private static record ToggleRow(String label, String hint, BooleanSupplier get, Consumer<Boolean> set) implements PackHubSettingsScreenBase.SettingRow {
      @Override
      public int height() {
         return 30;
      }
   }
}
