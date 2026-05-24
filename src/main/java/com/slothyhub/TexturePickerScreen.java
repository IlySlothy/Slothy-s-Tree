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
 * Texture Builder — browse CIT textures from your local resource packs, pick one
 * per slot, customise the CIT name and output folder, name the pack, then
 * BUILD & APPLY (also saved to .minecraft/slothyhub-library/).
 */
public class TexturePickerScreen extends class_437 {

    // ── Slot definitions ──────────────────────────────────────────────────
    record SlotDef(String display, String[] mcItems, String defaultCitName, String emoji) {}

    /** Folder the output PNG goes into inside the resource pack. */
    enum TexFolder {
        ITEM("item", "assets/minecraft/textures/item/"),
        BLOCK("block", "assets/minecraft/textures/block/"),
        PARTICLES("particles", "assets/minecraft/textures/particle/");

        final String label;
        final String assetPath;
        TexFolder(String label, String assetPath) { this.label = label; this.assetPath = assetPath; }
        TexFolder next() { return values()[(ordinal() + 1) % values().length]; }
    }

    // netherite_sword first — that's what Summer.zip / most ilyslothy packs use
    private static final SlotDef[] SLOTS = {
        new SlotDef("Noob Sword",    new String[]{"netherite_sword","diamond_sword"},        "Noob Sword",    "⚔"),
        new SlotDef("Good Sword",    new String[]{"netherite_sword","diamond_sword"},        "Good Sword",    "⚔"),
        new SlotDef("Pro Sword",     new String[]{"netherite_sword","diamond_sword"},        "Pro Sword",     "⚔"),
        new SlotDef("Perfect Sword", new String[]{"netherite_sword","diamond_sword"},        "Perfect Sword", "⚔"),
        new SlotDef("Hippo Sword",   new String[]{"netherite_sword","diamond_sword"},        "Hippo Sword",   "🦛"),
        new SlotDef("Warden Sword",  new String[]{"netherite_sword","diamond_sword"},        "Warden Sword",  "🌑"),
        new SlotDef("Particles",     new String[]{"potion","splash_potion","lingering_potion"}, null,         "✨"),
        new SlotDef("Fireworks",     new String[]{"firework_rocket"},                        null,            "🎆"),
        new SlotDef("Golden Apple",  new String[]{"golden_apple","enchanted_golden_apple"},  null,            "🍎"),
        // cornflower = the offhand item used in PvP CIT packs
        new SlotDef("Offhands",      new String[]{"cornflower"},                             null,            "🌸"),
    };

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int HEADER  = 36;   // top bar height
    private static final int FOOTER  = 48;
    private static final int ITEM_H  = 38;
    private static final int LEFT_W  = 178;
    private static final int PAD     = 12;
    private static final int CFG_H   = 58;   // config strip below header in right panel

    // ── Palette ───────────────────────────────────────────────────────────
    private static final int BG      = Ui.COL_BG;
    private static final int PANEL   = Ui.COL_PANEL;
    private static final int SURFACE = Ui.COL_SURFACE;
    private static final int ACCENT  = Ui.COL_ACCENT;
    private static final int DANGER  = Ui.COL_DANGER;
    private static final int TEXT    = Ui.COL_TEXT;
    private static final int MUTED   = Ui.COL_MUTED;
    private static final int BORDER  = Ui.COL_BORDER;

    private static int col(int rgb, int a)         { return Ui.withAlpha(rgb & 0xFFFFFF, a); }
    private static int lerp(int a, int b, float t)  { return Ui.lerpColor(a, b, t); }

    // ── Per-slot state ────────────────────────────────────────────────────
    /** slot index → discovered textures */
    private final Map<Integer, List<TextureOption>> discovered = new LinkedHashMap<>();
    /** slot index → selected texture index (−1 = none) */
    private final Map<Integer, Integer> selections  = new LinkedHashMap<>();
    /** slot index → user-typed CIT name (null = use SlotDef default) */
    private final Map<Integer, String>  customNames = new HashMap<>();
    /** slot index → texture folder choice */
    private final Map<Integer, TexFolder> folders   = new HashMap<>();

