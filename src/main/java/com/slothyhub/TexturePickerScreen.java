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
 * Texture Builder — pick CIT textures from local packs, customise names/folders,
 * build a resource pack (Summer/OptiFine format), save to slothyhub-library, apply.
 */
public class TexturePickerScreen extends class_437 {

    record SlotDef(String display, String primaryItem, String[] altItems,
                   String defaultCitName, String outputBaseName, String emoji,
                   boolean vanillaTexture, String vanillaOutputPath,
                   String[] textureDirs, String[] textureKeywords) {}

    enum TexFolder {
        ITEM("item",      "assets/minecraft/textures/item/",      "item"),
        BLOCK("block",    "assets/minecraft/textures/block/",     "block"),
        PARTICLES("particles", "assets/minecraft/textures/particle/", "particle");

        final String label, assetPath, citPrefix;
        TexFolder(String label, String assetPath, String citPrefix) {
            this.label = label; this.assetPath = assetPath; this.citPrefix = citPrefix;
        }
    }

    private static final SlotDef[] SLOTS = {
        new SlotDef("Noob Sword",    "netherite_sword", new String[]{"diamond_sword"}, "Noob Sword",    "noob_sword",    "⚔", false, null, new String[]{}, new String[]{}),
        new SlotDef("Good Sword",    "netherite_sword", new String[]{"diamond_sword"}, "Good Sword",    "good_sword",    "⚔", false, null, new String[]{}, new String[]{}),
        new SlotDef("Pro Sword",     "netherite_sword", new String[]{"diamond_sword"}, "Pro Sword",     "pro_sword",     "⚔", false, null, new String[]{}, new String[]{}),
        new SlotDef("Perfect Sword", "netherite_sword", new String[]{"diamond_sword"}, "Perfect Sword", "perfect_sword", "⚔", false, null, new String[]{}, new String[]{}),
        new SlotDef("Hippo Sword",   "netherite_sword", new String[]{"diamond_sword"}, "Hippo Sword",   "hippo_sword",   "🦛", false, null, new String[]{}, new String[]{}),
        new SlotDef("Warden Sword",  "netherite_sword", new String[]{"diamond_sword"}, "Warden Sword",  "warden_sword",  "🌑", false, null, new String[]{}, new String[]{}),
        new SlotDef("Particles",     "potion",          new String[]{},                null, "particles",   "✨", true,  null, new String[]{"assets/minecraft/textures/particle/"}, new String[]{}),
        new SlotDef("Fireworks",     "firework_rocket", new String[]{},                null, "fireworks",   "🎆", true,  "assets/minecraft/textures/item/firework_rocket.png", new String[]{"assets/minecraft/textures/item/"}, new String[]{"firework"}),
        new SlotDef("Golden Apple",  "golden_apple",    new String[]{},                null, "golden_apple","🍎", true,  "assets/minecraft/textures/item/golden_apple.png", new String[]{"assets/minecraft/textures/item/"}, new String[]{"golden_apple", "enchanted_golden_apple"}),
        new SlotDef("Offhands",      "cornflower",      new String[]{},                null, "cornflower",  "🌸", true,  "assets/minecraft/textures/item/cornflower.png", new String[]{"assets/minecraft/textures/item/", "assets/minecraft/textures/block/"}, new String[]{"cornflower"}),
    };

    private static final int HEADER  = 52;
    private static final int FOOTER  = 48;
    private static final int ITEM_H  = 38;
    private static final int LEFT_W  = 178;
    private static final int PAD     = 12;
    private static final int CFG_H   = 56;
    private static final int SEARCH_H = 22;

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

    /** One discoverable texture from a local pack. */
    record TextureOption(
        String label,
        Path   packPath,
        boolean isZip,
        String pngEntry,   // path inside zip/folder
        byte[] pngPreview  // may be null — loaded again at build time
    ) {}

    private final class_437 parent;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Map<Integer, List<TextureOption>> discovered = new LinkedHashMap<>();
    private final Map<Integer, Integer> selections  = new LinkedHashMap<>();
    private final Map<Integer, String>  customNames = new HashMap<>();
    private final Map<Integer, TexFolder> folders   = new HashMap<>();

    private String packName = "My Custom Pack";
    private boolean scanning = true;
    private String scanStatus = "Scanning resource packs…";
    private String buildStatus = null;
    private boolean buildOk = false;

    private int selectedSlot = 0;
    private double itemScroll = 0, itemScrollTarget = 0;
    private double texScroll = 0, texScrollTarget = 0;
    private final Map<Integer, Float> itemHover = new HashMap<>();
    private final Map<String, Float> texHover = new HashMap<>();

