package com.slothyhub;

import com.slothyhub.cit.CitRule;
import com.slothyhub.cit.CitRuleSet;
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
 * Texture Builder screen — lets the user pick item texture overrides
 * from their local ilyslothy packs and bake them into a custom resource pack.
 */
public class TexturePickerScreen extends class_437 {

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int HEADER = 52;
    private static final int FOOTER = 44;
    private static final int ITEM_H = 36;
    private static final int LEFT_W = 160;
    private static final int PAD    = 14;

    // ── Palette (shared) ──────────────────────────────────────────────────
    private static final int BG      = Ui.COL_BG;
    private static final int PANEL   = Ui.COL_PANEL;
    private static final int SURFACE = Ui.COL_SURFACE;
    private static final int ACCENT  = Ui.COL_ACCENT;
    private static final int DANGER  = Ui.COL_DANGER;
    private static final int TEXT    = Ui.COL_TEXT;
    private static final int MUTED   = Ui.COL_MUTED;
    private static final int BORDER  = Ui.COL_BORDER;

    private static int col(int rgb, int a) { return Ui.withAlpha(rgb & 0xFFFFFF, a); }
    private static int lerp(int a, int b, float t) { return Ui.lerpColor(a, b, t); }

    // Common Minecraft items the user can override
    private static final String[] ITEM_KEYS = {
        "minecraft:diamond_sword",
        "minecraft:netherite_sword",
        "minecraft:iron_sword",
        "minecraft:golden_sword",
        "minecraft:stone_sword",
        "minecraft:wooden_sword",
        "minecraft:diamond_pickaxe",
        "minecraft:netherite_pickaxe",
        "minecraft:iron_pickaxe",
        "minecraft:diamond_axe",
        "minecraft:netherite_axe",
        "minecraft:bow",
        "minecraft:crossbow",
        "minecraft:elytra",
        "minecraft:shield",
        "minecraft:totem_of_undying",
        "minecraft:diamond_helmet",
        "minecraft:diamond_chestplate",
        "minecraft:diamond_leggings",
        "minecraft:diamond_boots",
        "minecraft:netherite_helmet",
        "minecraft:netherite_chestplate",
        "minecraft:netherite_leggings",
        "minecraft:netherite_boots",
        "minecraft:trident",
        "minecraft:mace",
    };

    private static String prettyItem(String key) {
        String part = key.contains(":") ? key.split(":")[1] : key;
        return Arrays.stream(part.split("_"))
            .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
            .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
    }

    // ── State ─────────────────────────────────────────────────────────────
    private final class_437 parent;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** item key → list of available texture paths from local packs */
    private final Map<String, List<String>> availableTextures = new LinkedHashMap<>();
    /** item key → index into availableTextures list (current selection, -1 = none) */
    private final Map<String, Integer> selections = new LinkedHashMap<>();

    private boolean scanning = true;
    private String scanStatus = "Scanning local packs…";
    private String buildStatus = null;
    private boolean buildOk = false;

    private int selectedItem = 0;
    private double itemScroll = 0, itemScrollTarget = 0;
    private double texScroll = 0, texScrollTarget = 0;

    private final Map<String, Float> itemHover = new HashMap<>();
    private final Map<String, Float> texHover  = new HashMap<>();

    private class_342 searchField;
    private String searchQuery = "";