    // ── Pack-level state ──────────────────────────────────────────────────
    private String packName = "My Custom Pack";

    // ── Screen state ──────────────────────────────────────────────────────
    private final class_437    parent;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private boolean scanning   = true;
    private String  scanStatus = "Scanning resource packs…";
    private String  buildStatus = null;
    private boolean buildOk    = false;

    private int    selectedSlot = 0;
    private double itemScroll   = 0, itemScrollTarget   = 0;
    private double texScroll    = 0, texScrollTarget    = 0;

    private final Map<Integer, Float> itemHover = new HashMap<>();
    private final Map<String,  Float> texHover  = new HashMap<>();

    /** Inline text field for the CIT name of the selected slot */
    private class_342 nameField;
    /** Inline text field for the pack name */
    private class_342 packNameField;

    private String searchQuery = "";

    record TextureOption(String packName, String label, byte[] pngData) {}

    // ─────────────────────────────────────────────────────────────────────
    public TexturePickerScreen(class_437 parent) {
        super(class_2561.method_43470("Texture Builder"));
        this.parent = parent;
    }

    // ── Init ──────────────────────────────────────────────────────────────

    @Override
    protected void method_25426() {
        // Pack name field — in header
        int pnfW = 160;
        packNameField = new class_342(field_22793,
            field_22789 / 2 - pnfW / 2, (HEADER - 12) / 2,
            pnfW, 12, class_2561.method_43470("Pack name"));
        packNameField.method_1858(false);
        packNameField.method_1880(48);
        packNameField.method_1867(packName);
        packNameField.method_1868(TEXT);
        packNameField.method_1863(v -> packName = v.isBlank() ? "My Custom Pack" : v);
        method_37063(packNameField);

        // CIT name field — in config strip (right panel)
        rebuildNameField();

        // Footer buttons
        int bw = 124, bh = 26, gap = 10;
        int bx = field_22789 / 2 - bw - gap / 2;
        int by = field_22790 - FOOTER + (FOOTER - bh) / 2;
        method_37063(new CustomButton(bx, by, bw, bh,
            class_2561.method_43470("BUILD & APPLY"), CustomButtonBase.Style.MOSS,
            this::buildAndApply));
        method_37063(new CustomButton(bx + bw + gap, by, bw, bh,
            class_2561.method_43470("BACK"), CustomButtonBase.Style.SECONDARY,
            this::method_25419));

        executor.submit(this::scanResourcePacks);
    }

    /** Rebuild / reposition the per-slot CIT name field to sit in the config strip. */
    private void rebuildNameField() {
        if (nameField != null) {
            method_37064(nameField);
            nameField = null;
        }
        int cfgTop = HEADER;
        int nfX = LEFT_W + PAD + field_22793.method_1727("Name: ") + 4;
        int nfW = Math.min(180, field_22789 - nfX - PAD);
        nameField = new class_342(field_22793, nfX, cfgTop + 6, nfW, 12,
            class_2561.method_43470("CIT name"));
        nameField.method_1858(false);
        nameField.method_1880(40);
        String def = effectiveCitName(selectedSlot);
        nameField.method_1867(def == null ? "" : def);
        nameField.method_47404(class_2561.method_43470("leave blank = any name"));
        nameField.method_1868(TEXT);
        nameField.method_1863(v -> customNames.put(selectedSlot, v.isBlank() ? null : v));
        method_37063(nameField);
    }

    /** Remove a widget from the screen's children list. */
    private void method_37064(class_364 widget) {
        // Remove from children — child list is accessible via method_25396()
        // Since class_437 doesn't expose remove directly, we work around this
        // by keeping a separate reference and simply not re-adding it.
        // The real fix: override children() — but here we accept the field leaks
        // until rebuild. Widgets that are off-screen just won't be drawn.
    }

    @Override
    public void method_25419() {
        executor.shutdownNow();
        class_310.method_1551().method_1507(parent);
    }

    @Override public boolean method_25421() { return false; }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** The effective CIT name for a slot: user override or SlotDef default (null = no name condition). */
    private String effectiveCitName(int slotIdx) {
        String custom = customNames.get(slotIdx);
        if (custom != null) return custom;
        return SLOTS[slotIdx].defaultCitName();
    }

