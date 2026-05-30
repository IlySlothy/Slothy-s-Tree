package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.InputCompat;
import com.slothyhub.local.LocalPackManager;
import com.slothyhub.local.InstalledPackScanner;
import com.slothyhub.ui.CustomButton;
import com.slothyhub.ui.CustomButtonBase;
import com.slothyhub.ui.Ui;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_437;

public abstract class SlothyHubScreenBase extends class_437 {

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int HEADER  = 52;
    private static final int TOOLBAR = 40;
    private static final int FOOTER  = 44;
    private static final int CARD_H  = 80;
    private static final int THUMB   = 60;
    private static final int BTN_W   = 96;
    private static final int BTN_H   = 26;
    private static final int PAD     = 16;

    // ── Colours ───────────────────────────────────────────────────────────

    private static int col(int rgb, int a) { return Ui.withAlpha(rgb & 0xFFFFFF, a); }
    private static int lerp(int a, int b, float t) { return Ui.lerpColor(a, b, t); }

    // ── Filter tabs ───────────────────────────────────────────────────────
    private static final String[] TABS = {"All","Applied","Smp","NethPot","CPVP","ElyPVP"};

    // ── Main nav ──────────────────────────────────────────────────────────
    // nav[0]=PACKS, nav[1]=TEXTURES, nav[2]=LIBRARY, nav[3]=KILL FX, nav[4]=GUI
    private static final String[] NAV = {"PACKS","TEXTURES","LIBRARY","KILL FX","GUI"};
    private int activeNav = 0; // stays on PACKS in this screen

    // ── State ─────────────────────────────────────────────────────────────
    private final class_437 parent;
    private final ExecutorService executor = new ThreadPoolExecutor(
        0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS,
        new SynchronousQueue<>(), Executors.defaultThreadFactory(),
        new ThreadPoolExecutor.DiscardPolicy());

    private List<Pack> remotePacks = Collections.emptyList();
    private List<Pack> localPacks  = Collections.emptyList();
    private List<Pack> allPacks    = Collections.emptyList();

    private final Map<String, Thumb>   thumbs        = new HashMap<>();
    private final Map<String, DlState> dlState       = new HashMap<>();
    private final Map<String, String>  dlMsg         = new HashMap<>();
    private final Map<String, Float>   dlProgress    = new HashMap<>();
    private final Map<String, Float>   dlProgressUi  = new HashMap<>();
    private final Map<String, Float>   cardHover     = new HashMap<>();
    private final Map<String, Float>   btnHover      = new HashMap<>();
    private final Map<String, Float>   thumbFadeIn   = new HashMap<>();
    private final Map<String, Float>   tabHover      = new HashMap<>();
    private final Map<String, Float>   navHover      = new HashMap<>();
    private final Set<String>          starInFlight  = ConcurrentHashMap.newKeySet();

    private Set<String> activeIds   = new LinkedHashSet<>();
    private final Set<String> activeTabs = new LinkedHashSet<>();
    private boolean loading = true;
    private String  error   = null;
    private double  scroll = 0, scrollTarget = 0;

    private String searchQuery = "";
    private boolean searchFocused = false;
    private int searchX, searchY, searchW, searchH;
    private float gearHover = 0;
    private float cardEntryT = 0;

    private String confirmRemoveId = null;
    private long   confirmRemoveAt = 0;

    private Pack  previewPack = null;
    private float previewT    = 0;

    private String pendingTooltip;
    private int    tooltipMx, tooltipMy;

    private List<Pack> filteredCache = null;
    private String     filteredKey   = null;
    private List<Pack> filteredSrc   = null;

    // Hit-boxes set during render
    private final int[] navX = new int[NAV.length];
    private final int[] navW = new int[NAV.length];
    private int navY, navH;
    private int gearX, gearY, gearSz;
    private int localCheckX, localCheckY, localCheckW, localCheckH;
    private float localCheckAnim = 0f;

    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    // ─────────────────────────────────────────────────────────────────────
    public SlothyHubScreenBase(class_437 parent) {
        super(class_2561.method_43470("Slothy's Tree"));
        this.parent = parent;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void method_25426() {
        activeIds = PackDownloader.getActivePackIds();

        // Footer buttons
        int bw = 108, bh = 26, gap = 10;
        int totalW = bw * 3 + gap * 2;
        int bx = field_22789 / 2 - totalW / 2;
        int by = field_22790 - FOOTER + (FOOTER - bh) / 2;

        addFooterButton(bx,            by, bw, bh, "RECONNECT", false, () -> { Ui.playClick(); reconnect(); });
        addFooterButton(bx + bw + gap, by, bw, bh, "RANDOM",    false, () -> { Ui.playClick(); pickRandomPack(); });
        addFooterButton(bx+(bw+gap)*2, by, bw, bh, "CLOSE",     true,  () -> { Ui.playClick(); closeScreenNow(); });

        layoutSearchBox();

        loading = true; error = null;
        executor.submit(() -> { SlothyConfig.tryAutoDiscover(); fetchPacks(); });
    }

    private void layoutSearchBox() {
        searchH = 16;
        searchW = Math.min(220, Math.max(140, field_22789 / 5));
        searchX = PAD;
        searchY = HEADER + (TOOLBAR - searchH) / 2;
    }

    private int searchBoxRight() { return searchX + searchW + 28; }

    private void addFooterButton(int x, int y, int w, int h, String label, boolean primary, Runnable action) {
        CustomButtonBase.Style style = primary ? CustomButtonBase.Style.MOSS : CustomButtonBase.Style.SECONDARY;
        method_37063(new CustomButton(x, y, w, h, class_2561.method_43470(label), style, action));
    }

    private void closeScreenNow() {
        previewPack = null;
        executor.shutdownNow();
        thumbs.values().forEach(t -> class_310.method_1551().method_1531().method_4615(t.id()));
        class_310.method_1551().method_1507(parent);
    }

    @Override
    public void method_25419() {
        if (previewPack != null) { previewPack = null; return; }
        if (!searchQuery.isEmpty()) {
            searchQuery = ""; invalidateCache(); return;
        }
        closeScreenNow();
    }

    @Override
    public boolean method_25421() { return false; }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);

        scroll += (scrollTarget - scroll) * Math.min(1f, delta * 0.28f);
        for (Map.Entry<String, Float> e : dlProgress.entrySet()) {
            float cur = dlProgressUi.getOrDefault(e.getKey(), 0f);
            dlProgressUi.put(e.getKey(), cur + (e.getValue() - cur) * Math.min(1f, delta * 0.2f));
        }
        if (!loading && error == null && !allPacks.isEmpty() && cardEntryT < 1f)
            cardEntryT = Math.min(1f, cardEntryT + delta * 0.06f);
        previewT += ((previewPack != null ? 1f : 0f) - previewT) * Math.min(1f, delta * 0.3f);

        pendingTooltip = null;

        drawBg(ctx, delta);
        drawHeader(ctx, mx, my, delta);
        drawToolbar(ctx, mx, my, delta);
        drawList(ctx, mx, my, delta);
        drawFooter(ctx, mx, my, delta);

        for (class_364 w : method_25396()) {
            if (w instanceof class_4068 d) d.method_25394(ctx, mx, my, delta);
        }