    // ─────────────────────────────────────────────────────────────────────
    public TexturePickerScreen(class_437 parent) {
        super(class_2561.method_43470("Texture Builder"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
        // Footer buttons
        int bw = 116, bh = 26, gap = 10;
        int totalW = bw * 2 + gap;
        int bx = field_22789 / 2 - totalW / 2;
        int by = field_22790 - FOOTER + (FOOTER - bh) / 2;
        method_37063(new CustomButton(bx, by, bw, bh,
            class_2561.method_43470("BUILD & APPLY"), CustomButtonBase.Style.MOSS,
            this::buildAndApply));
        method_37063(new CustomButton(bx + bw + gap, by, bw, bh,
            class_2561.method_43470("BACK"), CustomButtonBase.Style.SECONDARY,
            this::method_25419));

        // Search within textures
        int sfW = Math.min(200, (field_22789 - LEFT_W - PAD * 3) / 2);
        searchField = new class_342(field_22793, field_22789 - sfW - PAD,
            HEADER + PAD / 2, sfW, 12, class_2561.method_43470("Filter textures"));
        searchField.method_1858(false);
        searchField.method_1880(60);
        searchField.method_47404(class_2561.method_43470("Filter textures…"));
        searchField.method_1868(TEXT);
        searchField.method_1863(q -> searchQuery = q);
        method_37063(searchField);

        // Scan async
        executor.submit(this::scanLocalPacks);
    }

    @Override
    public void method_25419() {
        executor.shutdownNow();
        class_310.method_1551().method_1507(parent);
    }

    @Override
    public boolean method_25421() { return false; }

    // ── Scanning ─────────────────────────────────────────────────────────

    private void scanLocalPacks() {
        Path localDir = com.slothyhub.local.LocalPackManager.getLocalPackDir();
        Map<String, List<String>> found = new LinkedHashMap<>();
        for (String key : ITEM_KEYS) found.put(key, new ArrayList<>());

        try {
            if (Files.isDirectory(localDir)) {
                try (Stream<Path> entries = Files.list(localDir)) {
                    List<Path> packs = entries.toList();
                    int total = packs.size(), done = 0;
                    for (Path pack : packs) {
                        String pname = pack.getFileName().toString();
                        String statusMsg = "Scanning " + pname + " (" + (++done) + "/" + total + ")";
                        class_310.method_1551().execute(() -> scanStatus = statusMsg);
                        if (pack.getFileName().toString().toLowerCase().endsWith(".zip")) {
                            scanZip(pack, found);
                        } else if (Files.isDirectory(pack)) {
                            scanFolder(pack, found);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("TexturePicker scan error: {}", e.getMessage());
        }

        // Also include textures already declared in CIT rules
        CitRuleSet ruleSet = CitRuleSet.active();
        if (!ruleSet.isEmpty()) {
            for (CitRule rule : ruleSet.allRules()) {
                String firstItem = CitRuleSet.firstItem(rule);
                if (firstItem == null || rule.texture == null) continue;
                String mcId = firstItem.contains(":") ? firstItem : "minecraft:" + firstItem;
                { // scope for mcId
                List<String> list = found.computeIfAbsent(mcId, k -> new ArrayList<>());
                String label = "[CIT] " + rule.texture;
                if (!list.contains(label)) list.add(label);
                } // end scope
            }
        }

        class_310.method_1551().execute(() -> {
            availableTextures.clear();
            availableTextures.putAll(found);
            scanning = false;
        });
    }

    /**
     * Scan a zip resource pack for OptiFine CIT .properties files.
     * Parses each one to get the items list and texture name, then
     * adds an entry like "PackName / TextureName (by name)" to the map.
     */
    private void scanZip(Path zipFile, Map<String, List<String>> found) {
        String packName = zipFile.getFileName().toString().replaceAll("(?i)\\.zip$", "");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                if (ze.isDirectory()) continue;
                String entryPath = ze.getName();
                String lower = entryPath.toLowerCase(Locale.ROOT);
                // Parse CIT .properties files
                if (lower.contains("optifine/cit") || lower.contains("citresewn")) {
                    if (lower.endsWith(".properties")) {
                        try (InputStream in = zf.getInputStream(ze)) {
                            parseCitEntry(packName, entryPath, in, found);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void scanFolder(Path folder, Map<String, List<String>> found) {
        String packName = folder.getFileName().toString();
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path f : walk.toList()) {
                if (Files.isDirectory(f)) continue;
                String rel = folder.relativize(f).toString().replace('\\', '/');
                String lower = rel.toLowerCase(Locale.ROOT);
                if ((lower.contains("optifine/cit") || lower.contains("citresewn"))
                        && lower.endsWith(".properties")) {
                    try (InputStream in = Files.newInputStream(f)) {
                        parseCitEntry(packName, rel, in, found);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private void parseCitEntry(String packName, String entryPath,
                                InputStream in, Map<String, List<String>> found) throws IOException {
        java.util.Properties props = new java.util.Properties();
        props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));

        String type = props.getProperty("type", "item");
        if (!type.equalsIgnoreCase("item") && !type.equalsIgnoreCase("items")) return;

        String itemsRaw = props.getProperty("items", props.getProperty("matchItems", ""));
        if (itemsRaw.isBlank()) return;

        String textureProp = props.getProperty("texture", props.getProperty("model", ""));
        String nameProp    = props.getProperty("name", props.getProperty("nbt.display.Name", ""));

        // Build a human-readable label
        String texName = textureProp.isBlank()
            ? entryPath.substring(entryPath.lastIndexOf('/') + 1).replace(".properties", "")
            : textureProp;
        String label = packName + " / " + texName
            + (nameProp.isBlank() ? "" : "  (" + nameProp + ")");

        for (String rawItem : itemsRaw.trim().split("\\s+")) {
            String normalized = rawItem.contains(":") ? rawItem : "minecraft:" + rawItem;
            List<String> list = found.get(normalized);
            if (list != null && !list.contains(label)) list.add(label);
        }
    }

    // ── Build ─────────────────────────────────────────────────────────────

    private void buildAndApply() {
        if (scanning) return;
        buildStatus = "Building custom pack…"; buildOk = false;
        executor.submit(() -> {
            try {
                Path out = buildCustomPack();
                class_310.method_1551().execute(() -> {
                    buildStatus = "Built! Applying…"; buildOk = true;
                });
                // Apply via PackDownloader
                Pack fakePack = makeFakePack(out);
                class_310.method_1551().execute(() ->
                    PackDownloader.downloadAndApply(fakePack, null, new PackDownloader.ProgressCallback() {
                        public void onProgress(float f) {}
                        public void onApplying() { class_310.method_1551().execute(() -> buildStatus = "Applying…"); }
                        public void onDone()     { class_310.method_1551().execute(() -> { buildStatus = "Applied! ✓"; buildOk = true; }); }
                        public void onError(String m) { class_310.method_1551().execute(() -> { buildStatus = "Error: " + m; buildOk = false; }); }
                    })
                );
            } catch (Exception e) {
                class_310.method_1551().execute(() -> { buildStatus = "Build failed: " + e.getMessage(); buildOk = false; });
            }
        });
    }

    private Path buildCustomPack() throws Exception {
        Path tmp = Files.createTempFile("slothyhub_custom_", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            // pack.mcmeta
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            String meta = "{\"pack\":{\"pack_format\":34,\"description\":\"Slothy's Tree Custom Textures\"}}";
            zos.write(meta.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            // CIT properties for each selected item
            Path localDir = com.slothyhub.local.LocalPackManager.getLocalPackDir();
            int ruleNum = 0;
            for (Map.Entry<String, Integer> sel : selections.entrySet()) {
                if (sel.getValue() < 0) continue;
                String itemKey = sel.getKey();
                List<String> textures = availableTextures.get(itemKey);
                if (textures == null || sel.getValue() >= textures.size()) continue;
                String texLabel = textures.get(sel.getValue());
                // texLabel format: "packName / relative/path/to/file.png"
                String[] parts = texLabel.startsWith("[CIT]")
                    ? null : texLabel.split(" / ", 2);
                if (parts == null) continue;
                String packName = parts[0].trim();
                String relPath  = parts[1].trim().replace('\\', '/');
                // Find the actual file inside the zip/folder
                byte[] texData = extractTextureBytes(localDir, packName, relPath);
                if (texData == null) continue;
                String itemName = itemKey.split(":")[1];
                String texEntry = "assets/minecraft/textures/item/slothy_" + itemName + "_" + ruleNum + ".png";
                zos.putNextEntry(new ZipEntry(texEntry));
                zos.write(texData);
                zos.closeEntry();
                // CIT properties
                String propEntry = "assets/minecraft/optifine/cit/slothy_" + itemName + "_" + ruleNum + ".properties";
                zos.putNextEntry(new ZipEntry(propEntry));
                String prop = "type=item\n" +
                    "items=" + itemName + "\n" +
                    "texture=" + "slothy_" + itemName + "_" + ruleNum + "\n";
                zos.write(prop.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                ruleNum++;
            }
        }
        return tmp;
    }

    private byte[] extractTextureBytes(Path localDir, String packName, String relPath) {
        // Try zip first
        Path[] candidates = {
            localDir.resolve(packName + ".zip"),
            localDir.resolve(packName),
        };
        for (Path candidate : candidates) {
            if (!Files.exists(candidate)) continue;
            if (Files.isDirectory(candidate)) {
                try { return Files.readAllBytes(candidate.resolve(relPath.replace('/', File.separatorChar))); }
                catch (Exception ignored) {}
            } else {
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(candidate.toFile())) {
                    ZipEntry ze = zf.getEntry(relPath);
                    if (ze == null) continue;
                    try (InputStream in = zf.getInputStream(ze)) { return in.readAllBytes(); }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private Pack makeFakePack(Path zipPath) {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("id", "local:slothy_custom_textures.zip");
        obj.addProperty("name", "Slothy Custom Textures");
        obj.addProperty("pack_filename", "slothy_custom_textures.zip");
        obj.addProperty("author_name", "You");
        obj.addProperty("author_id", "local");
        obj.addProperty("showcase_path", "");
        obj.addProperty("pack_url", zipPath.toUri().toString());
        obj.addProperty("is_zip", true);
        obj.addProperty("has_local_file", true);
        obj.addProperty("star_count", 0);
        obj.addProperty("downloads", 0);
        obj.addProperty("sha256", "");
        obj.addProperty("viewer_starred", false);
        Pack p = new com.google.gson.Gson().fromJson(obj, Pack.class);
        p.setLocal(true);
        return p;
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void method_25394(class_332 ctx, int mx, int my, float delta) {
        // Animate scroll
        itemScroll += (itemScrollTarget - itemScroll) * Math.min(1f, delta * 0.28f);
        texScroll  += (texScrollTarget  - texScroll)  * Math.min(1f, delta * 0.28f);

        drawBg(ctx, delta);
        drawHeader(ctx, mx, my);
        drawItemList(ctx, mx, my, delta);
        drawTexturePanel(ctx, mx, my, delta);
        drawFooter(ctx);
        drawStatus(ctx);

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

        // Back arrow
        DrawHelper.drawText(ctx, field_22793, "←", PAD, (HEADER - 9) / 2 + 1, MUTED, false);

        // Title
        String title = "TEXTURE BUILDER";
        int tw = field_22793.method_1727(title);
        DrawHelper.drawText(ctx, field_22793, title, field_22789 / 2 - tw / 2, (HEADER - 9) / 2 + 1, ACCENT, false);

        // Sub info
        int selected = (int) selections.values().stream().filter(v -> v >= 0).count();
        String info = selected + " / " + ITEM_KEYS.length + " items configured";
        DrawHelper.drawText(ctx, field_22793, info,
            field_22789 - PAD - field_22793.method_1727(info), (HEADER - 9) / 2 + 1, MUTED, false);
    }

    private void drawItemList(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER, bot = field_22790 - FOOTER;
        ctx.method_25294(0, top, LEFT_W, bot, PANEL);
        ctx.method_25294(LEFT_W - 1, top, LEFT_W, bot, BORDER);

        // Section label
        DrawHelper.drawText(ctx, field_22793, "ITEMS", PAD, top + 8, MUTED, false);

        ctx.method_44379(0, top + 20, LEFT_W, bot);
        int y = top + 20 - (int) itemScroll;
        for (int i = 0; i < ITEM_KEYS.length; i++) {
            String key = ITEM_KEYS[i];
            boolean sel = i == selectedItem;
            boolean hov = mx >= 0 && mx < LEFT_W && my >= y && my <= y + ITEM_H && my > top + 20 && my < bot;
            float ht = itemHover.getOrDefault(key, 0f);
            ht += ((hov ? 1f : 0f) - ht) * Math.min(1f, delta * 0.4f);
            itemHover.put(key, ht);

            if (sel) {
                ctx.method_25294(0, y, LEFT_W, y + ITEM_H, col(ACCENT & 0xFFFFFF, 25));
                ctx.method_25294(0, y, 3, y + ITEM_H, ACCENT);
            } else if (ht > 0.02f) {
                ctx.method_25294(0, y, LEFT_W, y + ITEM_H, col(SURFACE & 0xFFFFFF, (int)(140 * ht)));
            }

            String pretty = prettyItem(key);
            int textCol = sel ? ACCENT : lerp(MUTED, TEXT, ht);
            DrawHelper.drawText(ctx, field_22793, pretty, PAD + 4, y + (ITEM_H - 9) / 2, textCol, false);

            // Check-mark if configured
            Integer selIdx = selections.get(key);
            if (selIdx != null && selIdx >= 0) {
                DrawHelper.drawText(ctx, field_22793, "✓", LEFT_W - 14, y + (ITEM_H - 9) / 2, ACCENT, false);
            }

            // Separator
            ctx.method_25294(PAD, y + ITEM_H - 1, LEFT_W - PAD, y + ITEM_H, col(BORDER & 0xFFFFFF, 80));
            y += ITEM_H;
        }
        ctx.method_44380();
    }

    private void drawTexturePanel(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER, bot = field_22790 - FOOTER;
        int panelX = LEFT_W;
        int panelW = field_22789 - LEFT_W;

        ctx.method_25294(panelX, top, field_22789, bot, BG);

        // Header row
        String selectedName = prettyItem(ITEM_KEYS[selectedItem]);
        DrawHelper.drawText(ctx, field_22793, selectedName, panelX + PAD, top + 8, TEXT, false);
        DrawHelper.drawText(ctx, field_22793, "Pick a texture override →", panelX + PAD, top + 19, MUTED, false);

        if (scanning) {
            int cy = (top + bot) / 2;
            Ui.spinner(ctx, field_22789 / 2 + LEFT_W / 2, cy, 8,
                (float)(System.currentTimeMillis() % 1200L) / 1200f, col(ACCENT & 0xFFFFFF, 200));
            DrawHelper.drawText(ctx, field_22793, scanStatus,
                field_22789 / 2 + LEFT_W / 2 - field_22793.method_1727(scanStatus) / 2, cy + 16, MUTED, false);
            return;
        }

        String key = ITEM_KEYS[selectedItem];
        List<String> textures = availableTextures.getOrDefault(key, Collections.emptyList());

        // Filter
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        List<String> shown = q.isEmpty() ? textures
            : textures.stream().filter(t -> t.toLowerCase(Locale.ROOT).contains(q)).toList();

        if (shown.isEmpty()) {
            String msg = textures.isEmpty() ? "No textures found in local packs for this item."
                : "No textures match \"" + q + "\"";
            int cy = (top + bot) / 2;
            DrawHelper.drawText(ctx, field_22793, msg,
                panelX + panelW / 2 - field_22793.method_1727(msg) / 2, cy, MUTED, false);
            return;
        }

        int TEX_H = 30;
        int innerTop = top + 32;
        ctx.method_44379(panelX, innerTop, field_22789, bot);

        int y = innerTop - (int) texScroll;
        Integer curSel = selections.getOrDefault(key, -1);

        // "None" option
        {
            boolean isNone = curSel == null || curSel < 0;
            boolean hov = mx >= panelX && mx < field_22789 && my >= y && my < y + TEX_H && my > innerTop && my < bot;
            if (isNone) ctx.method_25294(panelX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, 140));
            else if (hov) ctx.method_25294(panelX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, 60));
            if (isNone) ctx.method_25294(panelX, y, panelX + 3, y + TEX_H, DANGER);
            DrawHelper.drawText(ctx, field_22793, "✕  None (no override)", panelX + PAD, y + (TEX_H - 9) / 2,
                isNone ? DANGER : MUTED, false);
            ctx.method_25294(panelX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }

        for (int i = 0; i < shown.size(); i++) {
            String tex = shown.get(i);
            boolean sel = Objects.equals(curSel, textures.indexOf(tex));
            boolean hov = mx >= panelX && mx < field_22789 && my >= y && my < y + TEX_H && my > innerTop && my < bot;
            String hKey = key + "/" + i;
            float ht = texHover.getOrDefault(hKey, 0f);
            ht += ((hov ? 1f : 0f) - ht) * Math.min(1f, delta * 0.4f);
            texHover.put(hKey, ht);

            if (sel) {
                ctx.method_25294(panelX, y, field_22789, y + TEX_H, col(ACCENT & 0xFFFFFF, 25));
                ctx.method_25294(panelX, y, panelX + 3, y + TEX_H, ACCENT);
            } else if (ht > 0.02f) {
                ctx.method_25294(panelX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, (int)(100 * ht)));
            }

            String display = tex.length() > 70 ? "…" + tex.substring(tex.length() - 68) : tex;
            int fg = sel ? ACCENT : lerp(MUTED, TEXT, ht);
            DrawHelper.drawText(ctx, field_22793, display, panelX + PAD, y + (TEX_H - 9) / 2, fg, false);
            ctx.method_25294(panelX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }

        ctx.method_44380();

        // Scrollbar
        int totalH = (shown.size() + 1) * 30;
        int listH  = bot - innerTop;
        if (totalH > listH) {
            int trkH = listH - 8, trkX = field_22789 - 4;
            int tmbH = Math.max(20, trkH * listH / totalH);
            int tmbY = innerTop + 4 + (int)((double)(trkH - tmbH) * texScroll / Math.max(1, totalH - listH));
            ctx.method_25294(trkX, innerTop + 4, trkX + 3, innerTop + 4 + trkH, col(BORDER & 0xFFFFFF, 100));
            ctx.method_25294(trkX, tmbY, trkX + 3, tmbY + tmbH, col(ACCENT & 0xFFFFFF, 160));
        }
    }

    private void drawFooter(class_332 ctx) {
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, PANEL);
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790 - FOOTER + 1, BORDER);
    }

    private void drawStatus(class_332 ctx) {
        if (buildStatus == null) return;
        int col = buildOk ? ACCENT : DANGER;
        int sw = field_22793.method_1727(buildStatus);
        int sx = field_22789 / 2 - sw / 2;
        int sy = field_22790 - FOOTER + 6;
        DrawHelper.drawText(ctx, field_22793, buildStatus, sx, sy, col, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────

    @Override
    public boolean method_25402(double mx, double my, int button) {
        if (button != 0) return false;

        // Back arrow
        if (mx >= PAD && mx <= PAD + 14 && my >= HEADER / 2 - 6 && my <= HEADER / 2 + 6) {
            method_25419(); return true;
        }

        int top = HEADER, bot = field_22790 - FOOTER;

        // Item list click
        if (mx >= 0 && mx < LEFT_W && my > top + 20 && my < bot) {
            int y = top + 20 - (int) itemScroll;
            for (int i = 0; i < ITEM_KEYS.length; i++) {
                if (my >= y && my < y + ITEM_H) { selectedItem = i; texScroll = texScrollTarget = 0; return true; }
                y += ITEM_H;
            }
        }

        // Texture selection click
        if (mx >= LEFT_W && mx < field_22789 && my > top + 32 && my < bot && !scanning) {
            String key = ITEM_KEYS[selectedItem];
            List<String> textures = availableTextures.getOrDefault(key, Collections.emptyList());
            String q = searchQuery.toLowerCase(Locale.ROOT).trim();
            List<String> shown = q.isEmpty() ? textures
                : textures.stream().filter(t -> t.toLowerCase(Locale.ROOT).contains(q)).toList();

            int TEX_H = 30;
            int y = top + 32 - (int) texScroll;
            // "None" row
            if (my >= y && my < y + TEX_H) { selections.put(key, -1); return true; }
            y += TEX_H;
            for (int i = 0; i < shown.size(); i++) {
                if (my >= y && my < y + TEX_H) {
                    selections.put(key, textures.indexOf(shown.get(i))); return true;
                }
                y += TEX_H;
            }
        }

        return false;
    }

    public boolean onScrollDelta(double v) {
        int top = HEADER, bot = field_22790 - FOOTER;
        // TODO: detect which panel the mouse is in — default to texture panel
        int TEX_H = 30;
        String key = ITEM_KEYS[selectedItem];
        List<String> textures = availableTextures.getOrDefault(key, Collections.emptyList());
        int totalH = (textures.size() + 1) * TEX_H;
        int listH  = bot - (top + 32);
        texScrollTarget = Math.max(0, Math.min(texScrollTarget - v * 20, Math.max(0, totalH - listH)));
        return true;
    }

    public boolean method_25403(double mx, double my, double hDelta, double vDelta) { return onScrollDelta(vDelta); }
    public boolean method_25401(double mx, double my, double vDelta) { return onScrollDelta(vDelta); }

    @Override
    public boolean method_25404(int key, int scan, int mods) {
        if (key == 256) { method_25419(); return true; }
        return super.method_25404(key, scan, mods);
    }
}
