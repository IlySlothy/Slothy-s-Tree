package com.packhub;

import com.packhub.compat.DrawHelper;
import com.packhub.compat.InputCompat;
import com.packhub.ui.Ui;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_437;

public class PackEditScreenBase extends class_437 {
   private static final int MAX_ASSETS_PER_TAB = 250;
   private static final int TAB_Y = 72;
   private static final int TAB_H = 22;
   private static final int HOVER_LINE_Y = 98;
   private static final int ROW_H = 48;
   private static final int THUMB = 36;
   private static final int THUMB_PAD = 14;
   private final class_437 parent;
   private final String catalogPackId;
   private final String folderName;
   private final Path packRoot;
   private final Map<String, String> hints = new LinkedHashMap<>();
   private String initialTitleText = "";
   private List<String> currentPaths = new ArrayList<>();
   private int tab = 0;
   private float scroll;
   private float scrollTarget;
   private class_342 titleField;
   private String statusLine;
   private final Map<String, class_2960> editThumbs = new ConcurrentHashMap<>();
   private final Set<String> editThumbsLoading = ConcurrentHashMap.newKeySet();
   private int thumbIdSeq;
   private final Map<String, class_342> rowHintFields = new LinkedHashMap<>();
   private final InputCompat.Poller inputPoller = new InputCompat.Poller();
   private String hoverVanillaLine;

   public PackEditScreenBase(class_437 parent, String catalogPackId, String folderName, String titleGuess) {
      super(class_2561.method_43470("Pack Editor"));
      this.parent = parent;
      this.catalogPackId = catalogPackId;
      this.folderName = folderName;
      Path root = PackDownloader.resolveResourcePackPath(folderName);
      this.packRoot = root;
      if (root != null) {
         String sideTitle = PackEditStore.loadSidecarDisplayName(root);
         String mcTitle = PackEditStore.readPackMcmetaDescription(root);
         this.initialTitleText = !sideTitle.isBlank() ? sideTitle : (!mcTitle.isBlank() ? mcTitle : (titleGuess != null ? titleGuess : folderName));
         this.hints.putAll(PackEditStore.loadHints(root));
      } else {
         this.initialTitleText = titleGuess != null ? titleGuess : "";
      }
   }

   private void disposeEditThumbs() {
      class_310 mc = class_310.method_1551();

      for (class_2960 id : this.editThumbs.values()) {
         mc.method_1531().method_4615(id);
      }

      this.editThumbs.clear();
      this.editThumbsLoading.clear();
   }

   private void loadEditThumb(String relPath) {
      if (this.packRoot != null && !this.editThumbs.containsKey(relPath) && this.editThumbsLoading.add(relPath)) {
         Path file = this.packRoot;

         for (String seg : relPath.split("/")) {
            if (!seg.isEmpty()) {
               file = file.resolve(seg);
            }
         }

         Path pathToRead = file;
         class_310 mc = class_310.method_1551();
         new Thread(() -> {
            try (InputStream in = Files.newInputStream(pathToRead)) {
               class_1011 image = class_1011.method_4309(in);
               mc.execute(() -> {
                  try {
                     int n = ++this.thumbIdSeq;
                     class_2960 id = class_2960.method_60655("packhub", "pack_edit_thumb_" + n + "_" + Integer.toHexString(relPath.hashCode()));
                     class_1043 tex = DrawHelper.createNativeTexture("packhub_packedit_" + n, image);
                     if (tex != null) {
                        mc.method_1531().method_4616(id, tex);
                        this.editThumbs.put(relPath, id);
                     }
                  } catch (Exception var10) {
                     PackHubMod.LOGGER.warn("Pack edit thumb failed for {}: {}", relPath, var10.getMessage());
                  } finally {
                     this.editThumbsLoading.remove(relPath);
                  }
               });
            } catch (Exception var91) {
               mc.execute(() -> this.editThumbsLoading.remove(relPath));
            }
         }, "packhub-packedit-thumb").start();
      }
   }

   private void clearRowHintFields() {
      for (class_342 w : this.rowHintFields.values()) {
         this.method_37066(w);
      }

      this.rowHintFields.clear();
   }

