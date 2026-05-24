package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.ui.CustomButton;
import com.slothyhub.ui.CustomButtonBase;
import com.slothyhub.ui.Ui;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_364;
import net.minecraft.class_4068;
import net.minecraft.class_437;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Texture Builder — pick a texture for each named-item slot from your local packs
 * and bake them into a ready-to-apply CIT resource pack.
 *
 * Each SlotDef maps to:
 *   - a human display name shown in the UI  ("Pro Sword")
 *   - one or more Minecraft item IDs the CIT rule targets  ("diamond_sword")
 *   - an optional name-match string for the CIT rule       ("Pro Sword")
 *     (null = texture applies unconditionally, like for fireworks)
 */
public class TexturePickerScreen extends class_437 {

    // ── Slot definitions ──────────────────────────────────────────────────
    record SlotDef(String display, String[] mcItems, String citName, String emoji) {}

    private static final SlotDef[] SLOTS = {
        new SlotDef("Noob Sword",    new String[]{"diamond_sword","netherite_sword","iron_sword"}, "Noob Sword",    "⚔"),
        new SlotDef("Good Sword",    new String[]{"diamond_sword","netherite_sword"},              "Good Sword",    "⚔"),
        new SlotDef("Pro Sword",     new String[]{"diamond_sword","netherite_sword"},              "Pro Sword",     "⚔"),
        new SlotDef("Perfect Sword", new String[]{"diamond_sword","netherite_sword"},              "Perfect Sword", "⚔"),
        new SlotDef("Hippo Sword",   new String[]{"diamond_sword","netherite_sword"},              "Hippo Sword",   "🦛"),
        new SlotDef("Warden Sword",  new String[]{"diamond_sword","netherite_sword"},              "Warden Sword",  "🌑"),
        new SlotDef("Particles",     new String[]{"potion","splash_potion","lingering_potion"},    null,            "✨"),
        new SlotDef("Fireworks",     new String[]{"firework_rocket"},                              null,            "🎆"),
        new SlotDef("Golden Apple",  new String[]{"golden_apple","enchanted_golden_apple"},        null,            "🍎"),
        new SlotDef("Offhands",      new String[]{"shield","totem_of_undying"},                    null,            "🛡"),
    };

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int HEADER = 52;
    private static final int FOOTER = 48;
    private static final int ITEM_H = 40;
    private static final int LEFT_W = 180;
    private static final int PAD    = 14;

    // ── Palette ───────────────────────────────────────────────────────────
    private static final int BG      = Ui.COL_BG;
    private static final int PANEL   = Ui.COL_PANEL;
    private static final int SURFACE = Ui.COL_SURFACE;
    private static final int ACCENT  = Ui.COL_ACCENT;
    private static final int DANGER  = Ui.COL_DANGER;
    private static final int TEXT    = Ui.COL_TEXT;
    private static final int MUTED   = Ui.COL_MUTED;
    private static final int BORDER  = Ui.COL_BORDER;

    private static int col(int rgb, int a)        { return Ui.withAlpha(rgb & 0xFFFFFF, a); }
    private static int lerp(int a, int b, float t) { return Ui.lerpColor(a, b, t); }

    // ── State ─────────────────────────────────────────────────────────────
    private final class_437 parent;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** slot index → list of TextureOption discovered in local packs */
    private final Map<Integer, List<TextureOption>> discovered = new LinkedHashMap<>();
    /** slot index → chosen TextureOption index (−1 = none) */
    private final Map<Integer, Integer> selections = new LinkedHashMap<>();

    private boolean scanning  = true;
    private String  scanStatus = "Scanning local packs…";
    private String  buildStatus = null;
    private boolean buildOk   = false;

    private int    selectedSlot = 0;
    private double itemScroll   = 0, itemScrollTarget   = 0;
    private double texScroll    = 0, texScrollTarget    = 0;

    private final Map<Integer, Float> itemHover = new HashMap<>();
    private final Map<String,  Float> texHover  = new HashMap<>();