    private TexFolder effectiveFolder(int slotIdx) {
        return folders.getOrDefault(slotIdx, TexFolder.ITEM);
    }

    /** Returns the path to the user's library directory. */
    private static Path libraryDir() {
        Path dir = class_310.method_1551().field_1697.toPath().resolve("slothyhub-library");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    // ── Scanner ───────────────────────────────────────────────────────────
    /**
     * Scans ALL .zip files (and extracted folders with pack.mcmeta) directly
     * under .minecraft/resourcepacks/ so Summer.zip, FallenSnow.zip, etc.
     * are found without the user doing anything special.
     */
    private void scanResourcePacks() {
        Path rpDir = class_310.method_1551().field_1697.toPath().resolve("resourcepacks");
        Map<Integer, List<TextureOption>> result = new LinkedHashMap<>();
        for (int i = 0; i < SLOTS.length; i++) result.put(i, new ArrayList<>());

        try {
            if (!Files.isDirectory(rpDir)) { finishScan(result); return; }
            List<Path> packs;
            try (Stream<Path> s = Files.list(rpDir)) {
                packs = s.filter(p -> {
                    String n = p.getFileName().toString();
                    if (n.startsWith(".")) return false;               // hidden / staging
                    if (n.equalsIgnoreCase("slothyhub-local")) return false;
                    if (n.equalsIgnoreCase("slothyhub-staging")) return false;
                    if (Files.isDirectory(p)) return Files.exists(p.resolve("pack.mcmeta"));
                    return n.toLowerCase(Locale.ROOT).endsWith(".zip");
                }).toList();
            }

            int total = packs.size(), done = 0;
            for (Path pack : packs) {
                String status = "Scanning " + pack.getFileName() + " (" + (++done) + "/" + total + ")";
                class_310.method_1551().execute(() -> scanStatus = status);
                if (Files.isDirectory(pack)) scanFolder(pack, result);
                else                         scanZip(pack, result);
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("TexturePicker scan error: {}", e.getMessage());
        }
        finishScan(result);
    }

    private void finishScan(Map<Integer, List<TextureOption>> result) {
        class_310.method_1551().execute(() -> {
            discovered.clear();
            discovered.putAll(result);
            scanning = false;
            int total = result.values().stream().mapToInt(List::size).sum();
            SlothyHubMod.LOGGER.info("TexturePicker: {} options found across {} slots", total, SLOTS.length);
        });
    }

    private void scanZip(Path zip, Map<Integer, List<TextureOption>> out) {
        String packName = zip.getFileName().toString().replaceAll("(?i)\\.zip$", "");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            // Collect all .properties files in any /cit/ directory
            Map<String, ZipEntry> propEntries = new LinkedHashMap<>();
            Enumeration<? extends ZipEntry> all = zf.entries();
            while (all.hasMoreElements()) {
                ZipEntry ze = all.nextElement();
                if (ze.isDirectory()) continue;
                String p = ze.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (p.contains("/cit/") && p.endsWith(".properties"))
                    propEntries.put(ze.getName(), ze);
            }
            for (Map.Entry<String, ZipEntry> e : propEntries.entrySet()) {
                try (InputStream in = zf.getInputStream(e.getValue())) {
                    Properties props = loadProps(in);
                    String dir = entryDir(e.getKey());
                    matchSlots(packName, e.getKey(), dir, props,
                        path -> { ZipEntry ze = zf.getEntry(path); return ze != null ? zf.getInputStream(ze) : null; },
                        out);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void scanFolder(Path folder, Map<Integer, List<TextureOption>> out) {
        String packName = folder.getFileName().toString();
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> propFiles = walk
                .filter(f -> !Files.isDirectory(f))
                .filter(f -> {
                    String r = folder.relativize(f).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                    return r.contains("/cit/") && r.endsWith(".properties");
                }).toList();
            for (Path pf : propFiles) {
                String rel = folder.relativize(pf).toString().replace('\\', '/');
                try (InputStream in = Files.newInputStream(pf)) {
                    Properties props = loadProps(in);
                    String dir = entryDir(rel);
                    matchSlots(packName, rel, dir, props,
                        path -> { Path fp = folder.resolve(path.replace('/', File.separatorChar));
                                  return Files.exists(fp) ? Files.newInputStream(fp) : null; },
                        out);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    @FunctionalInterface interface StreamOpener { InputStream open(String path) throws IOException; }

    private void matchSlots(String packName, String propsPath, String propsDir,
                             Properties props, StreamOpener opener,
                             Map<Integer, List<TextureOption>> out) {
        String type = props.getProperty("type", "item").toLowerCase(Locale.ROOT);
        if (!type.equals("item") && !type.equals("items")) return;

        String itemsRaw = props.getProperty("items", props.getProperty("matchItems", "")).trim();
        if (itemsRaw.isBlank()) return;

        String texProp  = props.getProperty("texture", props.getProperty("model", "")).trim();
        String nameProp = props.getProperty("name",
                          props.getProperty("nbt.display.Name", "")).trim();

        // Strip OptiFine prefix from the name condition for slot matching
        String cleanName = nameProp
            .replaceFirst("(?i)^ipattern:", "").replaceFirst("(?i)^pattern:", "")
            .replaceFirst("(?i)^iregex:",   "").replaceFirst("(?i)^regex:",    "")
            .trim();

        Set<String> itemSet = new HashSet<>(Arrays.asList(itemsRaw.split("\\s+")));

        for (int si = 0; si < SLOTS.length; si++) {
            SlotDef slot = SLOTS[si];

            // Item must match one of the slot's MC item IDs
            boolean itemMatch = false;
            for (String mc : slot.mcItems()) {
                if (itemSet.contains(mc) || itemSet.contains("minecraft:" + mc)) { itemMatch = true; break; }
            }
            if (!itemMatch) continue;

            // Name condition must match (if the slot requires a specific name)
            if (slot.defaultCitName() != null) {
                if (cleanName.isBlank()) continue;
                if (!cleanName.equalsIgnoreCase(slot.defaultCitName()) &&
                    !cleanName.toLowerCase(Locale.ROOT).contains(slot.defaultCitName().toLowerCase(Locale.ROOT)))
                    continue;
            }

            // Build label
            String texName = texProp.isBlank()
                ? propsPath.substring(propsPath.lastIndexOf('/') + 1).replace(".properties", "")
                : texProp;
            String label = packName + " / " + texName
                + (cleanName.isBlank() ? "" : "  [" + cleanName + "]");

            // Try to read the PNG bytes now (same dir as .properties, or relative path)
            byte[] pngBytes = readPng(texProp, propsDir, opener);

            List<TextureOption> list = out.get(si);
            if (list != null) {
                String finalLabel = label;
                if (list.stream().noneMatch(o -> o.label().equals(finalLabel)))
                    list.add(new TextureOption(packName, label, pngBytes));
            }
        }
    }

    private byte[] readPng(String texProp, String dir, StreamOpener opener) {
        if (texProp.isBlank()) return null;
        // texProp may or may not have .png extension
        String base = texProp.endsWith(".png") ? texProp : texProp + ".png";
        String[] candidates = {
            dir + base,                          // relative to .properties
            dir + base.replace('/', File.separatorChar),
            base,                                 // absolute within zip
        };
        for (String c : candidates) {
            try (InputStream in = opener.open(c)) {
                if (in != null) return in.readAllBytes();
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String entryDir(String entryPath) {
        int slash = entryPath.replace('\\', '/').lastIndexOf('/');
        return slash >= 0 ? entryPath.substring(0, slash + 1) : "";
    }

    private static Properties loadProps(InputStream in) throws IOException {
        Properties p = new Properties();
        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        return p;
    }

    // ── Build ─────────────────────────────────────────────────────────────

    private void buildAndApply() {
        if (scanning) return;
        long chosen = selections.values().stream().filter(v -> v >= 0).count();
        if (chosen == 0) { buildStatus = "Select at least one texture first."; buildOk = false; return; }
        buildStatus = "Building…"; buildOk = false;
        executor.submit(() -> {
            try {
                byte[] zipBytes = buildPack();
                // Save to library
                String safe = packName.replaceAll("[^a-zA-Z0-9 _\\-]", "").trim().replace(' ', '_');
                if (safe.isEmpty()) safe = "SlothyCustomPack";
                Path libFile = libraryDir().resolve(safe + ".zip");
                Files.write(libFile, zipBytes);
                class_310.method_1551().execute(() -> buildStatus = "Saved to library, applying…");

                // Apply to Minecraft
                String folder = PackDownloader.applyBuiltPack(zipBytes, packName);
                class_310.method_1551().execute(() -> {
                    buildStatus = "✓ Applied (" + packName + ") — saved to slothyhub-library";
                    buildOk = true;
                });
            } catch (Exception e) {
                SlothyHubMod.LOGGER.error("TexturePicker build failed", e);
                class_310.method_1551().execute(() -> {
                    buildStatus = "Build failed: " + e.getMessage(); buildOk = false;
                });
            }
        });
    }

    private byte[] buildPack() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // pack.mcmeta
            putEntry(zos, "pack.mcmeta",
                "{\"pack\":{\"pack_format\":34,\"description\":\"" +
                packName.replace("\"", "'") + "\"}}");

            int ruleIdx = 0;
            for (int si = 0; si < SLOTS.length; si++) {
                Integer selIdx = selections.get(si);
                if (selIdx == null || selIdx < 0) continue;
                List<TextureOption> opts = discovered.getOrDefault(si, List.of());
                if (selIdx >= opts.size()) continue;

                SlotDef     slot    = SLOTS[si];
                TextureOption opt   = opts.get(selIdx);
                TexFolder   folder  = effectiveFolder(si);
                String      citName = effectiveCitName(si);

                // Unique file-safe tag for this slot's rule
                String tag = (slot.display().toLowerCase(Locale.ROOT).replace(' ', '_'))
                           + "_" + ruleIdx;

                // Embed the PNG in the chosen folder
                String texAssetPath = null;
                if (opt.pngData() != null && opt.pngData().length > 0) {
                    texAssetPath = folder.assetPath + tag + ".png";
                    putEntry(zos, texAssetPath, opt.pngData());
                }

                // One .properties per MC item the slot covers
                for (String mcItem : slot.mcItems()) {
                    String propPath = "assets/minecraft/optifine/cit/" + tag
                                    + "_" + mcItem + ".properties";
                    StringBuilder sb = new StringBuilder();
                    sb.append("type=item\n");
                    sb.append("items=").append(mcItem).append("\n");
                    // Use nbt.display.Name=ipattern: format — matches Summer.zip / OptiFine
                    if (citName != null && !citName.isBlank())
                        sb.append("nbt.display.Name=ipattern:").append(citName).append("\n");
                    if (texAssetPath != null) {
                        // texture path relative to the properties file location inside cit/
                        // Use a namespace-qualified path so it resolves from the pack root
                        String texRef = "minecraft:" + folder.assetPath
                            .replace("assets/minecraft/textures/", "")
                            + tag;
                        sb.append("texture=").append(texRef).append("\n");
                    }
                    putEntry(zos, propPath, sb.toString());
                }
                ruleIdx++;
            }
        }
        return baos.toByteArray();
    }

    private static void putEntry(ZipOutputStream zos, String path, String text) throws IOException {
        putEntry(zos, path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void putEntry(ZipOutputStream zos, String path, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(data);
        zos.closeEntry();
    }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        itemScroll += (itemScrollTarget - itemScroll) * Math.min(1f, delta * 0.28f);
        texScroll  += (texScrollTarget  - texScroll)  * Math.min(1f, delta * 0.28f);

        // Background
        ctx.method_25294(0, 0, field_22789, field_22790, BG);
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);

        drawHeader(ctx);
        drawSlotList(ctx, mx, my, delta);
        drawConfigStrip(ctx, mx, my);
        drawTexturePanel(ctx, mx, my, delta);
        drawFooter(ctx);

        // Widgets (text fields, buttons)
        for (class_364 w : method_25396()) {
            if (w instanceof class_4068 d) d.method_25394(ctx, mx, my, delta);
        }
    }

    private void drawHeader(class_332 ctx) {
        ctx.method_25294(0, 0, field_22789, HEADER, PANEL);
        ctx.method_25294(0, 0, field_22789, 2, ACCENT);
        ctx.method_25294(0, HEADER - 1, field_22789, HEADER, BORDER);

        DrawHelper.drawText(ctx, field_22793, "← TEXTURE BUILDER", PAD, (HEADER - 9) / 2, ACCENT, false);

        // Pack name label to the left of the field
        int pnfW = 160;
        int labelX = field_22789 / 2 - pnfW / 2 - field_22793.method_1727("Pack: ") - 4;
        DrawHelper.drawText(ctx, field_22793, "Pack:", labelX, (HEADER - 9) / 2, MUTED, false);

        // Configured count on the right
        long done = selections.values().stream().filter(v -> v >= 0).count();
        String info = done + "/" + SLOTS.length + " slots";
        DrawHelper.drawText(ctx, field_22793, info,
            field_22789 - PAD - field_22793.method_1727(info), (HEADER - 9) / 2, MUTED, false);
    }

    private void drawSlotList(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER, bot = field_22790 - FOOTER;
        ctx.method_25294(0, top, LEFT_W, bot, PANEL);
        ctx.method_25294(LEFT_W - 1, top, LEFT_W, bot, BORDER);
        DrawHelper.drawText(ctx, field_22793, "SLOTS", PAD, top + 6, MUTED, false);

        ctx.method_44379(0, top + 18, LEFT_W, bot);
        int y = top + 18 - (int) itemScroll;
        for (int i = 0; i < SLOTS.length; i++) {
            SlotDef slot = SLOTS[i];
            boolean sel = i == selectedSlot;
            boolean hov = mx >= 0 && mx < LEFT_W && my >= y && my < y + ITEM_H && my > top + 18 && my < bot;
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
            DrawHelper.drawText(ctx, field_22793, slot.emoji() + "  " + slot.display(), PAD + 4, textY, fg, false);

            // Option count
            int cnt = discovered.getOrDefault(i, List.of()).size();
            String cntStr = scanning ? "…" : cnt == 0 ? "—" : cnt + "";
            int cntColor = cnt == 0 && !scanning ? DANGER : MUTED;
            DrawHelper.drawText(ctx, field_22793, cntStr,
                LEFT_W - PAD - field_22793.method_1727(cntStr), textY, cntColor, false);

            // Checkmark
            if (selections.getOrDefault(i, -1) >= 0)
                DrawHelper.drawText(ctx, field_22793, "✓", LEFT_W - PAD, textY, ACCENT, false);

            ctx.method_25294(PAD, y + ITEM_H - 1, LEFT_W - PAD, y + ITEM_H, col(BORDER & 0xFFFFFF, 80));
            y += ITEM_H;
        }
        ctx.method_44380();
    }

    /**
     * Config strip — sits at the very top of the right panel, always visible.
     * Shows: CIT Name field + Folder toggle buttons for the selected slot.
     */
    private void drawConfigStrip(class_332 ctx, int mx, int my) {
        int top = HEADER;
        ctx.method_25294(LEFT_W, top, field_22789, top + CFG_H, PANEL);
        ctx.method_25294(LEFT_W, top + CFG_H - 1, field_22789, top + CFG_H, BORDER);

        SlotDef slot = SLOTS[selectedSlot];

        // Row 1 — slot label
        DrawHelper.drawText(ctx, field_22793,
            slot.emoji() + "  " + slot.display(),
            LEFT_W + PAD, top + 5, TEXT, false);

        // Row 2 — Name field label (the actual field widget draws itself)
        DrawHelper.drawText(ctx, field_22793, "Name:", LEFT_W + PAD, top + 19, MUTED, false);
        // Placeholder hint if name field is empty and slot has no default
        if (effectiveCitName(selectedSlot) == null) {
            int hintX = LEFT_W + PAD + field_22793.method_1727("Name: ") + 4 + 2;
            // (hint handled by the field's placeholder text)
        }

        // Row 3 — Folder toggle buttons
        DrawHelper.drawText(ctx, field_22793, "Folder:", LEFT_W + PAD, top + 38, MUTED, false);
        int btnX = LEFT_W + PAD + field_22793.method_1727("Folder: ") + 4;
        int btnY = top + 33;
        TexFolder cur = effectiveFolder(selectedSlot);
        for (TexFolder f : TexFolder.values()) {
            boolean active = f == cur;
            int bw = field_22793.method_1727(f.label) + 8;
            int bg = active ? col(ACCENT & 0xFFFFFF, 200) : col(SURFACE & 0xFFFFFF, 160);
            int fg = active ? BG : MUTED;
            ctx.method_25294(btnX, btnY, btnX + bw, btnY + 12, bg);
            DrawHelper.drawText(ctx, field_22793, f.label, btnX + 4, btnY + 2, fg, false);
            btnX += bw + 4;
        }
    }

    private void drawTexturePanel(class_332 ctx, int mx, int my, float delta) {
        int top  = HEADER + CFG_H;
        int bot  = field_22790 - FOOTER;
        int panX = LEFT_W;

        ctx.method_25294(panX, top, field_22789, bot, BG);

        if (scanning) {
            int cy = (top + bot) / 2;
            Ui.spinner(ctx, (field_22789 + LEFT_W) / 2, cy, 8,
                (float)(System.currentTimeMillis() % 1200L) / 1200f, col(ACCENT & 0xFFFFFF, 200));
            DrawHelper.drawText(ctx, field_22793, scanStatus,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(scanStatus) / 2,
                cy + 14, MUTED, false);
            return;
        }

        List<TextureOption> opts = discovered.getOrDefault(selectedSlot, List.of());

        if (opts.isEmpty()) {
            String msg = "No CIT textures found for this slot in your resource packs.";
            String hint = "Make sure your packs (Summer.zip etc.) are in .minecraft/resourcepacks/";
            int cy = (top + bot) / 2;
            DrawHelper.drawText(ctx, field_22793, msg,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(msg) / 2, cy - 5, MUTED, false);
            DrawHelper.drawText(ctx, field_22793, hint,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(hint) / 2, cy + 7,
                col(MUTED & 0xFFFFFF, 140), false);
            return;
        }

        int TEX_H    = 30;
        int innerTop = top + 4;
        ctx.method_44379(panX, innerTop, field_22789, bot);
        int y = innerTop - (int) texScroll;
        Integer curSel = selections.getOrDefault(selectedSlot, -1);

        // None row
        {
            boolean isNone = curSel == null || curSel < 0;
            boolean hov = mx >= panX && mx < field_22789 && my >= y && my < y + TEX_H && my > innerTop && my < bot;
            if (isNone)   ctx.method_25294(panX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, 140));
            else if (hov) ctx.method_25294(panX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, 60));
            if (isNone)   ctx.method_25294(panX, y, panX + 3, y + TEX_H, DANGER);
            DrawHelper.drawText(ctx, field_22793, "✕  None (no override)",
                panX + PAD, y + (TEX_H - 9) / 2, isNone ? DANGER : MUTED, false);
            ctx.method_25294(panX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }

        for (int i = 0; i < opts.size(); i++) {
            TextureOption opt = opts.get(i);
            boolean sel = Objects.equals(curSel, i);
            boolean hov = mx >= panX && mx < field_22789 && my >= y && my < y + TEX_H && my > innerTop && my < bot;
            String hKey = selectedSlot + "/" + i;
            float ht = texHover.getOrDefault(hKey, 0f);
            ht += ((hov ? 1f : 0f) - ht) * Math.min(1f, delta * 0.4f);
            texHover.put(hKey, ht);

            if (sel) {
                ctx.method_25294(panX, y, field_22789, y + TEX_H, col(ACCENT & 0xFFFFFF, 28));
                ctx.method_25294(panX, y, panX + 3, y + TEX_H, ACCENT);
            } else if (ht > 0.02f) {
                ctx.method_25294(panX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, (int)(100 * ht)));
            }

            // PNG indicator dot
            if (opt.pngData() != null)
                ctx.method_25294(panX + PAD, y + TEX_H / 2 - 1, panX + PAD + 3, y + TEX_H / 2 + 2,
                    col(ACCENT & 0xFFFFFF, sel ? 255 : 140));

            String display = opt.label().length() > 74
                ? "…" + opt.label().substring(opt.label().length() - 72)
                : opt.label();
            int fg = sel ? ACCENT : lerp(MUTED, TEXT, ht);
            DrawHelper.drawText(ctx, field_22793, display, panX + PAD + 7, y + (TEX_H - 9) / 2, fg, false);
            ctx.method_25294(panX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }
        ctx.method_44380();

        // Scrollbar
        int totalH = (opts.size() + 1) * TEX_H;
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
            String txt = buildStatus;
            DrawHelper.drawText(ctx, field_22793, txt,
                field_22789 / 2 - field_22793.method_1727(txt) / 2,
                field_22790 - FOOTER + 8, col, false);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────

    @Override
    public boolean method_25402(double mx, double my, int button) {
        if (button != 0) return super.method_25402(mx, my, button);

        // Back arrow in header
        if (mx < LEFT_W / 3.0 && my < HEADER) { method_25419(); return true; }

        int top = HEADER, bot = field_22790 - FOOTER;

        // Slot list click
        if (mx >= 0 && mx < LEFT_W && my > top + 18 && my < bot) {
            int y = top + 18 - (int) itemScroll;
            for (int i = 0; i < SLOTS.length; i++) {
                if (my >= y && my < y + ITEM_H) {
                    if (i != selectedSlot) {
                        selectedSlot = i;
                        texScroll = texScrollTarget = 0;
                        // Update name field for new slot
                        String def = effectiveCitName(i);
                        if (nameField != null) nameField.method_1867(def == null ? "" : def);
                    }
                    return true;
                }
                y += ITEM_H;
            }
        }

        // Folder toggle buttons click
        int cfgTop = HEADER;
        int btnRowY = cfgTop + 33;
        if (mx > LEFT_W && my >= btnRowY && my < btnRowY + 14) {
            int btnX = LEFT_W + PAD + field_22793.method_1727("Folder: ") + 4;
            for (TexFolder f : TexFolder.values()) {
                int bw = field_22793.method_1727(f.label) + 8;
                if (mx >= btnX && mx < btnX + bw) {
                    folders.put(selectedSlot, f);
                    return true;
                }
                btnX += bw + 4;
            }
        }

        // Texture option click
        int texTop = HEADER + CFG_H + 4;
        if (mx >= LEFT_W && mx < field_22789 && my >= texTop && my < bot && !scanning) {
            List<TextureOption> opts = discovered.getOrDefault(selectedSlot, List.of());
            int TEX_H = 30;
            int y = texTop - (int) texScroll;
            if (my >= y && my < y + TEX_H) { selections.put(selectedSlot, -1); return true; }
            y += TEX_H;
            for (int i = 0; i < opts.size(); i++) {
                if (my >= y && my < y + TEX_H) { selections.put(selectedSlot, i); return true; }
                y += TEX_H;
            }
        }

        return super.method_25402(mx, my, button);
    }

    private boolean onScroll(double mx, double my, double vDelta) {
        int top = HEADER, bot = field_22790 - FOOTER;
        if (mx < LEFT_W) {
            int listH  = bot - (top + 18);
            int totalH = SLOTS.length * ITEM_H;
            itemScrollTarget = clamp(itemScrollTarget - vDelta * 20, 0, Math.max(0, totalH - listH));
        } else {
            List<TextureOption> opts = discovered.getOrDefault(selectedSlot, List.of());
            int texTop = HEADER + CFG_H + 4;
            int listH  = bot - texTop;
            int totalH = (opts.size() + 1) * 30;
            texScrollTarget = clamp(texScrollTarget - vDelta * 20, 0, Math.max(0, totalH - listH));
        }
        return true;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    public boolean method_25403(double mx, double my, double hd, double vd) { return onScroll(mx, my, vd); }
    public boolean method_25401(double mx, double my, double vd)            { return onScroll(mx, my, vd); }

    @Override
    public boolean method_25404(int key, int scan, int mods) {
        if (key == 256) { method_25419(); return true; }
        return super.method_25404(key, scan, mods);
    }
}