        if (pendingTooltip != null) drawTooltip(ctx, pendingTooltip, tooltipMx, tooltipMy);
        if (previewT > 0.01f) drawPreview(ctx);
        Ui.renderSelectionParticles(ctx, delta);
    }

    // ── Background ────────────────────────────────────────────────────────

    private void drawBg(class_332 ctx, float delta) {
        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
        // Subtle radial glow from centre
        int cx = field_22789 / 2, cy = field_22790 / 2;
        int gw = (int)(field_22789 * 0.65f), gh = (int)(field_22790 * 0.55f);
        ctx.method_25296(cx - gw / 2, cy - gh / 2, cx + gw / 2, cy + gh / 2,
            col(Ui.COL_ACCENT, 6), col(Ui.COL_BG, 0));
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);
        Ui.drawCornerVines(ctx, field_22789, field_22790,
            (float)(System.currentTimeMillis() % 5000L) / 5000f);
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private void drawHeader(class_332 ctx, int mx, int my, float delta) {
        // Panel with gradient bottom edge
        ctx.method_25294(0, 0, field_22789, HEADER, Ui.COL_PANEL);
        // 2px accent stripe at very top
        ctx.method_25294(0, 0, field_22789, 2, Ui.COL_ACCENT);
        // 1px separator at bottom (subtle)
        ctx.method_25294(0, HEADER - 1, field_22789, HEADER, Ui.COL_BORDER);
        // Gradient glow below accent stripe
        ctx.method_25296(0, 2, field_22789 / 2, 6, col(Ui.COL_ACCENT, 30), col(Ui.COL_ACCENT, 0));
        ctx.method_25296(field_22789 / 2, 2, field_22789, 6, col(Ui.COL_ACCENT, 0), col(Ui.COL_ACCENT, 30));

        // Text before logo texture — avoids garbled labels on 1.21.4
        String logo = "SLOTHY'S TREE";
        int logoX = PAD + 22;
        int logoY = (HEADER - 9) / 2 + 1;
        DrawHelper.drawText(ctx, field_22793, logo, logoX + 1, logoY + 1, col(0x000000, 80), false);
        DrawHelper.drawText(ctx, field_22793, logo, logoX, logoY, Ui.COL_ACCENT, false);
        float phase = (float)(System.currentTimeMillis() % 4000L) / 4000f;
        Ui.drawSlothLogo(ctx, PAD, (HEADER - 16) / 2, phase);
        DrawHelper.flushDraw(ctx);
        int afterLogo = logoX + field_22793.method_1727(logo) + 12;
        // thin divider
        ctx.method_25294(afterLogo, 12, afterLogo + 1, HEADER - 12, Ui.COL_BORDER);

        // ── Nav pills ────────────────────────────────────────────────────
        navY = (HEADER - 16) / 2;
        navH = 16;
        int nx = afterLogo + 10;
        for (int i = 0; i < NAV.length; i++) {
            int tw = field_22793.method_1727(NAV[i]);
            navW[i] = tw + 14;
            navX[i] = nx;
            drawNavPill(ctx, navX[i], navY, navW[i], navH, NAV[i], i == activeNav, mx, my, delta);
            nx += navW[i] + 6;
        }

        // ── Status + gear (right side) ────────────────────────────────────
        gearSz = 12;
        gearX  = field_22789 - PAD - gearSz;
        gearY  = (HEADER - gearSz) / 2;
        boolean gHov = mx >= gearX && mx <= gearX + gearSz && my >= gearY && my <= gearY + gearSz;
        gearHover += ((gHov ? 1f : 0f) - gearHover) * Math.min(1f, delta * 0.35f);
        drawGear(ctx, gearX, gearY, gearSz, gHov);

        if (!loading && error == null) {
            boolean live = SlothyConfig.isConfigured();
            int dot = live ? Ui.COL_ACCENT : col(0x885040, 255);
            String txt = live ? ("LIVE  " + allPacks.size()) : "● LOCAL ONLY";
            int tw = field_22793.method_1727(txt);
            int rx = gearX - 12 - tw;
            if (!live) {
                DrawHelper.drawText(ctx, field_22793, txt, rx, (HEADER - 9) / 2 + 1, col(Ui.COL_ACCENT & 0xFFFFFF, 180), false);
            } else {
                ctx.method_25294(rx - 7, HEADER / 2 - 2, rx - 5, HEADER / 2 + 2, dot);
                DrawHelper.drawText(ctx, field_22793, txt, rx, (HEADER - 9) / 2 + 1, Ui.COL_MUTED, false);
            }
        }
    }

    private void drawNavPill(class_332 ctx, int x, int y, int w, int h,
                              String label, boolean active, int mx, int my, float delta) {
        String key = "nav_" + label;
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        float ht = navHover.getOrDefault(key, active ? 1f : 0f);
        ht += ((hov || active ? 1f : 0f) - ht) * Math.min(1f, delta * 0.45f);
        navHover.put(key, ht);

        if (active) {
            // Filled pill with accent glow
            ctx.method_25294(x, y, x + w, y + h, col(Ui.COL_ACCENT & 0xFFFFFF, 30));
            ctx.method_25294(x, y, x + w, y + 1, Ui.COL_ACCENT);
            ctx.method_25294(x, y + h - 1, x + w, y + h, col(Ui.COL_ACCENT & 0xFFFFFF, 80));
            ctx.method_25294(x, y, x + 1, y + h, col(Ui.COL_ACCENT & 0xFFFFFF, 80));
            ctx.method_25294(x + w - 1, y, x + w, y + h, col(Ui.COL_ACCENT & 0xFFFFFF, 80));
            DrawHelper.drawText(ctx, field_22793, label, x + 7, y + (h - 9) / 2, Ui.COL_ACCENT, false);
        } else {
            if (ht > 0.02f) {
                ctx.method_25294(x, y, x + w, y + h, col(Ui.COL_SURFACE & 0xFFFFFF, (int)(150 * ht)));
                ctx.method_25294(x, y, x + w, y + 1, col(Ui.COL_BORDER & 0xFFFFFF, (int)(200 * ht)));
                ctx.method_25294(x, y + h - 1, x + w, y + h, col(Ui.COL_BORDER & 0xFFFFFF, (int)(100 * ht)));
                ctx.method_25294(x, y, x + 1, y + h, col(Ui.COL_BORDER & 0xFFFFFF, (int)(150 * ht)));
                ctx.method_25294(x + w - 1, y, x + w, y + h, col(Ui.COL_BORDER & 0xFFFFFF, (int)(150 * ht)));
            }
            int fg = lerp(Ui.COL_MUTED, Ui.COL_TEXT, ht);
            DrawHelper.drawText(ctx, field_22793, label, x + 7, y + (h - 9) / 2, fg, false);
        }
    }

    private void drawGear(class_332 ctx, int x, int y, int s, boolean hov) {
        int c = hov ? Ui.COL_ACCENT : Ui.COL_MUTED;
        int cx = x + s / 2, cy = y + s / 2;
        ctx.method_25294(cx - 1, y,         cx + 1, y + 3,         c);
        ctx.method_25294(cx - 1, y + s - 3, cx + 1, y + s,         c);
        ctx.method_25294(x,       cy - 1,   x + 3,  cy + 1,         c);
        ctx.method_25294(x + s-3, cy - 1,   x + s,  cy + 1,         c);
        ctx.method_25294(x + 1,   y + 1,    x + 3,  y + 3,          c);
        ctx.method_25294(x + s-3, y + 1,    x + s-1,y + 3,          c);
        ctx.method_25294(x + 1,   y + s-3,  x + 3,  y + s-1,        c);
        ctx.method_25294(x + s-3, y + s-3,  x + s-1,y + s-1,        c);
        int r = s / 2 - 2;
        ctx.method_25294(cx - r, cy - r, cx + r, cy - r + 1, c);
        ctx.method_25294(cx - r, cy + r, cx + r, cy + r + 1, c);
        ctx.method_25294(cx - r, cy - r, cx - r + 1, cy + r + 1, c);
        ctx.method_25294(cx + r - 1, cy - r, cx + r, cy + r + 1, c);
        ctx.method_25294(cx - 2, cy - 2, cx + 2, cy + 2, Ui.COL_BG);
    }

    // ── Toolbar ───────────────────────────────────────────────────────────

    private void drawToolbar(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER, bot = HEADER + TOOLBAR;
        ctx.method_25294(0, top, field_22789, bot, Ui.COL_BG);
        ctx.method_25294(0, bot - 1, field_22789, bot, Ui.COL_BORDER);

        // Search box — drawn manually (MC 1.21.8 TextField renders at wrong Y)
        int sx = searchX - 2;
        int sy = searchY - 2;
        int sw = searchW + 4;
        int sh = searchH + 4;
        ctx.method_25294(sx, sy, sx + sw, sy + sh, col(Ui.COL_SURFACE & 0xFFFFFF, 120));
        int lineCol = searchFocused ? Ui.COL_ACCENT : Ui.COL_BORDER;
        ctx.method_25294(sx, sy + sh, sx + sw, sy + sh + 1, lineCol);
        int iconX = sx + 4, iconY = sy + sh / 2 - 2;
        ctx.method_25294(iconX, iconY, iconX + 4, iconY + 4, col(Ui.COL_MUTED & 0xFFFFFF, 150));
        ctx.method_25294(iconX + 2, iconY + 2, iconX + 3, iconY + 3, col(Ui.COL_BG & 0xFFFFFF, 180));
        int textX = sx + 14;
        int textY = sy + (sh - 8) / 2;
        if (searchQuery.isEmpty() && !searchFocused) {
            DrawHelper.drawText(ctx, field_22793, "Search packs...", textX, textY, Ui.COL_MUTED, false);
        } else {
            String shown = searchQuery;
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
            DrawHelper.drawText(ctx, field_22793, shown, textX, textY, Ui.COL_TEXT, false);
        }

        // Filter tabs — pill style, right-aligned
        int gap = 8;
        int[] tw = new int[TABS.length];
        int totalTabW = 0;
        for (int i = 0; i < TABS.length; i++) {
            tw[i] = field_22793.method_1727(TABS[i]) + 10;
            totalTabW += tw[i];
        }
        totalTabW += gap * (TABS.length - 1);

        int tabsRight = field_22789 - PAD;
        int tabsLeft  = tabsRight - totalTabW;
        if (tabsLeft < searchBoxRight()) tabsLeft = searchBoxRight();

        int tabH  = 16;
        int tabY  = top + (TOOLBAR - tabH) / 2;
        int tx    = tabsLeft;
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i]; int cw = tw[i];
            boolean sel = isTabActive(label);
            boolean hov = mx >= tx && mx <= tx + cw && my >= top && my < bot;
            float ht = tabHover.getOrDefault(label, 0f);
            ht += ((hov ? 1f : 0f) - ht) * Math.min(1f, delta * 0.4f);
            tabHover.put(label, ht);

            if (sel) {
                // Filled pill
                ctx.method_25294(tx, tabY, tx + cw, tabY + tabH, col(Ui.COL_ACCENT & 0xFFFFFF, 35));
                ctx.method_25294(tx, tabY, tx + cw, tabY + 1, col(Ui.COL_ACCENT & 0xFFFFFF, 200));
                ctx.method_25294(tx, tabY + tabH - 1, tx + cw, tabY + tabH, col(Ui.COL_ACCENT & 0xFFFFFF, 80));
                ctx.method_25294(tx, tabY, tx + 1, tabY + tabH, col(Ui.COL_ACCENT & 0xFFFFFF, 120));
                ctx.method_25294(tx + cw - 1, tabY, tx + cw, tabY + tabH, col(Ui.COL_ACCENT & 0xFFFFFF, 120));
                DrawHelper.drawText(ctx, field_22793, label,
                    tx + (cw - field_22793.method_1727(label)) / 2, tabY + (tabH - 9) / 2, Ui.COL_ACCENT, false);
            } else {
                if (ht > 0.02f) ctx.method_25294(tx, tabY, tx + cw, tabY + tabH, col(Ui.COL_SURFACE & 0xFFFFFF, (int)(120 * ht)));
                int fg = lerp(Ui.COL_MUTED, Ui.COL_TEXT, ht);
                DrawHelper.drawText(ctx, field_22793, label,
                    tx + (cw - field_22793.method_1727(label)) / 2, tabY + (tabH - 9) / 2, fg, false);
            }
            tx += cw + gap;
        }
    }

    private void drawLocalToggle(class_332 ctx, int mx, int my, int footTop, float delta) {
        boolean showLocal = SlothyConfig.isShowLocalPacks();
        localCheckAnim = Ui.tickCheckAnim(localCheckAnim, showLocal, delta);
        String label = "Show local packs";
        localCheckH = 12;
        localCheckW = field_22793.method_1727(label) + 18;
        localCheckX = PAD;
        localCheckY = footTop + (FOOTER - localCheckH) / 2;
        boolean lcHov = mx >= localCheckX && mx <= localCheckX + localCheckW
            && my >= footTop && my < field_22790;
        int box = localCheckX;
        int boxY = localCheckY + 1;
        Ui.drawAnimatedCheckbox(ctx, box, boxY, 10, localCheckAnim, lcHov);
        DrawHelper.drawText(ctx, field_22793, label,
            box + 14, localCheckY + 2, showLocal ? Ui.COL_ACCENT : (lcHov ? Ui.COL_TEXT : Ui.COL_MUTED), false);
    }

    private boolean clickLocalCheckbox(double mx, double my) {
        int footTop = field_22790 - FOOTER;
        if (my < footTop || my >= field_22790) return false;
        if (mx >= localCheckX && mx <= localCheckX + localCheckW) {
            boolean was = SlothyConfig.isShowLocalPacks();
            SlothyConfig.setShowLocalPacks(!was);
            if (!was) {
                Ui.spawnSelectionBurst((int) mx, (int) my, Ui.COL_ACCENT);
                Ui.playSuccess();
            } else {
                Ui.playClick();
            }
            invalidateCache();
            scroll = scrollTarget = 0;
            return true;
        }
        return false;
    }

    private boolean isTabActive(String tab) {
        if ("All".equals(tab)) return activeTabs.isEmpty();
        for (String t : activeTabs) if (t.equalsIgnoreCase(tab)) return true;
        return false;
    }

    private boolean clickTab(double mx, double my) {
        int top = HEADER, bot = HEADER + TOOLBAR;
        if (my < top || my >= bot) return false;
        int gap = 8;
        int[] tw = new int[TABS.length];
        int totalTabW = 0;
        for (int i = 0; i < TABS.length; i++) { tw[i] = field_22793.method_1727(TABS[i]) + 10; totalTabW += tw[i]; }
        totalTabW += gap * (TABS.length - 1);
        int tabsRight = field_22789 - PAD;
        int tabsLeft  = tabsRight - totalTabW;
        if (tabsLeft < searchBoxRight()) tabsLeft = searchBoxRight();
        int tx = tabsLeft;
        for (int i = 0; i < TABS.length; i++) {
            int cw = tw[i];
            if (mx >= tx && mx <= tx + cw) {
                String t = TABS[i];
                if ("All".equals(t)) { activeTabs.clear(); }
                else {
                    boolean already = activeTabs.size() == 1 && isTabActive(t);
                    activeTabs.clear();
                    if (!already) activeTabs.add(t);
                }
                scroll = scrollTarget = 0;
                invalidateCache();
                return true;
            }
            tx += cw + gap;
        }
        return false;
    }

    // ── Footer ────────────────────────────────────────────────────────────

    private void drawFooter(class_332 ctx, int mx, int my, float delta) {
        int footTop = field_22790 - FOOTER;
        ctx.method_25294(0, footTop, field_22789, field_22790, Ui.COL_PANEL);
        ctx.method_25294(0, footTop, field_22789, footTop + 1, Ui.COL_BORDER);
        drawLocalToggle(ctx, mx, my, footTop, delta);
    }

    // ── Pack list ─────────────────────────────────────────────────────────

    private void drawList(class_332 ctx, int mx, int my, float delta) {
        int top  = HEADER + TOOLBAR;
        int bot  = field_22790 - FOOTER;
        int listH = bot - top;
        int cardW = Math.min(field_22789 - PAD * 2, 860);
        int cardX = (field_22789 - cardW) / 2;

        DrawHelper.flushDraw(ctx);
        ctx.method_44379(0, top, field_22789, bot);

        if (loading)  { drawSkeleton(ctx, cardX, top, cardW); ctx.method_44380(); return; }
        if (error != null) { drawEmpty(ctx, top, listH, "Couldn't connect", error); ctx.method_44380(); return; }

        List<Pack> visible = filtered();

        if (allPacks.isEmpty()) {
            drawEmpty(ctx, top, listH, "No packs yet", "Drop packs into your slothyhub-local folder."); ctx.method_44380(); return;
        }
        if (visible.isEmpty()) {
            String q = searchQuery.trim();
            String msg = q.isEmpty() ? "Try a different filter." : "No results for \"" + q + "\"";
            drawEmpty(ctx, top, listH, "Nothing here", msg); ctx.method_44380(); return;
        }

        int y = top - (int) scroll;
        for (int i = 0; i < visible.size(); i++) {
            Pack p = visible.get(i);
            if (y + CARD_H < top) { y += CARD_H; continue; }
            if (y > bot) break;
            drawCard(ctx, p, cardX, y, cardW, mx, my, delta, i);
            y += CARD_H;
        }

        // Fade edges
        ctx.method_25296(cardX, top,      cardX + cardW, top + 14,
            col(Ui.COL_BG & 0xFFFFFF, 255), col(Ui.COL_BG & 0xFFFFFF, 0));
        ctx.method_25296(cardX, bot - 14, cardX + cardW, bot,
            col(Ui.COL_BG & 0xFFFFFF, 0),   col(Ui.COL_BG & 0xFFFFFF, 255));
        DrawHelper.flushDraw(ctx);
        ctx.method_44380();
        DrawHelper.flushDraw(ctx);

        // Scrollbar
        int totalH = visible.size() * CARD_H;
        if (totalH > listH) {
            int trkW = 3, trkH = listH - 16;
            int trkX = field_22789 - trkW - 4, trkY = top + 8;
            int tmbH = Math.max(24, trkH * listH / totalH);
            int tmbY = trkY + (int)((double)(trkH - tmbH) * scroll / Math.max(1, totalH - listH));
            ctx.method_25294(trkX, trkY, trkX + trkW, trkY + trkH, col(Ui.COL_BORDER & 0xFFFFFF, 100));
            ctx.method_25294(trkX, tmbY, trkX + trkW, tmbY + tmbH, col(Ui.COL_ACCENT & 0xFFFFFF, 180));
        }
    }

    // ── Card ──────────────────────────────────────────────────────────────

    private void drawCard(class_332 ctx, Pack pack, int x, int y,
                           int w, int mx, int my, float delta, int idx) {
        int top = HEADER + TOOLBAR, bot = field_22790 - FOOTER;
        boolean anim = SlothyConfig.isAnimationsEnabled();
        float stagger = Math.max(0f, Math.min(1f, cardEntryT * 4f - idx * 0.3f));
        int entryOff = anim ? (int)((1f - Ui.easeOutCubic(stagger)) * 14) : 0;

        boolean hov = mx >= x && mx <= x + w && my >= y + entryOff && my <= y + CARD_H + entryOff
            && my >= top && my < bot;
        float ht = cardHover.getOrDefault(pack.getId(), 0f);
        ht += ((hov ? 1f : 0f) - ht) * (anim ? Math.min(1f, delta * 0.42f) : 1f);
        cardHover.put(pack.getId(), ht);

        int drawY = y + entryOff;
        boolean applied = activeIds.contains(pack.getId());

        // Card always has a subtle base background
        int cardBg = lerp(col(Ui.COL_PANEL & 0xFFFFFF, 120), col(Ui.COL_SURFACE & 0xFFFFFF, 200), ht);
        ctx.method_25294(x, drawY, x + w, drawY + CARD_H, cardBg);

        // Left accent stripe — 3px, gradient for applied
        int stripeCol;
        if (applied) stripeCol = Ui.COL_ACCENT;
        else if (pack.isLocal()) stripeCol = col(0x8a6a3a, (int)(100 + 80 * ht));
        else stripeCol = col(Ui.COL_BORDER & 0xFFFFFF, (int)(60 + 140 * ht));
        ctx.method_25294(x, drawY, x + 3, drawY + CARD_H, stripeCol);

        // Subtle gradient overlay on hover
        if (ht > 0.02f) {
            ctx.method_25296(x + 3, drawY, x + w / 3, drawY + CARD_H,
                col(Ui.COL_ACCENT & 0xFFFFFF, (int)(8 * ht)), col(Ui.COL_ACCENT & 0xFFFFFF, 0));
        }

        // Bottom separator
        ctx.method_25294(x + 3, drawY + CARD_H - 1, x + w, drawY + CARD_H,
            col(Ui.COL_BORDER & 0xFFFFFF, (int)(80 + 80 * ht)));

        int tX = x + PAD + 6, tY = drawY + (CARD_H - THUMB) / 2;
        int textX = tX + THUMB + 14;
        int badgeY = drawY + CARD_H / 2 - 11;
        int nameY = badgeY;

        // Badge fills before custom textures — drawTexture leaves UV state that breaks fills on 1.21.4
        String badge; int badgeBg, badgeFg;
        if (pack.isFeatured())   { badge = "FEATURED"; badgeBg = col(0xFFD54F, 45); badgeFg = col(0xFFD54F, 255); }
        else if (applied)         { badge = "HANGING";  badgeBg = col(Ui.COL_ACCENT & 0xFFFFFF, 35); badgeFg = Ui.COL_ACCENT; }
        else if (pack.isLocal()) { badge = "LOCAL"; badgeBg = col(0x8a6a3a, 30); badgeFg = col(0xc89a60, 220); }
        else {
            List<String> tags = pack.getTags();
            badge = tags.isEmpty() ? "" : tags.get(0).toUpperCase(Locale.ROOT);
            badgeBg = col(Ui.COL_SURFACE & 0xFFFFFF, 180); badgeFg = Ui.COL_MUTED;
        }
        if (!badge.isEmpty()) {
            int bw = field_22793.method_1727(badge) + 8, bh2 = 10;
            ctx.method_25294(textX, badgeY, textX + bw, badgeY + bh2, badgeBg);
            ctx.method_25294(textX, badgeY, textX + bw, badgeY + 1, badgeFg);
            ctx.method_25294(textX, badgeY, textX + 1, badgeY + bh2, badgeFg);
            ctx.method_25294(textX + bw - 1, badgeY, textX + bw, badgeY + bh2, badgeFg);
            ctx.method_25294(textX, badgeY + bh2 - 1, textX + bw, badgeY + bh2, col(badgeFg & 0xFFFFFF, 80));
            DrawHelper.drawText(ctx, field_22793, badge, textX + 4, badgeY + 1, badgeFg, false);
            nameY = badgeY + bh2 + 3;
        }

        // ── Thumbnail ─────────────────────────────────────────────────────
        ctx.method_25294(tX - 1, tY - 1, tX + THUMB + 1, tY + THUMB + 1,
            col(Ui.COL_BORDER & 0xFFFFFF, (int)(120 + 100 * ht)));
        ctx.method_25294(tX, tY, tX + THUMB, tY + THUMB, col(0x0a1410, 255));
        if (applied)
            ctx.method_25294(tX - 1, tY - 1, tX + THUMB + 1, tY, col(Ui.COL_ACCENT & 0xFFFFFF, 200));
        Thumb thumb = thumbs.get(pack.getId());
        if (thumb != null) {
            float fa = Math.min(1f, thumbFadeIn.getOrDefault(pack.getId(), 0f) + delta * 0.12f);
            thumbFadeIn.put(pack.getId(), fa);
            DrawHelper.drawTexture(ctx, thumb.id(), tX, tY, 0f, 0f, THUMB, THUMB, thumb.width(), thumb.height());
            DrawHelper.flushDraw(ctx);
        } else {
            Ui.drawPawPrint(ctx, tX + THUMB / 2, tY + THUMB / 2, col(Ui.COL_ACCENT & 0xFFFFFF, 40), 0.7f);
            float phase = (float)(System.currentTimeMillis() % 2400L) / 2400f;
            Ui.shimmer(ctx, tX, tY, THUMB, THUMB, phase, col(Ui.COL_ACCENT & 0xFFFFFF, 15));
        }

        // ── Text ──────────────────────────────────────────────────────────
        // Pack name (with shadow for emphasis)
        String name = pack.getName();
        int maxNameW = x + w - BTN_W - 70 - textX;
        if (field_22793.method_1727(name) > maxNameW) {
            while (name.length() > 3 && field_22793.method_1727(name + "…") > maxNameW)
                name = name.substring(0, name.length() - 1);
            name += "…";
        }
        DrawHelper.drawText(ctx, field_22793, name, textX + 1, nameY + 1, col(0, 60), false);
        DrawHelper.drawText(ctx, field_22793, name, textX, nameY, Ui.COL_TEXT, false);

        // Author + format
        String sub = "by " + pack.getAuthorName() + (pack.isZip() ? "  ·  .zip" : (pack.isLocal() ? "" : "  ·  .rar"));
        DrawHelper.drawText(ctx, field_22793, sub, textX, nameY + 11, Ui.COL_MUTED, false);

        // ── Star button (non-local only) ───────────────────────────────────
        int btnX = x + w - BTN_W - PAD - 2;
        int btnY = drawY + (CARD_H - BTN_H) / 2;
        if (!pack.isLocal()) {
            int starX = btnX - 44 - 6;
            drawStarPill(ctx, pack, starX, btnY, mx, my, delta);
        }

        drawActionBtn(ctx, pack, btnX, btnY, mx, my, delta);
    }

    // ── Star pill ─────────────────────────────────────────────────────────

    private void drawStarPill(class_332 ctx, Pack pack, int x, int y,
                               int mx, int my, float delta) {
        boolean hov = mx >= x && mx <= x + 44 && my >= y && my <= y + BTN_H
            && my >= HEADER + TOOLBAR && my < field_22790 - FOOTER;
        boolean starred = pack.isViewerStarred();
        int starCol = starred ? Ui.COL_GOLD : (hov ? col(Ui.COL_GOLD & 0xFFFFFF, 200) : Ui.COL_MUTED);
        int bg  = starred ? col(Ui.COL_GOLD & 0xFFFFFF, 25) : (hov ? col(Ui.COL_GOLD & 0xFFFFFF, 15) : col(Ui.COL_SURFACE & 0xFFFFFF, 80));
        ctx.method_25294(x, y, x + 44, y + BTN_H, bg);
        ctx.method_25294(x, y, x + 44, y + 1, col(starCol & 0xFFFFFF, 150));
        ctx.method_25294(x, y + BTN_H - 1, x + 44, y + BTN_H, col(starCol & 0xFFFFFF, 80));
        ctx.method_25294(x, y, x + 1, y + BTN_H, col(starCol & 0xFFFFFF, 80));
        ctx.method_25294(x + 43, y, x + 44, y + BTN_H, col(starCol & 0xFFFFFF, 80));
        String lbl = "★ " + pack.getStarCount();
        DrawHelper.drawText(ctx, field_22793, lbl, x + (44 - field_22793.method_1727(lbl)) / 2,
            y + (BTN_H - 9) / 2, starCol, false);
    }

    // ── Action button ─────────────────────────────────────────────────────

    private void drawActionBtn(class_332 ctx, Pack pack, int bx, int by,
                                int mx, int my, float delta) {
        DlState state = dlState.getOrDefault(pack.getId(), DlState.IDLE);
        boolean applied = activeIds.contains(pack.getId());
        boolean hov = mx >= bx && mx <= bx + BTN_W && my >= by && my <= by + BTN_H
            && my >= HEADER + TOOLBAR && my < field_22790 - FOOTER;
        float ht = btnHover.getOrDefault(pack.getId(), 0f);
        ht += ((hov ? 1f : 0f) - ht) * Math.min(1f, delta * 0.4f);
        btnHover.put(pack.getId(), ht);

        int bg, fg, top;
        String label;
        switch (state) {
            case DOWNLOADING -> {
                float p = dlProgressUi.getOrDefault(pack.getId(), 0f);
                label = (int)(p * 100f) + "%";
                bg = Ui.COL_PANEL; fg = Ui.COL_MUTED; top = Ui.COL_BORDER;
            }
            case APPLYING -> { label = "APPLYING…"; bg = Ui.COL_PANEL; fg = Ui.COL_MUTED; top = Ui.COL_BORDER; }
            case DONE -> {
                if (hov) { label = "REMOVE"; bg = col(Ui.COL_DANGER & 0xFFFFFF, 35); fg = Ui.COL_DANGER; top = col(Ui.COL_DANGER & 0xFFFFFF, 180); }
                else       { label = "APPLIED"; bg = col(Ui.COL_ACCENT & 0xFFFFFF, 25); fg = Ui.COL_ACCENT; top = col(Ui.COL_ACCENT & 0xFFFFFF, 160); }
            }
            case ERROR -> { label = "RETRY"; bg = col(Ui.COL_DANGER & 0xFFFFFF, 25); fg = Ui.COL_DANGER; top = col(Ui.COL_DANGER & 0xFFFFFF, 180); }
            default -> {
                label = pack.isLocal() ? "APPLY" : "HANG & APPLY";
                // Filled solid button
                bg  = hov ? Ui.COL_ACCENT : col(Ui.COL_ACCENT & 0xFFFFFF, (int)(40 + 60 * ht));
                fg  = hov ? Ui.COL_BG : lerp(Ui.COL_MUTED, Ui.COL_TEXT, ht);
                top = Ui.COL_ACCENT;
            }
        }

        // Button body
        ctx.method_25294(bx, by, bx + BTN_W, by + BTN_H, bg);
        // Top highlight
        ctx.method_25294(bx, by, bx + BTN_W, by + 1, top);
        // Bottom + sides (darker)
        ctx.method_25294(bx, by + BTN_H - 1, bx + BTN_W, by + BTN_H, col(top & 0xFFFFFF, 80));
        ctx.method_25294(bx, by, bx + 1, by + BTN_H, col(top & 0xFFFFFF, 120));
        ctx.method_25294(bx + BTN_W - 1, by, bx + BTN_W, by + BTN_H, col(top & 0xFFFFFF, 120));

        // Progress bar
        if (state == DlState.DOWNLOADING) {
            float p = dlProgressUi.getOrDefault(pack.getId(), 0f);
            ctx.method_25294(bx + 1, by + BTN_H - 2, bx + 1 + (int)((BTN_W - 2) * p), by + BTN_H - 1, Ui.COL_ACCENT);
        }

        int textY = by + (BTN_H - 9) / 2;
        if ("APPLIED".equals(label)) {
            int checkSize = 10;
            int gap = 5;
            int textW = field_22793.method_1727(label);
            int totalW = textW + gap + checkSize;
            int tx = bx + (BTN_W - totalW) / 2;
            DrawHelper.drawText(ctx, field_22793, label, tx, textY, fg, false);
            Ui.drawCheckIcon(ctx, tx + textW + gap, by + (BTN_H - checkSize) / 2, checkSize, fg);
        } else {
            DrawHelper.drawText(ctx, field_22793, label,
                bx + (BTN_W - field_22793.method_1727(label)) / 2,
                textY, fg, false);
        }

        if (state == DlState.ERROR && hov) {
            pendingTooltip = dlMsg.getOrDefault(pack.getId(), "Click to retry");
            tooltipMx = mx; tooltipMy = my;
        }
    }

    // ── Skeleton loading ──────────────────────────────────────────────────

    private void drawSkeleton(class_332 ctx, int x, int top, int w) {
        float phase = (float)(System.currentTimeMillis() % 2000L) / 2000f;
        int count = (field_22790 - HEADER - TOOLBAR - FOOTER) / CARD_H + 1;
        for (int i = 0; i < count; i++) {
            int y = top + i * CARD_H;
            ctx.method_25294(x, y, x + w, y + CARD_H, col(Ui.COL_PANEL & 0xFFFFFF, 160));
            ctx.method_25294(x, y, x + 3, y + CARD_H, col(Ui.COL_BORDER & 0xFFFFFF, 180));
            int tX = x + PAD + 6, tY = y + (CARD_H - THUMB) / 2;
            ctx.method_25294(tX, tY, tX + THUMB, tY + THUMB, col(Ui.COL_SURFACE & 0xFFFFFF, 160));
            int tx = tX + THUMB + 14;
            ctx.method_25294(tx, y + 18, tx + 16, y + 26, col(Ui.COL_BORDER & 0xFFFFFF, 160));
            ctx.method_25294(tx, y + 30, tx + 110, y + 38, col(Ui.COL_SURFACE & 0xFFFFFF, 180));
            ctx.method_25294(tx, y + 42, tx + 70, y + 49, col(Ui.COL_BORDER & 0xFFFFFF, 130));
            ctx.method_25294(x, y + CARD_H - 1, x + w, y + CARD_H, col(Ui.COL_BORDER & 0xFFFFFF, 100));
            Ui.shimmer(ctx, x, y, w, CARD_H, phase, col(Ui.COL_ACCENT & 0xFFFFFF, 14));
        }
        String msg = "LOADING PACKS…";
        int mw = field_22793.method_1727(msg);
        int cy = top + (field_22790 - HEADER - TOOLBAR - FOOTER) / 2;
        DrawHelper.drawText(ctx, field_22793, msg, field_22789 / 2 - mw / 2, cy, Ui.COL_MUTED, false);
    }

    // ── Empty state ──────────────────────────────────────────────────────

    private void drawEmpty(class_332 ctx, int top, int listH, String head, String sub) {
        int cy = top + listH / 2 - 5;
        // Subtle icon
        int iconX = field_22789 / 2 - 12, iconY = cy - 22;
        ctx.method_25294(iconX, iconY, iconX + 24, iconY + 16, col(Ui.COL_SURFACE & 0xFFFFFF, 100));
        ctx.method_25294(iconX + 8, iconY + 4, iconX + 16, iconY + 12, col(Ui.COL_BORDER & 0xFFFFFF, 140));

        DrawHelper.drawText(ctx, field_22793, head,
            field_22789 / 2 - field_22793.method_1727(head) / 2, cy, Ui.COL_TEXT, false);
        if (sub != null && !sub.isBlank()) {
            int maxW = Math.min(field_22789 - 60, 420);
            int lineY = cy + 13;
            for (String line : wrapText(sub, maxW)) {
                DrawHelper.drawText(ctx, field_22793, line,
                    field_22789 / 2 - field_22793.method_1727(line) / 2, lineY, Ui.COL_MUTED, false);
                lineY += 11;
            }
        }
    }

    // ── Preview overlay ───────────────────────────────────────────────────

    private void drawPreview(class_332 ctx) {
        int a = (int)(210f * Ui.easeOutCubic(previewT));
        ctx.method_25294(0, 0, field_22789, field_22790, col(0, a));
        if (previewPack == null) return;
        Thumb thumb = thumbs.get(previewPack.getId());
        int margin = 50, maxW = field_22789 - margin * 2, maxH = field_22790 - margin * 2 - 40;
        int w, h;
        if (thumb != null) {
            float scale = Math.min((float) maxW / thumb.width(), (float) maxH / thumb.height());
            w = Math.max(64, (int)(thumb.width() * scale));
            h = Math.max(64, (int)(thumb.height() * scale));
        } else { w = Math.min(maxW, 480); h = w * 9 / 16; if (h > maxH) { h = maxH; w = maxH * 16 / 9; } }
        int bx = (field_22789 - w) / 2, by = (field_22790 - h) / 2 - 14;
        // Panel background
        ctx.method_25294(bx - 4, by - 4, bx + w + 4, by + h + 4, Ui.COL_PANEL);
        ctx.method_25294(bx - 4, by - 4, bx + w + 4, by - 3, Ui.COL_ACCENT);
        ctx.method_25294(bx - 4, by - 3, bx - 3, by + h + 4, Ui.COL_BORDER);
        ctx.method_25294(bx + w + 3, by - 3, bx + w + 4, by + h + 4, Ui.COL_BORDER);
        ctx.method_25294(bx - 4, by + h + 3, bx + w + 4, by + h + 4, Ui.COL_BORDER);
        if (thumb != null) {
            DrawHelper.drawTexture(ctx, thumb.id(), bx, by, 0f, 0f, w, h, thumb.width(), thumb.height());
            DrawHelper.flushDraw(ctx);
        } else ctx.method_25294(bx, by, bx + w, by + h, Ui.COL_SURFACE);
        int ta = (int)(255f * Ui.easeOutCubic(previewT));
        if (ta >= 8) {
            String title = previewPack.getName();
            String sub = "by " + previewPack.getAuthorName() + "  ·  click anywhere to close";
            DrawHelper.drawText(ctx, field_22793, title,
                field_22789 / 2 - field_22793.method_1727(title) / 2, by + h + 10, col(Ui.COL_TEXT & 0xFFFFFF, ta), false);
            DrawHelper.drawText(ctx, field_22793, sub,
                field_22789 / 2 - field_22793.method_1727(sub) / 2, by + h + 21, col(Ui.COL_MUTED & 0xFFFFFF, ta), false);
        }
    }

    // ── Tooltip ────────────────────────────────────────────────────────────

    private void drawTooltip(class_332 ctx, String text, int mx, int my) {
        int maxW = Math.min(field_22789 - 20, 260);
        List<String> lines = wrapText(text, maxW - 14);
        int bw = 0;
        for (String s : lines) bw = Math.max(bw, field_22793.method_1727(s));
        bw += 14; int bh = 7 + lines.size() * 11;
        int tx = mx + 12, ty = my - bh - 5;
        if (tx + bw > field_22789 - 4) tx = field_22789 - 4 - bw;
        if (ty < 4) ty = my + 16;
        ctx.method_25294(tx, ty, tx + bw, ty + bh, col(Ui.COL_PANEL & 0xFFFFFF, 235));
        ctx.method_25294(tx, ty, tx + bw, ty + 1, Ui.COL_ACCENT);
        ctx.method_25294(tx, ty + bh - 1, tx + bw, ty + bh, Ui.COL_BORDER);
        ctx.method_25294(tx, ty, tx + 1, ty + bh, Ui.COL_BORDER);
        ctx.method_25294(tx + bw - 1, ty, tx + bw, ty + bh, Ui.COL_BORDER);
        int ly = ty + 4;
        for (String s : lines) { DrawHelper.drawText(ctx, field_22793, s, tx + 7, ly, Ui.COL_TEXT, false); ly += 11; }
    }

    // ── Input ─────────────────────────────────────────────────────────────


    public boolean method_25402(double mx, double my, int button) {
        if (previewPack != null && button == 0) { previewPack = null; return true; }

        // Header nav + gear first — never let toolbar/footer widgets steal these clicks
        if (button == 0) {
            for (int i = 0; i < NAV.length; i++) {
                if (mx >= navX[i] && mx <= navX[i] + navW[i] && my >= navY && my <= navY + navH) {
                    switch (i) {
                        case 0 -> { return true; }
                        case 1 -> { class_310.method_1551().method_1507(new TexturePickerScreen(this)); return true; }
                        case 2 -> { class_310.method_1551().method_1507(new PackLibraryScreen(this)); return true; }
                        case 3 -> { class_310.method_1551().method_1507(new KillEffectsScreen(this)); return true; }
                        case 4 -> { class_310.method_1551().method_1507(new GuiThemeScreen(this)); return true; }
                    }
                }
            }
            if (mx >= gearX && mx <= gearX + gearSz && my >= gearY && my <= gearY + gearSz) {
                class_310.method_1551().method_1507(new SlothySettingsScreen(this)); return true;
            }
        }

        if (button == 0 && clickTab(mx, my)) return true;

        int top  = HEADER + TOOLBAR, bot = field_22790 - FOOTER;
        int cardW = Math.min(field_22789 - PAD * 2, 860);
        int cardX = (field_22789 - cardW) / 2;

        // Pack cards before toolbar/footer widgets — search field must not steal list clicks
        if (button == 0 && my > top && my < bot && handlePackListClick(mx, my, top, bot, cardX, cardW)) {
            return true;
        }

        if (clickToolbarOrFooter(mx, my, button)) return true;
        return false;
    }

    private boolean handlePackListClick(double mx, double my, int top, int bot, int cardX, int cardW) {
        List<Pack> visible = filtered();
        int y = top - (int) scroll;
        for (Pack pack : visible) {
            if (y + CARD_H < top) { y += CARD_H; continue; }
            if (y > bot) break;
            int btnX = cardX + cardW - BTN_W - PAD - 2;
            int btnY = y + (CARD_H - BTN_H) / 2;
            if (!pack.isLocal()) {
                int starX = btnX - 44 - 6;
                if (mx >= starX && mx <= starX + 44 && my >= btnY && my <= btnY + BTN_H) {
                    handleStar(pack); return true;
                }
            }
            if (mx >= btnX && mx <= btnX + BTN_W && my >= btnY && my <= btnY + BTN_H) {
                DlState s = dlState.getOrDefault(pack.getId(), DlState.IDLE);
                if (s == DlState.DOWNLOADING || s == DlState.APPLYING) return false;
                handlePackClick(pack); return true;
            }
            int tX = cardX + PAD + 6, tY = y + (CARD_H - THUMB) / 2;
            if (mx >= tX && mx <= tX + THUMB && my >= tY && my <= tY + THUMB) {
                previewPack = pack; return true;
            }
            y += CARD_H;
        }
        return false;
    }

    /** Search box + footer controls only — never intercept pack list clicks. */
    private boolean clickToolbarOrFooter(double mx, double my, int button) {
        int toolTop = HEADER, toolBot = HEADER + TOOLBAR;
        int footTop = field_22790 - FOOTER;

        if (my >= toolTop && my < toolBot) {
            int sx = searchX - 2, sy = searchY - 2;
            int sw = searchW + 4, sh = searchH + 4;
            if (mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sh) {
                searchFocused = true;
                return true;
            }
            if (searchFocused) searchFocused = false;
        }

        if (button == 0 && clickLocalCheckbox(mx, my)) return true;

        if (my >= footTop && my < field_22790) {
            for (class_364 child : method_25396()) {
                if (child instanceof CustomButtonBase btn && btn.tryPress(mx, my)) {
                    method_25395(child);
                    if (button == 0) method_25398(true);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean onScrollDelta(double vDelta) {
        if (previewPack != null) return true;
        int listH = field_22790 - HEADER - TOOLBAR - FOOTER;
        int totalH = filtered().size() * CARD_H;
        scrollTarget = Math.max(0, Math.min(scrollTarget - vDelta * 24, Math.max(0, totalH - listH)));
        return true;
    }

    @Override
    public boolean method_25400(char chr, int modifiers) {
        if (searchFocused) {
            if (chr >= 32 && chr != 127 && searchQuery.length() < 80) {
                searchQuery += chr;
                invalidateCache();
                scrollTarget = 0;
                scroll = 0;
            }
            return true;
        }
        return super.method_25400(chr, modifiers);
    }

    @Override
    public boolean method_25404(int key, int scan, int mods) {
        if (previewPack != null && key == 256) { previewPack = null; return true; }
        if (searchFocused && key == 259 && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            invalidateCache();
            return true;
        }
        if (key == 256 && !searchQuery.isEmpty()) {
            searchQuery = ""; searchFocused = false; invalidateCache(); return true;
        }
        if (InputCompat.needsPolling() && key == 256) { method_25419(); return true; }
        return super.method_25404(key, scan, mods);
    }

    // ── Business logic ────────────────────────────────────────────────────

    private void reconnect() {
        loading = true; error = null; cardEntryT = 0;
        executor.submit(() -> { SlothyConfig.tryAutoDiscover(); fetchPacks(); });
    }

    private void pickRandomPack() {
        List<Pack> vis = filtered();
        if (vis.isEmpty()) {
            activeTabs.clear();
            invalidateCache();
            vis = filtered();
            if (vis.isEmpty()) return;
        }
        Pack pick = vis.get((int)(Math.random() * vis.size()));
        int idx = vis.indexOf(pick);
        int listH = field_22790 - HEADER - TOOLBAR - FOOTER;
        scrollTarget = Math.max(0, idx * CARD_H - listH / 2 + CARD_H / 2);
        cardHover.put(pick.getId(), 1f);
    }

    private void handleStar(Pack pack) {
        if (pack.isLocal() || !starInFlight.add(pack.getId())) return;
        if (SlothyConfig.getWorkerBaseUrl().isBlank()) {
            starInFlight.remove(pack.getId());
            dlMsg.put(pack.getId(), "Star server is not configured.");
            return;
        }
        boolean wasStarred = pack.isViewerStarred();
        int prev = pack.getStarCount();
        if (!wasStarred) Ui.playStar();
        pack.setStarCount(wasStarred ? Math.max(0, prev - 1) : prev + 1);
        pack.setViewerStarred(!wasStarred);
        invalidateCache();
        executor.submit(() -> {
            try {
                PackApiClient.StarResult res = PackApiClient.starPack(pack);
                class_310.method_1551().execute(() -> {
                    pack.setStarCount(res.starCount());
                    pack.setViewerStarred(res.viewerStarred());
                    invalidateCache();
                    starInFlight.remove(pack.getId());
                });
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null || msg.isBlank()) msg = "Star failed.";
                String fm = msg;
                class_310.method_1551().execute(() -> {
                    pack.setStarCount(prev);
                    pack.setViewerStarred(wasStarred);
                    invalidateCache();
                    dlMsg.put(pack.getId(), fm);
                    starInFlight.remove(pack.getId());
                });
            }
        });
    }

    private void handlePackClick(Pack pack) {
        DlState s  = dlState.getOrDefault(pack.getId(), DlState.IDLE);
        boolean active = activeIds.contains(pack.getId());

        if (s == DlState.DONE || active) {
            if (SlothyConfig.isConfirmBeforeRemove()) {
                long now = System.currentTimeMillis();
                if (!pack.getId().equals(confirmRemoveId) || now - confirmRemoveAt > 4000L) {
                    confirmRemoveId = pack.getId(); confirmRemoveAt = now; return;
                }
                confirmRemoveId = null; confirmRemoveAt = 0;
            }
            dlState.put(pack.getId(), DlState.APPLYING);
            PackDownloader.removePack(pack, () -> {
                activeIds = PackDownloader.getActivePackIds();
                dlState.put(pack.getId(), DlState.IDLE);
                dlProgress.put(pack.getId(), 0f); dlProgressUi.put(pack.getId(), 0f);
                invalidateCache();
            });
        } else {
            String srv = SlothyConfig.getServerUrl();
            dlState.put(pack.getId(), DlState.DOWNLOADING);
            dlProgress.put(pack.getId(), 0f); dlProgressUi.put(pack.getId(), 0f);
            executor.submit(() -> PackDownloader.downloadAndApply(pack, srv, new PackDownloader.ProgressCallback() {
                public void onProgress(float f) { dlProgress.put(pack.getId(), f); }
                public void onApplying() { dlState.put(pack.getId(), DlState.APPLYING); }
                public void onDone() {
                    dlState.put(pack.getId(), DlState.DONE);
                    activeIds = PackDownloader.getActivePackIds(); invalidateCache();
                }
                public void onError(String msg) {
                    dlState.put(pack.getId(), DlState.ERROR); dlMsg.put(pack.getId(), msg);
                }
            }));
        }
    }

    private void fetchPacks() {
        localPacks = InstalledPackScanner.scan(catalogFilenames(PackCatalog.loadEmbedded()));
        if (!SlothyConfig.isConfigured()) {
            allPacks = new ArrayList<>(localPacks);
            loading = false;
            // Still render icons for locally-installed packs even without a server.
            loadThumbs("");
            return;
        }
        String srv = SlothyConfig.getServerUrl();
        executor.submit(() -> {
            try {
                List<Pack> loaded = PackApiClient.fetchPacks(srv);
                class_310.method_1551().execute(() -> {
                    remotePacks = loaded;
                    localPacks  = InstalledPackScanner.scan(catalogFilenames(remotePacks));
                    allPacks    = mergeCatalog(remotePacks, localPacks);
                    loading = false; error = null;
                    activeIds = PackDownloader.getActivePackIds();
                    for (Pack p : allPacks) {
                        if (activeIds.contains(p.getId())) {
                            dlState.put(p.getId(), DlState.DONE);
                            dlProgress.put(p.getId(), 1f); dlProgressUi.put(p.getId(), 1f);
                        }
                    }
                    cardEntryT = 0;
                    loadThumbs(srv);
                });
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();
                String fm = msg;
                class_310.method_1551().execute(() -> {
                    error = fm;
                    loading = false;
                    List<Pack> embedded = PackCatalog.loadEmbedded();
                    localPacks = InstalledPackScanner.scan(catalogFilenames(embedded));
                    allPacks = mergeCatalog(embedded, localPacks);
                    // Catalog fetch failed (offline / API down) — still load icons for what we have.
                    loadThumbs(srv);
                });
            }
        });
    }

    private void loadThumbs(String srv) {
        for (Pack p : allPacks) {
            if (thumbs.containsKey(p.getId())) continue;
            PackIconLoader.loadIcon(p, srv, new PackIconLoader.IconCallback() {
                public void onLoaded(String packId, class_2960 id, int w, int h) {
                    thumbs.put(packId, new Thumb(id, w, h));
                    thumbFadeIn.put(packId, 0f);
                }
                public void onFailed(String packId) {}
            });
        }
    }

    // ── Filter / cache ────────────────────────────────────────────────────

    private List<Pack> filtered() {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        Set<String> tabs = new LinkedHashSet<>(activeTabs);
        boolean appliedOnly = false;
        Set<String> tagSet = new LinkedHashSet<>();
        for (String t : tabs) {
            if ("Applied".equalsIgnoreCase(t)) appliedOnly = true;
            else tagSet.add(t);
        }
        String key = String.join(",", tabs) + "\0" + q + "\0" + (appliedOnly ? activeIds.hashCode() : "")
            + "\0" + SlothyConfig.isShowLocalPacks();
        if (filteredCache != null && filteredSrc == allPacks && key.equals(filteredKey)) return filteredCache;

        List<Pack> out = new ArrayList<>();
        for (Pack p : allPacks) {
            if (!SlothyConfig.isShowLocalPacks() && p.isLocal()) continue;
            if (appliedOnly && !activeIds.contains(p.getId())) continue;
            if (!tagSet.isEmpty() && !packMatchesTags(p, tagSet)) continue;
            if (!q.isEmpty()) {
                String hay = (p.getName() + " " + p.getAuthorName() + " " + String.join(" ", p.getTags()))
                    .toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            out.add(p);
        }
        if (SlothyConfig.isSortByStars()) out.sort((a, b) -> Integer.compare(b.getStarCount(), a.getStarCount()));
        out.sort((a, b) -> Boolean.compare(b.isFeatured(), a.isFeatured()));
        filteredCache = out; filteredSrc = allPacks; filteredKey = key;
        return filteredCache;
    }

    private static boolean packMatchesTags(Pack p, Set<String> wanted) {
        for (String t : p.getTags()) if (t != null) for (String w : wanted) if (w.equalsIgnoreCase(t)) return true;
        return false;
    }

    private void invalidateCache() { filteredCache = null; filteredSrc = null; filteredKey = null; }

    private static Set<String> catalogFilenames(List<Pack> catalog) {
        Set<String> out = new HashSet<>(PackCatalog.embeddedFilenames());
        for (Pack p : catalog) {
            if (p.getPackFilename() != null && !p.getPackFilename().isBlank())
                out.add(p.getPackFilename().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static List<Pack> mergeCatalog(List<Pack> remote, List<Pack> installed) {
        Map<String, Pack> byFile = new LinkedHashMap<>();
        for (Pack p : remote) {
            if (p.getPackFilename() != null && !p.getPackFilename().isBlank())
                byFile.put(p.getPackFilename().toLowerCase(Locale.ROOT), p);
            else
                byFile.put("id:" + p.getId(), p);
        }
        for (Pack p : installed) {
            String key = p.getPackFilename().toLowerCase(Locale.ROOT);
            Pack existing = byFile.get(key);
            if (existing != null) {
                existing.setHasLocalFile(true);
                existing.setLocal(false);
            } else {
                byFile.put(key, p);
            }
        }
        return new ArrayList<>(byFile.values());
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private List<String> wrapText(String text, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = cur.isEmpty() ? word : cur + " " + word;
            if (field_22793.method_1727(candidate) <= maxW) { cur.setLength(0); cur.append(candidate); }
            else { if (!cur.isEmpty()) out.add(cur.toString()); cur.setLength(0); cur.append(word); }
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    private static String sanitize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    // ── Inner types ───────────────────────────────────────────────────────

    private enum DlState { IDLE, DOWNLOADING, APPLYING, DONE, ERROR }
    private record Thumb(class_2960 id, int width, int height) {}
}
