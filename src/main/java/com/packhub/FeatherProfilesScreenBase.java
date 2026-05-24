package com.packhub;

import com.packhub.compat.DrawHelper;
import com.packhub.compat.InputCompat;
import com.packhub.ui.Ui;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_437;

public abstract class FeatherProfilesScreenBase extends class_437 {
   private static final int HEADER = 48;
   private static final int FOOTER = 44;
   private static final int TABS_H = 28;
   private static final int COL_BG = -16119286;
   private static final int COL_SURFACE = -15461356;
   private static final int COL_TEXT = -1;
   private static final int COL_MUTED = -7697782;
   private static final int COL_DIM = -10855846;
   private static final int COL_HAIRLINE = -14737633;
   private static final int COL_ACCENT = -8470748;
   private static final int COL_GREEN = -12339839;
   private static final int COL_RED = -1228219;
   private final class_437 parent;
   private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "FeatherProfiles-IO");
      t.setDaemon(true);
      return t;
   });
   private float openT = 0.0F;
   private FeatherProfilesScreenBase.Tab activeTab = FeatherProfilesScreenBase.Tab.LOCAL;
   private final List<String> profiles = new ArrayList<>();
   private final List<PackApiClient.FeatherProfile> communityProfiles = new ArrayList<>();
   private boolean loadingCommunity = false;
   private String communityError = null;
   private String statusMessage = null;
   private long statusTime = 0L;
   private float scroll = 0.0F;
   private float scrollTarget = 0.0F;
   private final InputCompat.Poller inputPoller = new InputCompat.Poller();

   protected FeatherProfilesScreenBase(class_437 parent) {
      super(class_2561.method_43470("Feather Profiles"));
      this.parent = parent;
   }

   protected void method_25426() {
      Ui.playOpen();
      this.loadProfiles();
      this.fetchCommunityProfiles();
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25419() {
      Ui.playClose();
      class_310.method_1551().method_1507(this.parent);
   }

   private Path getProfilesDir() {
      return class_310.method_1551().field_1697.toPath().resolve("feather").resolve("configuration").resolve("profiles");
   }

   private void loadProfiles() {
      this.profiles.clear();
      Path dir = this.getProfilesDir();
      if (Files.isDirectory(dir)) {
         try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> this.profiles.add(p.getFileName().toString()));
         } catch (Exception var71) {
            PackHubMod.LOGGER.warn("Failed to list Feather profiles", var71);
         }
      }
   }

   private void fetchCommunityProfiles() {
      if (PackHubConfig.isConfigured()) {
         this.loadingCommunity = true;
         this.communityError = null;
         this.executor.submit(() -> {
            try {
               PackApiClient.FeatherProfilesResponse resp = PackApiClient.fetchFeatherProfiles(PackHubConfig.getServerUrl());
               class_310.method_1551().execute(() -> {
                  this.communityProfiles.clear();
                  if (resp.profiles != null) {
                     this.communityProfiles.addAll(resp.profiles);
                  }

                  this.loadingCommunity = false;
               });
            } catch (Exception var21) {
               PackHubMod.LOGGER.warn("Failed to fetch community profiles", var21);
               class_310.method_1551().execute(() -> {
                  this.communityError = var21.getMessage();
                  this.loadingCommunity = false;
               });
            }
         });
      }
   }

   private void importProfile(PackApiClient.FeatherProfile profile) {
      Path dir = this.getProfilesDir();
      if (!Files.isDirectory(dir)) {
         this.statusMessage = "⚠ Feather not installed";
         this.statusTime = System.currentTimeMillis();
         Ui.playError();
      } else {
         this.executor.submit(() -> {
            try {
               String json = PackApiClient.downloadFeatherProfile(PackHubConfig.getServerUrl(), profile.id);
               String cleaned = json.replaceAll("\"_profile_[^\"]*\"\\s*:\\s*\"[^\"]*\",?", "");
               Path dest = dir.resolve(profile.name.replaceAll("[^a-zA-Z0-9_ \\-]", "") + ".json");
               Files.writeString(dest, cleaned.isEmpty() ? json : cleaned);
               class_310.method_1551().execute(() -> {
                  this.statusMessage = "✅ Imported! Check your Feather profiles.";
                  this.statusTime = System.currentTimeMillis();
                  Ui.playSuccess();
                  this.loadProfiles();
               });
            } catch (Exception var61) {
               class_310.method_1551().execute(() -> {
                  this.statusMessage = "⚠ Import failed: " + var61.getMessage();
                  this.statusTime = System.currentTimeMillis();
                  Ui.playError();
               });
            }
         });
      }
   }

   private void createPackHubProfile() {
      try {
         Path dir = this.getProfilesDir();
         if (!Files.isDirectory(dir)) {
            this.statusMessage = "⚠ Feather not installed";
            this.statusTime = System.currentTimeMillis();
            Ui.playError();
            return;
         }

         String json = buildFeatherProfileJson();
         Files.writeString(dir.resolve("PackHub.json"), json);
         this.statusMessage = "✅ PackHub profile saved!";
         this.statusTime = System.currentTimeMillis();
         Ui.playSuccess();
         this.loadProfiles();
      } catch (Exception var31) {
         this.statusMessage = "⚠ Failed: " + var31.getMessage();
         this.statusTime = System.currentTimeMillis();
         Ui.playError();
      }
   }

   private void deleteProfile(String name) {
      try {
         Path file = this.getProfilesDir().resolve(name);
         Files.deleteIfExists(file);
         this.statusMessage = "Deleted " + name;
         this.statusTime = System.currentTimeMillis();
         Ui.playClick();
         this.loadProfiles();
      } catch (Exception var31) {
         this.statusMessage = "⚠ Failed: " + var31.getMessage();
         this.statusTime = System.currentTimeMillis();
         Ui.playError();
      }
   }

   public boolean onScrollDelta(double delta) {
      int maxScroll;
      if (this.activeTab == FeatherProfilesScreenBase.Tab.LOCAL) {
         maxScroll = Math.max(0, this.profiles.size() * 36 - (this.field_22790 - 48 - 28 - 44 - 50));
      } else {
         maxScroll = Math.max(0, this.communityProfiles.size() * 48 - (this.field_22790 - 48 - 28 - 44 - 20));
      }

      this.scrollTarget = Math.max(0.0F, Math.min((float)maxScroll, this.scrollTarget - (float)delta * 28.0F));
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

      ctx.method_25294(0, 0, this.field_22789, 48, -15461356);
      ctx.method_25294(0, 0, this.field_22789, 2, -8470748);
      ctx.method_25294(0, 47, this.field_22789, 48, -14737633);
      boolean backHov = mx >= 8 && mx <= 48 && my >= 10 && my <= 36;
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "<", 14, 16, backHov ? -8470748 : -7697782);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, "FEATHER PROFILES", 30, 10, -1);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, this.profiles.size() + " local • " + this.communityProfiles.size() + " community", 30, 24, -7697782);
      int cbw = 140;
      int cbh = 22;
      int cbx = this.field_22789 - cbw - 16;
      int cby = 14;
      boolean cbHov = mx >= cbx && mx <= cbx + cbw && my >= cby && my <= cby + cbh;
      ctx.method_25294(cbx, cby, cbx + cbw, cby + cbh, cbHov ? -8470748 : -10843622);
      String cbLabel = "+ PackHub Profile";
      int cbLabelW = this.field_22793.method_1727(cbLabel);
      DrawHelper.drawTextWithShadow(ctx, this.field_22793, cbLabel, cbx + (cbw - cbLabelW) / 2, cby + 7, -1);
      int tabY = 48;
      ctx.method_25294(0, tabY, this.field_22789, tabY + 28, -15461356);
      ctx.method_25294(0, tabY + 28 - 1, this.field_22789, tabY + 28, -14737633);
      String[] tabLabels = new String[]{"My Profiles", "Community"};
      FeatherProfilesScreenBase.Tab[] tabValues = new FeatherProfilesScreenBase.Tab[]{
         FeatherProfilesScreenBase.Tab.LOCAL, FeatherProfilesScreenBase.Tab.COMMUNITY
      };
      int tx = 16;

      for (int i = 0; i < tabLabels.length; i++) {
         int tw = this.field_22793.method_1727(tabLabels[i]) + 14;
         boolean act = this.activeTab == tabValues[i];
         boolean hov = mx >= tx && mx <= tx + tw && my >= tabY && my <= tabY + 28;
         if (act) {
            ctx.method_25294(tx, tabY + 28 - 3, tx + tw, tabY + 28 - 1, -8470748);
         }

         DrawHelper.drawTextWithShadow(ctx, this.field_22793, tabLabels[i], tx + 7, tabY + 9, act ? -1 : (hov ? -1 : -7697782));
         tx += tw + 4;
      }

      ctx.method_25294(0, this.field_22790 - 44, this.field_22789, this.field_22790, -15461356);
      ctx.method_25294(0, this.field_22790 - 44, this.field_22789, this.field_22790 - 44 + 1, -14737633);
      if (this.statusMessage != null) {
         long elapsed = System.currentTimeMillis() - this.statusTime;
         if (elapsed < 3000L) {
            int alpha = elapsed > 2000L ? (int)(255.0F * (1.0F - (float)(elapsed - 2000L) / 1000.0F)) : 255;
            int col = Ui.withAlpha(16777215, alpha);
            int stw = this.field_22793.method_1727(this.statusMessage);
            DrawHelper.drawTextWithShadow(ctx, this.field_22793, this.statusMessage, this.field_22789 / 2 - stw / 2, this.field_22790 - 44 + 18, col);
         } else {
            this.statusMessage = null;
         }
      }

      this.scroll = this.scroll + (this.scrollTarget - this.scroll) * Math.min(1.0F, delta * 0.35F);
      int top = 84;
      int bot = this.field_22790 - 44;
      ctx.method_44379(0, top, this.field_22789, bot);
      if (this.activeTab == FeatherProfilesScreenBase.Tab.LOCAL) {
         this.drawLocalProfiles(ctx, mx, my, top, bot);
      } else {
         this.drawCommunityProfiles(ctx, mx, my, top, bot);
      }

      ctx.method_44380();
      DrawHelper.popMatrices(ctx);

      for (class_364 child : this.method_25396()) {
         if (child instanceof class_4068 d) {
            d.method_25394(ctx, mx, my, delta);
         }
      }
   }

   private void drawLocalProfiles(class_332 ctx, int mx, int my, int top, int bot) {
      if (!Files.isDirectory(this.getProfilesDir())) {
         String msg = "Feather client not detected. Install Feather to manage profiles.";
         int tw = this.field_22793.method_1727(msg);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, msg, this.field_22789 / 2 - tw / 2, top + 40, -7697782);
      } else if (this.profiles.isEmpty()) {
         String msg = "No profiles found. Click '+ PackHub Profile' to create one.";
         int tw = this.field_22793.method_1727(msg);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, msg, this.field_22789 / 2 - tw / 2, top + 40, -7697782);
      } else {
         int rowH = 36;
         int padX = 28;
         int rowW = this.field_22789 - 56;

         for (int i = 0; i < this.profiles.size(); i++) {
            String name = this.profiles.get(i);
            int ry = top + i * rowH - (int)this.scroll;
            if (ry + rowH >= top && ry <= bot) {
               boolean isPackHub = name.equalsIgnoreCase("PackHub.json");
               boolean rowHov = mx >= padX && mx <= padX + rowW && my >= ry && my < ry + rowH && my >= top && my < bot;
               ctx.method_25294(padX, ry, padX + rowW, ry + rowH, rowHov ? -15066582 : -15461356);
               ctx.method_25294(padX, ry + rowH - 1, padX + rowW, ry + rowH, -14737633);
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, isPackHub ? "★" : "○", padX + 8, ry + (rowH - 9) / 2, isPackHub ? -8470748 : -10855846);
               String displayName = name.replace(".json", "");
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, displayName, padX + 24, ry + 6, -1);
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, name, padX + 24, ry + 18, -10855846);
               String del = "✕";
               int delW = this.field_22793.method_1727(del) + 12;
               int delX = padX + rowW - delW - 4;
               int delY = ry + (rowH - 14) / 2;
               boolean delHov = mx >= delX && mx <= delX + delW && my >= delY && my <= delY + 14 && my >= top && my < bot;
               ctx.method_25294(delX, delY, delX + delW, delY + 14, delHov ? -1228219 : -14018022);
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, del, delX + 6, delY + 3, delHov ? -1 : -10855846);
            }
         }
      }
   }

   private void drawCommunityProfiles(class_332 ctx, int mx, int my, int top, int bot) {
      if (this.loadingCommunity) {
         Ui.spinner(ctx, this.field_22789 / 2, top + 50, 12, (float)((double)(System.currentTimeMillis() % 2000L) / 2000.0), -8470748);
         String msg = "Loading community profiles...";
         int tw = this.field_22793.method_1727(msg);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, msg, this.field_22789 / 2 - tw / 2, top + 70, -7697782);
      } else if (this.communityError != null) {
         String msg = "Error: " + this.communityError;
         int tw = this.field_22793.method_1727(msg);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, msg, this.field_22789 / 2 - tw / 2, top + 40, -1228219);
      } else if (this.communityProfiles.isEmpty()) {
         String msg = "No community profiles available yet.";
         int tw = this.field_22793.method_1727(msg);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, msg, this.field_22789 / 2 - tw / 2, top + 40, -7697782);
         String hint = "Upload yours at the PackHub upload page!";
         int hw = this.field_22793.method_1727(hint);
         DrawHelper.drawTextWithShadow(ctx, this.field_22793, hint, this.field_22789 / 2 - hw / 2, top + 56, -10855846);
      } else {
         int rowH = 48;
         int padX = 28;
         int rowW = this.field_22789 - 56;

         for (int i = 0; i < this.communityProfiles.size(); i++) {
            PackApiClient.FeatherProfile profile = this.communityProfiles.get(i);
            int ry = top + i * rowH - (int)this.scroll;
            if (ry + rowH >= top && ry <= bot) {
               boolean rowHov = mx >= padX && mx <= padX + rowW && my >= ry && my < ry + rowH && my >= top && my < bot;
               ctx.method_25294(padX, ry, padX + rowW, ry + rowH, rowHov ? -15066582 : (i % 2 == 0 ? -15461356 : -15198184));
               ctx.method_25294(padX, ry + rowH - 1, padX + rowW, ry + rowH, -14737633);
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, "◆", padX + 8, ry + (rowH - 9) / 2, -8470748);
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, profile.name, padX + 24, ry + 8, -1);
               String desc = profile.description != null && !profile.description.isEmpty()
                  ? profile.description
                  : "by " + (profile.author != null ? profile.author : "Unknown");
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, desc, padX + 24, ry + 22, -10855846);
               String importLabel = "Import";
               int importW = this.field_22793.method_1727(importLabel) + 16;
               int importX = padX + rowW - importW - 8;
               int importY = ry + (rowH - 18) / 2;
               boolean importHov = mx >= importX && mx <= importX + importW && my >= importY && my <= importY + 18 && my >= top && my < bot;
               ctx.method_25294(importX, importY, importX + importW, importY + 18, importHov ? -12339839 : -15058390);
               DrawHelper.drawTextWithShadow(ctx, this.field_22793, importLabel, importX + 8, importY + 5, importHov ? -1 : -12339839);
            }
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
         int cbw = 140;
         int cbh = 22;
         int cbx = this.field_22789 - cbw - 16;
         int cby = 14;
         if (mx >= (double)cbx && mx <= (double)(cbx + cbw) && my >= (double)cby && my <= (double)(cby + cbh)) {
            this.createPackHubProfile();
            return true;
         } else {
            int tabY = 48;
            if (my >= (double)tabY && my <= (double)(tabY + 28)) {
               String[] tabLabels = new String[]{"My Profiles", "Community"};
               FeatherProfilesScreenBase.Tab[] tabValues = new FeatherProfilesScreenBase.Tab[]{
                  FeatherProfilesScreenBase.Tab.LOCAL, FeatherProfilesScreenBase.Tab.COMMUNITY
               };
               int tx = 16;

               for (int i = 0; i < tabLabels.length; i++) {
                  int tw = this.field_22793.method_1727(tabLabels[i]) + 14;
                  if (mx >= (double)tx && mx <= (double)(tx + tw)) {
                     this.activeTab = tabValues[i];
                     this.scroll = this.scrollTarget = 0.0F;
                     Ui.playClick();
                     return true;
                  }

                  tx += tw + 4;
               }
            }

            int top = 84;
            int bot = this.field_22790 - 44;
            if (this.activeTab == FeatherProfilesScreenBase.Tab.LOCAL) {
               if (my >= (double)top && my < (double)bot && Files.isDirectory(this.getProfilesDir())) {
                  int rowH = 36;
                  int padX = 28;
                  int rowW = this.field_22789 - 56;

                  for (int i = 0; i < this.profiles.size(); i++) {
                     int ry = top + i * rowH - (int)this.scroll;
                     if (ry + rowH >= top && ry <= bot) {
                        String del = "✕";
                        int delW = this.field_22793.method_1727(del) + 12;
                        int delX = padX + rowW - delW - 4;
                        int delY = ry + (rowH - 14) / 2;
                        if (mx >= (double)delX && mx <= (double)(delX + delW) && my >= (double)delY && my <= (double)(delY + 14)) {
                           this.deleteProfile(this.profiles.get(i));
                           return true;
                        }
                     }
                  }
               }
            } else if (my >= (double)top && my < (double)bot) {
               int rowH = 48;
               int padX = 28;
               int rowW = this.field_22789 - 56;

               for (int ix = 0; ix < this.communityProfiles.size(); ix++) {
                  PackApiClient.FeatherProfile profile = this.communityProfiles.get(ix);
                  int ry = top + ix * rowH - (int)this.scroll;
                  if (ry + rowH >= top && ry <= bot) {
                     String importLabel = "Import";
                     int importW = this.field_22793.method_1727(importLabel) + 16;
                     int importX = padX + rowW - importW - 8;
                     int importY = ry + (rowH - 18) / 2;
                     if (mx >= (double)importX && mx <= (double)(importX + importW) && my >= (double)importY && my <= (double)(importY + 18)) {
                        this.importProfile(profile);
                        return true;
                     }
                  }
               }
            }

            return InputCompat.delegateToChildren(this, mx, my, button);
         }
      }
   }

   private static String buildFeatherProfileJson() {
      return "{\"packOrganizer\":{\"**ArbitraryData**\":{},\"dragEntries\":\"true\",\"searchBar\":\"true\",\"coreModToggleKey\":\"0\",\"enabled\":\"true\",\"coreModToggleKeyNotify\":\"true\"},\"packdisplay\":{\"**ArbitraryData**\":{},\"border\":\"false\",\"hudAnchor\":\"top_right\",\"backgroundColor\":\"7/7/7/146\",\"borderColor\":\"0/0/0/255\",\"orientation\":\"vertical\",\"hudRelativeY\":\"15.0\",\"descriptionColor\":\"false/128/128/128/255\",\"hudRelativeX\":\"1.0\",\"hudScale\":\"1.0\",\"descriptionShadow\":\"true\",\"coreModToggleKey\":\"0\",\"enabled\":\"true\",\"borderThickness1\":\"1.0\",\"showDescription\":\"true\",\"titleColor\":\"false/255/255/255/255\",\"background\":\"true\",\"coreModToggleKeyNotify\":\"true\",\"hudEnabled1\":\"true\",\"titleShadow\":\"true\",\"alignment\":\"left\",\"showIcon\":\"true\"},\"fps\":{\"**ArbitraryData**\":{},\"border\":\"false\",\"hudAnchor\":\"top_left\",\"backgroundColor\":\"7/7/7/146\",\"borderColor\":\"0/0/0/255\",\"horizontalPadding\":\"8\",\"hudRelativeY\":\"0.0\",\"hudRelativeX\":\"0.0\",\"hudScale\":\"1.0\",\"textColor\":\"false/255/255/255/255\",\"displayMode\":\"justText\",\"coreModToggleKey\":\"0\",\"enabled\":\"true\",\"borderThickness1\":\"1.0\",\"backgroundHeight\":\"20\",\"backgroundWidth\":\"60\",\"coreModToggleKeyNotify\":\"true\",\"hudEnabled1\":\"true\",\"textShadow\":\"false\",\"backgroundType\":\"static\",\"verticalPadding\":\"6\",\"reversed\":\"false\"},\"brightness\":{\"**ArbitraryData**\":{},\"coreModToggleKeyNotify\":\"true\",\"brightnessValue\":\"100.0\",\"keyNightVision\":\"0\",\"type\":\"fullBright\",\"coreModToggleKey\":\"0\",\"enabled\":\"true\"},\"toggleSprint\":{\"**ArbitraryData**\":{},\"hudAnchor\":\"top_left\",\"borderColor\":\"0/0/0/255\",\"horizontalPadding\":\"8\",\"toggleSneakKey\":\"0\",\"flyBoostAmount\":\"1\",\"hudRelativeY\":\"52.0\",\"hudRelativeX\":\"0.0\",\"hudScale\":\"1.0\",\"hideText\":\"true\",\"enabled\":\"true\",\"flyBoostToggle\":\"false\",\"blockSprintWhenFlying\":\"false\",\"sprintingToggledText\":\"Sprinting (Toggled)\",\"hudEnabled1\":\"true\",\"flyingText\":\"Flying\",\"toggleSprint\":\"true\",\"textShadow\":\"false\",\"backgroundType\":\"static\",\"border\":\"false\",\"backgroundColor\":\"7/7/7/146\",\"textColor\":\"false/255/255/255/255\",\"displayMode\":\"background\",\"sprintingVanillaText\":\"Sprinting (Vanilla)\",\"coreModToggleKey\":\"0\",\"borderThickness1\":\"1.0\",\"sprintingKeyHeldText\":\"Sprinting (Key Held)\",\"sneakingToggledText\":\"Sneaking (Toggled)\",\"backgroundHeight\":\"16\",\"sneakingKeyHeldText\":\"Sneaking (Key Held)\",\"backgroundWidth\":\"110\",\"coreModToggleKeyNotify\":\"true\",\"toggleSneak\":\"false\",\"verticalPadding\":\"6\"},\"scoreboard\":{\"**ArbitraryData**\":{},\"border\":\"false\",\"hudAnchor\":\"center_right\",\"backgroundColor\":\"0/0/0/80\",\"borderColor\":\"0/0/0/255\",\"disableCornerRadius\":\"false\",\"hudRelativeY\":\"-0.5\",\"showNumbers\":\"false\",\"hudRelativeX\":\"-1.0\",\"hudScale\":\"1.0\",\"textColor\":\"false/255/255/255/255\",\"coreModToggleKey\":\"0\",\"enabled\":\"true\",\"titleBackgroundColor\":\"0/0/0/96\",\"borderThickness1\":\"1.0\",\"background\":\"true\",\"coreModToggleKeyNotify\":\"true\",\"hudEnabled1\":\"true\",\"textShadow\":\"false\"},\"armorBar\":{\"**ArbitraryData**\":{},\"allProtectionColor\":\"0/255/255/100\",\"blastProtectionColor\":\"255/255/0/100\",\"protectionGlint\":\"true\",\"leatherColor\":\"104/63/34/255\",\"netheriteColor\":\"76/68/73/255\",\"armorLowPercent\":\"0.2\",\"materialColor\":\"true\",\"fireProtectionColor\":\"255/165/0/100\",\"diamondColor\":\"74/237/217/255\",\"showThorns\":\"true\",\"coreModToggleKey\":\"0\",\"enabled\":\"true\",\"projectileProtectionColor\":\"255/0/255/100\",\"showArmorLow\":\"false\",\"coreModToggleKeyNotify\":\"true\",\"goldColor\":\"234/238/87/255\",\"showMending\":\"true\",\"mendingColor\":\"255/255/255/255\",\"armorLowColor\":\"255/0/0/255\",\"thornsColor\":\"50/50/50/255\"},\"saturation\":{\"**ArbitraryData**\":{},\"saturationGainOverlay\":\"true\",\"foodExhaustionUnderlay\":\"true\",\"saturation:hudScale\":\"1.0\",\"saturation:verticalPadding\":\"6\",\"saturation:backgroundHeight\":\"20\",\"saturation:textShadow\":\"false\",\"saturation:backgroundType\":\"static\",\"colorSaturationGainOverlay\":\"false\",\"enabled\":\"true\",\"tooltipFoodValues\":\"shiftOnly\",\"saturation:textColor\":\"false/255/255/255/255\",\"saturation:hudRelativeY\":\"0.0\",\"saturation:displayMode\":\"background\",\"saturation:horizontalPadding\":\"8\",\"saturation:hudRelativeX\":\"151.0\",\"healthGainOverlay\":\"true\",\"saturation:borderColor\":\"0/0/0/255\",\"foodExhaustionColor\":\"128/128/128/255\",\"foodGainOverlay\":\"true\",\"naturalRegen\":\"true\",\"saturation:borderThickness1\":\"1.0\",\"colorTooltipSaturation\":\"false\",\"saturation:backgroundColor\":\"7/7/7/146\",\"colorSaturation\":\"false/255/0/0/255\",\"coreModToggleKey\":\"0\",\"animationSpeed\":\"10\",\"colorSaturationOverlay\":\"true\",\"saturation:backgroundWidth\":\"60\",\"saturation:hudEnabled1\":\"false\",\"saturation:hudAnchor\":\"bottom_center\",\"coreModToggleKeyNotify\":\"true\",\"saturation:border\":\"false\",\"saturationOverlay\":\"true\"},\"reconnect\":{\"**ArbitraryData**\":{},\"maxDelay\":\"20.0\",\"delay\":\"5.0\",\"incrementDelay\":\"false\",\"coreModToggleKeyNotify\":\"true\",\"increment\":\"5.0\",\"autoreconnect\":\"false\",\"coreModToggleKey\":\"0\",\"enabled\":\"true\",\"attempts\":\"3\"},\"camera\":{\"**ArbitraryData**\":{},\"loadDetach\":\"0\",\"normalDetach\":\"0\",\"yOffset\":\"0.0\",\"xOffset\":\"0.0\",\"distance\":\"4.0\",\"yRot\":\"0.0\",\"distanceIncrement\":\"0.5\",\"distanceDown\":\"0\",\"xRot\":\"0.0\",\"cameraSmoothZoom\":\"true\",\"saveDetach\":\"0\",\"smoothZoomEasing\":\"outQuint\",\"distanceUp\":\"0\",\"coreModToggleKey\":\"0\",\"enabled\":\"true\",\"scrollDistanceKey\":\"0\",\"coreModToggleKeyNotify\":\"true\"}}";
   }

   private static enum Tab {
      LOCAL,
      COMMUNITY;

      private Tab() {
      }
   }
}
