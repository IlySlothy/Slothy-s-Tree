package com.packhub;

import com.packhub.compat.DrawHelper;
import com.packhub.compat.InputCompat;
import com.packhub.ui.Ui;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy;
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

public abstract class PackBuilderScreenBase extends class_437 {
   private static final int HEADER = 48;
   private static final int TABS_H = 32;
   private static final int FOOTER = 60;
   private static final int CELL = 88;
   private static final int THUMB = 64;
   private static final int GAP = 10;
   private static final int ROW_H = 48;
   private static final int COL_BG = -16119286;
   private static final int COL_SURFACE = -15461356;
   private static final int COL_TEXT = -1;
   private static final int COL_MUTED = -7697782;
   private static final int COL_DIM = -10855846;
   private static final int COL_HAIRLINE = -14737633;
   private static final int COL_HAIRLINE_HOT = -12961222;
   private static final int COL_ACCENT = -8470748;
   private static final int COL_ACCENT_DIM = -10843622;
   private static final int COL_SELECTED = -15061488;
   private static final int COL_GREEN = -8470748;
   private static final int COL_RED = -1228219;
   private static final int COL_FIELD_BG = -15065580;
   private static final int COL_HOVER_GLOW = -14932968;
   private static final int COL_ROW_ALT = -15198184;
   private static final int COL_MAPPED_BAR = -8470748;
   private static final int COL_BADGE_BG = -8470748;
   private final class_437 parent;
   private final ExecutorService executor = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
      Thread t = new Thread(r, "PackBuilder-IO");
      t.setDaemon(true);
      return t;
   }, new DiscardPolicy());
   private PackBuilderScreenBase.Step step = PackBuilderScreenBase.Step.SELECT;
   private static List<String> cachedCategories = new ArrayList<>();
   private static List<PackApiClient.BuilderResource> cachedResources = new ArrayList<>();
   private static final Map<String, class_2960> cachedThumbs = new ConcurrentHashMap<>();
   private static final Set<String> cachedThumbsLoading = ConcurrentHashMap.newKeySet();
   private static boolean cacheLoaded = false;
   private List<String> categories = new ArrayList<>();
   private List<PackApiClient.BuilderResource> allResources = new ArrayList<>();
   private List<PackApiClient.BuilderResource> filtered = new ArrayList<>();
   private String activeCategory = "";
   private final Set<String> selectedIds = new LinkedHashSet<>();
   private boolean loading = true;
   private String error = null;
   private float scroll1 = 0.0F;
   private float scrollTarget1 = 0.0F;
   private final List<PackApiClient.BuilderResource> mappingList = new ArrayList<>();
   private final Map<String, String> vanillaMapping = new LinkedHashMap<>();
   private final Map<String, class_342> mappingFields = new LinkedHashMap<>();
   private float scroll2 = 0.0F;
   private float scrollTarget2 = 0.0F;
   private class_342 nameField;
   private String statusMessage = null;
   private boolean building = false;
   private float openT = 0.0F;
   private final Map<String, Float> cardHoverProgress = new HashMap<>();
   private final Map<String, Float> selectionPulse = new HashMap<>();
   private float tabIndicatorX = 0.0F;
   private float tabIndicatorTargetX = 0.0F;
   private float globalTime = 0.0F;
   private float frameDt = 0.016F;
   private long lastFrameNanos = System.nanoTime();
   private int hoveredCardIndex = -1;
   private int lastClickedCard = -1;
   private float clickAnimTime = 0.0F;
   private final List<String> newlySelected = new ArrayList<>();
   private float shimmerPhase = 0.0F;
   private final Map<String, Float> thumbLoadProgress = new HashMap<>();
   private final InputCompat.Poller inputPoller = new InputCompat.Poller();

   protected PackBuilderScreenBase(class_437 parent) {
      super(class_2561.method_43470("Pack Builder"));
      this.parent = parent;
   }

   protected void method_25426() {
      super.method_25426();
      Ui.playOpen();
      this.nameField = new class_342(this.field_22793, 60, this.field_22790 - 60 + 20, 200, 18, class_2561.method_43470("Pack Name"));
      this.nameField.method_1880(64);
      this.nameField.method_1852("My Custom Pack");
      this.nameField.method_1862(this.step == PackBuilderScreenBase.Step.MAP);
      this.method_37063(this.nameField);
      if (this.step == PackBuilderScreenBase.Step.MAP) {
         this.rebuildMappingFields();
      }

      if (cacheLoaded && !cachedCategories.isEmpty() && !cachedResources.isEmpty()) {
         this.categories = cachedCategories;
         this.allResources = cachedResources;
         if (!this.categories.isEmpty() && this.activeCategory.isEmpty()) {
            this.activeCategory = this.categories.get(0);
         }

         this.applyFilter();
         this.loading = false;
      } else {
         cacheLoaded = false;
         this.fetchResources();
      }
   }

   private void fetchResources() {
      this.loading = true;
      this.error = null;
      String serverUrl = PackHubConfig.getServerUrl();
      this.executor.submit(() -> {
         try {
            PackApiClient.BuilderResourcesResponse resp = PackApiClient.fetchBuilderResources(serverUrl, null);
            class_310.method_1551().execute(() -> {
               cachedCategories = resp.categories != null ? resp.categories : List.of();
               cachedResources = resp.resources != null ? resp.resources : List.of();
               cacheLoaded = true;
               this.categories = cachedCategories;
               this.allResources = cachedResources;
               if (!this.categories.isEmpty()) {
                  this.activeCategory = this.categories.get(0);
               }

               this.applyFilter();
               this.loading = false;
            });
         } catch (Exception var31) {
            PackHubMod.LOGGER.error("Builder: fetch failed", var31);
            class_310.method_1551().execute(() -> {
               this.error = var31.getMessage();
               this.loading = false;
            });
         }
      });
   }

   private void reloadResources() {
      cacheLoaded = false;
      cachedThumbs.clear();
      cachedThumbsLoading.clear();
      this.selectedIds.clear();
      this.fetchResources();
      this.playClick();
   }

   private void applyFilter() {
      this.filtered = this.activeCategory.isEmpty() ? new ArrayList<>(this.allResources) : new ArrayList<>();
      if (!this.activeCategory.isEmpty()) {
         for (PackApiClient.BuilderResource r : this.allResources) {
            if (r.category.equalsIgnoreCase(this.activeCategory)) {
               this.filtered.add(r);
            }
         }
      }

      this.scroll1 = this.scrollTarget1 = 0.0F;
   }

   private void goToMapping() {
      this.mappingList.clear();
      this.vanillaMapping.clear();
      Map<String, PackApiClient.BuilderResource> lookup = new HashMap<>();

      for (PackApiClient.BuilderResource r : this.allResources) {
         lookup.put(r.id, r);
      }

      for (String id : this.selectedIds) {
         PackApiClient.BuilderResource r = lookup.get(id);
         if (r != null) {
            this.mappingList.add(r);
            this.vanillaMapping.put(id, "");
         }
      }

      this.step = PackBuilderScreenBase.Step.MAP;
      this.scroll2 = this.scrollTarget2 = 0.0F;
      this.nameField.method_1862(true);
      this.rebuildMappingFields();
      this.playClick();
   }

   private void goBackToSelect() {
      this.step = PackBuilderScreenBase.Step.SELECT;
      this.nameField.method_1862(false);

      for (class_342 f : this.mappingFields.values()) {
         this.method_37066(f);
      }

      this.mappingFields.clear();
      this.playClick();
   }

   private void rebuildMappingFields() {
      for (class_342 f : this.mappingFields.values()) {
         this.method_37066(f);
      }

      this.mappingFields.clear();
      int fieldX = this.field_22789 / 2 + 30;
      int fieldW = Math.min(240, this.field_22789 / 3);

      for (int i = 0; i < this.mappingList.size(); i++) {
         PackApiClient.BuilderResource r = this.mappingList.get(i);
         class_342 field = new class_342(this.field_22793, fieldX, 0, fieldW, 18, class_2561.method_43470("e.g. diamond_sword"));
         field.method_1880(80);
         field.method_1852(this.vanillaMapping.getOrDefault(r.id, ""));
         field.method_1862(false);
         String rid = r.id;
         field.method_1863(s -> this.vanillaMapping.put(rid, s));
         this.method_37063(field);
         this.mappingFields.put(r.id, field);
      }
   }

   public boolean onScrollDelta(double delta) {
      if (this.step == PackBuilderScreenBase.Step.SELECT) {
         this.scrollTarget1 = clamp(this.scrollTarget1 - (float)delta * 28.0F, 0.0F, (float)this.maxScroll1());
      } else {
         this.scrollTarget2 = clamp(this.scrollTarget2 - (float)delta * 28.0F, 0.0F, (float)this.maxScroll2());
      }

      return true;
   }

   private int columns() {
      return Math.max(1, (Math.min(this.field_22789 - 40, 760) + 10) / 98);
   }

   private int maxScroll1() {
      int rows = (this.filtered.size() + this.columns() - 1) / this.columns();
      return Math.max(0, rows * 98 - (this.field_22790 - 48 - 32 - 60) + 10);
   }

   private int maxScroll2() {
      return Math.max(0, this.mappingList.size() * 48 + 30 - (this.field_22790 - 48 - 60));
   }

   public boolean method_25402(double mx, double my, int button) {
      if (button != 0) {
         return InputCompat.delegateToChildren(this, mx, my, button);
      } else if (mx >= 8.0 && mx <= 48.0 && my >= 10.0 && my <= 36.0) {
         if (this.step == PackBuilderScreenBase.Step.MAP) {
            this.goBackToSelect();
            return true;
         } else {
            this.playClick();
            this.method_25419();
            return true;
         }
      } else {
         if (this.step == PackBuilderScreenBase.Step.SELECT) {
            if (this.tryClickTab(mx, my)) {
               return true;
            }

            if (this.tryClickGrid(mx, my)) {
               return true;
            }

            if (this.tryClickReload(mx, my)) {
               return true;
            }

            if (this.tryClickNext(mx, my)) {
               return true;
            }
         } else {
            if (this.tryClickInventoryPeek(mx, my)) {
               return true;
            }

            if (this.tryClickBuild(mx, my)) {
               return true;
            }
         }

         return InputCompat.delegateToChildren(this, mx, my, button);
      }
   }

   private boolean tryClickInventoryPeek(double mx, double my) {
      if (this.step != PackBuilderScreenBase.Step.MAP) {
         return false;
      } else {
         int fy = this.field_22790 - 60;
         int invW = 130;
         int invH = 22;
         int invX = 16;
         int invY = fy + 8;
         if (mx >= (double)invX && mx <= (double)(invX + invW) && my >= (double)invY && my <= (double)(invY + invH)) {
            class_310 mc = class_310.method_1551();
            if (mc.field_1724 != null) {
               mc.method_1507(new PackHubInventoryPeekScreen(mc.field_1724, this));
               this.playClick();
            } else {
               this.statusMessage = "Join a world to check inventory ids.";
               this.playClick();
            }

            return true;
         } else {
            return false;
         }
      }
   }

   private boolean tryClickTab(double mx, double my) {
      int tabY = 48;
      if (!(my < (double)tabY) && !(my > (double)(tabY + 32))) {
         int x = 16;

         for (String cat : this.categories) {
            String label = capitalize(cat);
            int tw = this.field_22793.method_1727(label) + 14;
            if (mx >= (double)x && mx <= (double)(x + tw)) {
               this.activeCategory = cat;
               this.applyFilter();
               this.playClick();
               return true;
            }

            x += tw + 4;
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean tryClickGrid(double mx, double my) {
      int listTop = 80;
      int listBot = this.field_22790 - 60;
      if (!(my < (double)listTop) && !(my > (double)listBot)) {
         int cols = this.columns();
         int contentW = cols * 88 + (cols - 1) * 10;
         int startX = (this.field_22789 - contentW) / 2;

         for (int i = 0; i < this.filtered.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = startX + col * 98;
            int cy = listTop + row * 98 - (int)this.scroll1;
            if (cy + 88 >= listTop && cy <= listBot && mx >= (double)cx && mx <= (double)(cx + 88) && my >= (double)cy && my <= (double)(cy + 88)) {
               String rid = this.filtered.get(i).id;
               if (!this.selectedIds.remove(rid)) {
                  this.selectedIds.add(rid);
                  Ui.playSuccess();
               }

               this.lastClickedCard = i;
               this.playClick();
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private boolean tryClickReload(double mx, double my) {
      if (this.loading) {
         return false;
      } else {
         int rbw = 80;
         int rbh = 28;
         int totalBtnsW = rbw + 8 + 120;
         int rbx = (this.field_22789 - totalBtnsW) / 2;
         int rby = this.field_22790 - 60 + 16;
         if (mx >= (double)rbx && mx <= (double)(rbx + rbw) && my >= (double)rby && my <= (double)(rby + rbh)) {
            Ui.addRipple(rbx + rbw / 2, rby + rbh / 2, -8470748);
            this.reloadResources();
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean tryClickNext(double mx, double my) {
      int rbw = 80;
      int totalBtnsW = rbw + 8 + 120;
      int btnW = 120;
      int btnH = 28;
      int btnX = (this.field_22789 - totalBtnsW) / 2 + rbw + 8;
      int btnY = this.field_22790 - 60 + 16;
      if (!this.selectedIds.isEmpty() && mx >= (double)btnX && mx <= (double)(btnX + btnW) && my >= (double)btnY && my <= (double)(btnY + btnH)) {
         Ui.addRipple(btnX + btnW / 2, btnY + btnH / 2, -8470748);
         this.goToMapping();
         return true;
      } else {
         return false;
      }
   }

   private boolean tryClickBuild(double mx, double my) {
      if (this.building) {
         return false;
      } else {
         int btnW = 120;
         int btnH = 28;
         int btnX = this.field_22789 - btnW - 16;
         int btnY = this.field_22790 - 60 + 8;
         if (mx >= (double)btnX && mx <= (double)(btnX + btnW) && my >= (double)btnY && my <= (double)(btnY + btnH)) {
            Ui.addRipple(btnX + btnW / 2, btnY + btnH / 2, -8470748);
            Ui.playSuccess();
            this.startBuild();
            return true;
         } else {
            return false;
         }
      }
   }

   private void startBuild() {
      List<PackApiClient.BuilderMapping> mappings = new ArrayList<>();

      for (PackApiClient.BuilderResource r : this.mappingList) {
         String vanilla = this.vanillaMapping.getOrDefault(r.id, "").trim();
         if (vanilla.isEmpty()) {
            this.statusMessage = "Type a vanilla item name for every texture!";
            return;
         }

         mappings.add(new PackApiClient.BuilderMapping(r.id, vanilla));
      }

      this.building = true;
      this.statusMessage = "Building pack...";
      this.playClick();
      String packName = this.nameField.method_1882().trim();
      if (packName.isEmpty()) {
         packName = "My Custom Pack";
      }

      String serverUrl = PackHubConfig.getServerUrl();
      String finalName = packName;
      this.executor.submit(() -> {
         try {
            byte[] zipData = PackApiClient.buildCustomPack(serverUrl, finalName, mappings);
            PackDownloader.applyBuiltPack(zipData, finalName);
            class_310.method_1551().execute(() -> {
               this.building = false;
               this.method_25419();
            });
         } catch (Exception var5x) {
            PackHubMod.LOGGER.error("Builder: build failed", var5x);
            class_310.method_1551().execute(() -> {
               this.building = false;
               this.statusMessage = "Build failed: " + var5x.getMessage();
            });
         }
      });
   }

   public void method_25419() {
      Ui.playClose();
      class_310.method_1551().method_1507(this.parent);
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      this.inputPoller.poll(mx, my, (x, y, btn) -> this.method_25402(x, y, btn), null);
      long nowNanos = System.nanoTime();
      float dt = Math.min(0.05F, (float)(nowNanos - this.lastFrameNanos) / 1.0E9F);
      this.lastFrameNanos = nowNanos;
      this.frameDt = dt;
      this.globalTime += dt;
      this.shimmerPhase += dt * 0.6F;
      if (this.clickAnimTime > 0.0F) {
         this.clickAnimTime -= dt;
      }

      this.openT = Math.min(1.0F, this.openT + dt * 2.0F);
      float ease = Ui.easeOutCubic(this.openT);
      float scale = 0.92F + 0.08F * ease;
      float smoothing = Math.min(1.0F, dt * 10.0F);
      if (this.step == PackBuilderScreenBase.Step.SELECT) {
         this.scroll1 = Ui.lerp(this.scroll1, this.scrollTarget1, smoothing);
      } else {
         this.scroll2 = Ui.lerp(this.scroll2, this.scrollTarget2, smoothing);
      }

      this.tabIndicatorX = Ui.lerp(this.tabIndicatorX, this.tabIndicatorTargetX, smoothing);
      DrawHelper.pushMatrices(ctx);
      DrawHelper.translateMatrices(ctx, (float)this.field_22789 / 2.0F * (1.0F - scale), (float)this.field_22790 / 2.0F * (1.0F - scale), 0.0F);
      DrawHelper.scaleMatrices(ctx, scale, scale, 1.0F);
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, -16119286);
      if (PackHubConfig.isBackgroundEffects()) {
         Ui.renderShootingStars(ctx, this.field_22789, this.field_22790, dt);
         Ui.renderStarfield(ctx, this.field_22789, this.field_22790, dt);
      }

      Ui.renderRipples(ctx, dt);
      Ui.renderSelectionParticles(ctx, dt);
      this.drawHeader(ctx, mx, my);
      if (this.step == PackBuilderScreenBase.Step.SELECT) {
         this.drawTabs(ctx, mx, my);
         this.drawGrid(ctx, mx, my, delta);
         this.drawFooterSelect(ctx, mx, my, delta);
      } else {
         this.drawMappingList(ctx, mx, my, delta);
      }

      DrawHelper.popMatrices(ctx);
      if (this.step == PackBuilderScreenBase.Step.MAP) {
         this.drawFooterMap(ctx, mx, my, delta);
         this.updateNameFieldLayout();
         this.updateFieldPositions();
      }

      for (class_364 child : this.method_25396()) {
         if (child instanceof class_4068 d) {
            d.method_25394(ctx, mx, my, delta);
         }
      }

      this.cardHoverProgress.entrySet().removeIf(e -> e.getValue() < 0.01F && !this.isCardVisible(e.getKey()));
      this.selectionPulse.entrySet().removeIf(e -> e.getValue() <= 0.0F);
   }

   private boolean isCardVisible(String id) {
      for (PackApiClient.BuilderResource r : this.filtered) {
         if (r.id.equals(id)) {
            return true;
         }
      }

      return false;
   }

   private void drawHeader(class_332 ctx, int mx, int my) {
      ctx.method_25294(0, 0, this.field_22789, 48, -15461356);
      Ui.waveShimmer(ctx, 0, 0, this.field_22789, 2, this.globalTime, -8470748);
      ctx.method_25294(0, 0, this.field_22789, 2, -8470748);
      int hairlineAlpha = (int)(40.0 + 10.0 * Math.sin((double)this.globalTime * 0.8));
      int hairlineColor = -14737633;
      ctx.method_25294(0, 47, this.field_22789, 48, hairlineColor);
      boolean bh = mx >= 8 && mx <= 48 && my >= 10 && my <= 36;
      if (bh) {
         Ui.neonTrail(ctx, 8, 10, 40, 26, this.globalTime, -8470748);
      }

      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "<", 14, 16, bh ? -8470748 : -7697782);
      String title = this.step == PackBuilderScreenBase.Step.SELECT ? "PACK BUILDER  //  SELECT TEXTURES" : "PACK BUILDER  //  ASSIGN VANILLA ITEMS";
      int titleX = 30;
      int titleY = 10;
      if (this.openT > 0.5F) {
         int titleGlowAlpha = (int)((this.openT - 0.5F) * 2.0F * 30.0F);
         int titleGlow = Ui.withAlpha(8306468, titleGlowAlpha);
         ctx.method_25294(titleX, titleY + 10, titleX + this.field_22793.method_1727(title), titleY + 14, titleGlow);
      }

      DrawHelper.drawTextWithShadow(ctx, this.field_22793, title, titleX, titleY, -1);
      if (this.step == PackBuilderScreenBase.Step.SELECT && !this.selectedIds.isEmpty()) {
         String badge = String.valueOf(this.selectedIds.size());
         int badgeW = this.field_22793.method_1727(badge) + 10;
         int badgeX = 30;
         int badgeY = 24;
         int badgeGlowAlpha = (int)(50.0 + 20.0 * Math.sin((double)this.globalTime * 1.2));
         int badgeGlow = Ui.withAlpha(8306468, badgeGlowAlpha);
         ctx.method_25294(badgeX - 2, badgeY - 2, badgeX + badgeW + 2, badgeY + 14, badgeGlow);
         ctx.method_25294(badgeX, badgeY, badgeX + badgeW, badgeY + 12, -8470748);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, badge, badgeX + 5, badgeY + 2, -1);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, " selected", badgeX + badgeW + 2, badgeY + 2, -7697782);
      } else {
         String statusText = this.step == PackBuilderScreenBase.Step.SELECT ? "0 selected" : this.mappingList.size() + " items to map";
         int statusColor = this.step == PackBuilderScreenBase.Step.SELECT ? -7697782 : -8470748;
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, statusText, 30, 26, statusColor);
      }

      int stepX = this.field_22789 - 100;
      int stepY = 16;
      int currentStep = this.step == PackBuilderScreenBase.Step.SELECT ? 0 : 1;
      Ui.animatedStepIndicator(ctx, stepX, stepY, 2, currentStep, this.globalTime, -8470748, -10855846);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Select", stepX - 5, stepY + 16, this.step == PackBuilderScreenBase.Step.SELECT ? -1 : -7697782);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Map", stepX + 45, stepY + 16, this.step == PackBuilderScreenBase.Step.MAP ? -1 : -7697782);
   }

   private void drawTabs(class_332 ctx, int mx, int my) {
      int y = 48;
      ctx.method_25294(0, y, this.field_22789, y + 32, -15461356);
      ctx.method_25294(0, y + 32 - 1, this.field_22789, y + 32, -14737633);
      int x = 16;
      int activeX = 16;
      int activeW = 0;

      for (String cat : this.categories) {
         String label = capitalize(cat);
         int tw = this.field_22793.method_1727(label) + 14;
         boolean act = cat.equals(this.activeCategory);
         boolean hov = mx >= x && mx <= x + tw && my >= y && my <= y + 32;
         if (act) {
            activeX = x;
            activeW = tw;
         }

         if (hov && !act) {
            int hoverGlow = Ui.withAlpha(8306468, 30);
            ctx.method_25294(x, y + 4, x + tw, y + 32 - 4, hoverGlow);
         }

         float textLerp = act ? 1.0F : (hov ? 0.7F : 0.0F);
         int textColor = Ui.lerpColor(-7697782, -1, textLerp);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, label, x + 7, y + 11, textColor);
         if (hov && !act) {
            int underAlpha = (int)(140.0 + 40.0 * Math.sin((double)this.globalTime * 1.0));
            int underColor = Ui.withAlpha(8306468, underAlpha);
            ctx.method_25294(x + 4, y + 32 - 4, x + tw - 4, y + 32 - 2, underColor);
         }

         x += tw + 4;
      }

      this.tabIndicatorTargetX = (float)activeX;
      if (this.tabIndicatorX == 0.0F) {
         this.tabIndicatorX = (float)activeX;
      }

      int indicatorX = (int)Ui.lerp(this.tabIndicatorX, this.tabIndicatorTargetX, 0.3F);
      this.tabIndicatorX = (float)indicatorX;
      int pulseAlpha = (int)(230.0 + 20.0 * Math.sin((double)this.globalTime * 1.0));
      int accentColor = Ui.withAlpha(8306468, pulseAlpha);
      ctx.method_25294(indicatorX, y + 32 - 3, indicatorX + activeW, y + 32 - 1, accentColor);
      int glowAlpha = (int)(40.0 + 15.0 * Math.sin((double)this.globalTime * 0.8));
      int glowColor = Ui.withAlpha(8306468, glowAlpha);
      ctx.method_25294(indicatorX, y + 32 - 5, indicatorX + activeW, y + 32 - 3, glowColor);
   }

   private void drawGrid(class_332 ctx, int mx, int my, float delta) {
      int top = 80;
      int bot = this.field_22790 - 60;
      ctx.method_44379(0, top, this.field_22789, bot);
      if (this.loading) {
         float loadProgress = this.globalTime * 0.5F % 1.0F;
         Ui.progressRing(ctx, this.field_22789 / 2, top + 50, 20, loadProgress, -8470748, -13421773);
         Ui.spinner(ctx, this.field_22789 / 2, top + 50, 12, (float)((double)(System.currentTimeMillis() % 2000L) / 2000.0), -8470748);
         this.drawCentered(ctx, "Loading resources...", top + 80, -7697782);
         ctx.method_44380();
      } else if (this.error != null) {
         this.drawCentered(ctx, "Error: " + this.error, top + 30, -1228219);
         ctx.method_44380();
      } else if (this.filtered.isEmpty()) {
         this.drawCentered(ctx, "No resources in this category yet.", top + 30, -7697782);
         ctx.method_44380();
      } else {
         int cols = this.columns();
         int cw = cols * 88 + (cols - 1) * 10;
         int sx = (this.field_22789 - cw) / 2;
         this.hoveredCardIndex = -1;

         for (int i = 0; i < this.filtered.size(); i++) {
            PackApiClient.BuilderResource res = this.filtered.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = sx + col * 98;
            int cy = top + row * 98 - (int)this.scroll1;
            if (cy + 88 >= top && cy <= bot) {
               boolean sel = this.selectedIds.contains(res.id);
               boolean hov = mx >= cx && mx <= cx + 88 && my >= cy && my <= cy + 88 && my >= top && my <= bot;
               if (hov) {
                  this.hoveredCardIndex = i;
               }

               float currentHover = this.cardHoverProgress.getOrDefault(res.id, 0.0F);
               float targetHover = hov ? 1.0F : 0.0F;
               currentHover = Ui.lerp(currentHover, targetHover, Math.min(1.0F, this.frameDt * 12.0F));
               this.cardHoverProgress.put(res.id, currentHover);
               float pulse = 0.0F;
               if (sel) {
                  pulse = this.selectionPulse.getOrDefault(res.id, 0.0F);
                  if (pulse > 0.0F) {
                     pulse -= this.frameDt * 3.0F;
                     this.selectionPulse.put(res.id, Math.max(0.0F, pulse));
                  }
               }

               int liftY = cy - (int)(currentHover * 3.0F);
               float shadowAlpha = currentHover * 0.5F;
               if (currentHover > 0.01F) {
                  int shadow = (int)(shadowAlpha * 60.0F) << 24;
                  ctx.method_25294(cx + 3, liftY + 88, cx + 88 + 3, liftY + 88 + 4, shadow | 0);
               }

               int bgColor = Ui.lerpColor(sel ? -15061488 : -15461356, -14932968, currentHover);
               ctx.method_25294(cx, liftY, cx + 88, liftY + 88, bgColor);
               if (sel) {
                  int borderColor = -8470748;
                  ctx.method_25294(cx, liftY, cx + 88, liftY + 2, borderColor);
                  ctx.method_25294(cx, liftY + 88 - 2, cx + 88, liftY + 88, borderColor);
                  ctx.method_25294(cx, liftY, cx + 2, liftY + 88, borderColor);
                  ctx.method_25294(cx + 88 - 2, liftY, cx + 88, liftY + 88, borderColor);
               } else if (currentHover > 0.1F) {
                  int hoverBorder = Ui.withAlpha(8306468, (int)(currentHover * 80.0F));
                  ctx.method_25294(cx, liftY, cx + 88, liftY + 1, hoverBorder);
                  ctx.method_25294(cx, liftY + 88 - 1, cx + 88, liftY + 88, hoverBorder);
               }

               int tx = cx + 12;
               int ty = liftY + 6;
               class_2960 tex = cachedThumbs.get(res.id);
               if (tex != null) {
                  float loadProg = this.thumbLoadProgress.getOrDefault(res.id, 0.0F);
                  loadProg = Math.min(1.0F, loadProg + this.frameDt * 4.0F);
                  this.thumbLoadProgress.put(res.id, loadProg);
                  if (loadProg >= 1.0F) {
                     DrawHelper.drawTexture(ctx, tex, tx, ty, 0.0F, 0.0F, 64, 64, 64, 64);
                  } else {
                     Ui.thumbnailFadeIn(ctx, tx, ty, 64, 64, loadProg, -14540254);
                     DrawHelper.drawTexture(ctx, tex, tx, ty, 0.0F, 0.0F, 64, 64, 64, 64);
                  }

                  if (currentHover > 0.5F) {
                     int glowAlpha = (int)((currentHover - 0.5F) * 2.0F * 60.0F);
                     int glow = Ui.withAlpha(8306468, glowAlpha);
                     ctx.method_25294(tx - 2, ty - 2, tx + 64 + 2, ty, glow);
                     ctx.method_25294(tx - 2, ty + 64, tx + 64 + 2, ty + 64 + 2, glow);
                  }
               } else {
                  ctx.method_25294(tx, ty, tx + 64, ty + 64, -14540254);
                  Ui.shimmer(ctx, tx, ty, 64, 64, (this.globalTime * 0.5F + (float)i * 0.05F) % 1.0F, -12303292);
                  this.loadThumb(res);
               }

               String nm = this.truncate(res.name, 82);
               int nameColor = Ui.lerpColor(-7697782, -1, sel ? 1.0F : currentHover);
               int nameY = liftY + 88 - 14;
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, nm, cx + (88 - this.field_22793.method_1727(nm)) / 2, nameY, nameColor);
               if (sel) {
                  DrawHelper.drawTextWithShadow(ctx, this.field_22793, "✓", cx + 88 - 12, liftY + 4, -8470748);
               }
            }
         }

         ctx.method_44380();
         if (this.maxScroll1() > 0) {
            if (this.scroll1 > 5.0F) {
               Ui.scrollIndicator(ctx, this.field_22789 / 2, top - 12, true, this.globalTime, -8470748);
            }

            if (this.scroll1 < (float)(this.maxScroll1() - 5)) {
               Ui.scrollIndicator(ctx, this.field_22789 / 2, bot + 8, false, this.globalTime, -8470748);
            }
         }

         this.drawScrollbar(ctx, top, bot, this.scroll1, this.maxScroll1());
      }
   }

   private void drawFooterSelect(class_332 ctx, int mx, int my, float delta) {
      int fy = this.field_22790 - 60;
      ctx.method_25294(0, fy, this.field_22789, this.field_22790, -15461356);
      ctx.method_25294(0, fy, this.field_22789, fy + 1, -14737633);
      int count = this.selectedIds.size();
      String countText = count + " textures selected";
      int countColor = count > 0 ? -8470748 : -7697782;
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, countText, 16, fy + 24, countColor);
      if (count > 0) {
         int maxDisplay = 20;
         int barW = Math.min(count, maxDisplay) * 8;
         int barY = fy + 38;
         ctx.method_25294(16, barY, 16 + barW, barY + 3, -15461356);

         for (int i = 0; i < Math.min(count, maxDisplay); i++) {
            int segX = 16 + i * 8;
            int segAlpha = (int)(210.0 + 40.0 * Math.sin((double)this.globalTime * 0.6 + (double)i * 0.3));
            int segColor = Ui.withAlpha(8306468, segAlpha);
            ctx.method_25294(segX, barY, segX + 6, barY + 3, segColor);
         }
      }

      int rbw = 80;
      int rbh = 28;
      int totalBtnsW = rbw + 8 + 120;
      int rbx = (this.field_22789 - totalBtnsW) / 2;
      int rby = fy + 16;
      boolean rHov = mx >= rbx && mx <= rbx + rbw && rby <= my && my <= rby + rbh;
      ctx.method_25294(rbx + 2, rby + 2, rbx + rbw + 2, rby + rbh + 2, 1140850688);
      int reloadBg = rHov ? -12961222 : -14737633;
      if (rHov) {
         Ui.pulseGlow(ctx, rbx, rby, rbw, rbh, this.globalTime, -12961222, -8470748);
      }

      ctx.method_25294(rbx, rby, rbx + rbw, rby + rbh, reloadBg);
      String reloadLabel = this.loading ? "Reloading..." : "↻ Reload";
      int reloadColor = this.loading ? -10855846 : (rHov ? -1 : -7697782);
      if (this.loading) {
         Ui.spinner(ctx, rbx + 12, rby + rbh / 2, 6, this.globalTime, -8470748);
      }

      DrawHelper.drawTextWithShadow(
         ctx,
         this.field_22793,
         reloadLabel,
         rbx + (this.loading ? 24 : (rbw - this.field_22793.method_1727(reloadLabel)) / 2),
         rby + (rbh - 9) / 2,
         reloadColor
      );
      int bw = 120;
      int bh = 28;
      int bx = rbx + rbw + 8;
      int by = fy + 16;
      boolean can = !this.selectedIds.isEmpty();
      boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
      ctx.method_25294(bx + 2, by + 2, bx + bw + 2, by + bh + 2, 1140850688);
      int btnBaseColor = can ? (hov ? -8470748 : -10843622) : -13421773;
      ctx.method_25294(bx, by, bx + bw, by + bh, btnBaseColor);
      if (can && hov) {
         Ui.neonTrail(ctx, bx, by, bw, bh, this.globalTime, -8470748);
         Ui.shimmer(ctx, bx, by, bw, bh, this.globalTime * 0.8F % 1.0F, Ui.withAlpha(8306468, 60));
      }

      if (!can) {
         int disabledAlpha = 25;
         int disabledOverlay = Ui.withAlpha(0, disabledAlpha);
         ctx.method_25294(bx, by, bx + bw, by + bh, disabledOverlay);
      }

      int btnTextColor = can ? -1 : -10855846;
      String btnText = "Next →";
      if (can && hov) {
         int arrowOffset = (int)(2.0 * Math.sin((double)this.globalTime * 1.5));
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Next", bx + (bw - this.field_22793.method_1727("Next →")) / 2, by + (bh - 9) / 2, btnTextColor);
         DrawHelper.drawTextWithShadow(
            ctx, this.field_22793, "→", bx + (bw + this.field_22793.method_1727("Next ")) / 2 + arrowOffset, by + (bh - 9) / 2, btnTextColor
         );
      } else {
         this.drawBtnLabel(ctx, btnText, bx, by, bw, bh, btnTextColor);
      }
   }

   private void drawMappingList(class_332 ctx, int mx, int my, float delta) {
      int top = 48;
      int bot = this.field_22790 - 60;
      ctx.method_44379(0, top, this.field_22789, bot);
      int nameCol = 68;
      int arrowX = this.field_22789 / 2 + 8;
      int fieldX = this.field_22789 / 2 + 30;
      int hy = top + 4 - (int)this.scroll2;
      if (hy > top - 16) {
         int headerShimmer = Ui.withAlpha(5921370, (int)(180.0 + 40.0 * Math.sin((double)this.globalTime * 0.8)));
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "TEXTURE", nameCol, hy, headerShimmer);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "REPLACES (type vanilla item name)", fieldX, hy, -10855846);
      }

      float arrowFloat = (float)Math.sin((double)this.globalTime * 0.8) * 1.5F;

      for (int i = 0; i < this.mappingList.size(); i++) {
         PackApiClient.BuilderResource r = this.mappingList.get(i);
         int cy = top + 20 + i * 48 - (int)this.scroll2;
         if (cy + 48 >= top && cy <= bot) {
            if (i % 2 == 1) {
               ctx.method_25294(0, cy, this.field_22789, cy + 48, -15198184);
            }

            boolean rowHover = mx >= 0 && mx <= this.field_22789 && my >= cy && my < cy + 48 && my >= top && my <= bot;
            if (rowHover) {
               int rowGlow = Ui.withAlpha(8306468, 20);
               ctx.method_25294(0, cy, this.field_22789, cy + 48, rowGlow);
            }

            String val = this.vanillaMapping.getOrDefault(r.id, "");
            if (!val.isEmpty()) {
               int barAlpha = (int)(220.0 + 30.0 * Math.sin((double)this.globalTime * 0.8 + (double)i * 0.5));
               int barColor = Ui.withAlpha(8306468, barAlpha);
               ctx.method_25294(0, cy, 3, cy + 48, barColor);
               int glowAlpha = (int)(15.0 + 8.0 * Math.sin((double)this.globalTime * 0.6 + (double)i));
               int glowColor = Ui.withAlpha(8306468, glowAlpha);
               ctx.method_25294(3, cy, 20, cy + 48, glowColor);
            }

            class_2960 tex = cachedThumbs.get(r.id);
            int thumbSize = 40;
            int thumbY = cy + (48 - thumbSize) / 2;
            if (tex != null) {
               float loadProg = this.thumbLoadProgress.getOrDefault(r.id, 0.0F);
               if (loadProg < 1.0F) {
                  loadProg = Math.min(1.0F, loadProg + this.frameDt * 5.0F);
                  this.thumbLoadProgress.put(r.id, loadProg);
               }

               DrawHelper.drawTexture(ctx, tex, 16, thumbY, 0.0F, 0.0F, thumbSize, thumbSize, thumbSize, thumbSize);
            } else {
               ctx.method_25294(16, thumbY, 16 + thumbSize, thumbY + thumbSize, -14540254);
               Ui.shimmer(ctx, 16, thumbY, thumbSize, thumbSize, (this.globalTime * 0.3F + (float)i * 0.05F) % 1.0F, -12303292);
               this.loadThumb(r);
            }

            int nameColor = rowHover ? Ui.lerpColor(-1, -8470748, 0.3F) : -1;
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, this.truncate(r.name, this.field_22789 / 3 - 10), nameCol, cy + 10, nameColor);
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, r.category, nameCol, cy + 24, -10855846);
            int arrowY = cy + 16 + (int)arrowFloat;
            int arrowAlpha = (int)(220.0 + 30.0 * Math.sin((double)this.globalTime * 0.8 + (double)i * 0.3));
            int arrowColor = Ui.withAlpha(8306468, arrowAlpha);
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, "➤", arrowX, arrowY, arrowColor);
            if (!val.isEmpty()) {
               float checkScale = 1.0F + (float)Math.sin((double)this.globalTime * 0.8 + (double)i) * 0.05F;
               int checkY = cy + 16 + (int)((checkScale - 1.0F) * 4.0F);
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, "✓", this.field_22789 - 20, checkY, -8470748);
            }

            int lineAlpha = rowHover ? 100 : 40;
            int lineColor = Ui.withAlpha(2039583, lineAlpha);
            ctx.method_25294(16, cy + 48 - 1, this.field_22789 - 16, cy + 48, lineColor);
         }
      }

      if (this.maxScroll2() > 0) {
         if (this.scroll2 > 5.0F) {
            Ui.scrollIndicator(ctx, this.field_22789 / 2, top - 8, true, this.globalTime, -8470748);
         }

         if (this.scroll2 < (float)(this.maxScroll2() - 5)) {
            Ui.scrollIndicator(ctx, this.field_22789 / 2, bot + 6, false, this.globalTime, -8470748);
         }
      }

      ctx.method_44380();
      this.drawScrollbar(ctx, top, bot, this.scroll2, this.maxScroll2());
   }

   private void updateNameFieldLayout() {
      if (this.step == PackBuilderScreenBase.Step.MAP && this.nameField != null) {
         int fy = this.field_22790 - 60;
         this.nameField.method_46421(92);
         this.nameField.method_46419(fy + 32);
         this.nameField.method_25358(Math.max(80, this.field_22789 - 92 - 140));
      }
   }

   private void updateFieldPositions() {
      int fieldX = this.field_22789 / 2 + 30;
      int fieldW = Math.min(240, this.field_22789 / 3);
      int top = 48;

      for (int i = 0; i < this.mappingList.size(); i++) {
         PackApiClient.BuilderResource r = this.mappingList.get(i);
         class_342 field = this.mappingFields.get(r.id);
         if (field != null) {
            int cy = top + 20 + i * 48 - (int)this.scroll2;
            field.method_46421(fieldX);
            field.method_46419(cy + 12);
            field.method_25358(fieldW);
            field.method_1862(cy + 20 > top && cy < this.field_22790 - 60 && this.step == PackBuilderScreenBase.Step.MAP);
         }
      }
   }

   private void drawFooterMap(class_332 ctx, int mx, int my, float delta) {
      int fy = this.field_22790 - 60;
      ctx.method_25294(0, fy, this.field_22789, this.field_22790, -15461356);
      ctx.method_25294(0, fy, this.field_22789, fy + 1, -14737633);
      int invW = 130;
      int invH = 22;
      int invX = 16;
      int invY = fy + 8;
      boolean invHov = mx >= invX && mx <= invX + invW && my >= invY && my <= invY + invH;
      int invBg = invHov ? -8470748 : -10843622;
      ctx.method_25294(invX, invY, invX + invW, invY + invH, invBg);
      DrawHelper.drawTextWithShadow(
         ctx, this.field_22793, "CHECK INV", invX + (invW - this.field_22793.method_1727("CHECK INV")) / 2, invY + (invH - 9) / 2, -1
      );
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Pack name:", 16, fy + 34, -7697782);
      int bw = 120;
      int bh = 28;
      int bx = this.field_22789 - bw - 16;
      int by = fy + 8;
      boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
      ctx.method_25294(bx + 2, by + 2, bx + bw + 2, by + bh + 2, 1140850688);
      if (this.building) {
         ctx.method_25294(bx, by, bx + bw, by + bh, -10843622);
         float buildProgress = this.globalTime * 0.5F % 1.0F;
         Ui.progressRing(ctx, bx + 14, by + bh / 2, 10, buildProgress, -8470748, -13421773);
         Ui.spinner(ctx, bx + 14, by + bh / 2, 8, this.globalTime * 2.0F, -1);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Building...", bx + 28, by + (bh - 9) / 2, -1);
         Ui.shimmer(ctx, bx, by, bw, bh, this.globalTime % 1.0F, 1627389951);
      } else {
         int btnColor = hov ? -8470748 : -10843622;
         ctx.method_25294(bx, by, bx + bw, by + bh, btnColor);
         if (hov) {
            Ui.neonTrail(ctx, bx, by, bw, bh, this.globalTime, -8470748);
            Ui.pulseGlow(ctx, bx, by, bw, bh, this.globalTime, -8470748, -6236096);
            Ui.shimmer(ctx, bx, by, bw, bh, this.globalTime * 0.5F % 1.0F, Ui.withAlpha(16777215, 40));
         }

         String btnText = "⚒ Build";
         int iconY = by + (bh - 9) / 2;
         if (hov) {
            float hammerBounce = (float)Math.sin((double)this.globalTime * 1.5) * 1.5F;
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, "⚒", bx + 12, iconY + (int)hammerBounce, -1);
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, "Build", bx + 28, iconY, -1);
         } else {
            this.drawBtnLabel(ctx, btnText, bx, by, bw, bh, -1);
         }
      }

      if (this.statusMessage != null) {
         String truncated = this.truncate(this.statusMessage, this.field_22789 - 40);
         int statusColor = this.statusMessage.contains("failed") ? -1228219 : (this.statusMessage.contains("Building") ? Ui.lerpColor(-1, -8470748, 0.5F) : -1);
         if (this.statusMessage.contains("failed")) {
            int errorAlpha = (int)(220.0 + 35.0 * Math.sin((double)this.globalTime * 1.5));
            statusColor = Ui.withAlpha(15548997, errorAlpha);
         }

         DrawHelper.drawTextWithShadow(ctx, this.field_22793, truncated, 16, fy + 6, statusColor);
         if (this.building && (int)(this.globalTime * 4.0F) % 2 == 0) {
            int cursorX = 16 + this.field_22793.method_1727(truncated);
            ctx.method_25294(cursorX, fy + 6, cursorX + 2, fy + 15, -8470748);
         }
      }

      int mappedCount = 0;

      for (PackApiClient.BuilderResource r : this.mappingList) {
         String val = this.vanillaMapping.getOrDefault(r.id, "");
         if (!val.isEmpty()) {
            mappedCount++;
         }
      }

      if (!this.mappingList.isEmpty()) {
         int progressY = fy + 52;
         int totalW = this.field_22789 - 32;
         int progressW = (int)((float)mappedCount / (float)this.mappingList.size() * (float)totalW);
         ctx.method_25294(16, progressY, 16 + totalW, progressY + 3, -15066598);
         if (progressW > 0) {
            ctx.method_25294(16, progressY, 16 + progressW, progressY + 3, -8470748);
         }

         String pct = mappedCount + "/" + this.mappingList.size();
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, pct, 16 + totalW + 6, progressY - 2, -7697782);
      }
   }

   private void drawScrollbar(class_332 ctx, int top, int bot, float scroll, int maxScroll) {
      if (maxScroll > 0) {
         int h = bot - top;
         int barH = Math.max(16, h * h / (h + maxScroll));
         int barY = top + (int)((float)(h - barH) * (scroll / (float)maxScroll));
         ctx.method_25294(this.field_22789 - 4, top, this.field_22789 - 1, bot, -15658735);
         ctx.method_25294(this.field_22789 - 4, barY, this.field_22789 - 1, barY + barH, -10855846);
      }
   }

   private void drawBtnLabel(class_332 ctx, String text, int x, int y, int w, int h, int color) {
      int tw = this.field_22793.method_1727(text);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, text, x + (w - tw) / 2, y + (h - 9) / 2, color);
   }

   private void drawCentered(class_332 ctx, String text, int y, int color) {
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, text, (this.field_22789 - this.field_22793.method_1727(text)) / 2, y, color);
   }

   private void loadThumb(PackApiClient.BuilderResource res) {
      if (!cachedThumbsLoading.contains(res.id)) {
         cachedThumbsLoading.add(res.id);
         this.executor.submit(() -> {
            try {
               byte[] data = PackApiClient.downloadBytes(res.url);
               class_310 mc = class_310.method_1551();
               mc.execute(() -> {
                  try {
                     class_1011 img = class_1011.method_4309(new ByteArrayInputStream(data));
                     String texName = "packhub_builder_" + res.id.replace("-", "_");
                     class_1043 tex = DrawHelper.createNativeTexture(texName, img);
                     if (tex == null) {
                        return;
                     }

                     class_2960 id = class_2960.method_60655("packhub", "builder/" + res.id.replace("-", "_"));
                     mc.method_1531().method_4616(id, tex);
                     cachedThumbs.put(res.id, id);
                  } catch (Exception var7) {
                  }
               });
            } catch (Exception var3) {
            }
         });
      }
   }

   private void playClick() {
      Ui.playClick();
   }

   private String truncate(String s, int maxPx) {
      if (this.field_22793.method_1727(s) <= maxPx) {
         return s;
      } else {
         while (s.length() > 1 && this.field_22793.method_1727(s + "..") > maxPx) {
            s = s.substring(0, s.length() - 1);
         }

         return s + "..";
      }
   }

   private static String capitalize(String s) {
      return s != null && !s.isEmpty() ? Character.toUpperCase(s.charAt(0)) + s.substring(1) : s;
   }

   private static float clamp(float v, float min, float max) {
      return Math.max(min, Math.min(max, v));
   }

   private static enum Step {
      SELECT,
      MAP;

      private Step() {
      }
   }
}
