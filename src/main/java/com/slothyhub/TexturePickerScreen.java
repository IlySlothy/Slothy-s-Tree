package com.slothyhub;

import com.slothyhub.cit.TextureAnimationUtil;
import com.slothyhub.builder.ResourceScanHelper;
import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.Identifiers;
import com.slothyhub.compat.InputCompat;
import com.slothyhub.local.LocalPackManager;
import com.slothyhub.kill.KillEffectAssets;
import com.slothyhub.ui.CustomButton;
import com.slothyhub.ui.CustomButtonBase;
import com.slothyhub.ui.Ui;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_3300;
import net.minecraft.class_310;
import net.minecraft.class_332;
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

    enum SlotCategory { SWORDS, GUI, PARTICLES, ITEMS, KILL_FX }

    record SlotDef(String display, String primaryItem, String[] altItems,
                   String defaultCitName, String outputBaseName, String emoji,
                   boolean vanillaTexture, String vanillaOutputPath,
                   String[] exactPaths, String[] textureDirs, String[] textureKeywords,
                   ParticleKind particleKind, SlotCategory category, TexFolder defaultFolder,
                   String soundOutputBase) {}

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
        new SlotDef("Noob Sword",    "netherite_sword", new String[]{"diamond_sword"}, "Noob Sword",    "noob_sword",    "⚔", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM, null),
        new SlotDef("Good Sword",    "netherite_sword", new String[]{"diamond_sword"}, "Good Sword",    "good_sword",    "⚔", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM, null),
        new SlotDef("Pro Sword",     "netherite_sword", new String[]{"diamond_sword"}, "Pro Sword",     "pro_sword",     "⚔", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM, null),
        new SlotDef("Perfect Sword", "netherite_sword", new String[]{"diamond_sword"}, "Perfect Sword", "perfect_sword", "⚔", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM, null),
        new SlotDef("Hippo Sword",   "dead_tube_coral", new String[]{}, null, "dead_tube_coral", "🦛", true,
            "assets/minecraft/textures/block/dead_tube_coral.png",
            new String[]{"assets/minecraft/textures/block/dead_tube_coral.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.BLOCK, null),
        new SlotDef("Warden Sword",  "netherite_sword", new String[]{"diamond_sword"}, "Warden Sword",  "warden_sword",  "🌑", false, null, new String[]{}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.SWORDS, TexFolder.ITEM, null),
        new SlotDef("Golden Crit",   "potion", new String[]{}, null, "golden_crit",   "✨", true, "assets/minecraft/textures/particle/golden_crit.png", new String[]{}, new String[]{"assets/minecraft/textures/particle/"}, new String[]{"golden_crit"}, ParticleKind.GOLDEN_CRIT, SlotCategory.PARTICLES, TexFolder.PARTICLES, null),
        new SlotDef("Critical Hit",  "potion", new String[]{}, null, "critical_hit",  "💥", true, "assets/minecraft/textures/particle/critical_hit.png", new String[]{}, new String[]{"assets/minecraft/textures/particle/"}, new String[]{"critical_hit"}, ParticleKind.CRITICAL_HIT, SlotCategory.PARTICLES, TexFolder.PARTICLES, null),
        new SlotDef("Enchant Hit",   "potion", new String[]{}, null, "enchanted_hit", "✦", true, "assets/minecraft/textures/particle/enchanted_hit.png", new String[]{}, new String[]{"assets/minecraft/textures/particle/"}, new String[]{"enchanted_hit"}, ParticleKind.ENCHANT_HIT, SlotCategory.PARTICLES, TexFolder.PARTICLES, null),
        new SlotDef("Hotbar",        "potion", new String[]{}, null, "hotbar",        "▣", true, "assets/minecraft/textures/gui/sprites/hud/hotbar.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/hotbar.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Hotbar Select", "potion", new String[]{}, null, "hotbar_selection", "◈", true, "assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/hotbar_selection.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Offhand Left",  "potion", new String[]{}, null, "hotbar_offhand_left", "◧", true, "assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_left.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Offhand Right", "potion", new String[]{}, null, "hotbar_offhand_right", "◨", true, "assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_right.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/hotbar_offhand_right.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Heart Full", "potion", new String[]{}, null, "heart_full", "♥", true,
            "assets/minecraft/textures/gui/sprites/hud/heart/full.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/heart/full.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Heart Half", "potion", new String[]{}, null, "heart_half", "♡", true,
            "assets/minecraft/textures/gui/sprites/hud/heart/half.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/heart/half.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Absorption Full", "potion", new String[]{}, null, "heart_absorption_full", "💛", true,
            "assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/heart/absorbing_full.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Absorption Half", "potion", new String[]{}, null, "heart_absorption_half", "💛", true,
            "assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png",
            new String[]{"assets/minecraft/textures/gui/sprites/hud/heart/absorbing_half.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Inventory",     "potion", new String[]{}, null, "inventory",     "🎒", true, "assets/minecraft/textures/gui/container/inventory.png",
            new String[]{"assets/minecraft/textures/gui/container/inventory.png"}, new String[]{}, new String[]{}, ParticleKind.NONE, SlotCategory.GUI, TexFolder.GUI, null),
        new SlotDef("Fireworks",     "firework_rocket", new String[]{}, null, "fireworks",   "🎆", true, "assets/minecraft/textures/item/firework_rocket.png",
            new String[]{"assets/minecraft/textures/item/firework_rocket.png"}, new String[]{"assets/minecraft/textures/item/"}, new String[]{"firework_rocket"}, ParticleKind.NONE, SlotCategory.ITEMS, TexFolder.ITEM, null),
        new SlotDef("Golden Apple",  "golden_apple", new String[]{}, null, "golden_apple", "🍎", true, "assets/minecraft/textures/item/golden_apple.png", new String[]{}, new String[]{"assets/minecraft/textures/item/"}, new String[]{"golden_apple"}, ParticleKind.NONE, SlotCategory.ITEMS, TexFolder.ITEM, null),
        new SlotDef("Netherite Sword", "netherite_sword", new String[]{}, null, "netherite_sword", "⚔", true,
            "assets/minecraft/textures/item/netherite_sword.png",
            new String[]{"assets/minecraft/textures/item/netherite_sword.png"},
            new String[]{"assets/minecraft/textures/item/"}, new String[]{"netherite_sword", "sword"},
            ParticleKind.NONE, SlotCategory.ITEMS, TexFolder.ITEM, null),
        new SlotDef("Offhands",      "cornflower", new String[]{}, null, "cornflower",  "🌸", true, "assets/minecraft/textures/item/cornflower.png", new String[]{}, new String[]{"assets/minecraft/textures/item/", "assets/minecraft/textures/block/"}, new String[]{"cornflower"}, ParticleKind.NONE, SlotCategory.ITEMS, TexFolder.ITEM, null),
        new SlotDef("Totem of Undying", "totem_of_undying", new String[]{}, null, "totem_of_undying", "♣", true,
            "assets/minecraft/textures/item/totem_of_undying.png",
            new String[]{"assets/minecraft/textures/item/totem_of_undying.png"},
            new String[]{"assets/minecraft/textures/item/"}, new String[]{"totem"},
            ParticleKind.NONE, SlotCategory.KILL_FX, TexFolder.ITEM, null),
        new SlotDef("Totem Pop Sound", "totem_of_undying", new String[]{}, null, "totem_kill_sound", "🔊", false, null,
            new String[]{}, new String[]{"assets/minecraft/sounds/"}, new String[]{"totem", "use_totem"},
            ParticleKind.NONE, SlotCategory.KILL_FX, TexFolder.ITEM, "kill/totem_pop"),
    };

    private static final int GOLDEN_CRIT_SLOT = slotIndex(ParticleKind.GOLDEN_CRIT);
    private static final int CRITICAL_HIT_SLOT = slotIndex(ParticleKind.CRITICAL_HIT);
    private static final int HEART_FULL_SLOT = slotIndexByOutput("heart_full");
    private static final int HEART_HALF_SLOT = slotIndexByOutput("heart_half");
    private static final int ABSORPTION_FULL_SLOT = slotIndexByOutput("heart_absorption_full");
    private static final int ABSORPTION_HALF_SLOT = slotIndexByOutput("heart_absorption_half");
    private static final int HIPPO_SLOT = slotIndexByOutput("dead_tube_coral");
    private static final int NETHERITE_SWORD_SLOT = slotIndexByOutput("netherite_sword");
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
        int prev = selections.getOrDefault(slotIdx, -1);
        selections.put(slotIdx, textureIdx);
        if (textureIdx >= 0 && textureIdx != prev) Ui.playClick();
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

    private static int configTop() { return HEADER + CATEGORY_H; }

    private static String categoryLabel(SlotCategory c) {
        return switch (c) {
            case SWORDS -> "Swords";
            case GUI -> "GUI";
            case PARTICLES -> "Potions";
            case ITEMS -> "Items";
            case KILL_FX -> "Kill FX";
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
    private final Map<Integer, Float> slotCheckAnim = new HashMap<>();

    private String citNameEdit = "";
    private boolean citNameFocused = false;
    private int citBarX, citBarY, citBarW, citBarH;
    private String packNameDraft = "My Custom Pack";
    private String searchQuery = "";
    private boolean searchFocused = false;
    private int searchBarX, searchBarY, searchBarW, searchBarH;
    private SlotCategory activeCategory = SlotCategory.SWORDS;
    private final Map<String, class_2960> texThumbs = new HashMap<>();
    private final Map<String, int[]> texThumbSizes = new HashMap<>();
    private final InputCompat.Poller inputPoller = new InputCompat.Poller();

    public TexturePickerScreen(class_437 parent) {
        super(class_2561.method_43470("Texture Builder"));
        this.parent = parent;
    }

    @Override
    protected void method_25426() {
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

    /** Persist the CIT name edit into the slot map before switching away. */
    private void commitCitNameEdit(int slotIdx) {
        if (slotIdx < 0 || slotIdx >= SLOTS.length) return;
        if (SLOTS[slotIdx].vanillaTexture()) return;
        String text = (citNameFocused ? citNameEdit : defaultNameForSlot(slotIdx)).trim();
        customNames.put(slotIdx, text.isBlank() ? null : text);
        citNameFocused = false;
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

        int packCount = 0;
        try {
            List<Path> packs = ResourceScanHelper.collectPackRoots(rpDir, LocalPackManager.getLocalPackDir());
            packCount = packs.size();
            int done = 0;
            for (Path pack : packs) {
                if (BuiltPackLibrary.shouldSkipForScanner(pack)) continue;
                int d = ++done;
                final int total = packCount;
                class_310.method_1551().execute(() ->
                    scanStatus = "Scanning " + pack.getFileName() + " (" + d + "/" + total + ")");
                if (Files.isDirectory(pack)) scanFolder(pack, result);
                else                         scanZip(pack, result);
                scanVanillaTextures(pack, Files.isDirectory(pack), packLabelFrom(pack), result);
                scanSounds(pack, Files.isDirectory(pack), packLabelFrom(pack), result);
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("TexturePicker scan error: {}", e.getMessage());
        }

        mergeSharedSwordPool(result);

        class_310 mc = class_310.method_1551();
        java.util.concurrent.CountDownLatch loaded = new java.util.concurrent.CountDownLatch(1);
        mc.execute(() -> {
            try {
                scanStatus = "Scanning active resource packs…";
                int added = scanLoadedResourcePacks(result);
                SlothyHubMod.LOGGER.info("TexturePicker: loaded-resource scan added {} options", added);
            } catch (Exception e) {
                SlothyHubMod.LOGGER.warn("TexturePicker loaded-resource scan error: {}", e.getMessage());
            } finally {
                loaded.countDown();
            }
        });
        try { loaded.await(20, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        mergeSharedSwordPool(result);
        int totalOpts = result.values().stream().mapToInt(List::size).sum();
        final int finalPackCount = packCount;
        final int finalTotalOpts = totalOpts;

        mc.execute(() -> {
            discovered.clear();
            discovered.putAll(result);
            scanning = false;
            if (finalTotalOpts == 0) {
                scanStatus = finalPackCount == 0
                    ? "No packs found — add packs to resourcepacks/ or slothyhub-local/"
                    : "No matching textures in " + finalPackCount + " pack(s)";
            }
            SlothyHubMod.LOGGER.info("TexturePicker: {} options across {} slots ({} pack roots)",
                finalTotalOpts, SLOTS.length, finalPackCount);
        });
    }

    /** Scan textures from packs currently loaded in-game (includes server resource packs). */
    private int scanLoadedResourcePacks(Map<Integer, List<TextureOption>> result) {
        class_3300 manager = ResourceScanHelper.resourceManager();
        if (manager == null) return 0;

        int added = 0;
        String packLabel = "Active packs";
        StreamOpener opener = path -> ResourceScanHelper.openPath(manager, path);

        for (String namespace : ResourceScanHelper.namespaces(manager)) {
            java.util.Map<class_2960, ?> props = ResourceScanHelper.findResources(manager, namespace, id -> {
                String path = id.method_12832();
                return path.endsWith(".properties") && path.contains("cit/");
            });
            for (var entry : props.entrySet()) {
                class_2960 rid = entry.getKey();
                try (InputStream in = ResourceScanHelper.openMapEntry(manager, rid, entry.getValue())) {
                    if (in == null) continue;
                    String propPath = "assets/" + rid.method_12836() + "/" + rid.method_12832();
                    int before = countOptions(result);
                    parseCitEntry(packLabel, null, false, propPath, entryDir(propPath),
                        loadProps(in), opener, result);
                    added += countOptions(result) - before;
                } catch (Exception ignored) {}
            }
        }

        java.util.Map<class_2960, ?> pngs = ResourceScanHelper.findResources(manager, "minecraft", id -> {
            String path = id.method_12832().toLowerCase(Locale.ROOT);
            if (!path.endsWith(".png")) return false;
            if (path.contains("/cit/")) return false;
            return path.startsWith("textures/item/")
                || path.startsWith("textures/block/")
                || path.startsWith("textures/particle/")
                || path.startsWith("textures/gui/");
        });
        for (var entry : pngs.entrySet()) {
            class_2960 rid = entry.getKey();
            String entryPath = "assets/minecraft/" + rid.method_12832();
            try (InputStream in = ResourceScanHelper.openMapEntry(manager, rid, entry.getValue())) {
                if (in == null) continue;
                int before = countOptions(result);
                addVanillaTextureOption(packLabel, null, false, entryPath, in.readAllBytes(), result);
                added += countOptions(result) - before;
            } catch (Exception ignored) {}
        }

        java.util.Map<class_2960, ?> oggs = ResourceScanHelper.findResources(manager, "minecraft", id ->
            id.method_12832().endsWith(".ogg") && id.method_12832().startsWith("sounds/"));
        for (var entry : oggs.entrySet()) {
            class_2960 rid = entry.getKey();
            String entryPath = "assets/minecraft/" + rid.method_12832();
            try (InputStream in = ResourceScanHelper.openMapEntry(manager, rid, entry.getValue())) {
                if (in == null) continue;
                int before = countOptions(result);
                addSoundOption(packLabel, null, false, entryPath, in.readAllBytes(), result);
                added += countOptions(result) - before;
            } catch (Exception ignored) {}
        }

        return added;
    }

    private static int countOptions(Map<Integer, List<TextureOption>> result) {
        return result.values().stream().mapToInt(List::size).sum();
    }

    @FunctionalInterface
    private interface StreamOpener { InputStream open(String path) throws IOException; }

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
            if (slot.soundOutputBase() != null) continue;
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
                        String kwLower = kw.toLowerCase(Locale.ROOT);
                        if (fileName.toLowerCase(Locale.ROOT).equals(kwLower)
                            || fileName.toLowerCase(Locale.ROOT).contains(kwLower)) {
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

    private void scanSounds(Path packPath, boolean isFolder, String packLabel,
                            Map<Integer, List<TextureOption>> out) {
        if (isFolder) scanSoundsFolder(packPath, packLabel, out);
        else scanSoundsZip(packPath, packLabel, out);
    }

    private void scanSoundsZip(Path zip, String packLabel, Map<Integer, List<TextureOption>> out) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> all = zf.entries();
            while (all.hasMoreElements()) {
                ZipEntry ze = all.nextElement();
                if (ze.isDirectory()) continue;
                String entry = ze.getName().replace('\\', '/');
                if (!entry.toLowerCase(Locale.ROOT).endsWith(".ogg")) continue;
                if (!entry.contains("assets/minecraft/sounds/")) continue;
                byte[] data;
                try (InputStream in = zf.getInputStream(ze)) { data = in.readAllBytes(); }
                catch (Exception e) { data = new byte[0]; }
                addSoundOption(packLabel, zip, true, entry, data, out);
            }
        } catch (Exception ignored) {}
    }

    private void scanSoundsFolder(Path folder, String packLabel, Map<Integer, List<TextureOption>> out) {
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path f : walk.filter(p -> !Files.isDirectory(p)).toList()) {
                String rel = folder.relativize(f).toString().replace('\\', '/');
                if (!rel.toLowerCase(Locale.ROOT).endsWith(".ogg")) continue;
                if (!rel.contains("assets/minecraft/sounds/")) continue;
                byte[] data = new byte[0];
                try { data = Files.readAllBytes(f); } catch (Exception ignored) {}
                addSoundOption(packLabel, folder, false, rel, data, out);
            }
        } catch (Exception ignored) {}
    }

    private void addSoundOption(String packLabel, Path packPath, boolean isZip,
                                String entry, byte[] data,
                                Map<Integer, List<TextureOption>> out) {
        String lower = entry.toLowerCase(Locale.ROOT);
        String fileName = entry.substring(entry.lastIndexOf('/') + 1).replace(".ogg", "");

        for (int si = 0; si < SLOTS.length; si++) {
            SlotDef slot = SLOTS[si];
            if (slot.soundOutputBase() == null) continue;

            boolean dirMatch = false;
            for (String dir : slot.textureDirs()) {
                if (lower.contains(dir.toLowerCase(Locale.ROOT))) { dirMatch = true; break; }
            }
            if (!dirMatch) continue;

            if (slot.textureKeywords().length > 0) {
                boolean kwMatch = false;
                for (String kw : slot.textureKeywords()) {
                    if (fileName.toLowerCase(Locale.ROOT).contains(kw.toLowerCase(Locale.ROOT))
                        || lower.contains(kw.toLowerCase(Locale.ROOT))) {
                        kwMatch = true; break;
                    }
                }
                if (!kwMatch) continue;
            }

            String label = packLabel + " / " + entry.substring(entry.indexOf("sounds/") + 7);
            TextureOption opt = new TextureOption(label, packPath, isZip, entry, data);
            List<TextureOption> list = out.get(si);
            if (list.stream().noneMatch(o -> o.label().equals(label))) list.add(opt);
        }
    }

    private static boolean packHasEntry(Path packPath, boolean isZip, String entry) {
        if (packPath == null || entry == null || entry.isBlank()) return false;
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

    private static boolean isNetheriteVanillaSwordSlot(SlotDef slot) {
        return slot.category() == SlotCategory.ITEMS
            && "netherite_sword".equals(slot.outputBaseName())
            && slot.vanillaTexture();
    }

    private static boolean sharesSwordCitPool(SlotDef slot) {
        return isSwordCitSlot(slot) || isHippoSlot(slot) || isNetheriteVanillaSwordSlot(slot);
    }

    /** After scan, bidirectionally merge textures across all sword CIT pool slots. */
    private static void mergeSharedSwordPool(Map<Integer, List<TextureOption>> result) {
        List<Integer> poolSlots = new ArrayList<>();
        for (int si = 0; si < SLOTS.length; si++) {
            if (sharesSwordCitPool(SLOTS[si])) poolSlots.add(si);
        }
        if (poolSlots.size() < 2) return;

        for (int dest : poolSlots) {
            List<TextureOption> destList = result.get(dest);
            for (int src : poolSlots) {
                if (src == dest) continue;
                for (TextureOption opt : result.getOrDefault(src, List.of())) {
                    if (destList.stream().noneMatch(o ->
                        o.label().equals(opt.label()) && Objects.equals(o.pngEntry(), opt.pngEntry())))
                        destList.add(opt);
                }
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

    /**
     * Last-line defense before writing {@code nbt.display.Name=ipattern:...} into a
     * generated .properties file. Strips ipattern: prefixes the user may have typed,
     * collapses exact repetitions (e.g. "Noob SwordNoob Sword" → "Noob Sword"), and
     * falls back to the slot's default name when the value still contains the default
     * embedded multiple times.
     */
    private static String sanitizeWrittenCitName(String raw, String defaultName) {
        if (raw == null) return null;
        String s = stripNamePrefix(raw).trim();
        if (s.isEmpty()) return defaultName == null ? null : defaultName.trim();

        String collapsed = collapseExactRepetition(s);
        if (!collapsed.equals(s)) s = collapsed;

        if (defaultName != null && !defaultName.isBlank()) {
            String defLower = defaultName.toLowerCase(Locale.ROOT).trim();
            String sLower = s.toLowerCase(Locale.ROOT);
            int count = 0;
            int idx = 0;
            while ((idx = sLower.indexOf(defLower, idx)) >= 0) { count++; idx += defLower.length(); }
            if (count >= 2) s = defaultName.trim();
        }
        return s;
    }

    /** Mirror of CitRuleParser.reduceExactRepetition for write-side use. */
    private static String collapseExactRepetition(String s) {
        if (s == null) return null;
        int len = s.length();
        if (len < 4) return s;
        for (int k = 1; k <= len / 2; k++) {
            if (len % k != 0) continue;
            String head = s.substring(0, k);
            boolean repeats = true;
            for (int i = k; i < len; i += k) {
                if (!s.regionMatches(i, head, 0, k)) { repeats = false; break; }
            }
            if (repeats) return head;
        }
        return s;
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
        commitCitNameEdit(selectedSlot);
        searchFocused = false;
    }

    private void restoreBackgroundFields() {
        citNameFocused = false;
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
        commitCitNameEdit(selectedSlot);
        executor.submit(() -> {
            try {
                byte[] zipBytes = buildPack();
                String safe = BuiltPackLibrary.sanitizeName(packName);
                final String libName = safe;
                PackDownloader.applyBuiltPack(zipBytes, packName);
                class_310.method_1551().execute(() -> {
                    buildStatus = "Saved to resourcepacks/" + libName + ".zip + library";
                    buildOk = true;
                    KillEffectAssets.invalidate();
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
                PackMetaUtil.buildCompatibleMcmetaBytes(packName));
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
                        if (slot.category() == SlotCategory.KILL_FX && outPath.contains("totem_of_undying")) {
                            SlothyConfig.setKillTotemTexture("minecraft:item/totem_of_undying");
                        }
                    }
                } else if (slot.soundOutputBase() != null) {
                    writeTotemSoundBundle(zos, slot, opt, png);
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

        String citName = sanitizeWrittenCitName(effectiveCitName(si), slot.defaultCitName());
        StringBuilder sb = new StringBuilder();
        sb.append("type=item\n");
        sb.append("items=").append(slot.primaryItem()).append("\n");
        if (citName != null && !citName.isBlank())
            sb.append("nbt.display.Name=ipattern:").append(citName).append("\n");
        sb.append("texture=item/").append(texName).append("\n");
        putEntry(zos, citFolder + citBase + ".properties", sb.toString());

        String vanillaPath = folder.assetPath + texName + ".png";
        putEntry(zos, vanillaPath, png);
        copyMcmetaIfPresent(zos, opt, vanillaPath);
    }

    private void writeTotemSoundBundle(ZipOutputStream zos, SlotDef slot, TextureOption opt, byte[] ogg) throws IOException {
        String base = slot.soundOutputBase();
        putEntry(zos, "assets/minecraft/sounds/" + base + ".ogg", ogg);
        String soundsJson = "{\n  \"item.totem.use\": {\n    \"sounds\": [ \"" + base + "\" ]\n  }\n}\n";
        putEntry(zos, "assets/minecraft/sounds.json", soundsJson);
        SlothyConfig.setKillTotemSound("minecraft:item.totem.use");
    }

    private void copyMcmetaIfPresent(ZipOutputStream zos, TextureOption opt, String pngAssetPath) throws IOException {
        String mcmetaPath = pngAssetPath + ".mcmeta";
        byte[] mcmeta = null;
        if (opt.mcmetaEntry() != null && !opt.mcmetaEntry().isBlank()) {
            mcmeta = readOptionalFromPack(opt.packPath(), opt.isZip(), opt.mcmetaEntry());
        }
        if (mcmeta == null) {
            String derived = opt.pngEntry() != null ? opt.pngEntry().replace(".png", ".png.mcmeta") : null;
            if (derived != null) mcmeta = readOptionalFromPack(opt.packPath(), opt.isZip(), derived);
        }
        if (mcmeta == null && opt.pngEntry() != null) {
            try {
                byte[] png = readBytesFromPack(opt.packPath(), opt.isZip(), opt.pngEntry());
                class_1011 img = class_1011.method_4309(new java.io.ByteArrayInputStream(png));
                mcmeta = TextureAnimationUtil.ensureMcmeta(null, img.method_4307(), img.method_4323());
            } catch (Exception ignored) {}
        } else if (mcmeta != null && opt.pngEntry() != null) {
            try {
                byte[] png = readBytesFromPack(opt.packPath(), opt.isZip(), opt.pngEntry());
                class_1011 img = class_1011.method_4309(new java.io.ByteArrayInputStream(png));
                mcmeta = TextureAnimationUtil.ensureMcmeta(mcmeta, img.method_4307(), img.method_4323());
            } catch (Exception ignored) {}
        }
        if (mcmeta != null) putEntry(zos, mcmetaPath, mcmeta);
    }

    private static byte[] readBytesFromPack(Path packPath, boolean isZip, String entry) throws IOException {
        if (packPath == null) throw new IOException("No pack path for " + entry);
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

        ctx.method_25294(0, 0, field_22789, field_22790, Ui.COL_BG);
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

        inputPoller.poll(mx, my, (x, y, btn) -> method_25402(x, y, btn), null);

        if (nameDialogOpen) {
            ctx.method_25294(0, 0, field_22789, field_22790, col(0x000000, 140));
            drawNameDialogPanel(ctx);
            drawNameDialogButtons(ctx, mx, my);
            return;
        }

        for (class_364 w : method_25396()) {
            if (w instanceof class_4068 d) d.method_25394(ctx, mx, my, delta);
        }
        Ui.renderSelectionParticles(ctx, delta);
    }

    private void drawNameDialogPanel(class_332 ctx) {
        int dx = dialogX(), dy = dialogY();
        ctx.method_25294(dx, dy, dx + DLG_W, dy + DLG_H, Ui.COL_PANEL);
        ctx.method_25294(dx, dy, dx + DLG_W, dy + 2, Ui.COL_ACCENT);
        Ui.drawPawPrint(ctx, dx + 20, dy + 30, col(Ui.COL_ACCENT & 0xFFFFFF, 180), 0.8f);
        DrawHelper.drawText(ctx, field_22793, "Name your pack", dx + 36, dy + 22, Ui.COL_TEXT, false);
        DrawHelper.drawText(ctx, field_22793, "Pack name", dx + 16, dy + 46, Ui.COL_MUTED, false);
        ctx.method_25294(dx + 14, dy + 54, dx + DLG_W - 14, dy + 72, col(Ui.COL_SURFACE & 0xFFFFFF, 180));
        ctx.method_25294(dx + 14, dy + 71, dx + DLG_W - 14, dy + 72, Ui.COL_ACCENT);
        int inputX = dx + 18, inputY = dy + 58;
        if (packNameDraft.isBlank()) {
            DrawHelper.drawText(ctx, field_22793, "My Custom Pack", inputX, inputY,
                col(Ui.COL_MUTED & 0xFFFFFF, 140), false);
        } else {
            DrawHelper.drawText(ctx, field_22793, packNameDraft, inputX, inputY, Ui.COL_TEXT, false);
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                int cx = inputX + field_22793.method_1727(packNameDraft);
                ctx.method_25294(cx, inputY - 1, cx + 1, inputY + 10, Ui.COL_ACCENT);
            }
        }
        DrawHelper.drawText(ctx, field_22793, "Enter then click BUILD or press Enter", dx + 16, dy + 78, Ui.COL_MUTED, false);
    }

    private void drawNameDialogButtons(class_332 ctx, int mx, int my) {
        int dx = dialogX(), dy = dialogY();
        int btnW = 72, btnH = 20, btnY = dy + DLG_H - 28;
        int buildX = dx + DLG_W / 2 - btnW - 6;
        int cancelX = dx + DLG_W / 2 + 6;
        boolean buildHov = mx >= buildX && mx <= buildX + btnW && my >= btnY && my <= btnY + btnH;
        boolean cancelHov = mx >= cancelX && mx <= cancelX + btnW && my >= btnY && my <= btnY + btnH;
        ctx.method_25294(buildX, btnY, buildX + btnW, btnY + btnH,
            buildHov ? col(Ui.COL_ACCENT & 0xFFFFFF, 230) : col(Ui.COL_SURFACE & 0xFFFFFF, 200));
        ctx.method_25294(cancelX, btnY, cancelX + btnW, btnY + btnH,
            cancelHov ? col(Ui.COL_DANGER & 0xFFFFFF, 200) : col(Ui.COL_SURFACE & 0xFFFFFF, 160));
        DrawHelper.drawText(ctx, field_22793, "BUILD", buildX + 18, btnY + 6, buildHov ? Ui.COL_BG : Ui.COL_ACCENT, false);
        DrawHelper.drawText(ctx, field_22793, "CANCEL", cancelX + 12, btnY + 6, cancelHov ? Ui.COL_BG : Ui.COL_MUTED, false);
    }

    private void drawCategoryTabs(class_332 ctx, int mx, int my) {
        int top = HEADER, y = top + 2, x = PAD;
        for (SlotCategory c : SlotCategory.values()) {
            String label = categoryLabel(c);
            int w = field_22793.method_1727(label) + 14;
            boolean sel = c == activeCategory;
            boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + CATEGORY_H - 2;
            ctx.method_25294(x, y, x + w, y + CATEGORY_H - 2,
                sel ? col(Ui.COL_ACCENT & 0xFFFFFF, 40) : (hov ? col(Ui.COL_SURFACE & 0xFFFFFF, 120) : col(Ui.COL_PANEL & 0xFFFFFF, 80)));
            DrawHelper.drawText(ctx, field_22793, label, x + 7, y + 5, sel ? Ui.COL_ACCENT : Ui.COL_MUTED, false);
            x += w + 10;
        }
        ctx.method_25294(0, top + CATEGORY_H - 1, field_22789, top + CATEGORY_H, Ui.COL_BORDER);
    }

    private boolean handleCategoryTabClick(double mx, double my) {
        int top = HEADER, y = top + 2;
        if (my < y || my > y + CATEGORY_H - 2) return false;
        int tabX = PAD;
        for (SlotCategory c : SlotCategory.values()) {
            String label = categoryLabel(c);
            int w = field_22793.method_1727(label) + 14;
            if (mx >= tabX && mx <= tabX + w) {
                activeCategory = c;
                itemScroll = itemScrollTarget = 0;
                List<Integer> vis = visibleSlotIndices();
                if (!vis.isEmpty() && SLOTS[selectedSlot].category() != c) {
                    commitCitNameEdit(selectedSlot);
                    selectedSlot = vis.get(0);
                }
                return true;
            }
            tabX += w + 10;
        }
        return false;
    }

    private String texThumbKey(TextureOption opt) {
        return opt.packPath() + "|" + opt.pngEntry();
    }

    private void ensureTexThumb(TextureOption opt) {
        String key = texThumbKey(opt);
        if (texThumbs.containsKey(key)) return;
        byte[] png = opt.pngPreview();
        if (png == null || png.length == 0) return;
        if (!PackIconLoader.isValidPng(png)) return;
        try {
            class_1011 img = class_1011.method_4309(new ByteArrayInputStream(png));
            img = TextureAnimationUtil.firstFrameFromImage(img, null);
            if (img == null) return;
            int texW = img.method_4307();
            int texH = img.method_4323();
            class_1043 tex = DrawHelper.createNativeTexture("slothyhub_tex_" + Math.abs(key.hashCode()), img);
            if (tex == null) return;
            class_2960 id = Identifiers.of("slothyhub", "texthumb/" + Math.abs(key.hashCode()));
            DrawHelper.registerDynamicTexture(id, tex, img);
            texThumbs.put(key, id);
            texThumbSizes.put(key, new int[]{texW, texH});
        } catch (Exception ignored) {}
    }

    private void drawHeader(class_332 ctx) {
        float phase = (float)(System.currentTimeMillis() % 4000L) / 4000f;
        ctx.method_25294(0, 0, field_22789, HEADER, Ui.COL_PANEL);
        ctx.method_25294(0, 0, field_22789, 2, Ui.COL_ACCENT);
        ctx.method_25294(0, HEADER - 1, field_22789, HEADER, Ui.COL_BORDER);

        DrawHelper.drawText(ctx, field_22793, "←", PAD + 22, (HEADER - 9) / 2, Ui.COL_MUTED, false);
        DrawHelper.drawText(ctx, field_22793, "TEXTURE BUILDER",
            PAD + 34, (HEADER - 9) / 2, Ui.COL_ACCENT, false);
        Ui.drawSlothLogo(ctx, PAD, (HEADER - 16) / 2, phase);
        DrawHelper.flushDraw(ctx);

        long done = selections.values().stream().filter(v -> v != null && v >= 0).count();
        String info = done + "/" + SLOTS.length + " slots";
        DrawHelper.drawText(ctx, field_22793, info,
            field_22789 - PAD - field_22793.method_1727(info), (HEADER - 9) / 2, Ui.COL_MUTED, false);
    }

    private void drawSlotList(class_332 ctx, int mx, int my, float delta) {
        int top = HEADER + CATEGORY_H, bot = field_22790 - FOOTER;
        ctx.method_25294(0, top, LEFT_W, bot, Ui.COL_PANEL);
        ctx.method_25294(LEFT_W - 1, top, LEFT_W, bot, Ui.COL_BORDER);
        DrawHelper.drawText(ctx, field_22793, categoryLabel(activeCategory), PAD, top + 6, Ui.COL_MUTED, false);

        List<Integer> visible = visibleSlotIndices();
        DrawHelper.flushDraw(ctx);
        ctx.method_44379(0, top + 18, LEFT_W, bot);
        int y = top + 18 - (int) itemScroll;
        for (int i : visible) {
            boolean sel = i == selectedSlot;
            boolean hov = mx < LEFT_W && my >= y && my < y + ITEM_H && my > top + 18 && my < bot;
            float ht = itemHover.getOrDefault(i, 0f);
            ht += ((hov ? 1f : 0f) - ht) * Math.min(1f, delta * 0.4f);
            itemHover.put(i, ht);

            if (sel) {
                ctx.method_25294(0, y, LEFT_W, y + ITEM_H, col(Ui.COL_ACCENT & 0xFFFFFF, 28));
                ctx.method_25294(0, y, 3, y + ITEM_H, Ui.COL_ACCENT);
            } else if (ht > 0.02f) {
                ctx.method_25294(0, y, LEFT_W, y + ITEM_H, col(Ui.COL_SURFACE & 0xFFFFFF, (int)(140 * ht)));
            }

            int textY = y + (ITEM_H - 9) / 2;
            int fg = sel ? Ui.COL_ACCENT : lerp(Ui.COL_MUTED, Ui.COL_TEXT, ht);
            DrawHelper.drawText(ctx, field_22793, SLOTS[i].emoji() + "  " + SLOTS[i].display(), PAD + 4, textY, fg, false);

            int cnt = discovered.getOrDefault(i, List.of()).size();
            DrawHelper.drawText(ctx, field_22793, scanning ? "…" : (cnt == 0 ? "—" : String.valueOf(cnt)),
                LEFT_W - PAD - 22, textY, cnt == 0 && !scanning ? Ui.COL_DANGER : Ui.COL_MUTED, false);
            boolean hasSel = selections.getOrDefault(i, -1) >= 0;
            float checkT = slotCheckAnim.getOrDefault(i, hasSel ? 1f : 0f);
            checkT = Ui.tickCheckAnim(checkT, hasSel, delta);
            slotCheckAnim.put(i, checkT);
            if (checkT > 0.01f) {
                int ckX = LEFT_W - PAD - 12, ckY = y + (ITEM_H - 8) / 2;
                Ui.drawAnimatedCheckbox(ctx, ckX, ckY, 8, checkT, false);
            }

            ctx.method_25294(PAD, y + ITEM_H - 1, LEFT_W - PAD, y + ITEM_H, col(Ui.COL_BORDER & 0xFFFFFF, 80));
            y += ITEM_H;
        }
        DrawHelper.flushDraw(ctx);
        ctx.method_44380();
        DrawHelper.flushDraw(ctx);

        int listTop = top + 18, listH = bot - listTop;
        int totalH = visible.size() * ITEM_H;
        if (totalH > listH) {
            int trkX = LEFT_W - 5, trkY = listTop + 4, trkH = listH - 8;
            int thumbH = Math.max(20, trkH * listH / totalH);
            int thumbY = trkY + (int)((double)(trkH - thumbH) * itemScroll / Math.max(1, totalH - listH));
            ctx.method_25294(trkX, trkY, trkX + 3, trkY + trkH, col(Ui.COL_BORDER & 0xFFFFFF, 100));
            ctx.method_25294(trkX, thumbY, trkX + 3, thumbY + thumbH, col(Ui.COL_ACCENT & 0xFFFFFF, 200));
            float phase = (float)(System.currentTimeMillis() % 2000L) / 2000f;
            if (itemScroll > 1) Ui.scrollIndicator(ctx, LEFT_W / 2, listTop + 2, true, phase, Ui.COL_ACCENT);
            if (itemScroll < totalH - listH - 1)
                Ui.scrollIndicator(ctx, LEFT_W / 2, bot - 10, false, phase, Ui.COL_ACCENT);
        }
    }

    private void drawConfigStrip(class_332 ctx, int mx, int my) {
        int top = configTop();
        ctx.method_25294(LEFT_W, top, field_22789, top + CFG_H, Ui.COL_PANEL);
        ctx.method_25294(LEFT_W, top + CFG_H - 1, field_22789, top + CFG_H, Ui.COL_BORDER);

        SlotDef slot = SLOTS[selectedSlot];
        DrawHelper.drawText(ctx, field_22793, slot.emoji() + "  " + slot.display(),
            LEFT_W + PAD, top + 6, Ui.COL_TEXT, false);

        if (slot.vanillaTexture()) {
            String hint = switch (slot.particleKind()) {
                case GOLDEN_CRIT -> "Either Golden Crit or Critical Hit — includes .mcmeta + particles/crit.json";
                case CRITICAL_HIT -> "Either Golden Crit or Critical Hit — from textures/particle/";
                case ENCHANT_HIT -> "Optional — from textures/particle/ in your packs.";
                default -> isHippoSlot(slot)
                    ? "Pick any sword CIT texture — outputs textures/block/dead_tube_coral.png."
                    : isNetheriteVanillaSwordSlot(slot)
                    ? "Pick any sword CIT texture — outputs textures/item/netherite_sword.png."
                    : "From textures/ folder in your packs.";
            };
            DrawHelper.drawText(ctx, field_22793, hint, LEFT_W + PAD, top + 26, Ui.COL_MUTED, false);
        } else {
            DrawHelper.drawText(ctx, field_22793, "CIT Name",
                LEFT_W + PAD, top + 26, Ui.COL_MUTED, false);
            drawCitNameBar(ctx);
            DrawHelper.drawText(ctx, field_22793, "Folder",
                LEFT_W + PAD, top + 42, Ui.COL_MUTED, false);
            int btnX = LEFT_W + PAD + field_22793.method_1727("Folder ") + 6;
            int btnY = top + 38;
            for (TexFolder f : TexFolder.values()) {
                boolean active = f == effectiveFolder(selectedSlot);
                int bw = field_22793.method_1727(f.label) + 10;
                ctx.method_25294(btnX, btnY, btnX + bw, btnY + 14,
                    active ? col(Ui.COL_ACCENT & 0xFFFFFF, 220) : col(Ui.COL_SURFACE & 0xFFFFFF, 160));
                DrawHelper.drawText(ctx, field_22793, f.label, btnX + 5, btnY + 3,
                    active ? Ui.COL_BG : Ui.COL_MUTED, false);
                btnX += bw + 6;
            }
        }
    }

    private int citLabelW() { return field_22793.method_1727("CIT Name  ") + 2; }

    private void layoutCitBar() {
        citBarX = LEFT_W + PAD + citLabelW();
        citBarY = configTop() + 22;
        citBarW = Math.min(220, field_22789 - citBarX - PAD);
        citBarH = 14;
    }

    private void drawCitNameBar(class_332 ctx) {
        if (nameDialogOpen || SLOTS[selectedSlot].vanillaTexture()) return;
        layoutCitBar();
        int sx = citBarX, sy = citBarY, sw = citBarW, sh = citBarH;
        ctx.method_25294(sx, sy, sx + sw, sy + sh, col(Ui.COL_SURFACE & 0xFFFFFF, 200));
        int lineCol = citNameFocused ? Ui.COL_ACCENT : Ui.COL_BORDER;
        ctx.method_25294(sx, sy, sx + sw, sy + 1, lineCol);
        ctx.method_25294(sx, sy + sh - 1, sx + sw, sy + sh, lineCol);
        ctx.method_25294(sx, sy, sx + 1, sy + sh, lineCol);
        ctx.method_25294(sx + sw - 1, sy, sx + sw, sy + sh, lineCol);
        if (citNameFocused) {
            ctx.method_25294(sx, sy + sh, sx + sw, sy + sh + 1, Ui.COL_ACCENT);
        }

        int textX = sx + 6, textY = sy + (sh - 8) / 2;
        String display = citNameFocused ? citNameEdit : defaultNameForSlot(selectedSlot);
        if (display.isEmpty() && !citNameFocused) {
            DrawHelper.drawText(ctx, field_22793, "e.g. Pro Sword", textX, textY,
                col(Ui.COL_MUTED & 0xFFFFFF, 140), false);
        } else {
            String shown = display;
            if (citNameFocused && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
            DrawHelper.drawText(ctx, field_22793, shown, textX, textY, Ui.COL_TEXT, false);
        }
    }

    private int searchLabelW() { return field_22793.method_1727("Search") + 8; }

    private void layoutSearchBar() {
        searchBarX = LEFT_W + PAD + searchLabelW();
        searchBarY = configTop() + CFG_H + 4;
        searchBarW = Math.max(120, field_22789 - searchBarX - PAD);
        searchBarH = SEARCH_H - 6;
    }

    private void drawSearchStrip(class_332 ctx) {
        int top = configTop() + CFG_H;
        ctx.method_25294(LEFT_W, top, field_22789, top + SEARCH_H + 6, col(Ui.COL_PANEL & 0xFFFFFF, 180));
        ctx.method_25294(LEFT_W, top + SEARCH_H + 5, field_22789, top + SEARCH_H + 6, Ui.COL_BORDER);
        DrawHelper.drawText(ctx, field_22793, "Search", LEFT_W + PAD, top + 7, Ui.COL_MUTED, false);

        layoutSearchBar();
        int sx = searchBarX, sy = searchBarY, sw = searchBarW, sh = searchBarH;
        ctx.method_25294(sx, sy, sx + sw, sy + sh, col(Ui.COL_SURFACE & 0xFFFFFF, 200));
        int lineCol = searchFocused ? Ui.COL_ACCENT : Ui.COL_BORDER;
        ctx.method_25294(sx, sy, sx + sw, sy + 1, lineCol);
        ctx.method_25294(sx, sy + sh - 1, sx + sw, sy + sh, lineCol);
        ctx.method_25294(sx, sy, sx + 1, sy + sh, lineCol);
        ctx.method_25294(sx + sw - 1, sy, sx + sw, sy + sh, lineCol);
        if (searchFocused) {
            ctx.method_25294(sx, sy + sh, sx + sw, sy + sh + 1, Ui.COL_ACCENT);
        }

        int textX = sx + 6, textY = sy + (sh - 8) / 2;
        if (searchQuery.isEmpty() && !searchFocused) {
            DrawHelper.drawText(ctx, field_22793, "Search textures…", textX, textY,
                col(Ui.COL_MUTED & 0xFFFFFF, 140), false);
        } else {
            String shown = searchQuery;
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) shown += "_";
            DrawHelper.drawText(ctx, field_22793, shown, textX, textY, Ui.COL_TEXT, false);
        }
    }

    private List<TextureOption> filteredOptions(int slotIdx) {
        List<TextureOption> all = discovered.getOrDefault(slotIdx, List.of());
        String q = searchQuery.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return all;
        return all.stream().filter(o -> o.label().toLowerCase(Locale.ROOT).contains(q)).toList();
    }

    private void drawTexturePanel(class_332 ctx, int mx, int my, float delta) {
        int top = configTop() + CFG_H + SEARCH_H + 8;
        int bot = field_22790 - FOOTER, panX = LEFT_W;
        ctx.method_25294(panX, top, field_22789, bot, Ui.COL_BG);

        if (scanning) {
            int cy = (top + bot) / 2;
            Ui.spinner(ctx, (field_22789 + LEFT_W) / 2, cy, 8,
                (float)(System.currentTimeMillis() % 1200L) / 1200f, col(Ui.COL_ACCENT & 0xFFFFFF, 200));
            DrawHelper.drawText(ctx, field_22793, scanStatus,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(scanStatus) / 2, cy + 14, Ui.COL_MUTED, false);
            return;
        }

        List<TextureOption> allOpts = discovered.getOrDefault(selectedSlot, List.of());
        List<TextureOption> opts = filteredOptions(selectedSlot);

        if (allOpts.isEmpty()) {
            String msg = "No textures found for this slot.";
            String hint = sharesSwordCitPool(SLOTS[selectedSlot])
                ? "Shows sword CIT textures from optifine/cit/ in your packs."
                : SLOTS[selectedSlot].vanillaTexture()
                ? "Looks in assets/minecraft/textures/ in your packs."
                : "Looks in optifine/cit/ in your packs.";
            String tip = "Put packs in resourcepacks/ or slothyhub-local/, or join a server with a resource pack loaded.";
            int cy = (top + bot) / 2;
            DrawHelper.drawText(ctx, field_22793, msg,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(msg) / 2, cy - 12, Ui.COL_MUTED, false);
            DrawHelper.drawText(ctx, field_22793, hint,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(hint) / 2, cy + 2,
                col(Ui.COL_MUTED & 0xFFFFFF, 140), false);
            DrawHelper.drawText(ctx, field_22793, tip,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(tip) / 2, cy + 16,
                col(Ui.COL_MUTED & 0xFFFFFF, 120), false);
            return;
        }

        if (opts.isEmpty()) {
            String msg = "No textures match \"" + searchQuery + "\"";
            int cy = (top + bot) / 2;
            DrawHelper.drawText(ctx, field_22793, msg,
                (field_22789 + LEFT_W) / 2 - field_22793.method_1727(msg) / 2, cy, Ui.COL_MUTED, false);
            return;
        }

        int TEX_H = 30, innerTop = top + 4;
        DrawHelper.flushDraw(ctx);
        ctx.method_44379(panX, innerTop, field_22789, bot);
        int y = innerTop - (int) texScroll;
        int curSel = selections.getOrDefault(selectedSlot, -1);

        drawTexRow(ctx, panX, y, TEX_H, innerTop, bot, mx, my, curSel < 0,
            "✕  None (no override)", curSel < 0, Ui.COL_DANGER);
        y += TEX_H;

        for (int i = 0; i < opts.size(); i++) {
            TextureOption opt = opts.get(i);
            int realIdx = allOpts.indexOf(opt);
            boolean sel = curSel == realIdx;
            boolean hov = mx >= panX && mx < field_22789 && my >= y && my < y + TEX_H && my > innerTop && my < bot;
            if (sel) {
                ctx.method_25294(panX, y, field_22789, y + TEX_H, col(Ui.COL_ACCENT & 0xFFFFFF, 28));
                ctx.method_25294(panX, y, panX + 3, y + TEX_H, Ui.COL_ACCENT);
            } else if (hov) {
                ctx.method_25294(panX, y, field_22789, y + TEX_H, col(Ui.COL_SURFACE & 0xFFFFFF, 80));
            }
            ensureTexThumb(opt);
            class_2960 tid = texThumbs.get(texThumbKey(opt));
            if (tid != null) {
                int[] dim = texThumbSizes.get(texThumbKey(opt));
                int texW = dim != null ? dim[0] : 24;
                int texHImg = dim != null ? dim[1] : 24;
                DrawHelper.drawTexture(ctx, tid, panX + PAD, y + 3, 0f, 0f, 24, 24, texW, texHImg);
            } else if (opt.pngPreview() != null) {
                ctx.method_25294(panX + PAD, y + 7, panX + PAD + 16, y + 23, col(Ui.COL_ACCENT & 0xFFFFFF, sel ? 255 : 120));
            }
            ctx.method_25294(panX + PAD, y + TEX_H - 1, field_22789 - PAD, y + TEX_H, col(Ui.COL_BORDER & 0xFFFFFF, 80));
            y += TEX_H;
        }

        DrawHelper.flushDraw(ctx);
        y = innerTop - (int) texScroll;
        drawTexRowText(ctx, panX, y, TEX_H, innerTop, bot, mx, my, curSel < 0,
            "✕  None (no override)", curSel < 0, Ui.COL_DANGER);
        y += TEX_H;
        for (int i = 0; i < opts.size(); i++) {
            TextureOption opt = opts.get(i);
            int realIdx = allOpts.indexOf(opt);
            boolean sel = curSel == realIdx;
            String display = opt.label().length() > 64
                ? "…" + opt.label().substring(opt.label().length() - 62) : opt.label();
            DrawHelper.drawText(ctx, field_22793, display, panX + PAD + 30, y + (TEX_H - 9) / 2,
                sel ? Ui.COL_ACCENT : Ui.COL_TEXT, false);
            y += TEX_H;
        }
        DrawHelper.flushDraw(ctx);
        ctx.method_44380();
        DrawHelper.flushDraw(ctx);

        int listH = bot - innerTop;
        int totalH = (opts.size() + 1) * TEX_H;
        if (totalH > listH) {
            int trkX = field_22789 - 6, trkY = innerTop + 4, trkH = listH - 8;
            int thumbH = Math.max(20, trkH * listH / totalH);
            int thumbY = trkY + (int)((double)(trkH - thumbH) * texScroll / Math.max(1, totalH - listH));
            ctx.method_25294(trkX, trkY, trkX + 3, trkY + trkH, col(Ui.COL_BORDER & 0xFFFFFF, 100));
            ctx.method_25294(trkX, thumbY, trkX + 3, thumbY + thumbH, col(Ui.COL_ACCENT & 0xFFFFFF, 200));
            float phase = (float)(System.currentTimeMillis() % 2000L) / 2000f;
            if (texScroll > 1) Ui.scrollIndicator(ctx, panX + (field_22789 - panX) / 2, innerTop + 2, true, phase, Ui.COL_ACCENT);
            if (texScroll < totalH - listH - 1)
                Ui.scrollIndicator(ctx, panX + (field_22789 - panX) / 2, bot - 10, false, phase, Ui.COL_ACCENT);
        }
    }

    private void drawTexRow(class_332 ctx, int panX, int y, int h, int innerTop, int bot,
                             int mx, int my, boolean hov, String text, boolean sel, int selCol) {
        if (sel) ctx.method_25294(panX, y, field_22789, y + h, col(Ui.COL_SURFACE & 0xFFFFFF, 140));
        else if (hov && mx >= panX && my >= y && my < y + h && my > innerTop && my < bot)
            ctx.method_25294(panX, y, field_22789, y + h, col(Ui.COL_SURFACE & 0xFFFFFF, 60));
        if (sel) ctx.method_25294(panX, y, panX + 3, y + h, selCol);
        ctx.method_25294(panX + PAD, y + h - 1, field_22789 - PAD, y + h, col(Ui.COL_BORDER & 0xFFFFFF, 80));
    }

    private void drawTexRowText(class_332 ctx, int panX, int y, int h, int innerTop, int bot,
                                int mx, int my, boolean hov, String text, boolean sel, int selCol) {
        DrawHelper.drawText(ctx, field_22793, text, panX + PAD, y + (h - 9) / 2, sel ? selCol : Ui.COL_MUTED, false);
    }

    private void drawFooter(class_332 ctx) {
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790, Ui.COL_PANEL);
        ctx.method_25294(0, field_22790 - FOOTER, field_22789, field_22790 - FOOTER + 1, Ui.COL_BORDER);
        if (buildStatus != null) {
            DrawHelper.drawText(ctx, field_22793, buildStatus,
                field_22789 / 2 - field_22793.method_1727(buildStatus) / 2,
                field_22790 - FOOTER + 8, buildOk ? Ui.COL_ACCENT : Ui.COL_DANGER, false);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────


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
        if (InputCompat.delegateToChildren(this, mx, my, button)) return true;
        if (button != 0) return false;

        if (mx >= PAD && mx <= PAD + 18 && my >= 8 && my <= HEADER - 8) {
            method_25419(); return true;
        }
        if (mx >= PAD + 18 && mx <= PAD + 32 && my >= 8 && my <= HEADER - 8) {
            method_25419(); return true;
        }

        layoutSearchBar();
        layoutCitBar();
        if (!SLOTS[selectedSlot].vanillaTexture() && !nameDialogOpen
            && mx >= citBarX && mx <= citBarX + citBarW
            && my >= citBarY && my <= citBarY + citBarH) {
            citNameEdit = defaultNameForSlot(selectedSlot);
            citNameFocused = true;
            searchFocused = false;
            return true;
        }
        if (mx >= searchBarX && mx <= searchBarX + searchBarW
            && my >= searchBarY && my <= searchBarY + searchBarH) {
            commitCitNameEdit(selectedSlot);
            searchFocused = true;
            return true;
        }
        if (citNameFocused) commitCitNameEdit(selectedSlot);
        searchFocused = false;

        if (handleCategoryTabClick(mx, my)) return true;

        int top = HEADER + CATEGORY_H, bot = field_22790 - FOOTER;

        // Slot list
        if (mx < LEFT_W && my > top + 18 && my < bot) {
            int y = top + 18 - (int) itemScroll;
            for (int i : visibleSlotIndices()) {
                if (my >= y && my < y + ITEM_H) {
                    if (i != selectedSlot) commitCitNameEdit(selectedSlot);
                    selectedSlot = i;
                    texScroll = texScrollTarget = 0;
                    searchQuery = "";
                    searchFocused = false;
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
        if (citNameFocused && chr >= 32 && chr != 127 && citNameEdit.length() < 40) {
            citNameEdit += chr;
            return true;
        }
        if (searchFocused && chr >= 32 && chr != 127 && searchQuery.length() < 60) {
            searchQuery += chr;
            texScroll = texScrollTarget = 0;
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
        if (citNameFocused) {
            if (key == 259 && !citNameEdit.isEmpty()) {
                citNameEdit = citNameEdit.substring(0, citNameEdit.length() - 1);
                return true;
            }
            if (key == 256 || key == 257) {
                commitCitNameEdit(selectedSlot);
                return true;
            }
        }
        if (searchFocused) {
            if (key == 259 && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                texScroll = texScrollTarget = 0;
                return true;
            }
            if (key == 256) {
                searchQuery = "";
                searchFocused = false;
                texScroll = texScrollTarget = 0;
                return true;
            }
            if (key == 257) {
                searchFocused = false;
                return true;
            }
        }
        if (key == 256) { method_25419(); return true; }
        return super.method_25404(key, scan, mods);
    }
}