    private class_342 nameField;
    private class_342 packNameField;
    private class_342 searchField;
    private String searchQuery = "";

    public TexturePickerScreen(class_437 parent) {
        super(class_2561.method_43470("Texture Builder"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
        int pnfW = 150;
        packNameField = new class_342(field_22793,
            field_22789 / 2 - pnfW / 2, HEADER / 2 - 6,
            pnfW, 12, class_2561.method_43470("Pack name"));
        packNameField.method_1858(false);
        packNameField.method_1880(48);
        packNameField.method_1867(packName);
        packNameField.method_1868(TEXT);
        packNameField.method_1863(v -> packName = v.isBlank() ? "My Custom Pack" : v.trim());
        method_37063(packNameField);

        int nfX = LEFT_W + PAD + field_22793.method_1727("CIT Name  ") + 2;
        int nfW = Math.min(220, field_22789 - nfX - PAD);
        nameField = new class_342(field_22793, nfX, HEADER + 24, nfW, 12,
            class_2561.method_43470("CIT name"));
        nameField.method_1858(false);
        nameField.method_1880(40);
        nameField.method_1867(defaultNameForSlot(0));
        nameField.method_47404(class_2561.method_43470("e.g. Pro Sword"));
        nameField.method_1868(TEXT);
        nameField.method_1863(v -> customNames.put(selectedSlot, v.isBlank() ? null : v.trim()));
        method_37063(nameField);

        int searchTop = HEADER + CFG_H;
        int searchLabelW = field_22793.method_1727("Search") + 8;
        int searchW = field_22789 - LEFT_W - PAD * 2 - searchLabelW;
        searchField = new class_342(field_22793, LEFT_W + PAD + searchLabelW, searchTop + 4,
            Math.max(80, searchW), 12, class_2561.method_43470("Search"));
        searchField.method_1858(false);
        searchField.method_1880(60);
        searchField.method_47404(class_2561.method_43470("Search textures…"));
        searchField.method_1868(TEXT);
        searchField.method_1863(q -> { searchQuery = q; texScroll = texScrollTarget = 0; });
        method_37063(searchField);

        int bw = 124, bh = 26, gap = 10;
        int bx = field_22789 / 2 - bw - gap / 2;
        int by = field_22790 - FOOTER + (FOOTER - bh) / 2;
        method_37063(new CustomButton(bx, by, bw, bh,
            class_2561.method_43470("BUILD & APPLY"), CustomButtonBase.Style.MOSS, this::buildAndApply));
        method_37063(new CustomButton(bx + bw + gap, by, bw, bh,
            class_2561.method_43470("BACK"), CustomButtonBase.Style.SECONDARY, this::method_25419));

        executor.submit(this::scanResourcePacks);
        updateSlotWidgets();
    }

    @Override
    public void method_25419() {
        executor.shutdownNow();
        class_310.method_1551().method_1507(parent);
    }

    @Override public boolean method_25421() { return false; }

    private String defaultNameForSlot(int idx) {
        String custom = customNames.get(idx);
        if (custom != null) return custom;
        String def = SLOTS[idx].defaultCitName();
        return def == null ? "" : def;
    }

    private String effectiveCitName(int idx) {
        String custom = customNames.get(idx);
        if (custom != null && !custom.isBlank()) return custom;
        return SLOTS[idx].defaultCitName();
    }

    private TexFolder effectiveFolder(int idx) {
        return folders.getOrDefault(idx, TexFolder.ITEM);
    }

    private static Path libraryDir() {
        Path dir = class_310.method_1551().field_1697.toPath().resolve("slothyhub-library");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    // ── Scan ──────────────────────────────────────────────────────────────

    private void scanResourcePacks() {
        Path rpDir = class_310.method_1551().field_1697.toPath().resolve("resourcepacks");
        Map<Integer, List<TextureOption>> result = new LinkedHashMap<>();
        for (int i = 0; i < SLOTS.length; i++) result.put(i, new ArrayList<>());

        try {
            if (Files.isDirectory(rpDir)) {
                List<Path> packs;
                try (Stream<Path> s = Files.list(rpDir)) {
                    packs = s.filter(p -> {
                        String n = p.getFileName().toString();
                        if (n.startsWith(".")) return false;
                        if (Files.isDirectory(p)) return Files.exists(p.resolve("pack.mcmeta"));
                        return n.toLowerCase(Locale.ROOT).endsWith(".zip");
                    }).toList();
                }
                int total = packs.size(), done = 0;
                for (Path pack : packs) {
                    int d = ++done;
                    class_310.method_1551().execute(() ->
                        scanStatus = "Scanning " + pack.getFileName() + " (" + d + "/" + total + ")");
                    if (Files.isDirectory(pack)) scanFolder(pack, result);
                    else                         scanZip(pack, result);
                    scanVanillaTextures(pack, Files.isDirectory(pack), packLabelFrom(pack), result);
                }
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("TexturePicker scan error: {}", e.getMessage());
        }

        class_310.method_1551().execute(() -> {
            discovered.clear();
            discovered.putAll(result);
            scanning = false;
            SlothyHubMod.LOGGER.info("TexturePicker: {} options across {} slots",
                result.values().stream().mapToInt(List::size).sum(), SLOTS.length);
        });
    }

    private static String packLabelFrom(Path pack) {
        String n = pack.getFileName().toString();
        return n.toLowerCase(Locale.ROOT).endsWith(".zip") ? n.replaceAll("(?i)\\.zip$", "") : n;
    }

    /** Scan assets/minecraft/textures/ for vanilla overrides (apple, fireworks, particles, etc.). */
    private void scanVanillaTextures(Path packPath, boolean isFolder, String packLabel,
                                      Map<Integer, List<TextureOption>> out) {
        if (isFolder) scanVanillaTexturesFolder(packPath, packLabel, out);
        else scanVanillaTexturesZip(packPath, packLabel, out);
    }

    private void scanVanillaTexturesZip(Path zip, String packLabel, Map<Integer, List<TextureOption>> out) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> all = zf.entries();
            while (all.hasMoreElements()) {
                ZipEntry ze = all.nextElement();
                if (ze.isDirectory()) continue;
                String entry = ze.getName().replace('\\', '/');
                if (!entry.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                if (entry.toLowerCase(Locale.ROOT).contains("/cit/")) continue;
                if (!entry.contains("assets/minecraft/textures/")) continue;
                byte[] preview;
                try (InputStream in = zf.getInputStream(ze)) { preview = in.readAllBytes(); }
                catch (Exception e) { preview = null; }
                addVanillaTextureOption(packLabel, zip, true, entry, preview, out);
            }
        } catch (Exception ignored) {}
    }

    private void scanVanillaTexturesFolder(Path folder, String packLabel, Map<Integer, List<TextureOption>> out) {
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path f : walk.filter(p -> !Files.isDirectory(p)).toList()) {
                String rel = folder.relativize(f).toString().replace('\\', '/');
                if (!rel.toLowerCase(Locale.ROOT).endsWith(".png")) continue;
                if (rel.toLowerCase(Locale.ROOT).contains("/cit/")) continue;
                if (!rel.contains("assets/minecraft/textures/")) continue;
                byte[] preview = null;
                try { preview = Files.readAllBytes(f); } catch (Exception ignored) {}
                addVanillaTextureOption(packLabel, folder, false, rel, preview, out);
            }
        } catch (Exception ignored) {}
    }

    private void addVanillaTextureOption(String packLabel, Path packPath, boolean isZip,
                                          String entry, byte[] preview,
                                          Map<Integer, List<TextureOption>> out) {
        String lower = entry.toLowerCase(Locale.ROOT);
        String fileName = entry.substring(entry.lastIndexOf('/') + 1).replace(".png", "");

        for (int si = 0; si < SLOTS.length; si++) {
            SlotDef slot = SLOTS[si];
            if (!slot.vanillaTexture()) continue;

            boolean dirMatch = false;
            for (String dir : slot.textureDirs()) {
                if (lower.contains(dir.toLowerCase(Locale.ROOT))) { dirMatch = true; break; }
            }
            if (!dirMatch) continue;

            if (slot.textureKeywords().length > 0) {
                boolean kwMatch = false;
                for (String kw : slot.textureKeywords()) {
                    if (fileName.toLowerCase(Locale.ROOT).contains(kw.toLowerCase(Locale.ROOT))) {
                        kwMatch = true; break;
                    }
                }
                if (!kwMatch) continue;
            }

            String label = packLabel + " / " + entry.substring(entry.indexOf("textures/") + 9);
            TextureOption opt = new TextureOption(label, packPath, isZip, entry, preview);
            List<TextureOption> list = out.get(si);
            if (list.stream().noneMatch(o -> o.label().equals(label))) list.add(opt);
        }
    }

    private void scanZip(Path zip, Map<Integer, List<TextureOption>> out) {
        String packLabel = zip.getFileName().toString().replaceAll("(?i)\\.zip$", "");
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            List<String> propPaths = new ArrayList<>();
            Enumeration<? extends ZipEntry> all = zf.entries();
            while (all.hasMoreElements()) {
                ZipEntry ze = all.nextElement();
                if (ze.isDirectory()) continue;
                String p = ze.getName().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (p.contains("/cit/") && p.endsWith(".properties"))
                    propPaths.add(ze.getName());
            }
            for (String propPath : propPaths) {
                ZipEntry pe = zf.getEntry(propPath);
                if (pe == null) continue;
                try (InputStream in = zf.getInputStream(pe)) {
                    parseCitEntry(packLabel, zip, true, propPath, entryDir(propPath),
                        loadProps(in), path -> {
                            ZipEntry e = zf.getEntry(path);
                            return e != null ? zf.getInputStream(e) : null;
                        }, out);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void scanFolder(Path folder, Map<Integer, List<TextureOption>> out) {
        String packLabel = folder.getFileName().toString();
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path pf : walk.filter(f -> !Files.isDirectory(f)).toList()) {
                String rel = folder.relativize(pf).toString().replace('\\', '/');
                if (!rel.toLowerCase(Locale.ROOT).contains("/cit/") || !rel.endsWith(".properties")) continue;
                try (InputStream in = Files.newInputStream(pf)) {
                    parseCitEntry(packLabel, folder, false, rel, entryDir(rel),
                        loadProps(in), path -> {
                            Path fp = folder.resolve(path.replace('/', File.separatorChar));
                            return Files.exists(fp) ? Files.newInputStream(fp) : null;
                        }, out);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    @FunctionalInterface interface StreamOpener { InputStream open(String path) throws IOException; }

    private void parseCitEntry(String packLabel, Path packPath, boolean isZip,
                                String propPath, String propDir, Properties props,
                                StreamOpener opener, Map<Integer, List<TextureOption>> out) {
        String type = props.getProperty("type", "item").toLowerCase(Locale.ROOT);
        if (!type.equals("item") && !type.equals("items")) return;

        String itemsRaw = props.getProperty("items", props.getProperty("matchItems", "")).trim();
        if (itemsRaw.isBlank()) return;

        String texProp = props.getProperty("texture", props.getProperty("model", "")).trim();
        if (texProp.isBlank()) return;

        String nameProp = props.getProperty("name", props.getProperty("nbt.display.Name", "")).trim();
        String cleanName = stripNamePrefix(nameProp);
        Set<String> items = new HashSet<>(Arrays.asList(itemsRaw.split("\\s+")));

        String texBase = texProp.replace('\\', '/');
        if (texBase.endsWith(".png")) texBase = texBase.substring(0, texBase.length() - 4);
        int slash = texBase.lastIndexOf('/');
        String texFile = slash >= 0 ? texBase.substring(slash + 1) : texBase;

        String pngEntry = resolvePngEntry(propDir, texProp, opener);
        byte[] preview = null;
        if (pngEntry != null) {
            try (InputStream in = opener.open(pngEntry)) {
                if (in != null) preview = in.readAllBytes();
            } catch (Exception ignored) {}
        }

        String label = packLabel + " / " + texFile
            + (cleanName.isBlank() ? "" : "  [" + cleanName + "]");
        TextureOption opt = new TextureOption(label, packPath, isZip, pngEntry, preview);

        for (int si = 0; si < SLOTS.length; si++) {
            SlotDef slot = SLOTS[si];
            if (!itemMatchesSlot(items, slot)) continue;
            if (slot.defaultCitName() != null && !nameMatches(cleanName, slot.defaultCitName())) continue;

            List<TextureOption> list = out.get(si);
            if (list.stream().noneMatch(o -> o.label().equals(label)))
                list.add(opt);
        }
    }

    private static boolean itemMatchesSlot(Set<String> items, SlotDef slot) {
        if (items.contains(slot.primaryItem()) || items.contains("minecraft:" + slot.primaryItem())) return true;
        for (String alt : slot.altItems()) {
            if (items.contains(alt) || items.contains("minecraft:" + alt)) return true;
        }
        return false;
    }

    private static boolean nameMatches(String citName, String slotName) {
        if (slotName == null) return true;
        if (citName == null || citName.isBlank()) return false;
        String a = citName.toLowerCase(Locale.ROOT).trim();
        String b = slotName.toLowerCase(Locale.ROOT).trim();
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static String stripNamePrefix(String name) {
        return name.replaceFirst("(?i)^ipattern:", "").replaceFirst("(?i)^pattern:", "")
            .replaceFirst("(?i)^iregex:", "").replaceFirst("(?i)^regex:", "").trim();
    }

    private static String resolvePngEntry(String propDir, String texProp, StreamOpener opener) {
        String base = texProp.replace('\\', '/');
        String[] tries = {
            propDir + (base.endsWith(".png") ? base : base + ".png"),
            base.endsWith(".png") ? base : base + ".png",
            propDir + base.substring(base.lastIndexOf('/') + 1) + ".png"
        };
        for (String t : tries) {
            try (InputStream in = opener.open(t)) {
                if (in != null) return t;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String entryDir(String path) {
        String n = path.replace('\\', '/');
        int slash = n.lastIndexOf('/');
        return slash >= 0 ? n.substring(0, slash + 1) : "";
    }

    private static Properties loadProps(InputStream in) throws IOException {
        Properties p = new Properties();
        p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        return p;
    }

    // ── Build ─────────────────────────────────────────────────────────────

    private void buildAndApply() {
        if (scanning) return;
        long chosen = selections.values().stream().filter(v -> v != null && v >= 0).count();
        if (chosen == 0) { buildStatus = "Select at least one texture first."; buildOk = false; return; }

        buildStatus = "Building…"; buildOk = false;
        executor.submit(() -> {
            try {
                byte[] zipBytes = buildPack();
                String safe = packName.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim().replace(' ', '_');
                if (safe.isEmpty()) safe = "SlothyCustomPack";
                final String libName = safe;
                Files.write(libraryDir().resolve(libName + ".zip"), zipBytes);

                PackDownloader.applyBuiltPack(zipBytes, packName);
                class_310.method_1551().execute(() -> {
                    buildStatus = "Applied & saved to slothyhub-library/" + libName + ".zip";
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
            putEntry(zos, "pack.mcmeta",
                "{\"pack\":{\"pack_format\":34,\"description\":\"" +
                packName.replace("\"", "'") + "\"}}");

            for (int si = 0; si < SLOTS.length; si++) {
                Integer selIdx = selections.get(si);
                if (selIdx == null || selIdx < 0) continue;
                List<TextureOption> opts = discovered.getOrDefault(si, List.of());
                if (selIdx >= opts.size()) continue;

                SlotDef slot = SLOTS[si];
                TextureOption opt = opts.get(selIdx);

                byte[] png = opt.pngPreview();
                if ((png == null || png.length == 0) && opt.pngEntry() != null)
                    png = readBytesFromPack(opt.packPath(), opt.isZip(), opt.pngEntry());
                if (png == null || png.length == 0)
                    throw new IOException("Could not read PNG for " + slot.display());

                if (slot.vanillaTexture()) {
                    // Direct vanilla texture override — no CIT .properties needed
                    String outPath = slot.vanillaOutputPath();
                    if (outPath == null || outPath.isBlank()) {
                        // Particles: keep original path inside the pack (e.g. textures/particle/spark_0.png)
                        outPath = opt.pngEntry().replace('\\', '/');
                        if (!outPath.startsWith("assets/"))
                            outPath = "assets/minecraft/textures/" + outPath.substring(outPath.indexOf("textures/") + 9);
                    }
                    putEntry(zos, outPath, png);
                } else {
                    TexFolder folder = effectiveFolder(si);
                    String citName = effectiveCitName(si);
                    String texName = slot.outputBaseName();
                    String pngPath = folder.assetPath + texName + ".png";
                    putEntry(zos, pngPath, png);

                    String propPath = "assets/minecraft/optifine/cit/" + texName + ".properties";
                    StringBuilder sb = new StringBuilder();
                    sb.append("type=item\n");
                    sb.append("items=").append(slot.primaryItem()).append("\n");
                    if (citName != null && !citName.isBlank())
                        sb.append("nbt.display.Name=ipattern:").append(citName).append("\n");
                    sb.append("texture=").append(folder.citPrefix).append("/").append(texName).append("\n");
                    putEntry(zos, propPath, sb.toString());
                }
            }
        }
        return baos.toByteArray();
    }

    private static byte[] readBytesFromPack(Path packPath, boolean isZip, String entry) throws IOException {
        if (isZip) {
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(packPath.toFile())) {
                ZipEntry ze = zf.getEntry(entry);
                if (ze == null) throw new IOException("Missing " + entry);
                try (InputStream in = zf.getInputStream(ze)) { return in.readAllBytes(); }
            }
        }
        return Files.readAllBytes(packPath.resolve(entry.replace('/', File.separatorChar)));
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

        ctx.method_25294(0, 0, field_22789, field_22790, BG);
        if (SlothyConfig.isBackgroundEffects())
            Ui.renderLeafParallax(ctx, field_22789, field_22790, delta);

        drawHeader(ctx);
        drawSlotList(ctx, mx, my, delta);
        drawConfigStrip(ctx, mx, my);
        drawSearchStrip(ctx);
        drawTexturePanel(ctx, mx, my, delta);
        drawFooter(ctx);

        for (class_364 w : method_25396()) {
            if (w instanceof class_4068 d) d.method_25394(ctx, mx, my, delta);
        }
    }

    private void drawHeader(class_332 ctx) {
        ctx.method_25294(0, 0, field_22789, HEADER, PANEL);
        ctx.method_25294(0, 0, field_22789, 2, ACCENT);
        ctx.method_25294(0, HEADER - 1, field_22789, HEADER, BORDER);

        DrawHelper.drawText(ctx, field_22793, "←", PAD, (HEADER - 9) / 2, MUTED, false);
        DrawHelper.drawText(ctx, field_22793, "TEXTURE BUILDER",
            PAD + 14, (HEADER - 9) / 2, ACCENT, false);

        int pnfW = 150;
        DrawHelper.drawText(ctx, field_22793, "Pack:",
            field_22789 / 2 - pnfW / 2 - field_22793.method_1727("Pack: ") - 2,
            (HEADER - 9) / 2, MUTED, false);

        long done = selections.values().stream().filter(v -> v != null && v >= 0).count();
        String info = done + "/" + SLOTS.length;
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
            boolean sel = i == selectedSlot;
            boolean hov = mx < LEFT_W && my >= y && my < y + ITEM_H && my > top + 18 && my < bot;
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
            DrawHelper.drawText(ctx, field_22793, SLOTS[i].emoji() + "  " + SLOTS[i].display(), PAD + 4, textY, fg, false);

            int cnt = discovered.getOrDefault(i, List.of()).size();
            DrawHelper.drawText(ctx, field_22793, scanning ? "…" : (cnt == 0 ? "—" : String.valueOf(cnt)),
                LEFT_W - PAD - 16, textY, cnt == 0 && !scanning ? DANGER : MUTED, false);
            if (selections.getOrDefault(i, -1) >= 0)
                DrawHelper.drawText(ctx, field_22793, "✓", LEFT_W - 10, textY, ACCENT, false);

            ctx.method_25294(PAD, y + ITEM_H - 1, LEFT_W - PAD, y + ITEM_H, col(BORDER & 0xFFFFFF, 80));
            y += ITEM_H;
        }
        ctx.method_44380();
    }

    private void drawConfigStrip(class_332 ctx, int mx, int my) {
        int top = HEADER;
        ctx.method_25294(LEFT_W, top, field_22789, top + CFG_H, PANEL);
        ctx.method_25294(LEFT_W, top + CFG_H - 1, field_22789, top + CFG_H, BORDER);

        SlotDef slot = SLOTS[selectedSlot];
        DrawHelper.drawText(ctx, field_22793, slot.emoji() + "  " + slot.display(),
            LEFT_W + PAD, top + 6, TEXT, false);

        if (slot.vanillaTexture()) {
            DrawHelper.drawText(ctx, field_22793, "From textures/ folder (not CIT)",
                LEFT_W + PAD, top + 26, MUTED, false);
        } else {
            DrawHelper.drawText(ctx, field_22793, "CIT Name",
                LEFT_W + PAD, top + 26, MUTED, false);
            DrawHelper.drawText(ctx, field_22793, "Folder",
                LEFT_W + PAD, top + 42, MUTED, false);
            int btnX = LEFT_W + PAD + field_22793.method_1727("Folder ") + 6;
            int btnY = top + 38;
            for (TexFolder f : TexFolder.values()) {
                boolean active = f == effectiveFolder(selectedSlot);
                int bw = field_22793.method_1727(f.label) + 10;
                ctx.method_25294(btnX, btnY, btnX + bw, btnY + 14,
                    active ? col(ACCENT & 0xFFFFFF, 220) : col(SURFACE & 0xFFFFFF, 160));
                DrawHelper.drawText(ctx, field_22793, f.label, btnX + 5, btnY + 3,
                    active ? BG : MUTED, false);
                btnX += bw + 6;
            }
        }
    }

    private void drawSearchStrip(class_332 ctx) {
        int top = HEADER + CFG_H;
        ctx.method_25294(LEFT_W, top, field_22789, top + SEARCH_H + 6, col(PANEL & 0xFFFFFF, 180));
        ctx.method_25294(LEFT_W, top + SEARCH_H + 5, field_22789, top + SEARCH_H + 6, BORDER);
        DrawHelper.drawText(ctx, field_22793, "Search", LEFT_W + PAD, top + 7, MUTED, false);
    }

    private List<TextureOption> filteredOptions(int slotIdx) {
        List<TextureOption> all = discovered.getOrDefault(slotIdx, List.of());
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return all;
        return all.stream().filter(o -> o.label().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void updateSlotWidgets() {
        SlotDef slot = SLOTS[selectedSlot];
        if (nameField != null) {
            int nfX = LEFT_W + PAD + field_22793.method_1727("CIT Name  ") + 2;
            int nfW = Math.min(220, field_22789 - nfX - PAD);
            if (slot.vanillaTexture()) {
                nameField.method_55444(nfX, -100, nfW, 12);
            } else {
                nameField.method_55444(nfX, HEADER + 24, nfW, 12);
                nameField.method_1867(defaultNameForSlot(selectedSlot));
            }
        }
    }

    private void drawTexturePanel(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER + CFG_H + SEARCH_H + 8;
        int bot = field_22790 - FOOTER, panX = LEFT_W;
        ctx.method_25294(panX, top, field_22789, bot, BG);

        if (scanning) {
            int cy = (top + bot) / 2;
            Ui.spinner(ctx, (field_22789 + LEFT_W) / 2, cy, 8,
                (float)(System.currentTimeMillis() % 1200L) / 1200f, col(ACCENT & 0xFFFFFF, 200));
            DrawHelper.drawText(ctx, field_22793, scanStatus,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(scanStatus) / 2, cy + 14, MUTED, false);
            return;
        }

        List<TextureOption> allOpts = discovered.getOrDefault(selectedSlot, List.of());
        List<TextureOption> opts = filteredOptions(selectedSlot);

        if (allOpts.isEmpty()) {
            String msg = "No textures found for this slot.";
            String hint = SLOTS[selectedSlot].vanillaTexture()
                ? "Looks in assets/minecraft/textures/ in your packs."
                : "Looks in optifine/cit/ in your packs.";
            int cy = (top + bot) / 2;
            DrawHelper.drawText(ctx, field_22793, msg,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(msg) / 2, cy - 5, MUTED, false);
            DrawHelper.drawText(ctx, field_22793, hint,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(hint) / 2, cy + 8,
                col(MUTED & 0xFFFFFF, 140), false);
            return;
        }

        if (opts.isEmpty()) {
            String msg = "No textures match \"" + searchQuery + "\"";
            int cy = (top + bot) / 2;
            DrawHelper.drawText(ctx, field_22793, msg,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(msg) / 2, cy, MUTED, false);
            return;
        }

        int TEX_H = 30, innerTop = top + 4;
        ctx.method_44379(panX, innerTop, field_22789, bot);
        int y = innerTop - (int) texScroll;
        int curSel = selections.getOrDefault(selectedSlot, -1);

        drawTexRow(ctx, panX, y, TEX_H, innerTop, bot, mx, my, curSel < 0,
            "✕  None (no override)", curSel < 0, DANGER);
        y += TEX_H;

        for (int i = 0; i < opts.size(); i++) {
            TextureOption opt = opts.get(i);
            int realIdx = allOpts.indexOf(opt);
            boolean sel = curSel == realIdx;
            boolean hov = mx >= panX && mx < field_22789 && my >= y && my < y + TEX_H && my > innerTop && my < bot;
            if (sel) {
                ctx.method_25294(panX, y, field_22789, y + TEX_H, col(ACCENT & 0xFFFFFF, 28));
                ctx.method_25294(panX, y, panX + 3, y + TEX_H, ACCENT);
            } else if (hov) {
                ctx.method_25294(panX, y, field_22789, y + TEX_H, col(SURFACE & 0xFFFFFF, 80));
            }
            if (opt.pngPreview() != null)
                ctx.method_25294(panX + PAD, y + 12, panX + PAD + 4, y + 16, col(ACCENT & 0xFFFFFF, sel ? 255 : 120));
            String display = opt.label().length() > 72
                ? "…" + opt.label().substring(opt.label().length() - 70) : opt.label();
            DrawHelper.drawText(ctx, field_22793, display, panX + PAD + 8, y + (TEX_H - 9) / 2,
                sel ? ACCENT : TEXT, false);
            ctx.method_25294(panX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }
        ctx.method_44380();
    }

    private void drawTexRow(class_332 ctx, int panX, int y, int h, int innerTop, int bot,
                             int mx, int my, boolean hov, String text, boolean sel, int selCol) {
        if (sel) ctx.method_25294(panX, y, field_22789, y + h, col(SURFACE & 0xFFFFFF, 140));
        else if (hov && mx >= panX && my >= y && my < y + h && my > innerTop && my < bot)
            ctx.method_25294(panX, y, field_22789, y + h, col(SURFACE & 0xFFFFFF, 60));
        if (sel) ctx.method_25294(panX, y, panX + 3, y + h, selCol);
        DrawHelper.drawText(ctx, field_22793, text, panX + PAD, y + (h - 9) / 2, sel ? selCol : MUTED, false);
        ctx.method_25294(panX + PAD, y + h - 1, field_22789 - PAD, y + h, col(BORDER & 0xFFFFFF, 80));
    }

    private void drawFooter(class_332 ctx) {
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, PANEL);
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790 - FOOTER + 1, BORDER);
        if (buildStatus != null) {
            DrawHelper.drawText(ctx, field_22793, buildStatus,
                field_22789 / 2 - field_22793.method_1727(buildStatus) / 2,
                field_22790 - FOOTER + 8, buildOk ? ACCENT : DANGER, false);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────

    @Override
    public boolean method_25402(double mx, double my, int button) {
        // Buttons + text fields first
        if (super.method_25402(mx, my, button)) return true;
        if (button != 0) return false;

        // Back arrow only (narrow hitbox)
        if (mx >= PAD && mx <= PAD + 12 && my >= 8 && my <= HEADER - 8) {
            method_25419(); return true;
        }

        int top = HEADER, bot = field_22790 - FOOTER;

        // Slot list
        if (mx < LEFT_W && my > top + 18 && my < bot) {
            int y = top + 18 - (int) itemScroll;
            for (int i = 0; i < SLOTS.length; i++) {
                if (my >= y && my < y + ITEM_H) {
                    selectedSlot = i;
                    texScroll = texScrollTarget = 0;
                    searchQuery = "";
                    if (searchField != null) searchField.method_1867("");
                    updateSlotWidgets();
                    return true;
                }
                y += ITEM_H;
            }
        }

        // Folder toggles (CIT slots only)
        if (!SLOTS[selectedSlot].vanillaTexture()) {
            int btnY = HEADER + 38;
            if (mx > LEFT_W && my >= btnY && my < btnY + 14) {
                int btnX = LEFT_W + PAD + field_22793.method_1727("Folder ") + 6;
                for (TexFolder f : TexFolder.values()) {
                    int bw = field_22793.method_1727(f.label) + 10;
                    if (mx >= btnX && mx < btnX + bw) {
                        folders.put(selectedSlot, f);
                        return true;
                    }
                    btnX += bw + 6;
                }
            }
        }

        // Texture list
        int texTop = HEADER + CFG_H + SEARCH_H + 8;
        if (mx >= LEFT_W && my >= texTop && my < bot && !scanning) {
            List<TextureOption> allOpts = discovered.getOrDefault(selectedSlot, List.of());
            List<TextureOption> opts = filteredOptions(selectedSlot);
            int TEX_H = 30;
            int y = texTop - (int) texScroll;
            if (my >= y && my < y + TEX_H) { selections.put(selectedSlot, -1); return true; }
            y += TEX_H;
            for (int i = 0; i < opts.size(); i++) {
                if (my >= y && my < y + TEX_H) {
                    selections.put(selectedSlot, allOpts.indexOf(opts.get(i)));
                    return true;
                }
                y += TEX_H;
            }
        }
        return false;
    }

    private boolean onScroll(double mx, double vDelta) {
        int top = HEADER, bot = field_22790 - FOOTER;
        if (mx < LEFT_W) {
            itemScrollTarget = clamp(itemScrollTarget - vDelta * 20, 0,
                Math.max(0, SLOTS.length * ITEM_H - (bot - top - 18)));
        } else {
            List<TextureOption> opts = filteredOptions(selectedSlot);
            int texTop = HEADER + CFG_H + SEARCH_H + 8;
            texScrollTarget = clamp(texScrollTarget - vDelta * 20, 0,
                Math.max(0, (opts.size() + 1) * 30 - (bot - texTop - 4)));
        }
        return true;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    public boolean method_25403(double mx, double my, double hd, double vd) { return onScroll(mx, vd); }
    public boolean method_25401(double mx, double my, double vd) { return onScroll(mx, vd); }

    @Override
    public boolean method_25404(int key, int scan, int mods) {
        if (key == 256) { method_25419(); return true; }
        return super.method_25404(key, scan, mods);
    }
}