   private void rebuildRowHintFields() {
      if (this.packRoot != null) {
         for (String path : this.currentPaths) {
            class_342 tf = new class_342(this.field_22793, 0, 0, 160, 18, class_2561.method_43470("Vanilla id"));
            tf.method_1880(120);
            tf.method_47404(class_2561.method_43470("e.g. diamond_sword"));
            String def = this.hints.getOrDefault(path, PackEditStore.guessVanillaIdFromPath(path));
            tf.method_1852(def);
            this.method_37063(tf);
            this.rowHintFields.put(path, tf);
         }
      }
   }

   private void reloadTabPaths() {
      this.clearRowHintFields();
      this.disposeEditThumbs();
      if (this.packRoot == null) {
         this.currentPaths = List.of();
      } else {
         try {
            String sub = this.tab == 0 ? "item" : (this.tab == 1 ? "block" : "particle");
            this.currentPaths = PackEditStore.scanTexturePaths(this.packRoot, sub, 250);
         } catch (IOException var21) {
            this.currentPaths = List.of();
            this.statusLine = var21.getMessage();
         }
      }

      this.scroll = this.scrollTarget = 0.0F;
      this.rebuildRowHintFields();
   }

   private int listTop() {
      return 112;
   }

   private int listBot() {
      return this.field_22790 - 56;
   }

   private int maxScroll() {
      int view = this.listBot() - this.listTop();
      int total = this.currentPaths.size() * 48;
      return Math.max(0, total - view + 8);
   }

   public boolean onScrollDelta(double delta) {
      this.scrollTarget = clamp(this.scrollTarget - (float)delta * 24.0F, 0.0F, (float)this.maxScroll());
      return true;
   }

   private static float clamp(float v, float a, float b) {
      return Math.max(a, Math.min(b, v));
   }

   protected void method_25426() {
      super.method_25426();
      this.rowHintFields.clear();
      this.titleField = new class_342(this.field_22793, 120, 52, Math.min(320, this.field_22789 - 140), 18, class_2561.method_43470("Pack name"));
      this.titleField.method_1880(128);
      this.titleField.method_1852(this.initialTitleText);
      this.method_37063(this.titleField);
      this.reloadTabPaths();
   }

   private String tabLabel(int i) {
      return i == 0 ? "Items" : (i == 1 ? "Blocks" : "Particles");
   }

   public void method_25432() {
      this.clearRowHintFields();
      this.disposeEditThumbs();
      super.method_25432();
   }

   private void updateRowFieldPositions() {
      if (this.packRoot != null) {
         int lt = this.listTop();
         int lb = this.listBot();
         int fieldW = Math.min(200, Math.max(96, this.field_22789 / 4));
         int fieldX = this.field_22789 - fieldW - 14;

         for (int i = 0; i < this.currentPaths.size(); i++) {
            String path = this.currentPaths.get(i);
            class_342 f = this.rowHintFields.get(path);
            if (f != null) {
               int ry = lt + i * 48 - (int)this.scroll;
               f.method_46421(fieldX);
               f.method_46419(ry + 15);
               f.method_25358(fieldW);
               f.method_1862(ry + 48 > lt && ry < lb);
            }
         }
      }
   }

   private void updateHoverLine(int mx, int my) {
      this.hoverVanillaLine = null;
      if (this.packRoot != null) {
         int lt = this.listTop();
         int lb = this.listBot();
         if (my >= lt && my <= lb && mx >= 12 && mx <= this.field_22789 - 12) {
            int idx = (int)(((float)(my - lt) + this.scroll) / 48.0F);
            if (idx >= 0 && idx < this.currentPaths.size()) {
               String path = this.currentPaths.get(idx);
               String fromFile = PackEditStore.guessVanillaIdFromPath(path);
               class_342 tf = this.rowHintFields.get(path);
               String typed = tf != null ? tf.method_1882().trim() : "";
               String line = typed.isEmpty()
                  ? "Vanilla-style id from file name: " + fromFile + "  —  type in the row field to override"
                  : "Mapped id for builder: " + typed + (typed.equals(fromFile) ? "" : "  (file base name was: " + fromFile + ")");
               this.hoverVanillaLine = line;
            }
         }
      }
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      this.inputPoller.poll(mx, my, this::method_25402, null);
      float smooth = Math.min(1.0F, delta * 0.4F);
      this.scroll = this.scroll + (this.scrollTarget - this.scroll) * smooth;
      this.updateRowFieldPositions();
      this.updateHoverLine(mx, my);
      this.method_25420(ctx, mx, my, delta);
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, -16119286);
      if (PackHubConfig.isBackgroundEffects()) {
         Ui.renderShootingStars(ctx, this.field_22789, this.field_22790, delta);
      }

