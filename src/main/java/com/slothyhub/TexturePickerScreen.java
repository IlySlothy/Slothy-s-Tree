package com.slothyhub;

import com.slothyhub.compat.DrawHelper;
import com.slothyhub.ui.CustomButton;
import com.slothyhub.ui.CustomButtonBase;
import com.slothyhub.ui.Ui;
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

import java.io.*;
import java.io.ByteArrayInputStream;
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

    enum ParticleKind { NONE, GOLDEN_CRIT, CRITICAL_HIT, ENCHANT_HIT }

    enum SlotCategory { SWORDS, GUI, PARTICLES, ITEMS }

    record SlotDef(String display, String primaryItem, String[] altItems,
                   String defaultCitName, String outputBaseName, String emoji,
                   boolean vanillaTexture, String vanillaOutputPath,
                   String[] exactPaths, String[] textureDirs, String[] textureKeywords,
                   ParticleKind particleKind, SlotCategory category, TexFolder defaultFolder) {}

    enum TexFolder {
        ITEM("item",      "assets/minecraft/textures/item/",      "item"),
        BLOCK("block",    "assets/minecraft/textures/block/",     "block"),
        PARTICLES("particles", "assets/minecraft/textures/particle/", "particle"),
        GUI("gui",        "assets/minecraft/textures/gui/",       "gui");

        final String label, assetPath, citPrefix;
        TexFolder(String label, String assetPath, String citPrefix) {
            this.label = label; this.assetPath = assetPath; this.citPrefix = citPrefix;
        }
    }

    private static final SlotDef[] SLOTS = {
        new SlotDef("Noob Sword",    "netherite_sword", new String[]{"diamond_sword"}, "Noob Sword",    "noob_sword",    "⚔", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM),
        new SlotDef("Good Sword",    "netherite_sword", new String[]{"diamond_sword"}, "Good Sword",    "good_sword",    "⚔", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM),
        new SlotDef("Pro Sword",     "netherite_sword", new String[]{"diamond_sword"}, "Pro Sword",     "pro_sword",     "⚔", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM),
        new SlotDef("Perfect Sword", "netherite_sword", new String[]{"diamond_sword"}, "Perfect Sword", "perfect_sword", "⚔", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM),
        new SlotDef("Hippo Sword",   "dead_tube_coral", new String[]{}, null, "dead_tube_coral", "🦛", true,
            "assets/minecraft/textures/block/dead_tube_coral.png",
            new String[]{"assets/minecraft/textures/block/dead_tube_coral.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.BLOCK),
        new SlotDef("Warden Sword",  "netherite_sword", new String[]{"diamond_sword"}, "Warden Sword",  "warden_sword",  "🌑", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM),
        new SlotDef("Golden Crit",   "potion", new String[]{}, null, "golden_crit",   "✨", true, "assets/minecraft/textures/particle/golden_crit.png", new String[]{}, new String[]{"assets/minecraft/textures/particle/"}, new String[]{"golden_crit"}, ParticleKind.GOLDEN_CRIT, SlotCategory.PARTICLES, TexFolder.PARTICLES),
        new SlotDef("Critical Hit",  "potion", new String[]{}, null, "critical_hit",  "💥", true, "assets/minecraft/textures/particle/critical_hit.png", new String[]{}, new String[]{"assets/minecraft/textures/particle/"}, new String[]{"critical_hit"}, ParticleKind.CRITICAL_HIT, SlotCategory.PARTICLES, TexFolder.PARTICLES),
        new SlotDef("Enchant Hit",   "potion", new String[]{}, null, "enchanted_hit", "✦", true, "assets/minecraft/textures/particle/enchanted_hit.png", new String[]{}, new String[]{"assets/minecraft/textures/particle/"}, new String[]{"enchanted_hit"}, ParticleKind.ENCHANT_HIT, SlotCategory.PARTICLES, TexFolder.PARTICLES),
        new SlotDef("Hotbar",        "potion", new String[]{}, null, "hotbar",        "▣", true, "assets/minecraft/textures/gui/sprites/hud/hotbar.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/hotbar.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Hotbar Select", "potion", new String[]{}, null, "hotbar_selection", "◈", true, "assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Offhand Left",  "potion", new String[]{}, null, "hotbar_offhand_left", "◧", true, "assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Offhand Right", "potion", new String[]{}, null, "hotbar_offhand_right", "◨", true, "assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_right.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_right.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Heart Full", "potion", new String[]{}, null, "heart_full", "♥", true,
            "assets/minecraft/textures/gui/sprites/hud/heart/full.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/heart/full.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Heart Half", "potion", new String[]{}, null, "heart_half", "♡", true,
            "assets/minecraft/textures/gui/sprites/hud/heart/half.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/heart/half.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Absorption Full", "potion", new String[]{}, null, "heart_absorption_full", "💛", true,
            "assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Absorption Half", "potion", new String[]{}, null, "heart_absorption_half", "💛", true,
            "assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Inventory",     "potion", new String[]{}, null, "inventory",     "🎒", true, "assets/minecraft/textures/gui/container/inventory.png",
            new String[]{"assets/minecraft/textures/gui/container/inventory.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI),
        new SlotDef("Fireworks",     "firework_rocket", new String[]{}, null, "fireworks",   "🎆", true, "assets/minecraft/textures/item/firework_rocket.png", new String[]{}, new String[]{"assets/minecraft/textures/item/"}, new String[]{"firework"}, ParticleKind.NONE, SlotCategory.ITEMS, TexFolder.ITEM),
        new SlotDef("Golden Apple",  "golden_apple", new String[]{}, null, "golden_apple", "🍎", true, "assets/minecraft/textures/item/golden_apple.png", new String[]{}, new String[]{"assets/minecraft/textures/item/"}, new String[]{"golden_apple"}, ParticleKind.NONE, SlotCategory.ITEMS, TexFolder.ITEM),
        new SlotDef("Offhands",      "cornflower", new String[]{}, null, "cornflower",  "🌸", true, "assets/minecraft/textures/item/cornflower.png", new String[]{}, new String[]{"assets/minecraft/textures/item/", "assets/minecraft/textures/block/"}, new String[]{"cornflower"}, ParticleKind.NONE, SlotCategory.ITEMS, TexFolder.ITEM),
    };

    private static final int GOLDEN_CRIT_SLOT = slotIndex(ParticleKind.GOLDEN_CRIT);
    private static final int CRITICAL_HIT_SLOT = slotIndex(ParticleKind.CRITICAL_HIT);
    private static final int HEART_FULL_SLOT = slotIndexByOutput("heart_full");
    private static final int HEART_HALF_SLOT = slotIndexByOutput("heart_half");
    private static final int ABSORPTION_FULL_SLOT = slotIndexByOutput("heart_absorption_full");
    private static final int ABSORPTION_HALF_SLOT = slotIndexByOutput("heart_absorption_half");
    private static final int HIPPO_SLOT = slotIndexByOutput("dead_tube_coral");
    private static final int[][] HEART_PAIRS = {
        { HEART_FULL_SLOT, HEART_HALF_SLOT },
        { ABSORPTION_FULL_SLOT, ABSORPTION_HALF_SLOT },
    };

    private static int slotIndex(ParticleKind kind) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i].particleKind() == kind) return i;
        }
        return -1;
    }

    private static int slotIndexByOutput(String outputBaseName) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (outputBaseName.equals(SLOTS[i].outputBaseName())) return i;
        }
        return -1;
    }

    /** Golden Crit and Critical Hit are mutually exclusive; heart full/half pairs stay in sync per pack. */
    private void setSlotSelection(int slotIdx, int textureIdx) {
        selections.put(slotIdx, textureIdx);
        if (textureIdx >= 0) {
            if (slotIdx == GOLDEN_CRIT_SLOT) selections.put(CRITICAL_HIT_SLOT, -1);
            else if (slotIdx == CRITICAL_HIT_SLOT) selections.put(GOLDEN_CRIT_SLOT, -1);
            syncHeartPair(slotIdx, textureIdx);
        } else {
            clearHeartPairPartner(slotIdx);
        }
    }

    private void syncHeartPair(int slotIdx, int textureIdx) {
        for (int[] pair : HEART_PAIRS) {
            int partner = partnerHeartSlot(slotIdx, pair);
            if (partner < 0) continue;

            List<TextureOption> opts = discovered.getOrDefault(slotIdx, List.of());
            if (textureIdx < 0 || textureIdx >= opts.size()) {
                selections.put(partner, -1);
                return;
            }
            int partnerIdx = findMatchingTextureIndex(partner, opts.get(textureIdx));
            selections.put(partner, partnerIdx);
            return;
        }
    }

    private void clearHeartPairPartner(int slotIdx) {
        for (int[] pair : HEART_PAIRS) {
            int partner = partnerHeartSlot(slotIdx, pair);
            if (partner >= 0) {
                selections.put(partner, -1);
                return;
            }
        }
    }

    private static int partnerHeartSlot(int slotIdx, int[] pair) {
        if (slotIdx == pair[0]) return pair[1];
        if (slotIdx == pair[1]) return pair[0];
        return -1;
    }

    private int findMatchingTextureIndex(int slotIdx, TextureOption source) {
        List<TextureOption> opts = discovered.getOrDefault(slotIdx, List.of());
        for (int i = 0; i < opts.size(); i++) {
            if (samePack(source, opts.get(i))) return i;
        }
        return -1;
    }

    private static boolean samePack(TextureOption a, TextureOption b) {
        return a.isZip() == b.isZip() && Objects.equals(a.packPath(), b.packPath());
    }

    private static final String DEFAULT_GOLDEN_CRIT_MCMETA =
        "{\n  \"animation\": {\n    \"frametime\": 2\n  }\n}\n";
    private static final String DEFAULT_CRIT_PARTICLE_JSON =
        "{\n  \"textures\": [\n    \"minecraft:golden_crit\"\n  ]\n}\n";

    private static final int HEADER  = 52;
    private static final int FOOTER  = 48;
    private static final int ITEM_H  = 38;
    private static final int LEFT_W  = 178;
    private static final int PAD     = 12;
    private static final int CFG_H   = 56;
    private static final int SEARCH_H = 22;

    private static final int CATEGORY_H = 22;

    private static final int BG      = Ui.COL_BG;
    private static final int PANEL   = Ui.COL_PANEL;
    private static final int SURFACE = Ui.COL_SURFACE;
    private static final int ACCENT  = Ui.COL_ACCENT;
    private static final int DANGER  = Ui.COL_DANGER;
    private static final int TEXT    = Ui.COL_TEXT;
    private static final int MUTED   = Ui.COL_MUTED;
    private static final int BORDER  = Ui.COL_BORDER;

    private static int configTop() { return HEADER + CATEGORY_H; }

    private static String categoryLabel(SlotCategory c) {
        return switch (c) {
            case SWORDS -> "Swords";
            case GUI -> "GUI";
            case PARTICLES -> "Potions";
            case ITEMS -> "Items";
        };
    }
    private static final int DLG_W = 280;
    private static final int DLG_H = 140;

    private int dialogX() { return field_22789 / 2 - DLG_W / 2; }
    private int dialogY() { return field_22790 / 2 - DLG_H / 2; }
    private static int col(int rgb, int a) { return Ui.withAlpha(rgb & 0xFFFFFF, a); }
    private static int lerp(int a, int b, float t) { return Ui.lerpColor(a, b, t); }

    /** One discoverable texture from a local pack. */
    record TextureOption(
        String label,
        Path   packPath,
        boolean isZip,
        String pngEntry,
        byte[] pngPreview,
        String mcmetaEntry,
        String particleJsonEntry
    ) {
        TextureOption(String label, Path packPath, boolean isZip, String pngEntry, byte[] pngPreview) {
            this(label, packPath, isZip, pngEntry, pngPreview, null, null);
        }
    }

    private final class_437 parent;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final Map<Integer, List<TextureOption>> discovered = new LinkedHashMap<>();
    private final Map<Integer, Integer> selections  = new LinkedHashMap<>();
    private final Map<Integer, String>  customNames = new HashMap<>();
    private boolean suppressNameListener = false;
    private final Map<Integer, TexFolder> folders   = new HashMap<>();

    private String packName = "My Custom Pack";
    private boolean scanning = true;
    private String scanStatus = "Scanning resource packs…";
    private String buildStatus = null;
    private boolean buildOk = false;
    private boolean nameDialogOpen = false;

    private int selectedSlot = 0;
    private double itemScroll = 0, itemScrollTarget = 0;
    private double texScroll = 0, texScrollTarget = 0;
    private final Map<Integer, Float> itemHover = new HashMap<>();
    private final Map<String, Float> texHover = new HashMap<>();

    private class_342 nameField;
    private class_342 searchField;
    private String packNameDraft = "My Custom Pack";
    private String searchQuery = "";
    private SlotCategory activeCategory = SlotCategory.SWORDS;
    private final Map<String, class_2960> texThumbs = new HashMap<>();

    public TexturePickerScreen(class_437 parent) {
        super(class_2561.method_43470("Texture Builder"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
        int nfX = LEFT_W + PAD + field_22793.method_1727("CIT Name  ") + 2;
        int nfW = Math.min(220, field_22789 - nfX - PAD);
        int citY = configTop() + 24;
        nameField = new class_342(field_22793, nfX, citY, nfW, 12,
            class_2561.method_43470("CIT name"));
        positionTextField(nameField, nfX, citY, nfW, 12);
        nameField.method_1858(false);
        nameField.method_1880(40);
        suppressNameListener = true;
        nameField.method_1867(defaultNameForSlot(0));
        suppressNameListener = false;
        nameField.method_47404(class_2561.method_43470("e.g. Pro Sword"));
        nameField.method_1868(TEXT);
        nameField.method_1863(v -> {
            if (suppressNameListener) return;
            customNames.put(selectedSlot, v.isBlank() ? null : v.trim());
        });
        method_37063(nameField);

        int searchTop = configTop() + CFG_H;
        int searchLabelW = field_22793.method_1727("Search") + 8;
        int searchW = field_22789 - LEFT_W - PAD * 2 - searchLabelW;
        searchField = new class_342(field_22793, LEFT_W + PAD + searchLabelW, searchTop + 4,
            Math.max(80, searchW), 12, class_2561.method_43470("Search"));
        positionTextField(searchField, LEFT_W + PAD + searchLabelW, searchTop + 4,
            Math.max(80, searchW), 12);
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
            class_2561.method_43470("BUILD & APPLY"), CustomButtonBase.Style.MOSS, this::promptBuild));
        method_37063(new CustomButton(bx + bw + gap, by, bw, bh,
            class_2561.method_43470("BACK"), CustomButtonBase.Style.SECONDARY, this::method_25419));

        executor.submit(this::scanResourcePacks);
        for (int i = 0; i < SLOTS.length; i++)
            folders.putIfAbsent(i, SLOTS[i].defaultFolder());
        updateSlotWidgets();

    }

    /** MC 1.21.8: {@code method_55444} updates different fields than {@code method_46426}/{@code method_46427} used when drawing. */
    private static void positionTextField(class_342 field, int x, int y, int w, int h) {
        field.method_46421(x);
        field.method_46419(y);
        field.method_55444(x, y, w, h);
    }

    private static void setTextFieldVisible(class_342 field, boolean visible) {
        if (field == null) return;
        field.field_22764 = visible;
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
        String def = SLOTS[idx].defaultCitName();
        if (def == null || def.isBlank()) return null;

        String custom = customNames.get(idx);
        if (custom != null && !custom.isBlank()) {
            String trimmed = custom.trim();
            if (trimmed.equalsIgnoreCase(def)) return def;
            if (!citNameLooksCorrupted(idx, trimmed)) return trimmed;
        }
        return def;
    }

    /** Detect names accidentally merged from multiple sword slots (shared text field bug). */
    private boolean citNameLooksCorrupted(int idx, String custom) {
        String lower = custom.toLowerCase(Locale.ROOT);
        String def = SLOTS[idx].defaultCitName();
        if (def != null && lower.contains(def.toLowerCase(Locale.ROOT)) && custom.length() > def.length() + 2) {
            return true;
        }
        int others = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            if (i == idx) continue;
            String other = SLOTS[i].defaultCitName();
            if (other == null || other.isBlank()) continue;
            if (lower.contains(other.toLowerCase(Locale.ROOT))) others++;
        }
        return others > 0;
    }

    /** Persist the shared CIT name field into the slot map before switching away. */
    private void saveNameFieldForSlot(int slotIdx) {
        if (nameField == null || slotIdx < 0 || slotIdx >= SLOTS.length) return;
        if (SLOTS[slotIdx].vanillaTexture()) return;
        String text = nameField.method_1882().trim();
        customNames.put(slotIdx, text.isBlank() ? null : text);
    }

    private TexFolder effectiveFolder(int idx) {
        return folders.getOrDefault(idx, SLOTS[idx].defaultFolder());
    }

    private List<Integer> visibleSlotIndices() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < SLOTS.length; i++)
            if (SLOTS[i].category() == activeCategory) out.add(i);
        return out;
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
                    if (BuiltPackLibrary.shouldSkipForScanner(pack)) continue;
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

        mergeSwordPoolIntoHippo(result);

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

            if (slot.exactPaths().length > 0) {
                boolean exact = false;
                for (String ep : slot.exactPaths()) {
                    if (lower.equals(ep.toLowerCase(Locale.ROOT))) { exact = true; break; }
                }
                if (!exact) continue;
            } else {
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
            }

            String label = packLabel + " / " + entry.substring(entry.indexOf("textures/") + 9);
            String mcmeta = entry.replace(".png", ".png.mcmeta");
            String particleJson = null;
            if (slot.particleKind() == ParticleKind.GOLDEN_CRIT)
                particleJson = "assets/minecraft/particles/crit.json";
            TextureOption opt = new TextureOption(label, packPath, isZip, entry, preview, mcmeta, particleJson);
            List<TextureOption> list = out.get(si);
            if (list.stream().noneMatch(o -> o.label().equals(label))) list.add(opt);
        }
    }

    private static boolean packHasEntry(Path packPath, boolean isZip, String entry) {
        if (entry == null || entry.isBlank()) return false;
        try {
            if (isZip) {
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(packPath.toFile())) {
                    return zf.getEntry(entry.replace('\\', '/')) != null;
                }
            }
            return Files.exists(packPath.resolve(entry.replace('/', File.separatorChar)));
        } catch (Exception e) { return false; }
    }

    private static byte[] readOptionalFromPack(Path packPath, boolean isZip, String entry) {
        if (!packHasEntry(packPath, isZip, entry)) return null;
        try { return readBytesFromPack(packPath, isZip, entry); } catch (Exception e) { return null; }
    }

    private void writeGoldenCritBundle(ZipOutputStream zos, TextureOption opt, byte[] png) throws IOException {
        putEntry(zos, "assets/minecraft/textures/particle/golden_crit.png", png);

        byte[] mcmeta = readOptionalFromPack(opt.packPath(), opt.isZip(), opt.mcmetaEntry());
        if (mcmeta != null)
            putEntry(zos, "assets/minecraft/textures/particle/golden_crit.png.mcmeta", mcmeta);
        else
            putEntry(zos, "assets/minecraft/textures/particle/golden_crit.png.mcmeta", DEFAULT_GOLDEN_CRIT_MCMETA);

        byte[] critJson = readOptionalFromPack(opt.packPath(), opt.isZip(), opt.particleJsonEntry());
        if (critJson != null)
            putEntry(zos, "assets/minecraft/particles/crit.json", critJson);
        else
            putEntry(zos, "assets/minecraft/particles/crit.json", DEFAULT_CRIT_PARTICLE_JSON);
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
            scanSwordPngsZip(zf, packLabel, zip, out);
        } catch (Exception ignored) {}
    }

    /** Pick up sword PNGs in cit/swords/ even when not tied to a matching .properties name rule. */
    private void scanSwordPngsZip(java.util.zip.ZipFile zf, String packLabel, Path packPath,
                                   Map<Integer, List<TextureOption>> out) {
        Enumeration<? extends ZipEntry> all = zf.entries();
        while (all.hasMoreElements()) {
            ZipEntry ze = all.nextElement();
            if (ze.isDirectory()) continue;
            String p = ze.getName().replace('\\', '/');
            String lower = p.toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".png") || !lower.contains("/cit/")) continue;
            if (!isSwordCitAssetPath(lower)) continue;
            try (InputStream in = zf.getInputStream(ze)) {
                addSwordCitOption(packLabel, packPath, true, p, in.readAllBytes(), out);
            } catch (Exception ignored) {}
        }
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
            scanSwordPngsFolder(folder, packLabel, out);
        } catch (Exception ignored) {}
    }

    private void scanSwordPngsFolder(Path folder, String packLabel, Map<Integer, List<TextureOption>> out) {
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path pf : walk.filter(f -> !Files.isDirectory(f)).toList()) {
                String rel = folder.relativize(pf).toString().replace('\\', '/');
                String lower = rel.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".png") || !lower.contains("/cit/")) continue;
                if (!isSwordCitAssetPath(lower)) continue;
                try {
                    addSwordCitOption(packLabel, folder, false, rel, Files.readAllBytes(pf), out);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /** Sword CIT PNGs only — excludes armor layers and other non-sword CIT folders. */
    private static boolean isSwordCitAssetPath(String lowerPath) {
        if (!lowerPath.contains("/cit/")) return false;
        if (lowerPath.contains("_armor") || lowerPath.contains("_layer_")
            || lowerPath.contains("/armor/") || lowerPath.contains("/cit/") && lowerPath.contains("_armor/"))
            return false;
        if (lowerPath.contains("/cit/swords/")) return true;
        if (lowerPath.contains("sword")) return true;
        return false;
    }

    private static boolean isSwordCitSlot(SlotDef slot) {
        return slot.category() == SlotCategory.SWORDS && !slot.vanillaTexture();
    }

    /** Hippo uses block override but shares the sword CIT texture picker pool. */
    private static boolean isHippoSlot(SlotDef slot) {
        return "dead_tube_coral".equals(slot.outputBaseName());
    }

    private static boolean sharesSwordCitPool(SlotDef slot) {
        return isSwordCitSlot(slot) || isHippoSlot(slot);
    }

    /** After scan, ensure hippo tab lists every sword CIT texture found in other sword slots. */
    private static void mergeSwordPoolIntoHippo(Map<Integer, List<TextureOption>> result) {
        if (HIPPO_SLOT < 0) return;
        List<TextureOption> hippoList = result.get(HIPPO_SLOT);
        for (int si = 0; si < SLOTS.length; si++) {
            if (si == HIPPO_SLOT || !isSwordCitSlot(SLOTS[si])) continue;
            for (TextureOption opt : result.getOrDefault(si, List.of())) {
                if (hippoList.stream().noneMatch(o ->
                    o.label().equals(opt.label()) && Objects.equals(o.pngEntry(), opt.pngEntry())))
                    hippoList.add(opt);
            }
        }
    }

    private void addSwordCitOption(String packLabel, Path packPath, boolean isZip,
                                    String pngEntry, byte[] preview,
                                    Map<Integer, List<TextureOption>> out) {
        String fileName = pngEntry.substring(pngEntry.lastIndexOf('/') + 1).replace(".png", "");
        String label = packLabel + " / " + fileName;
        TextureOption opt = new TextureOption(label, packPath, isZip, pngEntry, preview);
        for (int si = 0; si < SLOTS.length; si++) {
            if (!sharesSwordCitPool(SLOTS[si])) continue;
            List<TextureOption> list = out.get(si);
            if (list.stream().noneMatch(o -> o.label().equals(label) && o.pngEntry().equals(pngEntry)))
                list.add(opt);
        }
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
            if ("dead_tube_coral".equals(slot.outputBaseName())) {
                if (!isSwordCitSource(propPath, texProp, items)) continue;
            } else if (slot.vanillaTexture()) {
                continue;
            } else if (isSwordCitSlot(slot)) {
                if (!itemMatchesSlot(items, slot) && !isSwordCitSource(propPath, texProp, items)) continue;
            } else {
                if (!itemMatchesSlot(items, slot)) continue;
                if (slot.defaultCitName() != null && !nameMatches(cleanName, slot.defaultCitName())) continue;
            }

            List<TextureOption> list = out.get(si);
            if (list.stream().noneMatch(o -> o.label().equals(label)))
                list.add(opt);
        }
    }

    /** Any CIT sword texture (good, pro, warden, etc.) can fill any sword slot picker, including hippo. */
    private static boolean isSwordCitSource(String propPath, String texProp, Set<String> items) {
        if (items.contains("netherite_sword") || items.contains("minecraft:netherite_sword")) return true;
        if (items.contains("diamond_sword") || items.contains("minecraft:diamond_sword")) return true;
        return isSwordCitAssetPath((propPath + " " + texProp).toLowerCase(Locale.ROOT));
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

    private void promptBuild() {
        if (scanning) return;
        long chosen = selections.values().stream().filter(v -> v != null && v >= 0).count();
        if (chosen == 0) { buildStatus = "Select at least one texture first."; buildOk = false; return; }
        nameDialogOpen = true;
        buildStatus = null;
        packNameDraft = packName;
        hideBackgroundFields();
    }

    private void hideBackgroundFields() {
        setTextFieldVisible(nameField, false);
        setTextFieldVisible(searchField, false);
    }

    private void restoreBackgroundFields() {
        if (searchField != null) {
            int searchTop = configTop() + CFG_H;
            int searchLabelW = field_22793.method_1727("Search") + 8;
            int searchW = field_22789 - LEFT_W - PAD * 2 - searchLabelW;
            positionTextField(searchField, LEFT_W + PAD + searchLabelW, searchTop + 4,
                Math.max(80, searchW), 12);
            setTextFieldVisible(searchField, true);
        }
        updateSlotWidgets();
    }

    private void closeNameDialog() {
        nameDialogOpen = false;
        restoreBackgroundFields();
    }

    private void confirmBuild() {
        packName = packNameDraft.isBlank() ? "My Custom Pack" : packNameDraft.trim();
        closeNameDialog();
        buildAndApply();
    }

    private void buildAndApply() {
        if (scanning) return;
        long chosen = selections.values().stream().filter(v -> v != null && v >= 0).count();
        if (chosen == 0) { buildStatus = "Select at least one texture first."; buildOk = false; return; }

        buildStatus = "Building…"; buildOk = false;
        saveNameFieldForSlot(selectedSlot);
        executor.submit(() -> {
            try {
                byte[] zipBytes = buildPack();
                String safe = BuiltPackLibrary.sanitizeName(packName);
                final String libName = safe;
                PackDownloader.applyBuiltPack(zipBytes, packName);
                class_310.method_1551().execute(() -> {
                    buildStatus = "Saved to resourcepacks/" + libName + ".zip + library";
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
                PackMetaUtil.buildMcmetaBytes(packName, 34));
            putEntry(zos, BuiltPackLibrary.BUILT_MARKER, "slothyhub-texture-builder\n");

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
                    if (slot.particleKind() == ParticleKind.GOLDEN_CRIT) {
                        writeGoldenCritBundle(zos, opt, png);
                    } else {
                        String outPath = slot.vanillaOutputPath();
                        putEntry(zos, outPath, png);
                        copyMcmetaIfPresent(zos, opt, outPath);
                    }
                } else {
                    writeCitBundle(zos, slot, opt, png, si);
                }
            }
        }
        return baos.toByteArray();
    }

    private void writeCitBundle(ZipOutputStream zos, SlotDef slot, TextureOption opt, byte[] png, int si) throws IOException {
        String texName = slot.outputBaseName();
        TexFolder folder = effectiveFolder(si);
        String citFolder = slot.category() == SlotCategory.SWORDS
            ? "assets/minecraft/optifine/cit/swords/"
            : "assets/minecraft/optifine/cit/slothyhub_" + texName + "/";
        String citBase = slot.category() == SlotCategory.SWORDS ? texName : texName;
        putEntry(zos, citFolder + citBase + ".png", png);
        copyMcmetaIfPresent(zos, opt, citFolder + citBase + ".png");

        String citName = effectiveCitName(si);
        StringBuilder sb = new StringBuilder();
        sb.append("type=item\n");
        sb.append("items=").append(slot.primaryItem()).append("\n");
        if (citName != null && !citName.isBlank())
            sb.append("nbt.display.Name=ipattern:").append(citName.trim()).append("\n");
        sb.append("texture=item/").append(texName).append("\n");
        putEntry(zos, citFolder + citBase + ".properties", sb.toString());

        String vanillaPath = folder.assetPath + texName + ".png";
        putEntry(zos, vanillaPath, png);
        copyMcmetaIfPresent(zos, opt, vanillaPath);
    }

    private void copyMcmetaIfPresent(ZipOutputStream zos, TextureOption opt, String pngAssetPath) throws IOException {
        String mcmetaPath = pngAssetPath + ".mcmeta";
        if (opt.mcmetaEntry() != null && !opt.mcmetaEntry().isBlank()) {
            byte[] mcmeta = readOptionalFromPack(opt.packPath(), opt.isZip(), opt.mcmetaEntry());
            if (mcmeta != null) { putEntry(zos, mcmetaPath, mcmeta); return; }
        }
        String derived = opt.pngEntry() != null ? opt.pngEntry().replace(".png", ".png.mcmeta") : null;
        if (derived != null) {
            byte[] mcmeta = readOptionalFromPack(opt.packPath(), opt.isZip(), derived);
            if (mcmeta != null) putEntry(zos, mcmetaPath, mcmeta);
        }
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
        Ui.drawCornerVines(ctx, field_22789, field_22790,
            (float)(System.currentTimeMillis() % 5000L) / 5000f);

        drawHeader(ctx);
        drawCategoryTabs(ctx, mx, my);
        drawSlotList(ctx, mx, my, delta);
        drawConfigStrip(ctx, mx, my);
        drawSearchStrip(ctx);
        drawTexturePanel(ctx, mx, my, delta);
        drawFooter(ctx);

        if (nameDialogOpen) {
            ctx.method_25294(0, 0, field_22789, field_22790, col(0x000000, 140));
            drawNameDialogPanel(ctx);
            drawNameDialogButtons(ctx, mx, my);
            return;
        }

        for (class_364 w : method_25396()) {
            if (w instanceof class_4068 d) d.method_25394(ctx, mx, my, delta);
        }
    }

    private void drawNameDialogPanel(class_332 ctx) {
        int dx = dialogX(), dy = dialogY();
        ctx.method_25294(dx, dy, dx + DLG_W, dy + DLG_H, PANEL);
        ctx.method_25294(dx, dy, dx + DLG_W, dy + 2, ACCENT);
        Ui.drawPawPrint(ctx, dx + 20, dy + 30, col(ACCENT & 0xFFFFFF, 180), 0.8f);
        DrawHelper.drawText(ctx, field_22793, "Name your pack", dx + 36, dy + 22, TEXT, false);
        DrawHelper.drawText(ctx, field_22793, "Pack name", dx + 16, dy + 46, MUTED, false);
        ctx.method_25294(dx + 14, dy + 54, dx + DLG_W - 14, dy + 72, col(SURFACE & 0xFFFFFF, 180));
        ctx.method_25294(dx + 14, dy + 71, dx + DLG_W - 14, dy + 72, ACCENT);
        int inputX = dx + 18, inputY = dy + 58;
        if (packNameDraft.isBlank()) {
            DrawHelper.drawText(ctx, field_22793, "My Custom Pack", inputX, inputY,
                col(MUTED & 0xFFFFFF, 140), false);
        } else {
            DrawHelper.drawText(ctx, field_22793, packNameDraft, inputX, inputY, TEXT, false);
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = inputX + field_22793.method_1727(packNameDraft);
                ctx.method_25294(cx, inputY - 1, cx + 1, inputY + 10, ACCENT);
            }
        }
        DrawHelper.drawText(ctx, field_22793, "Enter then click BUILD or press Enter", dx + 16, dy + 78, MUTED, false);
    }

    private void drawNameDialogButtons(class_332 ctx, int mx, int my) {
        int dx = dialogX(), dy = dialogY();
        int btnW = 72, btnH = 20, btnY = dy + DLG_H - 28;
        int buildX = dx + DLG_W / 2 - btnW - 6;
        int cancelX = dx + DLG_W / 2 + 6;
        boolean buildHov = mx >= buildX && mx <= buildX + btnW && my >= btnY && my <= btnY + btnH;
        boolean cancelHov = mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + btnH;
        ctx.method_25294(buildX, btnY, buildX + btnW, btnY + btnH,
            buildHov ? col(ACCENT & 0xFFFFFF, 230) : col(SURFACE & 0xFFFFFF, 200));
        ctx.method_25294(cancelX, btnY, cancelX + btnW, btnY + btnH,
            cancelHov ? col(DANGER & 0xFFFFFF, 200) : col(SURFACE & 0xFFFFFF, 160));
        DrawHelper.drawText(ctx, field_22793, "BUILD", buildX + 18, btnY + 6, buildHov ? BG : ACCENT, false);
        DrawHelper.drawText(ctx, field_22793, "CANCEL", cancelX + 12, btnY + 6, cancelHov ? BG : MUTED, false);
    }

    private void drawCategoryTabs(class_332 ctx, int mx, int my) {
        int top = HEADER, y = top + 2, x = PAD;
        for (SlotCategory c : SlotCategory.values()) {
            String label = categoryLabel(c);
            int w = field_22793.method_1727(label) + 14;
            boolean sel = c == activeCategory;
            boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + CATEGORY_H - 2;
            ctx.method_25294(x, y, x + w, y + CATEGORY_H - 2,
                sel ? col(ACCENT & 0xFFFFFF, 40) : (hov ? col(SURFACE & 0xFFFFFF, 120) : col(PANEL & 0xFFFFFF, 80)));
            DrawHelper.drawText(ctx, field_22793, label, x + 7, y + 5, sel ? ACCENT : MUTED, false);
            x += w + 10;
        }
        ctx.method_25294(0, top + CATEGORY_H - 1, LEFT_W, top + CATEGORY_H, BORDER);
    }

    private String texThumbKey(TextureOption opt) {
        return opt.packPath() + "|" + opt.pngEntry();
    }

    private void ensureTexThumb(TextureOption opt) {
        String key = texThumbKey(opt);
        if (texThumbs.containsKey(key)) return;
        byte[] png = opt.pngPreview();
        if (png == null || png.length == 0) return;
        try {
            class_1011 img = class_1011.method_4309(new ByteArrayInputStream(png));
            class_1043 tex = DrawHelper.createNativeTexture("slothyhub_tex_" + Math.abs(key.hashCode()), img);
            if (tex == null) return;
            class_2960 id = class_2960.method_60655("slothyhub", "texthumb/" + Math.abs(key.hashCode()));
            class_310.method_1551().method_1531().method_4616(id, tex);
            texThumbs.put(key, id);
        } catch (Exception ignored) {}
    }

    private void drawHeader(class_332 ctx) {
        float phase = (float)(System.currentTimeMillis() % 4000L) / 4000f;
        ctx.method_25294(0, 0, field_22789, HEADER, PANEL);
        ctx.method_25294(0, 0, field_22789, 2, ACCENT);
        ctx.method_25294(0, HEADER - 1, field_22789, HEADER, BORDER);

        DrawHelper.drawText(ctx, field_22793, "←", PAD, (HEADER - 9) / 2, MUTED, false);
        Ui.drawSlothBadge(ctx, field_22793, PAD + 14, (HEADER - 14) / 2, phase);
        DrawHelper.drawText(ctx, field_22793, "TEXTURE BUILDER",
            PAD + 38, (HEADER - 9) / 2, ACCENT, false);

        long done = selections.values().stream().filter(v -> v != null && v >= 0).count();
        String info = done + "/" + SLOTS.length + " slots";
        DrawHelper.drawText(ctx, field_22793, info,
            field_22789 - PAD - field_22793.method_1727(info), (HEADER - 9) / 2, MUTED, false);
    }

    private void drawSlotList(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER + CATEGORY_H, bot = field_22790 - FOOTER;
        ctx.method_25294(0, top, LEFT_W, bot, PANEL);
        ctx.method_25294(LEFT_W - 1, top, LEFT_W, bot, BORDER);
        DrawHelper.drawText(ctx, field_22793, categoryLabel(activeCategory), PAD, top + 6, MUTED, false);

        List<Integer> visible = visibleSlotIndices();
        ctx.method_44379(0, top + 18, LEFT_W, bot);
        int y = top + 18 - (int) itemScroll;
        for (int i : visible) {
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

        int listTop = top + 18, listH = bot - listTop;
        int totalH = visible.size() * ITEM_H;
        if (totalH > listH) {
            int trkX = LEFT_W - 5, trkY = listTop + 4, trkH = listH - 8;
            int thumbH = Math.max(20, trkH * listH / totalH);
            int thumbY = trkY + (int)((double)(trkH - thumbH) * itemScroll / Math.max(1, totalH - listH));
            ctx.method_25294(trkX, trkY, trkX + 3, trkY + trkH, col(BORDER & 0xFFFFFF, 100));
            ctx.method_25294(trkX, thumbY, trkX + 3, thumbY + thumbH, col(ACCENT & 0xFFFFFF, 200));
            float phase = (float)(System.currentTimeMillis() % 2000L) / 2000f;
            if (itemScroll > 1) Ui.scrollIndicator(ctx, LEFT_W / 2, listTop + 2, true, phase, ACCENT);
            if (itemScroll < totalH - listH - 1)
                Ui.scrollIndicator(ctx, LEFT_W / 2, bot - 10, false, phase, ACCENT);
        }
    }

    private void drawConfigStrip(class_332 ctx, int mx, int my) {
        int top = configTop();
        ctx.method_25294(LEFT_W, top, field_22789, top + CFG_H, PANEL);
        ctx.method_25294(LEFT_W, top + CFG_H - 1, field_22789, top + CFG_H, BORDER);

        SlotDef slot = SLOTS[selectedSlot];
        DrawHelper.drawText(ctx, field_22793, slot.emoji() + "  " + slot.display(),
            LEFT_W + PAD, top + 6, TEXT, false);

        if (slot.vanillaTexture()) {
            String hint = switch (slot.particleKind()) {
                case GOLDEN_CRIT -> "Either Golden Crit or Critical Hit — includes .mcmeta + particles/crit.json";
                case CRITICAL_HIT -> "Either Golden Crit or Critical Hit — from textures/particle/";
                case ENCHANT_HIT -> "Optional — from textures/particle/ in your packs.";
                default -> isHippoSlot(slot)
                    ? "Pick any sword CIT texture — outputs textures/block/dead_tube_coral.png."
                    : "From textures/ folder in your packs.";
            };
            DrawHelper.drawText(ctx, field_22793, hint, LEFT_W + PAD, top + 26, MUTED, false);
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
        int top = configTop() + CFG_H;
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
            if (nameDialogOpen || slot.vanillaTexture()) {
                setTextFieldVisible(nameField, false);
            } else {
                positionTextField(nameField, nfX, configTop() + 24, nfW, 12);
                setTextFieldVisible(nameField, true);
                suppressNameListener = true;
                nameField.method_1867(defaultNameForSlot(selectedSlot));
                suppressNameListener = false;
            }
        }
    }

    private void drawTexturePanel(class_332 ctx, int mx, int my, float delta) {
        int top = configTop() + CFG_H + SEARCH_H + 8;
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
            String hint = isHippoSlot(SLOTS[selectedSlot])
                ? "Shows sword CIT textures from optifine/cit/ in your packs."
                : SLOTS[selectedSlot].vanillaTexture()
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
            ensureTexThumb(opt);
            class_2960 tid = texThumbs.get(texThumbKey(opt));
            if (tid != null)
                DrawHelper.drawTexture(ctx, tid, panX + PAD, y + 3, 0f, 0f, 24, 24, 24, 24);
            else if (opt.pngPreview() != null)
                ctx.method_25294(panX + PAD, y + 7, panX + PAD + 16, y + 23, col(ACCENT & 0xFFFFFF, sel ? 255 : 120));
            String display = opt.label().length() > 64
                ? "…" + opt.label().substring(opt.label().length() - 62) : opt.label();
            DrawHelper.drawText(ctx, field_22793, display, panX + PAD + 30, y + (TEX_H - 9) / 2,
                sel ? ACCENT : TEXT, false);
            ctx.method_25294(panX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }
        ctx.method_44380();

        int listH = bot - innerTop;
        int totalH = (opts.size() + 1) * TEX_H;
        if (totalH > listH) {
            int trkX = field_22789 - 6, trkY = innerTop + 4, trkH = listH - 8;
            int thumbH = Math.max(20, trkH * listH / totalH);
            int thumbY = trkY + (int)((double)(trkH - thumbH) * texScroll / Math.max(1, totalH - listH));
            ctx.method_25294(trkX, trkY, trkX + 3, trkY + trkH, col(BORDER & 0xFFFFFF, 100));
            ctx.method_25294(trkX, thumbY, trkX + 3, thumbY + thumbH, col(ACCENT & 0xFFFFFF, 200));
            float phase = (float)(System.currentTimeMillis() % 2000L) / 2000f;
            if (texScroll > 1) Ui.scrollIndicator(ctx, panX + (field_22789 - panX) / 2, innerTop + 2, true, phase, ACCENT);
            if (texScroll < totalH - listH - 1)
                Ui.scrollIndicator(ctx, panX + (field_22789 - panX) / 2, bot - 10, false, phase, ACCENT);
        }
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
        if (nameDialogOpen && button == 0) {
            int dx = dialogX(), dy = dialogY();
            int btnW = 72, btnH = 20, btnY = dy + DLG_H - 28;
            int buildX = dx + DLG_W / 2 - btnW - 6;
            int cancelX = dx + DLG_W / 2 + 6;
            if (mx >= buildX && mx <= buildX + btnW && my >= btnY && my <= btnY + btnH) {
                confirmBuild(); return true;
            }
            if (mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + btnH) {
                closeNameDialog(); return true;
            }
            if (mx >= dx && mx <= dx + DLG_W && my >= dy && my <= dy + DLG_H) {
                return true;
            }
            closeNameDialog(); return true;
        }

        // Buttons + text fields first
        if (super.method_25402(mx, my, button)) return true;
        if (button != 0) return false;

        // Back arrow only (narrow hitbox)
        if (mx >= PAD && mx <= PAD + 12 && my >= 8 && my <= HEADER - 8) {
            method_25419(); return true;
        }

        int top = HEADER + CATEGORY_H, bot = field_22790 - FOOTER;

        if (mx < LEFT_W && my >= HEADER && my < HEADER + CATEGORY_H) {
            int tabX = PAD;
            for (SlotCategory c : SlotCategory.values()) {
                String label = categoryLabel(c);
                int w = field_22793.method_1727(label) + 14;
                if (mx >= tabX && mx <= tabX + w) {
                    activeCategory = c;
                    itemScroll = itemScrollTarget = 0;
                    List<Integer> vis = visibleSlotIndices();
                    if (!vis.isEmpty() && SLOTS[selectedSlot].category() != c) {
                        saveNameFieldForSlot(selectedSlot);
                        selectedSlot = vis.get(0);
                    }
                    updateSlotWidgets();
                    return true;
                }
                tabX += w + 10;
            }
        }

        // Slot list
        if (mx < LEFT_W && my > top + 18 && my < bot) {
            int y = top + 18 - (int) itemScroll;
            for (int i : visibleSlotIndices()) {
                if (my >= y && my < y + ITEM_H) {
                    if (i != selectedSlot) saveNameFieldForSlot(selectedSlot);
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
            int btnY = configTop() + 38;
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
        int texTop = configTop() + CFG_H + SEARCH_H + 8;
        if (mx >= LEFT_W && my >= texTop && my < bot && !scanning) {
            List<TextureOption> allOpts = discovered.getOrDefault(selectedSlot, List.of());
            List<TextureOption> opts = filteredOptions(selectedSlot);
            int TEX_H = 30;
            int y = texTop - (int) texScroll;
            if (my >= y && my < y + TEX_H) { setSlotSelection(selectedSlot, -1); return true; }
            y += TEX_H;
            for (int i = 0; i < opts.size(); i++) {
                if (my >= y && my < y + TEX_H) {
                    setSlotSelection(selectedSlot, allOpts.indexOf(opts.get(i)));
                    return true;
                }
                y += TEX_H;
            }
        }
        return false;
    }

    private boolean onScroll(double mx, double vDelta) {
        if (nameDialogOpen) return true;
        int top = HEADER + CATEGORY_H, bot = field_22790 - FOOTER;
        if (mx < LEFT_W) {
            int visCount = visibleSlotIndices().size();
            itemScrollTarget = clamp(itemScrollTarget - vDelta * 24, 0,
                Math.max(0, visCount * ITEM_H - (bot - top - 18)));
        } else {
            List<TextureOption> opts = filteredOptions(selectedSlot);
            int texTop = configTop() + CFG_H + SEARCH_H + 8;
            texScrollTarget = clamp(texScrollTarget - vDelta * 24, 0,
                Math.max(0, (opts.size() + 1) * 30 - (bot - texTop - 4)));
        }
        return true;
    }

    public boolean onScrollDelta(double mx, double vDelta) {
        return onScroll(mx, vDelta);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    public boolean method_25403(double mx, double my, double hd, double vd) { return onScroll(mx, vd); }
    public boolean method_25401(double mx, double my, double vd) { return onScroll(field_22789 / 2.0, vd); }

    @Override
    public boolean method_25400(char chr, int modifiers) {
        if (nameDialogOpen) {
            if (chr >= 32 && chr != 127 && packNameDraft.length() < 48)
                packNameDraft += chr;
            return true;
        }
        return super.method_25400(chr, modifiers);
    }

    @Override
    public boolean method_25404(int key, int scan, int mods) {
        if (nameDialogOpen) {
            if (key == 256) { closeNameDialog(); return true; }
            if (key == 257) { confirmBuild(); return true; }
            if (key == 259 && !packNameDraft.isEmpty()) {
                packNameDraft = packNameDraft.substring(0, packNameDraft.length() - 1);
                return true;
            }
            return true;
        }
        if (key == 256) { method_25419(); return true; }
        return super.method_25404(key, scan, mods);
    }
}
