package com.packhub;

import com.packhub.compat.DrawHelper;
import com.packhub.compat.InputCompat;
import com.packhub.ui.CustomButton;
import com.packhub.ui.CustomButtonBase;
import com.packhub.ui.Ui;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

public abstract class PackHubScreenBase extends class_437 {
   private static final int HEADER = 52;
   private static final int TOOLBAR = 44;
   private static final int FOOTER = 44;
   private static final int CARD_H = 84;
   private static final int CARD_PAD = 0;
   private static final int THUMB_SIZE = 64;
   private static final int BTN_W = 110;
   private static final int BTN_H = 26;
   private static final int STAR_W = 64;
   private static final int EDIT_W = 48;
   private static final int CARD_BTN_GAP = 8;
   private static final int GEAR_SIZE = 18;
   private static final String[] FILTER_TAGS = new String[]{"All", "Applied", "Smp", "NethPot", "CPVP", "ElyPVP"};
   private static final String TAG_APPLIED = "Applied";
   private static final int COL_BG = -16119286;
   private static final int COL_SURFACE = -15658735;
   private static final int COL_SURFACE_HL = -15329770;
   private static final int COL_TEXT = -1;
   private static final int COL_MUTED = -7697782;
   private static final int COL_DIM = -10855846;
   private static final int COL_HAIRLINE = -14737633;
   private static final int COL_HAIRLINE_HOT = -12961222;
   private static final int COL_ACCENT = -8470748;
   private static final int COL_ACCENT_2 = -6236096;
   private static final int COL_GREEN = -8470748;
   private static final int COL_RED = -42663;
   private static final int COL_CARD = -15658735;
   private static final int COL_CARD_HL = -15329770;
   private static final int COL_DIVIDER = -14737633;
   private final class_437 parent;
   private final ExecutorService executor = new ThreadPoolExecutor(
      0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), Executors.defaultThreadFactory(), new DiscardPolicy()
   );
   private List<Pack> packs = Collections.emptyList();
   private final Map<String, PackHubScreenBase.Thumb> thumbs = new HashMap<>();
   private final Map<String, PackHubScreenBase.DlState> dlState = new HashMap<>();
   private final Map<String, String> dlMsg = new HashMap<>();
   private final Map<String, Float> dlProgress = new HashMap<>();
   private final Map<String, Float> dlProgressUi = new HashMap<>();
   private final Map<String, Float> cardHoverT = new HashMap<>();
   private final Map<String, Float> btnHoverT = new HashMap<>();
   private final Map<String, Float> thumbAppearT = new HashMap<>();
   private Set<String> activeIds = new LinkedHashSet<>();
   private boolean loading = true;
   private String error = null;
   private double scroll = 0.0;
   private double scrollTarget = 0.0;
   private long openedAt;
   private final Set<String> activeTags = new LinkedHashSet<>();
   private class_342 searchField;
   private final Map<String, Float> tagHoverT = new HashMap<>();
   private float gearHoverT = 0.0F;
   private float randomHoverT = 0.0F;
   private float starHoverT = 0.0F;
   private final Set<String> starInFlight = ConcurrentHashMap.newKeySet();
   private String confirmRemoveId = null;
   private long confirmRemoveAt = 0L;
   private float cardEntryT = 0.0F;
   private int builderBtnX;
   private int builderBtnY;
   private int builderBtnW;
   private int builderBtnH;
   private int killFxBtnX;
   private int killFxBtnY;
   private int killFxBtnW;
   private int killFxBtnH;
   private int featherBtnX;
   private int featherBtnY;
   private int featherBtnW;
   private int featherBtnH;
   private static final int COL_ACCENT_DIM = -12961222;
   private List<Pack> filteredCache = null;
   private String filteredCacheTag = null;
   private String filteredCacheQuery = null;
   private List<Pack> filteredCacheSource = null;
   private String pendingTooltip = null;
   private int pendingTooltipMx = 0;
   private int pendingTooltipMy = 0;
   private Pack previewPack = null;
   private float previewT = 0.0F;
   private final InputCompat.Poller inputPoller = new InputCompat.Poller();
   private float frameAccentT;
   private float frameDonePulseT;

   public PackHubScreenBase(class_437 parent) {
      super(class_2561.method_43470("PackHub"));
      this.parent = parent;
   }

   protected void method_25426() {
      this.openedAt = System.currentTimeMillis();
      this.activeIds = PackDownloader.getActivePackIds();
      int bw = 120;
      int bh = 26;
      int by = this.field_22790 - 44 + (44 - bh) / 2;
      int gap = 12;
      int totalBtnW = bw * 3 + gap * 2;
      int btnStartX = this.field_22789 / 2 - totalBtnW / 2;
      this.method_37063(new CustomButton(btnStartX, by, bw, bh, class_2561.method_43470("RECONNECT"), CustomButtonBase.Style.SECONDARY, this::reconnect));
      this.method_37063(
         new CustomButton(btnStartX + bw + gap, by, bw, bh, class_2561.method_43470("RANDOM PACK"), CustomButtonBase.Style.SECONDARY, this::pickRandomPack)
      );
      this.method_37063(
         new CustomButton(btnStartX + (bw + gap) * 2, by, bw, bh, class_2561.method_43470("CLOSE"), CustomButtonBase.Style.PRIMARY, this::method_25419)
      );
      int searchW = Math.min(260, Math.max(140, this.field_22789 / 4));
      int searchH = 18;
      int searchX = 18;
      int searchY = 52 + (44 - searchH) / 2;
      String prevQuery = this.searchField != null ? this.searchField.method_1882() : "";
      this.searchField = new class_342(this.field_22793, searchX, searchY, searchW, searchH, class_2561.method_43470("Search packs"));
      this.searchField.method_1880(64);
      this.searchField.method_47404(class_2561.method_43470("Search packs, authors, tags..."));
      this.searchField.method_1858(false);
      this.searchField.method_1868(16777215);
      this.searchField.method_1860(8421504);
      this.searchField.method_1852(prevQuery);
      this.searchField.method_1863(t -> {
         this.scroll = 0.0;
         this.scrollTarget = 0.0;
      });
      this.method_37063(this.searchField);
      this.loading = true;
      this.error = null;
      this.executor.submit(() -> {
         PackHubConfig.tryAutoDiscover();
         this.fetchPacks();
      });
   }

   private void drawScreenBackground(class_332 ctx, float delta) {
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, -16119286);
      if (PackHubConfig.isBackgroundEffects()) {
         Ui.renderShootingStars(ctx, this.field_22789, this.field_22790, delta);
      }
   }

   public void method_25419() {
      if (InputCompat.needsPolling()) {
         if (this.previewPack != null) {
            this.previewPack = null;
            return;
         }

         if (this.searchField != null && this.searchField.method_25370() && !this.searchField.method_1882().isEmpty()) {
            this.searchField.method_1852("");
            return;
         }
      }

      this.executor.shutdownNow();
      this.thumbs.values().forEach(t -> class_310.method_1551().method_1531().method_4615(t.id()));
      class_310.method_1551().method_1507(this.parent);
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      this.inputPoller.poll(mx, my, (x, y, btn) -> this.method_25402(x, y, btn), null);
      long now = System.currentTimeMillis();
      this.frameAccentT = (float)(0.5 + 0.5 * Math.sin((double)now / 700.0));
      this.frameDonePulseT = (float)(0.5 + 0.5 * Math.sin((double)now / 250.0));
      this.scroll = this.scroll + (this.scrollTarget - this.scroll) * (double)Math.min(1.0F, delta * 0.35F);

      for (Entry<String, Float> e : this.dlProgress.entrySet()) {
         float cur = this.dlProgressUi.getOrDefault(e.getKey(), 0.0F);
         cur += (e.getValue() - cur) * Math.min(1.0F, delta * 0.2F);
         this.dlProgressUi.put(e.getKey(), cur);
      }

      if (!this.loading && this.error == null && !this.packs.isEmpty() && this.cardEntryT < 1.0F) {
         this.cardEntryT = Math.min(1.0F, this.cardEntryT + delta * 0.06F);
      }

      float pTarget = this.previewPack != null ? 1.0F : 0.0F;
      this.previewT = this.previewT + (pTarget - this.previewT) * Math.min(1.0F, delta * 0.35F);
      this.pendingTooltip = null;
      this.drawScreenBackground(ctx, delta);
      this.renderHeader(ctx, delta);
      this.renderToolbar(ctx, mx, my, delta);
      this.renderPackList(ctx, mx, my, delta);
      this.renderFooter(ctx);

      for (class_364 child : this.method_25396()) {
         if (child instanceof class_4068 d) {
            d.method_25394(ctx, mx, my, delta);
         }
      }

      if (this.pendingTooltip != null) {
         this.drawTooltip(ctx, this.pendingTooltip, this.pendingTooltipMx, this.pendingTooltipMy);
      }

      if (this.previewT > 0.01F) {
         this.renderPreviewOverlay(ctx, mx, my);
      }
   }

   private void renderHeader(class_332 ctx, float delta) {
      ctx.method_25294(0, 51, this.field_22789, 52, -14737633);
      int padX = 18;
      DrawHelper.drawText(ctx, this.field_22793, "PACKHUB", padX, 18, -1, false);
      DrawHelper.drawText(ctx, this.field_22793, "//", padX + this.field_22793.method_1727("PACKHUB") + 6, 18, -10855846, false);
      DrawHelper.drawText(ctx, this.field_22793, "TEXTURE LIBRARY", padX + this.field_22793.method_1727("PACKHUB") + 18, 18, -7697782, false);
      String builderLabel = "BUILDER";
      int blw = this.field_22793.method_1727(builderLabel);
      this.builderBtnX = padX
         + this.field_22793.method_1727("PACKHUB")
         + this.field_22793.method_1727("//")
         + this.field_22793.method_1727("TEXTURE LIBRARY")
         + 42;
      this.builderBtnY = 14;
      this.builderBtnW = blw + 12;
      this.builderBtnH = 16;
      ctx.method_25294(this.builderBtnX, this.builderBtnY, this.builderBtnX + this.builderBtnW, this.builderBtnY + this.builderBtnH, -12961222);
      DrawHelper.drawText(ctx, this.field_22793, builderLabel, this.builderBtnX + 6, this.builderBtnY + 4, -1, false);
      String killFxLabel = "KILL FX";
      int kfw = this.field_22793.method_1727(killFxLabel);
      this.killFxBtnX = this.builderBtnX + this.builderBtnW + 6;
      this.killFxBtnY = 14;
      this.killFxBtnW = kfw + 12;
      this.killFxBtnH = 16;
      ctx.method_25294(this.killFxBtnX, this.killFxBtnY, this.killFxBtnX + this.killFxBtnW, this.killFxBtnY + this.killFxBtnH, -12961222);
      DrawHelper.drawText(ctx, this.field_22793, killFxLabel, this.killFxBtnX + 6, this.killFxBtnY + 4, -1, false);
      String featherLabel = "FEATHER";
      int ftw = this.field_22793.method_1727(featherLabel);
      this.featherBtnX = this.killFxBtnX + this.killFxBtnW + 6;
      this.featherBtnY = 14;
      this.featherBtnW = ftw + 12;
      this.featherBtnH = 16;
      ctx.method_25294(this.featherBtnX, this.featherBtnY, this.featherBtnX + this.featherBtnW, this.featherBtnY + this.featherBtnH, -12961222);
      DrawHelper.drawText(ctx, this.field_22793, featherLabel, this.featherBtnX + 6, this.featherBtnY + 4, -1, false);
      if (!this.loading && this.error == null) {
         boolean ok = PackHubConfig.isConfigured();
         String state = ok ? "LIVE" : "OFFLINE";
         String count = ok && !this.packs.isEmpty() ? "  " + this.packs.size() + (this.packs.size() == 1 ? " PACK" : " PACKS") : "";
         int dotColor = ok ? -8470748 : -42663;
         String label = state + count;
         int lw = this.field_22793.method_1727(label);
         int rx = this.field_22789 - padX - lw;
         int ry = 18;
         ctx.method_25294(rx - 10, ry + 1, rx - 6, ry + 5, dotColor);
         DrawHelper.drawText(ctx, this.field_22793, label, rx, ry, -7697782, false);
      }
   }

   private void renderFooter(class_332 ctx) {
      ctx.method_25294(0, this.field_22790 - 44, this.field_22789, this.field_22790 - 44 + 1, -14737633);
   }

   private void renderToolbar(class_332 ctx, int mx, int my, float delta) {
      int top = 52;
      int bot = 96;
      ctx.method_25294(0, bot - 1, this.field_22789, bot, -14737633);
      if (this.searchField != null) {
         int sx = this.searchField.method_46426();
         int sy = this.searchField.method_46427() + this.searchField.method_25364() + 2;
         int sw = this.searchField.method_25368();
         int line = this.searchField.method_25370() ? -1 : -12961222;
         ctx.method_25294(sx, sy, sx + sw, sy + 1, line);
      }

      int[] widths = new int[FILTER_TAGS.length];
      int totalW = 0;
      int gap = 18;

      for (int i = 0; i < FILTER_TAGS.length; i++) {
         widths[i] = this.field_22793.method_1727(FILTER_TAGS[i]);
         totalW += widths[i];
      }

      totalW += gap * (FILTER_TAGS.length - 1);
      int tabsRight = this.field_22789 - 18 - 18 - 12;
      int tabsLeft = tabsRight - totalW;
      int searchRight = this.searchField != null ? this.searchField.method_46426() + this.searchField.method_25368() + 24 : 0;
      if (tabsLeft < searchRight) {
         tabsLeft = searchRight;
      }

      int tabH = 16;
      int tabY = top + (44 - tabH) / 2;
      int x = tabsLeft;

      for (int i = 0; i < FILTER_TAGS.length; i++) {
         String label = FILTER_TAGS[i];
         int cw = widths[i];
         boolean selected = this.isTagActive(label);
         boolean hovered = mx >= x - 4 && mx <= x + cw + 4 && my >= tabY - 2 && my <= tabY + tabH + 2;
         float t = this.tagHoverT.getOrDefault(label, 0.0F);
         float ease = PackHubConfig.isAnimationsEnabled() ? Math.min(1.0F, delta * 0.3F) : 1.0F;
         t += ((hovered ? 1.0F : 0.0F) - t) * ease;
         this.tagHoverT.put(label, t);
         int fg = selected ? -1 : Ui.lerpColor(-7697782, -1, t);
         DrawHelper.drawText(ctx, this.field_22793, label, x, tabY + 4, fg, false);
         if (selected) {
            ctx.method_25294(x, tabY + tabH, x + cw, tabY + tabH + 1, -1);
         } else if (t > 0.05F) {
            int alpha = (int)(204.0F * t);
            ctx.method_25294(x, tabY + tabH, x + cw, tabY + tabH + 1, Ui.withAlpha(16777215, alpha));
         }

         x += cw + gap;
      }

      int gx = this.field_22789 - 18 - 18;
      int gy = top + 13;
      boolean gearHovered = mx >= gx && mx <= gx + 18 && my >= gy && my <= gy + 18;
      float ge = PackHubConfig.isAnimationsEnabled() ? Math.min(1.0F, delta * 0.3F) : 1.0F;
      this.gearHoverT = this.gearHoverT + ((gearHovered ? 1.0F : 0.0F) - this.gearHoverT) * ge;
      this.renderGearIcon(ctx, gx, gy, 18, gearHovered);
   }

   private void renderGearIcon(class_332 ctx, int x, int y, int s, boolean hovered) {
      int fg = hovered ? -1 : -7697782;
      int cx = x + s / 2;
      int cy = y + s / 2;
      int r1 = s / 2 - 2;
      int r2 = s / 2;
      ctx.method_25294(cx - 1, y, cx + 1, y + 2, fg);
      ctx.method_25294(cx - 1, y + s - 2, cx + 1, y + s, fg);
      ctx.method_25294(x, cy - 1, x + 2, cy + 1, fg);
      ctx.method_25294(x + s - 2, cy - 1, x + s, cy + 1, fg);
      ctx.method_25294(x + 2, y + 2, x + 4, y + 4, fg);
      ctx.method_25294(x + s - 4, y + 2, x + s - 2, y + 4, fg);
      ctx.method_25294(x + 2, y + s - 4, x + 4, y + s - 2, fg);
      ctx.method_25294(x + s - 4, y + s - 4, x + s - 2, y + s - 2, fg);
      ctx.method_25294(cx - r1, cy - r1, cx + r1, cy - r1 + 1, fg);
      ctx.method_25294(cx - r1, cy + r1 - 1, cx + r1, cy + r1, fg);
      ctx.method_25294(cx - r1, cy - r1, cx - r1 + 1, cy + r1, fg);
      ctx.method_25294(cx + r1 - 1, cy - r1, cx + r1, cy + r1, fg);
      ctx.method_25294(cx - 2, cy - 2, cx + 2, cy + 2, -16119286);
   }

   private boolean isMouseOverGear(double mx, double my) {
      int gx = this.field_22789 - 18 - 18;
      int gy = 65;
      return mx >= (double)gx && mx <= (double)(gx + 18) && my >= (double)gy && my <= (double)(gy + 18);
   }

   private boolean isTagActive(String tag) {
      if (tag.equals("All")) {
         return this.activeTags.isEmpty();
      } else {
         for (String t : this.activeTags) {
            if (t.equalsIgnoreCase(tag)) {
               return true;
            }
         }

         return false;
      }
   }

   private boolean tryClickTagChip(double mx, double my, boolean additive) {
      int top = 52;
      if (!(my < (double)top) && !(my > (double)(top + 44))) {
         int gap = 18;
         int totalW = 0;
         int[] widths = new int[FILTER_TAGS.length];

         for (int i = 0; i < FILTER_TAGS.length; i++) {
            widths[i] = this.field_22793.method_1727(FILTER_TAGS[i]);
            totalW += widths[i];
         }

         totalW += gap * (FILTER_TAGS.length - 1);
         int tabsRight = this.field_22789 - 18 - 18 - 12;
         int tabsLeft = tabsRight - totalW;
         int searchRight = this.searchField != null ? this.searchField.method_46426() + this.searchField.method_25368() + 24 : 0;
         if (tabsLeft < searchRight) {
            tabsLeft = searchRight;
         }

         int tabH = 16;
         int tabY = top + (44 - tabH) / 2;
         int x = tabsLeft;

         for (int i = 0; i < FILTER_TAGS.length; i++) {
            int cw = widths[i];
            if (mx >= (double)(x - 4) && mx <= (double)(x + cw + 4) && my >= (double)(tabY - 2) && my <= (double)(tabY + tabH + 2)) {
               String picked = FILTER_TAGS[i];
               if (picked.equals("All")) {
                  this.activeTags.clear();
               } else if (additive) {
                  String hit = null;

                  for (String t : this.activeTags) {
                     if (t.equalsIgnoreCase(picked)) {
                        hit = t;
                        break;
                     }
                  }

                  if (hit != null) {
                     this.activeTags.remove(hit);
                  } else {
                     this.activeTags.add(picked);
                  }
               } else {
                  boolean already = this.activeTags.size() == 1 && this.isTagActive(picked);
                  this.activeTags.clear();
                  if (!already) {
                     this.activeTags.add(picked);
                  }
               }

               this.scroll = 0.0;
               this.scrollTarget = 0.0;
               this.invalidateFilteredCache();
               return true;
            }

            x += cw + gap;
         }

         return false;
      } else {
         return false;
      }
   }

   private List<Pack> applyFilters(List<Pack> source) {
      if (source != null && !source.isEmpty()) {
         String q = this.searchField != null ? this.searchField.method_1882() : "";
         q = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
         Set<String> tags = (Set<String>)(this.activeTags.isEmpty() ? Collections.emptySet() : new LinkedHashSet<>(this.activeTags));
         boolean appliedOnly = false;
         Set<String> noAppliedTags = tags;

         for (String t : tags) {
            if ("Applied".equalsIgnoreCase(t)) {
               appliedOnly = true;
               noAppliedTags = new LinkedHashSet<>(tags);
               noAppliedTags.removeIf(s -> "Applied".equalsIgnoreCase(s));
               break;
            }
         }

         boolean sort = PackHubConfig.isSortByStars();
         String activeKey = appliedOnly ? String.valueOf(this.activeIds.hashCode()) : "";
         String tagKey = String.join(",", tags);
         String fullKey = tagKey + "\u0000" + q + "\u0000" + activeKey + "\u0000" + (sort ? "S" : "U");
         if (this.filteredCache != null && this.filteredCacheSource == source && fullKey.equals(this.filteredCacheTag)) {
            return this.filteredCache;
         } else {
            List<Pack> out = new ArrayList<>(source.size());

            for (Pack p : source) {
               if ((!appliedOnly || this.activeIds.contains(p.getId())) && (noAppliedTags.isEmpty() || packMatchesAny(p, noAppliedTags))) {
                  if (!q.isEmpty()) {
                     String hay = (p.getName() + " " + p.getAuthorName() + " " + String.join(" ", p.getTags())).toLowerCase(Locale.ROOT);
                     if (!hay.contains(q)) {
                        continue;
                     }
                  }

                  out.add(p);
               }
            }

            if (sort) {
               out.sort((a, b) -> Integer.compare(b.getStarCount(), a.getStarCount()));
            }

            this.filteredCache = out;
            this.filteredCacheSource = source;
            this.filteredCacheTag = fullKey;
            this.filteredCacheQuery = q;
            return out;
         }
      } else {
         return Collections.emptyList();
      }
   }

   private static boolean packMatchesAny(Pack p, Set<String> wanted) {
      if (wanted.isEmpty()) {
         return true;
      } else {
         for (String pt : p.getTags()) {
            if (pt != null && !pt.isBlank()) {
               for (String w : wanted) {
                  if (w.equalsIgnoreCase(pt)) {
                     return true;
                  }
               }
            }
         }

         return false;
      }
   }

   private void renderPackList(class_332 ctx, int mx, int my, float delta) {
      int listTop = 96;
      int listBottom = this.field_22790 - 44;
      int listH = listBottom - listTop;
      int cardW = Math.min(this.field_22789 - 36, 760);
      int cardX = (this.field_22789 - cardW) / 2;
      ctx.method_44379(0, listTop, this.field_22789, listBottom);
      if (this.loading) {
         this.renderSkeletonCards(ctx, cardX, listTop, cardW, delta);
         ctx.method_44380();
      } else if (this.error != null) {
         this.renderEmptyState(ctx, listTop, listH, false, "Couldn't connect", this.error);
         ctx.method_44380();
      } else if (!PackHubConfig.isConfigured()) {
         this.renderEmptyState(ctx, listTop, listH, false, "Could not find the PackHub server", "Make sure the bot is running, then press RECONNECT");
         ctx.method_44380();
      } else {
         List<Pack> visible = this.applyFilters(this.packs);
         if (this.packs.isEmpty()) {
            this.renderEmptyState(ctx, listTop, listH, false, "No texture packs yet", "Submit one on the PackHub Discord!");
            ctx.method_44380();
         } else if (visible.isEmpty()) {
            String q = this.searchField != null ? this.searchField.method_1882().trim() : "";
            boolean appliedOnly = false;

            for (String t : this.activeTags) {
               if ("Applied".equalsIgnoreCase(t)) {
                  appliedOnly = true;
                  break;
               }
            }

            String otherTags = this.activeTags.stream().filter(tx -> !"Applied".equalsIgnoreCase(tx)).reduce((a, b) -> a + ", " + b).orElse("");
            String sub;
            String head;
            if (appliedOnly) {
               head = "No packs applied";
               sub = q.isEmpty() ? "Apply a pack from the catalog and it'll show up here." : "Nothing applied matches \"" + q + "\".";
            } else if (!q.isEmpty() && !otherTags.isEmpty()) {
               head = "No matches";
               sub = "Nothing matches \"" + q + "\" with tag " + otherTags + ".";
            } else if (!q.isEmpty()) {
               head = "No matches";
               sub = "Nothing matches \"" + q + "\".";
            } else {
               head = "No matches";
               sub = "No packs are tagged " + otherTags + " yet.";
            }

            this.renderEmptyState(ctx, listTop, listH, false, head, sub);
            ctx.method_44380();
         } else {
            int y = listTop - (int)this.scroll + 0;

            for (int ci = 0; ci < visible.size(); ci++) {
               Pack pack = visible.get(ci);
               if (y + 84 + 6 < listTop) {
                  y += 84;
               } else {
                  if (y > listBottom) {
                     break;
                  }

                  this.renderCard(ctx, pack, cardX, y, cardW, mx, my, delta, ci);
                  y += 84;
               }
            }

            ctx.method_25296(cardX, listTop, cardX + cardW, listTop + 8, Ui.withAlpha(657930, 255), Ui.withAlpha(657930, 0));
            ctx.method_25296(cardX, listBottom - 8, cardX + cardW, listBottom, Ui.withAlpha(657930, 0), Ui.withAlpha(657930, 255));
            ctx.method_44380();
            int totalH = visible.size() * 84;
            if (totalH > listH) {
               int trackW = 4;
               int trackH = listH - 8;
               int trackX = this.field_22789 - trackW - 4;
               int trackY = listTop + 4;
               int thumbH = Math.max(24, trackH * listH / totalH);
               int thumbY = trackY + (int)((double)(trackH - thumbH) * this.scroll / (double)Math.max(1, totalH - listH));
               boolean sbHover = mx >= trackX - 2 && mx <= trackX + trackW + 2 && my >= trackY && my <= trackY + trackH;
               ctx.method_25294(trackX, trackY, trackX + trackW, trackY + trackH, sbHover ? -12961222 : -14737633);
               ctx.method_25294(trackX, thumbY, trackX + trackW, thumbY + thumbH, sbHover ? -8470748 : -7697782);
            }
         }
      }
   }

   private void renderEmptyState(class_332 ctx, int listTop, int listH, boolean spinner, String msg, String sub) {
      int cy = listTop + listH / 2;
      if (spinner) {
         float phase = (float)(System.currentTimeMillis() % 1200L) / 1200.0F;
         Ui.spinner(ctx, this.field_22789 / 2, cy - 24, 6, phase, -1);
      }

      String head = msg == null ? "" : msg.toUpperCase(Locale.ROOT);
      int tw = this.field_22793.method_1727(head);
      DrawHelper.drawText(ctx, this.field_22793, head, this.field_22789 / 2 - tw / 2, cy - 4, -1, false);
      if (sub != null && !sub.isBlank()) {
         int maxW = Math.min(this.field_22789 - 60, 520);
         int yLine = cy + 12;

         for (String s : this.wrapText(sub, maxW)) {
            int sw = this.field_22793.method_1727(s);
            DrawHelper.drawText(ctx, this.field_22793, s, this.field_22789 / 2 - sw / 2, yLine, -7697782, false);
            yLine += 11;
         }
      }
   }

   private void renderSkeletonCards(class_332 ctx, int x, int listTop, int w, float delta) {
      float phase = (float)(System.currentTimeMillis() % 2400L) / 2400.0F;
      int count = Math.max(3, (this.field_22790 - 52 - 44 - 44) / 84);

      for (int i = 0; i < count; i++) {
         int y = listTop + i * 84;
         ctx.method_25294(x, y + 84 - 1, x + w, y + 84, -14737633);
         int thumbX = x + 18;
         int thumbY = y + 10;
         ctx.method_25294(thumbX, thumbY, thumbX + 64, thumbY + 64, -15329770);
         int textX = thumbX + 64 + 18;
         ctx.method_25294(textX, y + 18, textX + 50, y + 26, -14737633);
         ctx.method_25294(textX, y + 32, textX + 160, y + 40, -15329770);
         ctx.method_25294(textX, y + 48, textX + 100, y + 56, -14737633);
         Ui.shimmer(ctx, x, y, w, 84, phase, Ui.withAlpha(16777215, 12));
      }

      String msg = PackHubConfig.isConfigured() ? "LOADING PACKS..." : "CONNECTING...";
      int mw = this.field_22793.method_1727(msg);
      int cy = listTop + (this.field_22790 - 52 - 44 - 44) / 2;
      DrawHelper.drawText(ctx, this.field_22793, msg, this.field_22789 / 2 - mw / 2, cy, -7697782, false);
   }

   private void renderCard(class_332 ctx, Pack pack, int x, int y, int w, int mx, int my, float delta, int cardIndex) {
      boolean anim = PackHubConfig.isAnimationsEnabled();
      float stagger = Math.max(0.0F, Math.min(1.0F, this.cardEntryT * 6.0F - (float)cardIndex * 0.4F));
      float entryScale = anim ? Ui.easeOutBack(stagger) : 1.0F;
      int entryAlpha = (int)(255.0F * Math.min(1.0F, stagger * 2.0F));
      boolean pushed = anim && entryScale < 0.999F;
      if (pushed) {
         DrawHelper.pushMatrices(ctx);
         DrawHelper.translateMatrices(ctx, (float)x + (float)w / 2.0F, (float)y + 42.0F, 0.0F);
         DrawHelper.scaleMatrices(ctx, entryScale, entryScale, 1.0F);
         DrawHelper.translateMatrices(ctx, -((float)x + (float)w / 2.0F), -((float)y + 42.0F), 0.0F);
      }

      boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + 84 && my >= 52 && my <= this.field_22790 - 44;
      float ht = this.cardHoverT.getOrDefault(pack.getId(), 0.0F);
      float hoverEase = anim ? Math.min(1.0F, delta * 0.3F) : 1.0F;
      ht += ((hovered ? 1.0F : 0.0F) - ht) * hoverEase;
      this.cardHoverT.put(pack.getId(), ht);
      int liftY = (int)(-4.0F * ht);
      y += liftY;
      if (ht > 0.02F) {
         int shadowA = (int)(68.0F * ht);
         int spread = (int)(3.0F * ht);
         ctx.method_25294(x + spread, y + 84, x + w - spread, y + 84 + 2 + spread, Ui.withAlpha(0, shadowA));
         int surf = Ui.lerpColor(-15658735, -15329770, ht);
         ctx.method_25294(x, y, x + w, y + 84, surf);
      }

      ctx.method_25294(x, y + 84 - 1, x + w, y + 84, Ui.lerpColor(-14737633, -12961222, ht));
      if (ht > 0.02F) {
         int accent = Ui.withAlpha(16777215, (int)(255.0F * ht));
         ctx.method_25294(x, y, x + 2, y + 84, accent);
      }

      int padL = 18;
      int thumbX = x + padL;
      int thumbY = y + 10;
      boolean thumbHovered = mx >= thumbX && mx <= thumbX + 64 && my >= thumbY && my <= thumbY + 64 && my >= 52 && my <= this.field_22790 - 44;
      ctx.method_25294(thumbX, thumbY, thumbX + 64, thumbY + 64, -16448251);
      PackHubScreenBase.Thumb thumb = this.thumbs.get(pack.getId());
      if (thumb != null) {
         float at = this.thumbAppearT.getOrDefault(pack.getId(), 0.0F);
         at += (1.0F - at) * Math.min(1.0F, delta * 0.15F);
         this.thumbAppearT.put(pack.getId(), at);
         int alpha = (int)(255.0F * Ui.easeOutCubic(at));
         DrawHelper.drawTexture(ctx, thumb.id(), thumbX, thumbY, 0.0F, 0.0F, 64, 64, 64, 64);
         if (thumbHovered) {
            ctx.method_25294(thumbX, thumbY, thumbX + 64, thumbY + 64, 1426063360);
            String hint = "VIEW";
            int hw = this.field_22793.method_1727(hint);
            DrawHelper.drawText(ctx, this.field_22793, hint, thumbX + (64 - hw) / 2, thumbY + 32 - 4, -1, false);
         }
      } else {
         ctx.method_25294(thumbX, thumbY, thumbX + 64, thumbY + 64, -15329770);
      }

      int frameCol = Ui.lerpColor(-14737633, -12961222, Math.max(ht, thumbHovered ? 1.0F : 0.0F));
      ctx.method_25294(thumbX, thumbY, thumbX + 64, thumbY + 1, frameCol);
      ctx.method_25294(thumbX, thumbY + 64 - 1, thumbX + 64, thumbY + 64, frameCol);
      ctx.method_25294(thumbX, thumbY, thumbX + 1, thumbY + 64, frameCol);
      ctx.method_25294(thumbX + 64 - 1, thumbY, thumbX + 64, thumbY + 64, frameCol);
      int textX = thumbX + 64 + 18;
      int titleY = y + 18;
      boolean applied = this.activeIds.contains(pack.getId());
      List<String> packTags = pack.getTags();
      String tagStrip = packTags.isEmpty() ? "" : String.join(" · ", packTags.stream().map(t -> t.toUpperCase(Locale.ROOT)).toList());
      String eyebrow;
      int eyebrowCol;
      if (applied) {
         eyebrow = tagStrip.isEmpty() ? "APPLIED" : "APPLIED  //  " + tagStrip;
         eyebrowCol = -1;
      } else {
         eyebrow = tagStrip.isEmpty() ? "PACK" : tagStrip;
         eyebrowCol = -10855846;
      }

      DrawHelper.drawText(ctx, this.field_22793, eyebrow, textX, titleY, eyebrowCol, false);
      DrawHelper.drawText(ctx, this.field_22793, pack.getName(), textX, titleY + 12, -1, false);
      String author = "BY " + pack.getAuthorName().toUpperCase(Locale.ROOT);
      String format = pack.isZip() ? ".ZIP" : ".RAR";
      DrawHelper.drawText(ctx, this.field_22793, author, textX, titleY + 28, -7697782, false);
      int sepX = textX + this.field_22793.method_1727(author) + 10;
      DrawHelper.drawText(ctx, this.field_22793, "//", sepX, titleY + 28, -10855846, false);
      int afterFmtX = sepX + this.field_22793.method_1727("// ");
      DrawHelper.drawText(ctx, this.field_22793, format, afterFmtX, titleY + 28, -7697782, false);
      if (pack.getDownloads() > 0) {
         int dlX = afterFmtX + this.field_22793.method_1727(format) + 10;
         DrawHelper.drawText(ctx, this.field_22793, "//", dlX, titleY + 28, -10855846, false);
         String dlLabel = pack.getDownloads() + (pack.getDownloads() == 1 ? " DL" : " DLS");
         DrawHelper.drawText(ctx, this.field_22793, dlLabel, dlX + this.field_22793.method_1727("// "), titleY + 28, -7697782, false);
      }

      int btnX = x + w - 110 - 18;
      int btnY = y + 29;
      int starX = btnX - 64 - 8;
      if (this.canEditAppliedPack(pack)) {
         int editX = starX - 8 - 48;
         this.renderEditPill(ctx, editX, btnY, mx, my, delta);
      }

      this.renderStarPill(ctx, pack, starX, btnY, mx, my, delta);
      this.renderPackButton(ctx, pack, btnX, btnY, mx, my, delta);
      if (pushed) {
         DrawHelper.popMatrices(ctx);
      }
   }

   private boolean canEditAppliedPack(Pack pack) {
      if (!this.activeIds.contains(pack.getId())) {
         return false;
      } else {
         String folder = PackDownloader.getActivePackFolder(pack.getId());
         if (folder == null) {
            return false;
         } else {
            Path root = PackDownloader.resolveResourcePackPath(folder);
            return root != null && Files.isDirectory(root) && Files.isRegularFile(root.resolve("pack.mcmeta"));
         }
      }
   }

   private void renderEditPill(class_332 ctx, int editX, int btnY, int mx, int my, float delta) {
      boolean hovered = mx >= editX && mx <= editX + 48 && my >= btnY && my <= btnY + 26 && my >= 52 && my <= this.field_22790 - 44;
      int border = hovered ? -1 : -12961222;
      int fill = hovered ? -12040149 : -16119286;
      ctx.method_25294(editX, btnY, editX + 48, btnY + 26, fill);
      ctx.method_25294(editX, btnY, editX + 48, btnY + 1, border);
      ctx.method_25294(editX, btnY + 26 - 1, editX + 48, btnY + 26, border);
      ctx.method_25294(editX, btnY, editX + 1, btnY + 26, border);
      ctx.method_25294(editX + 48 - 1, btnY, editX + 48, btnY + 26, border);
      String lab = "EDIT";
      int lw = this.field_22793.method_1727(lab);
      DrawHelper.drawText(ctx, this.field_22793, lab, editX + (48 - lw) / 2, btnY + 9, hovered ? -1 : -7697782, false);
      if (hovered) {
         this.pendingTooltip = "Rename pack metadata and map textures to vanilla ids (.minecraft/resourcepacks)";
         this.pendingTooltipMx = mx;
         this.pendingTooltipMy = my;
      }
   }

   private void renderStarPill(class_332 ctx, Pack pack, int x, int y, int mx, int my, float delta) {
      boolean hovered = mx >= x && mx <= x + 64 && my >= y && my <= y + 26 && my >= 52 && my <= this.field_22790 - 44;
      boolean starred = pack.isViewerStarred();
      boolean inFlight = this.starInFlight.contains(pack.getId());
      int border;
      int fill;
      int fg;
      if (starred) {
         border = -1;
         fill = -1;
         fg = -16777216;
      } else {
         border = hovered ? -1 : -12961222;
         fill = -16119286;
         fg = hovered ? -1 : -7697782;
      }

      ctx.method_25294(x, y, x + 64, y + 26, fill);
      ctx.method_25294(x, y, x + 64, y + 1, border);
      ctx.method_25294(x, y + 26 - 1, x + 64, y + 26, border);
      ctx.method_25294(x, y, x + 1, y + 26, border);
      ctx.method_25294(x + 64 - 1, y, x + 64, y + 26, border);
      String label = "★ " + pack.getStarCount() + (inFlight ? "..." : "");
      int lw = this.field_22793.method_1727(label);
      DrawHelper.drawText(ctx, this.field_22793, label, x + (64 - lw) / 2, y + 9, fg, false);
      if (hovered && !starred) {
         this.pendingTooltip = "Click to upvote — top-voted packs surface first.";
         this.pendingTooltipMx = mx;
         this.pendingTooltipMy = my;
      } else if (hovered && starred) {
         this.pendingTooltip = "You've starred this pack.";
         this.pendingTooltipMx = mx;
         this.pendingTooltipMy = my;
      }
   }

   private void renderPackButton(class_332 ctx, Pack pack, int bx, int by, int mx, int my, float delta) {
      PackHubScreenBase.DlState state = this.dlState.getOrDefault(pack.getId(), PackHubScreenBase.DlState.IDLE);
      boolean hovBtn = mx >= bx && mx <= bx + 110 && my >= by && my <= by + 26 && my >= 52 && my <= this.field_22790 - 44;
      float bt = this.btnHoverT.getOrDefault(pack.getId(), 0.0F);
      bt += ((hovBtn ? 1.0F : 0.0F) - bt) * Math.min(1.0F, delta * 0.35F);
      this.btnHoverT.put(pack.getId(), bt);
      int bg;
      int fg;
      int border;
      String label;
      switch (state) {
         case DOWNLOADING:
            bg = -16119286;
            fg = -1;
            border = -12961222;
            float p = this.dlProgressUi.getOrDefault(pack.getId(), 0.0F);
            label = "DOWNLOADING " + (int)(p * 100.0F) + "%";
            break;
         case APPLYING:
            bg = -16119286;
            fg = -1;
            border = -12961222;
            label = "WORKING";
            break;
         case DONE:
            if (bt > 0.05F) {
               bg = Ui.lerpColor(-1, -42663, bt);
               fg = -16777216;
               border = Ui.lerpColor(-1, -42663, bt);
               label = "REMOVE";
            } else {
               bg = -1;
               fg = -16777216;
               border = -1;
               label = "APPLIED";
            }
            break;
         case ERROR:
            bg = bt > 0.05F ? -42663 : -16119286;
            fg = bt > 0.05F ? -16777216 : -42663;
            border = -42663;
            label = "RETRY";
            break;
         default:
            bg = bt > 0.05F ? Ui.lerpColor(-16119286, -1, bt) : -16119286;
            fg = bt > 0.05F ? Ui.lerpColor(-1, -16777216, bt) : -1;
            border = bt > 0.05F ? -1 : Ui.lerpColor(-12961222, -1, bt);
            label = "APPLY";
      }

      ctx.method_25294(bx, by, bx + 110, by + 26, bg);
      ctx.method_25294(bx, by, bx + 110, by + 1, border);
      ctx.method_25294(bx, by + 26 - 1, bx + 110, by + 26, border);
      ctx.method_25294(bx, by, bx + 1, by + 26, border);
      ctx.method_25294(bx + 110 - 1, by, bx + 110, by + 26, border);
      if (state == PackHubScreenBase.DlState.DOWNLOADING) {
         float p = this.dlProgressUi.getOrDefault(pack.getId(), 0.0F);
         int fillW = (int)(110.0F * Math.max(0.0F, Math.min(1.0F, p)));
         ctx.method_25294(bx, by + 26 - 2, bx + fillW, by + 26, -1);
      } else if (state == PackHubScreenBase.DlState.APPLYING) {
         long t = (System.currentTimeMillis() - this.openedAt) % 1400L;
         float phase = (float)t / 1400.0F;
         int segW = 27;
         int segX = bx + (int)(110.0F * phase) - segW / 2;
         int x1 = Math.max(bx, segX);
         int x2 = Math.min(bx + 110, segX + segW);
         if (x2 > x1) {
            ctx.method_25294(x1, by + 26 - 2, x2, by + 26, -1);
         }
      }

      int lw = this.field_22793.method_1727(label);
      DrawHelper.drawText(ctx, this.field_22793, label, bx + (110 - lw) / 2, by + 9, fg, false);
      if (state == PackHubScreenBase.DlState.ERROR && hovBtn) {
         String err = this.dlMsg.getOrDefault(pack.getId(), "Click to retry");
         this.pendingTooltip = err;
         this.pendingTooltipMx = mx;
         this.pendingTooltipMy = my;
      }

      if (state == PackHubScreenBase.DlState.DONE
         && hovBtn
         && pack.getId().equals(this.confirmRemoveId)
         && System.currentTimeMillis() - this.confirmRemoveAt < 4000L) {
         this.pendingTooltip = "Click again to remove this pack.";
         this.pendingTooltipMx = mx;
         this.pendingTooltipMy = my;
      }
   }

   private void drawTooltip(class_332 ctx, String text, int mx, int my) {
      int padX = 6;
      int padY = 4;
      int lineH = 11;
      int maxW = Math.min(this.field_22789 - 20, 280);
      List<String> lines = this.wrapText(text, maxW - padX * 2);
      int boxW = 0;

      for (String s : lines) {
         boxW = Math.max(boxW, this.field_22793.method_1727(s));
      }

      boxW += padX * 2;
      int boxH = padY * 2 + lines.size() * lineH - (lineH - 8);
      int tx = mx + 12;
      int ty = my + 10;
      if (tx + boxW > this.field_22789 - 4) {
         tx = this.field_22789 - 4 - boxW;
      }

      if (ty + boxH > this.field_22790 - 4) {
         ty = my - 6 - boxH;
      }

      if (tx < 4) {
         tx = 4;
      }

      if (ty < 4) {
         ty = 4;
      }

      ctx.method_25294(tx, ty, tx + boxW, ty + boxH, -234223094);
      ctx.method_25294(tx, ty, tx + boxW, ty + 1, -1);
      ctx.method_25294(tx, ty + boxH - 1, tx + boxW, ty + boxH, -1);
      ctx.method_25294(tx, ty, tx + 1, ty + boxH, -1);
      ctx.method_25294(tx + boxW - 1, ty, tx + boxW, ty + boxH, -1);
      int yLine = ty + padY;

      for (String s : lines) {
         DrawHelper.drawText(ctx, this.field_22793, s, tx + padX, yLine, -2039584, false);
         yLine += lineH;
      }
   }

   private int[] previewImageBounds() {
      if (this.previewPack == null) {
         return null;
      } else {
         PackHubScreenBase.Thumb thumb = this.thumbs.get(this.previewPack.getId());
         int margin = 60;
         int maxW = this.field_22789 - margin * 2;
         int maxH = this.field_22790 - margin * 2 - 40;
         int w;
         int h;
         if (thumb != null) {
            float scale = Math.min((float)maxW / (float)thumb.width(), (float)maxH / (float)thumb.height());
            w = Math.max(64, (int)((float)thumb.width() * scale));
            h = Math.max(64, (int)((float)thumb.height() * scale));
         } else {
            w = Math.min(maxW, 480);
            h = w * 9 / 16;
            if (h > maxH) {
               h = maxH;
               w = maxH * 16 / 9;
            }
         }

         int x = (this.field_22789 - w) / 2;
         int y = (this.field_22790 - h) / 2 - 12;
         return new int[]{x, y, w, h};
      }
   }

   private void renderPreviewOverlay(class_332 ctx, int mx, int my) {
      int backdropA = (int)(204.0F * Ui.easeOutCubic(this.previewT));
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, Ui.withAlpha(0, backdropA));
      if (this.previewPack != null) {
         PackHubScreenBase.Thumb thumb = this.thumbs.get(this.previewPack.getId());
         int[] b = this.previewImageBounds();
         float scaleAnim = 0.92F + 0.08F * Ui.easeOutCubic(this.previewT);
         if (b != null) {
            int w = (int)((float)b[2] * scaleAnim);
            int h = (int)((float)b[3] * scaleAnim);
            int x = b[0] + (b[2] - w) / 2;
            int y = b[1] + (b[3] - h) / 2;
            ctx.method_25294(x - 4, y - 4, x + w + 4, y + h + 4, -16448251);
            ctx.method_25294(x - 4, y - 4, x + w + 4, y - 3, -1);
            ctx.method_25294(x - 4, y + h + 3, x + w + 4, y + h + 4, -1);
            ctx.method_25294(x - 4, y - 4, x - 3, y + h + 4, -1);
            ctx.method_25294(x + w + 3, y - 4, x + w + 4, y + h + 4, -1);
            if (thumb != null) {
               DrawHelper.drawTexture(ctx, thumb.id(), x, y, 0.0F, 0.0F, w, h, w, h);
            } else {
               ctx.method_25294(x, y, x + w, y + h, -15329770);
               String msg = "NO SHOWCASE AVAILABLE";
               int mw = this.field_22793.method_1727(msg);
               int mAlpha = (int)(200.0F * Ui.easeOutCubic(this.previewT));
               DrawHelper.drawText(ctx, this.field_22793, msg, x + (w - mw) / 2, y + h / 2 - 4, Ui.withAlpha(9079434, mAlpha), false);
            }

            int stripA = (int)(200.0F * Ui.easeOutCubic(this.previewT));
            if (stripA > 8) {
               int stripH = 24;
               ctx.method_25294(x, y, x + w, y + stripH, Ui.withAlpha(0, stripA));
               int textA = (int)(255.0F * Ui.easeOutCubic(this.previewT));
               DrawHelper.drawText(ctx, this.field_22793, this.previewPack.getName(), x + 10, y + 8, Ui.withAlpha(16777215, textA), false);
               if (!this.previewPack.getTag().isEmpty()) {
                  String t = this.previewPack.getTag().toUpperCase(Locale.ROOT);
                  int tw = this.field_22793.method_1727(t);
                  int labelX = x + w - tw - 10;
                  int labelY = y + 8;
                  DrawHelper.drawText(ctx, this.field_22793, t, labelX, labelY, Ui.withAlpha(16777215, textA), false);
               }
            }
         }

         int alpha = (int)(255.0F * Ui.easeOutCubic(this.previewT));
         if (alpha >= 8) {
            String title = this.previewPack.getName();
            String sub = "BY " + this.previewPack.getAuthorName().toUpperCase(Locale.ROOT);
            int titleW = this.field_22793.method_1727(title);
            int subW = this.field_22793.method_1727(sub);
            int cy = b != null ? b[1] + b[3] + 12 : this.field_22790 / 2 + 60;
            DrawHelper.drawText(ctx, this.field_22793, title, this.field_22789 / 2 - titleW / 2, cy, Ui.withAlpha(16777215, alpha), false);
            DrawHelper.drawText(ctx, this.field_22793, sub, this.field_22789 / 2 - subW / 2, cy + 12, Ui.withAlpha(9079434, alpha), false);
            String hint = "CLICK ANYWHERE OR PRESS ESC TO CLOSE";
            int hw = this.field_22793.method_1727(hint);
            DrawHelper.drawText(ctx, this.field_22793, hint, this.field_22789 / 2 - hw / 2, this.field_22790 - 44 - 14, Ui.withAlpha(5921370, alpha), false);
         }
      }
   }

   private boolean isMouseOverThumbnail(double mx, double my, int cardX, int cardW, int cardY) {
      int thumbX = cardX + 18;
      int thumbY = cardY + 10;
      return mx >= (double)thumbX && mx <= (double)(thumbX + 64) && my >= (double)thumbY && my <= (double)(thumbY + 64);
   }

   public boolean method_25402(double mx, double my, int button) {
      if (this.previewPack != null) {
         this.previewPack = null;
         return true;
      } else if (button == 0
         && mx >= (double)this.builderBtnX
         && mx <= (double)(this.builderBtnX + this.builderBtnW)
         && my >= (double)this.builderBtnY
         && my <= (double)(this.builderBtnY + this.builderBtnH)) {
         class_310.method_1551().method_1507(new PackBuilderScreen(this));
         return true;
      } else if (button == 0
         && mx >= (double)this.killFxBtnX
         && mx <= (double)(this.killFxBtnX + this.killFxBtnW)
         && my >= (double)this.killFxBtnY
         && my <= (double)(this.killFxBtnY + this.killFxBtnH)) {
         class_310.method_1551().method_1507(new KillEffectsScreen(this));
         return true;
      } else if (button == 0
         && mx >= (double)this.featherBtnX
         && mx <= (double)(this.featherBtnX + this.featherBtnW)
         && my >= (double)this.featherBtnY
         && my <= (double)(this.featherBtnY + this.featherBtnH)) {
         class_310.method_1551().method_1507(new FeatherProfilesScreen(this));
         return true;
      } else if (button == 0 && this.isMouseOverGear(mx, my)) {
         class_310.method_1551().method_1507(new PackHubSettingsScreen(this));
         return true;
      } else if (button == 0 && this.tryClickTagChip(mx, my, InputCompat.isShiftDown())) {
         return true;
      } else if (InputCompat.delegateToChildren(this, mx, my, button)) {
         return true;
      } else {
         int listTop = 96;
         int listBottom = this.field_22790 - 44;
         int cardW = Math.min(this.field_22789 - 36, 760);
         int cardX = (this.field_22789 - cardW) / 2;
         List<Pack> visible = this.applyFilters(this.packs);
         if (button == 0 && my > (double)listTop && my < (double)listBottom) {
            int y = listTop - (int)this.scroll + 0;

            for (Pack pack : visible) {
               if (y + 84 < listTop) {
                  y += 84;
               } else {
                  if (y > listBottom) {
                     break;
                  }

                  int btnX = cardX + cardW - 110 - 18;
                  int btnY = y + 29;
                  int starX = btnX - 64 - 8;
                  if (this.canEditAppliedPack(pack)) {
                     int editX = starX - 8 - 48;
                     if (mx >= (double)editX && mx <= (double)(editX + 48) && my >= (double)btnY && my <= (double)(btnY + 26)) {
                        String folder = PackDownloader.getActivePackFolder(pack.getId());
                        class_310.method_1551().method_1507(new PackEditScreen(this, pack.getId(), folder, pack.getName()));
                        Ui.playClick();
                        return true;
                     }
                  }

                  if (mx >= (double)starX && mx <= (double)(starX + 64) && my >= (double)btnY && my <= (double)(btnY + 26)) {
                     this.handleStarClick(pack);
                     return true;
                  }

                  if (mx >= (double)btnX && mx <= (double)(btnX + 110) && my >= (double)btnY && my <= (double)(btnY + 26)) {
                     PackHubScreenBase.DlState state = this.dlState.getOrDefault(pack.getId(), PackHubScreenBase.DlState.IDLE);
                     if (state != PackHubScreenBase.DlState.IDLE && state != PackHubScreenBase.DlState.ERROR && state != PackHubScreenBase.DlState.DONE) {
                        return false;
                     }

                     this.handlePackClick(pack);
                     return true;
                  }

                  if (this.isMouseOverThumbnail(mx, my, cardX, cardW, y)) {
                     this.previewPack = pack;
                     return true;
                  }

                  y += 84;
               }
            }
         }

         return false;
      }
   }

   private void handleStarClick(Pack pack) {
      if (!pack.isViewerStarred() && this.starInFlight.add(pack.getId())) {
         String serverUrl = PackHubConfig.getServerUrl();
         if (serverUrl != null && !serverUrl.isBlank()) {
            Ui.playStar();
            int prev = pack.getStarCount();
            pack.setStarCount(prev + 1);
            pack.setViewerStarred(true);
            this.invalidateFilteredCache();
            this.executor.submit(() -> {
               try {
                  PackApiClient.StarResult res = PackApiClient.starPack(serverUrl, pack);
                  class_310.method_1551().execute(() -> {
                     pack.setStarCount(res.starCount());
                     this.invalidateFilteredCache();
                     this.starInFlight.remove(pack.getId());
                  });
               } catch (Exception var71) {
                  String msg = var71.getMessage();
                  if (msg == null || msg.isBlank()) {
                     msg = "Star failed.";
                  }

                  String finalMsg = msg;
                  class_310.method_1551().execute(() -> {
                     pack.setStarCount(prev);
                     pack.setViewerStarred(false);
                     this.dlMsg.put(pack.getId(), finalMsg);
                     this.invalidateFilteredCache();
                     this.starInFlight.remove(pack.getId());
                  });
               }
            });
         } else {
            this.starInFlight.remove(pack.getId());
         }
      }
   }

   public boolean onScrollDelta(double vScroll) {
      if (this.previewPack != null) {
         return true;
      } else {
         int listH = this.field_22790 - 52 - 44 - 44;
         int totalH = this.applyFilters(this.packs).size() * 84;
         int maxScroll = Math.max(0, totalH - listH);
         this.scrollTarget = Math.max(0.0, Math.min(this.scrollTarget - vScroll * 24.0, (double)maxScroll));
         return true;
      }
   }

   public boolean method_25404(int keyCode, int scanCode, int modifiers) {
      if (this.previewPack != null && keyCode == 256) {
         this.previewPack = null;
         return true;
      } else if (keyCode == 256 && this.searchField != null && this.searchField.method_25370() && !this.searchField.method_1882().isEmpty()) {
         this.searchField.method_1852("");
         return true;
      } else if (InputCompat.needsPolling()) {
         if (keyCode == 256) {
            this.method_25419();
            return true;
         } else {
            return false;
         }
      } else {
         return this.superKeyPressed(keyCode, scanCode, modifiers);
      }
   }

   private boolean superKeyPressed(int keyCode, int scanCode, int modifiers) {
      return super.method_25404(keyCode, scanCode, modifiers);
   }

   private void reconnect() {
      this.loading = true;
      this.error = null;
      this.executor.submit(() -> {
         PackHubConfig.tryAutoDiscover();
         this.fetchPacks();
      });
   }

   private void pickRandomPack() {
      List<Pack> visible = this.applyFilters(this.packs);
      if (visible.isEmpty()) {
         if (this.searchField != null) {
            this.searchField.method_1852("");
         }

         this.activeTags.clear();
         this.invalidateFilteredCache();
         visible = this.applyFilters(this.packs);
         if (visible.isEmpty()) {
            return;
         }
      }

      Ui.playClick();
      int idx = (int)(Math.random() * (double)visible.size());
      Pack pick = visible.get(idx);
      int listH = this.field_22790 - 52 - 44 - 44;
      int cardOffset = idx * 84;
      int target = Math.max(0, cardOffset - listH / 2 + 42);
      this.scrollTarget = (double)target;
      this.cardHoverT.put(pick.getId(), 1.0F);
   }

   private void handlePackClick(Pack pack) {
      if (!PackHubConfig.isConfigured()) {
         this.reconnect();
      } else {
         PackHubScreenBase.DlState current = this.dlState.getOrDefault(pack.getId(), PackHubScreenBase.DlState.IDLE);
         if (current != PackHubScreenBase.DlState.DONE && !this.activeIds.contains(pack.getId())) {
            String serverUrl = PackHubConfig.getServerUrl();
            this.dlState.put(pack.getId(), PackHubScreenBase.DlState.DOWNLOADING);
            this.dlProgress.put(pack.getId(), 0.0F);
            this.dlProgressUi.put(pack.getId(), 0.0F);
            this.executor.submit(() -> PackDownloader.downloadAndApply(pack, serverUrl, new PackDownloader.ProgressCallback() {
                  @Override
                  public void onProgress(float f) {
                     PackHubScreenBase.this.dlProgress.put(pack.getId(), f);
                  }

                  @Override
                  public void onApplying() {
                     PackHubScreenBase.this.dlState.put(pack.getId(), PackHubScreenBase.DlState.APPLYING);
                  }

                  @Override
                  public void onDone() {
                     PackHubScreenBase.this.dlState.put(pack.getId(), PackHubScreenBase.DlState.DONE);
                     PackHubScreenBase.this.activeIds = PackDownloader.getActivePackIds();
                     PackHubScreenBase.this.invalidateFilteredCache();
                  }

                  @Override
                  public void onError(String msg) {
                     PackHubScreenBase.this.dlState.put(pack.getId(), PackHubScreenBase.DlState.ERROR);
                     PackHubScreenBase.this.dlMsg.put(pack.getId(), msg);
                  }
               }));
         } else {
            if (PackHubConfig.isConfirmBeforeRemove()) {
               long now = System.currentTimeMillis();
               boolean armed = pack.getId().equals(this.confirmRemoveId) && now - this.confirmRemoveAt < 4000L;
               if (!armed) {
                  this.confirmRemoveId = pack.getId();
                  this.confirmRemoveAt = now;
                  this.dlMsg.put(pack.getId(), "Click again to remove.");
                  return;
               }

               this.confirmRemoveId = null;
               this.confirmRemoveAt = 0L;
               this.dlMsg.remove(pack.getId());
            }

            this.dlState.put(pack.getId(), PackHubScreenBase.DlState.APPLYING);
            PackDownloader.removePack(pack, () -> {
               this.activeIds = PackDownloader.getActivePackIds();
               this.dlState.put(pack.getId(), PackHubScreenBase.DlState.IDLE);
               this.dlProgress.put(pack.getId(), 0.0F);
               this.dlProgressUi.put(pack.getId(), 0.0F);
               this.invalidateFilteredCache();
            });
         }
      }
   }

   private void invalidateFilteredCache() {
      this.filteredCache = null;
      this.filteredCacheSource = null;
      this.filteredCacheTag = null;
      this.filteredCacheQuery = null;
   }

   private void fetchPacks() {
      if (!PackHubConfig.isConfigured()) {
         this.loading = false;
      } else {
         String serverUrl = PackHubConfig.getServerUrl();
         this.executor.submit(() -> {
            try {
               List<Pack> loaded = PackApiClient.fetchPacks(serverUrl);
               class_310.method_1551().execute(() -> {
                  this.packs = loaded;
                  this.loading = false;
                  this.error = null;
                  this.activeIds = PackDownloader.getActivePackIds();

                  for (Pack p : this.packs) {
                     if (this.activeIds.contains(p.getId())) {
                        this.dlState.put(p.getId(), PackHubScreenBase.DlState.DONE);
                        this.dlProgress.put(p.getId(), 1.0F);
                        this.dlProgressUi.put(p.getId(), 1.0F);
                     }
                  }

                  this.loadThumbnails(serverUrl);
               });
            } catch (Exception var51) {
               String msg = var51.getMessage();
               if (msg == null || msg.isBlank()) {
                  msg = var51.getClass().getSimpleName();
               }

               String finalMsg = msg;
               class_310.method_1551().execute(() -> {
                  this.error = finalMsg;
                  this.loading = false;
               });
            }
         });
      }
   }

   private void loadThumbnails(String serverUrl) {
      for (Pack pack : this.packs) {
         String url = pack.getShowcaseUrl(serverUrl);
         if (!url.isBlank() && !this.thumbs.containsKey(pack.getId())) {
            this.executor.submit(() -> {
               try {
                  byte[] bytes = PackApiClient.downloadBytes(url);
                  class_1011 img = class_1011.method_4309(new ByteArrayInputStream(bytes));
                  int w = img.method_4307();
                  int h = img.method_4323();
                  class_310 mc = class_310.method_1551();
                  mc.execute(() -> {
                     String texName = "packhub_thumb_" + sanitizeId(pack.getId());
                     class_1043 tex = DrawHelper.createNativeTexture(texName, img);
                     if (tex != null) {
                        class_2960 id = class_2960.method_60655("packhub", "thumb/" + sanitizeId(pack.getId()));
                        mc.method_1531().method_4616(id, tex);
                        this.thumbs.put(pack.getId(), new PackHubScreenBase.Thumb(id, w, h));
                        this.thumbAppearT.put(pack.getId(), 0.0F);
                     }
                  });
               } catch (Exception var81) {
                  PackHubMod.LOGGER.warn("Failed to load thumbnail for {} ({}): {}", new Object[]{pack.getId(), url, var81.getMessage()});
               }
            });
         }
      }
   }

   private static String sanitizeId(String s) {
      return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
   }

   private List<String> wrapText(String text, int maxW) {
      List<String> out = new ArrayList<>();
      StringBuilder cur = new StringBuilder();

      for (String word : text.split(" ")) {
         String candidate = cur.length() == 0 ? word : cur + " " + word;
         if (this.field_22793.method_1727(candidate) <= maxW) {
            cur.setLength(0);
            cur.append(candidate);
         } else {
            if (cur.length() > 0) {
               out.add(cur.toString());
            }

            cur.setLength(0);
            cur.append(word);
         }
      }

      if (cur.length() > 0) {
         out.add(cur.toString());
      }

      return out;
   }

   private static enum DlState {
      IDLE,
      DOWNLOADING,
      APPLYING,
      DONE,
      ERROR;

      private DlState() {
      }
   }

   private static record Thumb(class_2960 id, int width, int height) {
   }
}