      ctx.method_25294(0, 0, this.field_22789, 44, -15461356);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "<", 14, 16, mx >= 8 && mx <= 40 && my >= 10 && my <= 32 ? -8470748 : -7697782);
      String headExtra = this.catalogPackId != null && !this.catalogPackId.isBlank() ? this.catalogPackId + "  //  " : "";
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "PACK EDITOR  //  " + headExtra + this.folderName, 36, 12, -1);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Pack title (pack.mcmeta description):", 16, 54, -7697782);
      if (this.packRoot == null) {
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Invalid pack path.", 16, 120, -1228219);
      } else {
         int x = 12;

         for (int t = 0; t < 3; t++) {
            String lab = this.tabLabel(t);
            int tw = this.field_22793.method_1727(lab) + 16;
            boolean hov = mx >= x && mx <= x + tw && my >= 72 && my <= 94;
            boolean on = t == this.tab;
            ctx.method_25294(x, 72, x + tw, 94, on ? -10968080 : (hov ? -12040149 : -14145496));
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, lab, x + 8, 79, on ? -1 : -7697782);
            x += tw + 6;
         }

         String hoverDisp = this.hoverVanillaLine != null
            ? this.hoverVanillaLine
            : "Hover a row to see the vanilla-style id from the file name; edit ids in the right column.";
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, this.truncate(hoverDisp, this.field_22789 - 24), 12, 98, -8470748);
         int fieldW = Math.min(200, Math.max(96, this.field_22789 / 4));
         int fieldX = this.field_22789 - fieldW - 14;
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "TEXTURE PATH", 60, 106, -10855846);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "REPLACES (vanilla id)", fieldX, 106, -10855846);
         int lt = this.listTop();
         int lb = this.listBot();
         int pathMaxPx = fieldX - 14 - 36 - 10 - 12;
         ctx.method_44379(0, lt, this.field_22789, lb);
         ctx.method_25294(0, lt, this.field_22789, lb, -16053492);
         ctx.method_25294(10, lt, this.field_22789 - 10, lt + 1, -10968080);

         for (int i = 0; i < this.currentPaths.size(); i++) {
            int rowTop = lt + i * 48 - (int)this.scroll;
            if (rowTop + 48 >= lt && rowTop <= lb) {
               String path = this.currentPaths.get(i);
               boolean rh = mx >= 12 && mx <= this.field_22789 - 12 && my >= rowTop && my <= rowTop + 48 && my >= lt && my <= lb;
               if (rh) {
                  ctx.method_25294(12, rowTop, this.field_22789 - 12, rowTop + 48, 872415232);
               }

               int tpy = rowTop + 6;
               class_2960 tid = this.editThumbs.get(path);
               if (tid != null) {
                  DrawHelper.drawTexture(ctx, tid, 14, tpy, 0.0F, 0.0F, 36, 36, 36, 36);
               } else {
                  ctx.method_25294(14, tpy, 50, tpy + 36, -14540254);
                  this.loadEditThumb(path);
               }

               String kind = this.tabLabel(this.tab).toUpperCase();
               int textLeft = 60;
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, kind, textLeft, rowTop + 8, -10855846);
               String shortPath = this.truncate(path, pathMaxPx);
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, shortPath, textLeft, rowTop + 24, -1);
            }
         }

         ctx.method_44380();
         if (this.currentPaths.isEmpty()) {
            String msg = "No textures in assets/minecraft/textures/" + (this.tab == 0 ? "item" : (this.tab == 1 ? "block" : "particle"));
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, msg, 24, lt + 20, -7697782);
         }

         int sbw = 100;
         int sbx = this.field_22789 - sbw - 16;
         int sby = this.listBot() + 4;
         boolean sh = mx >= sbx && mx <= sbx + sbw && my >= sby && my <= sby + 24;
         ctx.method_25294(sbx, sby, sbx + sbw, sby + 24, sh ? -8470748 : -10843622);
         String sl = "SAVE";
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, sl, sbx + (sbw - this.field_22793.method_1727(sl)) / 2, sby + 8, -1);
      }

      if (this.statusLine != null) {
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, this.truncate(this.statusLine, this.field_22789 - 20), 16, this.field_22790 - 22, -8470748);
      }

      for (class_364 child : this.method_25396()) {
         if (child instanceof class_4068 d) {
            d.method_25394(ctx, mx, my, delta);
         }
      }
   }

   private String truncate(String s, int maxPx) {
      if (this.field_22793.method_1727(s) <= maxPx) {
         return s;
      } else {
         String ell = "…";

         while (s.length() > 4 && this.field_22793.method_1727(s + ell) > maxPx) {
            s = s.substring(1);
         }

         return ell + s;
      }
   }

   private boolean rowFieldHit(double mx, double my) {
      for (class_342 w : this.rowHintFields.values()) {
         if (w.method_1885() && w.method_25405(mx, my)) {
            return true;
         }
      }

      return false;
   }

   public boolean method_25402(double mx, double my, int button) {
      if (button != 0) {
         return InputCompat.delegateToChildren(this, mx, my, button);
      } else if (mx >= 8.0 && mx <= 40.0 && my >= 10.0 && my <= 32.0) {
         class_310.method_1551().method_1507(this.parent);
         return true;
      } else {
         if (this.packRoot != null) {
            int x = 12;

            for (int t = 0; t < 3; t++) {
               String lab = this.tabLabel(t);
               int tw = this.field_22793.method_1727(lab) + 16;
               if (mx >= (double)x && mx <= (double)(x + tw) && my >= 72.0 && my <= 94.0) {
                  if (t != this.tab) {
                     this.syncHintsFromRowFields();
                     this.tab = t;
                     this.reloadTabPaths();
                     Ui.playClick();
                  }

                  return true;
               }

               x += tw + 6;
            }

            int sbw = 100;
            int sbx = this.field_22789 - sbw - 16;
            int sby = this.listBot() + 4;
            if (mx >= (double)sbx && mx <= (double)(sbx + sbw) && my >= (double)sby && my <= (double)(sby + 24)) {
               this.save();
               return true;
            }

            int lt = this.listTop();
            int lb = this.listBot();
            if (my >= (double)lt && my <= (double)lb && mx >= 12.0 && mx <= (double)this.field_22789 - 12.0 && !this.rowFieldHit(mx, my)) {
               int rel = (int)((my - (double)lt + (double)this.scroll) / 48.0);
               if (rel >= 0 && rel < this.currentPaths.size()) {
                  class_342 f = this.rowHintFields.get(this.currentPaths.get(rel));
                  if (f != null && f.method_1885()) {
                     f.method_25365(true);
                     Ui.playClick();
                     return true;
                  }
               }
            }
         }

         return InputCompat.delegateToChildren(this, mx, my, button);
      }
   }

   private void syncHintsFromRowFields() {
      for (Entry<String, class_342> e : this.rowHintFields.entrySet()) {
         this.hints.put(e.getKey(), e.getValue().method_1882().trim());
      }
   }

   private void save() {
      this.syncHintsFromRowFields();

      try {
         PackEditStore.saveSidecar(this.packRoot, this.titleField.method_1882().trim(), this.hints);
         PackEditStore.writePackMcmetaDescription(this.packRoot, this.titleField.method_1882().trim());
         this.statusLine = "Saved packhub_edit.json and pack.mcmeta — re-open Resource Packs or use F3+T if needed.";
         Ui.playSuccess();
      } catch (IOException var2) {
         this.statusLine = "Save failed: " + var2.getMessage();
         Ui.playClick();
      }
   }

   public boolean method_25421() {
      return false;
   }
}