    private class_342 searchField;
    private String searchQuery = "";

    /** Represents one discoverable texture inside a local pack for a given slot. */
    record TextureOption(String packName, String label, String propsPath, byte[] pngData) {}

    // ─────────────────────────────────────────────────────────────────────
    public TexturePickerScreen(class_437 parent) {
        super(class_2561.method_43470("Texture Builder"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
        // Footer buttons
        int bw = 120, bh = 26, gap = 10;
        int totalW = bw * 2 + gap;
        int bx = field_22789 / 2 - totalW / 2;
        int by = field_22790 - FOOTER + (FOOTER - bh) / 2;
        method_37063(new CustomButton(bx, by, bw, bh,
            class_2561.method_43470("BUILD & APPLY"), CustomButtonBase.Style.MOSS,
            this::buildAndApply));
        method_37063(new CustomButton(bx + bw + gap, by, bw, bh,
            class_2561.method_43470("BACK"), CustomButtonBase.Style.SECONDARY,
            this::method_25419));

        // Search box
        int sfW = Math.min(200, (field_22789 - LEFT_W - PAD * 3) / 2);
        searchField = new class_342(field_22793, field_22789 - sfW - PAD,
            HEADER + PAD / 2, sfW, 12, class_2561.method_43470("Filter…"));
        searchField.method_1858(false);
        searchField.method_1880(60);
        searchField.method_47404(class_2561.method_43470("Filter textures…"));
        searchField.method_1868(TEXT);
        searchField.method_1863(q -> { searchQuery = q; texScroll = texScrollTarget = 0; });
        method_37063(searchField);

        executor.submit(this::scanLocalPacks);
    }

    @Override
    public void method_25419() {
        executor.shutdownNow();
        class_310.method_1551().method_1507(parent);
    }

    @Override public boolean method_25421() { return false; }

    // ── Scan ──────────────────────────────────────────────────────────────

    private void scanLocalPacks() {
        Path localDir = com.slothyhub.local.LocalPackManager.getLocalPackDir();
        Map<Integer, List<TextureOption>> result = new LinkedHashMap<>();
        for (int i = 0; i < SLOTS.length; i++) result.put(i, new ArrayList<>());

        try {
            if (Files.isDirectory(localDir)) {
                try (Stream<Path> entries = Files.list(localDir)) {
                    List<Path> packs = entries.toList();
                    int total = packs.size(), done = 0;
                    for (Path pack : packs) {
                        String status = "Scanning " + pack.getFileName() + " (" + (++done) + "/" + total + ")";
                        class_310.method_1551().execute(() -> scanStatus = status);
                        String lower = pack.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (lower.endsWith(".zip")) {
                            scanZip(pack, result);
                        } else if (Files.isDirectory(pack)) {
                            scanFolder(pack, result);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("TexturePicker scan error: {}", e.getMessage());
        }

        class_310.method_1551().execute(() -> {
            discovered.clear();
            discovered.putAll(result);
            scanning = false;
            SlothyHubMod.LOGGER.info("TexturePicker: scan done — {} total options across {} slots",
                result.values().stream().mapToInt(List::size).sum(), SLOTS.length);
        });
    }

    private void scanZip(Path zip, Map<Integer, List<TextureOption>> out) {
        String packName = zip.getFileName().toString().replaceAll("(?i)\\.zip$", "");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            // First pass: collect all .properties files in cit dirs
            Map<String, ZipEntry> propEntries = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> all = zf.entries();
            while (all.hasMoreElements()) {
                ZipEntry ze = all.nextElement();
                if (ze.isDirectory()) continue;
                String p = ze.getName().toLowerCase(Locale.ROOT);
                if ((p.contains("/cit/") || p.contains("\\cit\\")) && p.endsWith(".properties"))
                    propEntries.put(ze.getName(), ze);
            }
            // Second pass: parse & match slots
            for (Map.Entry<String, ZipEntry> e : propEntries.entrySet()) {
                try (InputStream in = zf.getInputStream(e.getValue())) {
                    Properties props = loadProps(in);
                    matchSlots(packName, e.getKey(), props, zf::getInputStream, out);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void scanFolder(Path folder, Map<Integer, List<TextureOption>> out) {
        String packName = folder.getFileName().toString();
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> propFiles = walk.filter(f -> !Files.isDirectory(f))
                .filter(f -> {
                    String r = f.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
                    return r.contains("/cit/") && r.endsWith(".properties");
                }).toList();
            for (Path pf : propFiles) {
                try (InputStream in = Files.newInputStream(pf)) {
                    Properties props = loadProps(in);
                    matchSlots(packName, folder.relativize(pf).toString().replace('\\', '/'),
                        props,
                        ze -> Files.newInputStream(folder.resolve(ze.getName().replace('/', File.separatorChar))),
                        out);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    @FunctionalInterface
    interface ZipStreamProvider { InputStream get(ZipEntry ze) throws IOException; }

    private void matchSlots(String packName, String propsPath, Properties props,
                             ZipStreamProvider streamProvider, Map<Integer, List<TextureOption>> out) {
        String type = props.getProperty("type", "item").toLowerCase(Locale.ROOT);
        if (!type.equals("item") && !type.equals("items")) return;

        String itemsRaw = props.getProperty("items", props.getProperty("matchItems", "")).trim();
        if (itemsRaw.isBlank()) return;

        String nameProp = props.getProperty("name", props.getProperty("nbt.display.Name", "")).trim();
        String texProp  = props.getProperty("texture", "").trim();
        if (texProp.isBlank()) texProp = props.getProperty("model", "").trim();

        // Strip OptiFine ipattern/pattern prefixes from the name condition
        String cleanName = nameProp
            .replaceFirst("(?i)^ipattern:", "")
            .replaceFirst("(?i)^pattern:", "")
            .replaceFirst("(?i)^iregex:", "")
            .trim();

        Set<String> itemSet = new HashSet<>(Arrays.asList(itemsRaw.split("\\s+")));

        for (int si = 0; si < SLOTS.length; si++) {
            SlotDef slot = SLOTS[si];
            // Check if any of the slot's MC items appear in this rule's items list
            boolean itemMatch = false;
            for (String mc : slot.mcItems()) {
                if (itemSet.contains(mc) || itemSet.contains("minecraft:" + mc)) { itemMatch = true; break; }
            }
            if (!itemMatch) continue;

            // Check name matching
            if (slot.citName() != null) {
                // This slot requires a specific CIT name — skip if name doesn't match
                if (cleanName.isBlank()) continue;
                if (!cleanName.equalsIgnoreCase(slot.citName()) &&
                    !cleanName.toLowerCase(Locale.ROOT).contains(slot.citName().toLowerCase(Locale.ROOT)))
                    continue;
            }

            // Build a label
            String label = packName + " / " + (texProp.isBlank()
                ? propsPath.substring(propsPath.lastIndexOf('/') + 1).replace(".properties", "")
                : texProp);
            if (!cleanName.isBlank()) label += "  [" + cleanName + "]";

            // Try to grab the PNG bytes eagerly so we can embed them in the built pack
            byte[] pngBytes = null;
            if (!texProp.isBlank()) {
                String dir = propsPath.contains("/") ? propsPath.substring(0, propsPath.lastIndexOf('/') + 1) : "";
                String pngRelPath = dir + texProp + (texProp.endsWith(".png") ? "" : ".png");
                try {
                    ZipEntry dummy = new ZipEntry(pngRelPath);
                    pngBytes = streamProvider.get(dummy).readAllBytes();
                } catch (Exception ignored) {}
            }

            List<TextureOption> list = out.get(si);
            if (list != null) {
                String finalLabel = label;
                boolean dup = list.stream().anyMatch(o -> o.label().equals(finalLabel));
                if (!dup) list.add(new TextureOption(packName, label, propsPath, pngBytes));
            }
        }
    }

    private static Properties loadProps(InputStream in) throws IOException {
        Properties p = new Properties();
        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        return p;
    }

    // ── Build ─────────────────────────────────────────────────────────────

    private void buildAndApply() {
        if (scanning) return;
        long selected = selections.values().stream().filter(v -> v >= 0).count();
        if (selected == 0) { buildStatus = "Pick at least one texture first."; buildOk = false; return; }
        buildStatus = "Building…"; buildOk = false;
        executor.submit(() -> {
            try {
                byte[] zipBytes = buildCustomPack();
                class_310.method_1551().execute(() -> { buildStatus = "Applying…"; buildOk = true; });
                String name = "Slothy Custom Pack";
                String folder = PackDownloader.applyBuiltPack(zipBytes, name);
                class_310.method_1551().execute(() ->
                    { buildStatus = "Applied ✓ (" + folder + ")"; buildOk = true; });
            } catch (Exception e) {
                SlothyHubMod.LOGGER.error("TexturePicker build failed", e);
                class_310.method_1551().execute(() ->
                    { buildStatus = "Build failed: " + e.getMessage(); buildOk = false; });
            }
        });
    }

    private byte[] buildCustomPack() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // pack.mcmeta
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write("{\"pack\":{\"pack_format\":34,\"description\":\"Slothy Custom Textures\"}}"
                .getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            int ruleNum = 0;
            for (int si = 0; si < SLOTS.length; si++) {
                Integer selIdx = selections.get(si);
                if (selIdx == null || selIdx < 0) continue;
                List<TextureOption> opts = discovered.getOrDefault(si, List.of());
                if (selIdx >= opts.size()) continue;

                SlotDef slot  = SLOTS[si];
                TextureOption opt = opts.get(selIdx);
                String tag = "slothy_" + slot.display().toLowerCase(Locale.ROOT).replace(' ', '_') + "_" + ruleNum;

                // Embed PNG if we have it
                if (opt.pngData() != null && opt.pngData().length > 0) {
                    String texPath = "assets/minecraft/textures/item/" + tag + ".png";
                    zos.putNextEntry(new ZipEntry(texPath));
                    zos.write(opt.pngData());
                    zos.closeEntry();
                }

                // CIT properties file for each MC item in this slot
                for (String mcItem : slot.mcItems()) {
                    String propPath = "assets/minecraft/optifine/cit/" + tag + "_" + mcItem + ".properties";
                    zos.putNextEntry(new ZipEntry(propPath));
                    StringBuilder sb = new StringBuilder();
                    sb.append("type=item\n");
                    sb.append("items=").append(mcItem).append("\n");
                    if (slot.citName() != null)
                        sb.append("name=").append(slot.citName()).append("\n");
                    if (opt.pngData() != null && opt.pngData().length > 0)
                        sb.append("texture=").append(tag).append("\n");
                    zos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                }
                ruleNum++;
            }
        }
        return baos.toByteArray();
    }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        itemScroll += (itemScrollTarget - itemScroll) * Math.min(1f, delta * 0.28f);
        texScroll  += (texScrollTarget  - texScroll)  * Math.min(1f, delta * 0.28f);

        drawBg(ctx, delta);
        drawHeader(ctx, mx, my);
        drawSlotList(ctx, mx, my, delta);
        drawTexturePanel(ctx, mx, my, delta);
        drawFooter(ctx);

        for (class_364 w : method_25396()) {
            if (w instanceof class_4068 d) d.method_25394(ctx, mx, my, delta);
        }
    }

    private void drawBg(class_332 ctx, float delta) {
        ctx.method_25294(0, 0, field_22789, field_22790, BG);
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);
    }

    private void drawHeader(class_332 ctx, int mx, int my) {
        ctx.method_25294(0, 0, field_22789, HEADER, PANEL);
        ctx.method_25294(0, 0, field_22789, 2, ACCENT);
        ctx.method_25294(0, HEADER - 1, field_22789, HEADER, BORDER);

        DrawHelper.drawText(ctx, field_22793, "←", PAD, (HEADER - 9) / 2 + 1, MUTED, false);

        String title = "TEXTURE BUILDER";
        DrawHelper.drawText(ctx, field_22793, title,
            field_22789 / 2 - field_22793.method_1727(title) / 2, (HEADER - 9) / 2 + 1, ACCENT, false);

        long done = selections.values().stream().filter(v -> v >= 0).count();
        String info = done + " / " + SLOTS.length + " configured";
        DrawHelper.drawText(ctx, field_22793, info,
            field_22789 - PAD - field_22793.method_1727(info), (HEADER - 9) / 2 + 1, MUTED, false);
    }

    private void drawSlotList(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER, bot = field_22790 - FOOTER;
        ctx.method_25294(0, top, LEFT_W, bot, PANEL);
        ctx.method_25294(LEFT_W - 1, top, LEFT_W, bot, BORDER);
        DrawHelper.drawText(ctx, field_22793, "SLOTS", PAD, top + 8, MUTED, false);

        ctx.method_44379(0, top + 22, LEFT_W, bot);
        int y = top + 22 - (int) itemScroll;
        for (int i = 0; i < SLOTS.length; i++) {
            SlotDef slot = SLOTS[i];
            boolean sel = i == selectedSlot;
            boolean hov = mx >= 0 && mx < LEFT_W && my >= y && my < y + ITEM_H && my > top + 22 && my < bot;
            float ht = itemHover.getOrDefault(i, 0f);
            ht += ((hov ? 1f : 0f) - ht) * Math.min(1f, delta * 0.4f);
            itemHover.put(i, ht);

            if (sel) {
                ctx.method_25294(0, y, LEFT_W, y + ITEM_H, col(ACCENT & 0xFFFFFF, 28));
                ctx.method_25294(0, y, 3, y + ITEM_H, ACCENT);
            } else if (ht > 0.02f) {
                ctx.method_25294(0, y, LEFT_W, y + ITEM_H, col(SURFACE & 0xFFFFFF, (int)(140 * ht)));
            }

            int textY = y + (ITEM_H - 9) / 2;
            int fg = sel ? ACCENT : lerp(MUTED, TEXT, ht);

            // Emoji + name
            DrawHelper.drawText(ctx, field_22793, slot.emoji(), PAD + 4, textY, fg, false);
            DrawHelper.drawText(ctx, field_22793, slot.display(), PAD + 16, textY, fg, false);

            // Option count
            int count = discovered.getOrDefault(i, List.of()).size();
            String countStr = scanning ? "…" : count + "";
            DrawHelper.drawText(ctx, field_22793, countStr,
                LEFT_W - PAD - field_22793.method_1727(countStr), textY, MUTED, false);

            // Check if configured
            Integer selIdx = selections.get(i);
            if (selIdx != null && selIdx >= 0) {
                DrawHelper.drawText(ctx, field_22793, "✓", LEFT_W - 14, textY, ACCENT, false);
            }

            ctx.method_25294(PAD, y + ITEM_H - 1, LEFT_W - PAD, y + ITEM_H, col(BORDER & 0xFFFFFF, 80));
            y += ITEM_H;
        }
        ctx.method_44380();
    }

    private void drawTexturePanel(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER, bot = field_22790 - FOOTER;
        int panelX = LEFT_W;

        ctx.method_25294(panelX, top, field_22789, bot, BG);

        SlotDef slot = SLOTS[selectedSlot];

        // Panel header
        ctx.method_25294(panelX, top, field_22789, top + 30, col(PANEL & 0xFFFFFF, 200));
        ctx.method_25294(panelX, top + 30, field_22789, top + 31, BORDER);
        DrawHelper.drawText(ctx, field_22793, slot.emoji() + "  " + slot.display(),
            panelX + PAD, top + 8, TEXT, false);
        String hint = slot.citName() != null
            ? "Matches items named \"" + slot.citName() + "\""
            : "Applies to all " + slot.mcItems()[0].replace('_', ' ') + "s";
        DrawHelper.drawText(ctx, field_22793, hint, panelX + PAD, top + 19, MUTED, false);

        if (scanning) {
            int cy = (top + bot) / 2;
            Ui.spinner(ctx, (field_22789 + LEFT_W) / 2, cy, 8,
                (float)(System.currentTimeMillis() % 1200L) / 1200f, col(ACCENT & 0xFFFFFF, 200));
            DrawHelper.drawText(ctx, field_22793, scanStatus,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(scanStatus) / 2,
                cy + 16, MUTED, false);
            return;
        }

        List<TextureOption> opts = discovered.getOrDefault(selectedSlot, List.of());
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        List<TextureOption> shown = q.isEmpty() ? opts
            : opts.stream().filter(o -> o.label().toLowerCase(Locale.ROOT).contains(q)).toList();

        if (shown.isEmpty()) {
            String msg = opts.isEmpty()
                ? "No matching textures found in your local packs."
                : "No textures match \"" + q + "\"";
            int cy = (top + bot) / 2;
            DrawHelper.drawText(ctx, field_22793, msg,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(msg) / 2, cy, MUTED, false);
            return;
        }

        int TEX_H = 30;
        int innerTop = top + 32;
        ctx.method_44379(panelX, innerTop, field_22789, bot);
        int y = innerTop - (int) texScroll;
        Integer curSel = selections.getOrDefault(selectedSlot, -1);

        // "None" row
        {
            boolean isNone = curSel == null || curSel < 0;
            boolean hov = mx >= panelX && mx < field_22789 && my >= y && my < y + TEX_H && my > innerTop && my < bot;
            if (isNone)     ctx.method_25294(panelX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, 140));
            else if (hov)   ctx.method_25294(panelX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, 60));
            if (isNone)     ctx.method_25294(panelX, y, panelX + 3, y + TEX_H, DANGER);
            DrawHelper.drawText(ctx, field_22793, "✕  None (no override)",
                panelX + PAD, y + (TEX_H - 9) / 2, isNone ? DANGER : MUTED, false);
            ctx.method_25294(panelX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }

        for (int i = 0; i < shown.size(); i++) {
            TextureOption opt = shown.get(i);
            int realIdx = opts.indexOf(opt);
            boolean sel = Objects.equals(curSel, realIdx);
            boolean hov = mx >= panelX && mx < field_22789 && my >= y && my < y + TEX_H && my > innerTop && my < bot;
            String hKey = selectedSlot + "/" + i;
            float ht = texHover.getOrDefault(hKey, 0f);
            ht += ((hov ? 1f : 0f) - ht) * Math.min(1f, delta * 0.4f);
            texHover.put(hKey, ht);

            if (sel) {
                ctx.method_25294(panelX, y, field_22789, y + TEX_H, col(ACCENT & 0xFFFFFF, 28));
                ctx.method_25294(panelX, y, panelX + 3, y + TEX_H, ACCENT);
            } else if (ht > 0.02f) {
                ctx.method_25294(panelX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, (int)(100 * ht)));
            }

            String display = opt.label().length() > 72
                ? "…" + opt.label().substring(opt.label().length() - 70) : opt.label();
            int fg = sel ? ACCENT : lerp(MUTED, TEXT, ht);
            DrawHelper.drawText(ctx, field_22793, display, panelX + PAD, y + (TEX_H - 9) / 2, fg, false);
            ctx.method_25294(panelX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }
        ctx.method_44380();

        // Scrollbar
        int totalH = (shown.size() + 1) * TEX_H;
        int listH  = bot - innerTop;
        if (totalH > listH) {
            int trkX = field_22789 - 4;
            int trkH = listH - 8;
            int tmbH = Math.max(20, trkH * listH / totalH);
            int tmbY = innerTop + 4 + (int)((double)(trkH - tmbH) * texScroll / Math.max(1, totalH - listH));
            ctx.method_25294(trkX, innerTop + 4, trkX + 3, innerTop + 4 + trkH, col(BORDER & 0xFFFFFF, 100));
            ctx.method_25294(trkX, tmbY, trkX + 3, tmbY + tmbH, col(ACCENT & 0xFFFFFF, 160));
        }
    }

    private void drawFooter(class_332 ctx) {
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, PANEL);
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790 - FOOTER + 1, BORDER);
        if (buildStatus != null) {
            int col = buildOk ? ACCENT : DANGER;
            int sw = field_22793.method_1727(buildStatus);
            DrawHelper.drawText(ctx, field_22793, buildStatus,
                field_22789 / 2 - sw / 2, field_22790 - FOOTER + 8, col, false);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────

    @Override
    public boolean method_25402(double mx, double my, int button) {
        if (button != 0) return super.method_25402(mx, my, button);

        // Back arrow
        if (mx >= PAD && mx <= PAD + 14 && my >= HEADER / 2 - 6 && my <= HEADER / 2 + 6) {
            method_25419(); return true;
        }

        int top = HEADER, bot = field_22790 - FOOTER;

        // Slot list click
        if (mx >= 0 && mx < LEFT_W && my > top + 22 && my < bot) {
            int y = top + 22 - (int) itemScroll;
            for (int i = 0; i < SLOTS.length; i++) {
                if (my >= y && my < y + ITEM_H) {
                    selectedSlot = i; texScroll = texScrollTarget = 0; searchQuery = "";
                    if (searchField != null) searchField.method_1867(""); // setText("")
                    return true;
                }
                y += ITEM_H;
            }
        }

        // Texture option click
        if (mx >= LEFT_W && mx < field_22789 && my > top + 32 && my < bot && !scanning) {
            List<TextureOption> opts = discovered.getOrDefault(selectedSlot, List.of());
            String q = searchQuery.toLowerCase(Locale.ROOT).trim();
            List<TextureOption> shown = q.isEmpty() ? opts
                : opts.stream().filter(o -> o.label().toLowerCase(Locale.ROOT).contains(q)).toList();

            int TEX_H = 30;
            int y = top + 32 - (int) texScroll;
            if (my >= y && my < y + TEX_H) { selections.put(selectedSlot, -1); return true; }
            y += TEX_H;
            for (int i = 0; i < shown.size(); i++) {
                if (my >= y && my < y + TEX_H) {
                    selections.put(selectedSlot, opts.indexOf(shown.get(i))); return true;
                }
                y += TEX_H;
            }
        }

        return super.method_25402(mx, my, button);
    }

    private boolean onScroll(double mx, double my, double vDelta) {
        int top = HEADER, bot = field_22790 - FOOTER;
        if (mx < LEFT_W) {
            int totalH = SLOTS.length * ITEM_H;
            int listH  = bot - (top + 22);
            itemScrollTarget = Math.max(0, Math.min(itemScrollTarget - vDelta * 20, Math.max(0, totalH - listH)));
        } else {
            List<TextureOption> opts = discovered.getOrDefault(selectedSlot, List.of());
            int totalH = (opts.size() + 1) * 30;
            int listH  = bot - (top + 32);
            texScrollTarget = Math.max(0, Math.min(texScrollTarget - vDelta * 20, Math.max(0, totalH - listH)));
        }
        return true;
    }

    public boolean method_25403(double mx, double my, double hDelta, double vDelta) { return onScroll(mx, my, vDelta); }
    public boolean method_25401(double mx, double my, double vDelta) { return onScroll(mx, my, vDelta); }

    @Override
    public boolean method_25404(int key, int scan, int mods) {
        if (key == 256) { method_25419(); return true; }
        return super.method_25404(key, scan, mods);
    }
}
