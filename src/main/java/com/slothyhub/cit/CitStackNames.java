package com.slothyhub.cit;

import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1836;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_9279;
import net.minecraft.class_9280;
import net.minecraft.class_9290;
import net.minecraft.class_9334;
import net.minecraft.class_9336;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves every string that should be tried against CIT name / component rules for a stack. */
final class CitStackNames {

    private static final int CACHE_MAX = 2048;
    private static final Map<Long, List<String>> CACHE = Collections.synchronizedMap(
        new LinkedHashMap<>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, List<String>> eldest) {
                return size() > CACHE_MAX;
            }
        });

    /** Full tooltip scan — expensive; off by default (lore component covers most PvP packs). */
    private static final boolean TOOLTIP_RESOLVE =
        Boolean.parseBoolean(System.getProperty("slothyhub.cit.tooltipResolve", "false"));
    /**
     * PvP servers (Elytra Box, etc.) often put tier tags in lore instead of renaming the item.
     * Summer-style CIT still expects "Warden Sword" — map common tier tags to those names.
     */
    private static final Map<String, String> LORE_TIER_TO_CIT = Map.ofEntries(
        Map.entry("noob", "Noob Sword"),
        Map.entry("good", "Good Sword"),
        Map.entry("pro", "Pro Sword"),
        Map.entry("perfect", "Perfect Sword"),
        Map.entry("mythic", "Warden Sword"),
        Map.entry("exotic", "Warden Sword"),
        Map.entry("warden", "Warden Sword"),
        Map.entry("hippo", "Hippo Sword"),
        Map.entry("bubble", "Bubble Coral")
    );

    private CitStackNames() {}

    static void clearCache() {
        CACHE.clear();
    }

    static long cacheKey(class_1799 stack) {
        if (stack == null || stack.method_7960()) return 0L;
        long h = stack.method_7909().hashCode();
        try {
            class_2561 renamed = stack.method_65130();
            if (renamed != null) h = 31 * h + renamed.getString().hashCode();
            class_2561 custom = stack.method_58694(class_9334.field_49631);
            if (custom != null) h = 31 * h + custom.getString().hashCode();
            class_9290 lore = stack.method_58694(class_9334.field_49632);
            if (lore != null) h = 31 * h + lore.hashCode();
            class_9279 customData = stack.method_58694(class_9334.field_49628);
            if (customData != null && !customData.method_57458()) h = 31 * h + customData.hashCode();
        } catch (Exception ignored) {}
        return h;
    }

    static List<String> resolve(class_1799 stack) {
        if (stack == null || stack.method_7960()) return List.of();
        long key = cacheKey(stack);
        if (key != 0L) {
            List<String> hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        List<String> resolved = List.copyOf(resolveUncached(stack));
        if (key != 0L) CACHE.put(key, resolved);
        return resolved;
    }

    private static List<String> resolveUncached(class_1799 stack) {
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        java.util.function.Consumer<String> add = raw -> {
            if (raw == null) return;
            String plain = CitItemRenderer.stripFormatting(raw);
            if (plain.isBlank() || isIgnoredTooltipLine(plain)) return;
            String key = plain.toLowerCase(Locale.ROOT);
            if (seen.add(key)) out.add(plain);
        };

        java.util.function.Consumer<String> addWithTier = raw -> {
            add.accept(raw);
            if (raw != null) mapLoreTierTag(CitItemRenderer.stripFormatting(raw), add);
        };

        try {
            // Renamed / plugin names — must use method_58694 (method_57381 is wrong on ItemStack).
            class_2561 renamed = stack.method_65130();
            if (renamed != null) addWithTier.accept(renamed.getString());

            class_2561 custom = stack.method_58694(class_9334.field_49631);
            if (custom != null) addWithTier.accept(custom.getString());

            class_2561 itemName = stack.method_58694(class_9334.field_50239);
            if (itemName != null) addWithTier.accept(itemName.getString());

            if (TOOLTIP_RESOLVE) {
                addTooltipLines(stack, addWithTier);
            }

            class_2561 display = stack.method_7964();
            if (display != null) addWithTier.accept(display.getString());

            class_9290 lore = stack.method_58694(class_9334.field_49632);
            if (lore != null) {
                for (class_2561 line : lore.comp_2400()) {
                    if (line != null) addWithTier.accept(line.getString());
                }
            }

            for (class_9336<?> comp : stack.method_57353()) {
                if (comp == null) continue;
                Object val = comp.comp_2444();
                if (val instanceof class_2561 text) {
                    addWithTier.accept(text.getString());
                }
                scanCustomData(comp.toString(), add);
                if (val != null) scanCustomData(val.toString(), add);
            }

            class_9280 cmd = stack.method_58694(class_9334.field_49637);
            if (cmd != null) {
                for (String s : cmd.comp_3356()) {
                    add.accept(s);
                    add.accept(humanizeToken(s));
                }
                for (Float f : cmd.comp_3354()) {
                    if (f != null) add.accept(String.valueOf(f.intValue()));
                }
            }

            class_2960 itemModel = stack.method_58694(class_9334.field_54199);
            if (itemModel != null && !isDefaultItemModel(stack, itemModel)) {
                add.accept(itemModel.toString());
                add.accept(itemModel.method_12832());
                add.accept(humanizeToken(itemModel.method_12832()));
            }

            class_9279 customData = stack.method_58694(class_9334.field_49628);
            if (customData != null && !customData.method_57458()) {
                scanCustomData(customData.toString(), add);
                scanCustomData(customData.method_57461().toString(), add);
            }
        } catch (Exception ignored) {}

        return out;
    }

    private static boolean isIgnoredTooltipLine(String plain) {
        String lower = plain.toLowerCase(Locale.ROOT);
        return lower.contains("attack speed")
            || lower.startsWith("damage:")
            || lower.contains("when in ")
            || lower.startsWith("item lives")
            || lower.endsWith(" component(s)");
    }

    /** Full hover tooltip — includes Mythic/plugin lines not stored in the lore component. */
    private static void addTooltipLines(class_1799 stack, java.util.function.Consumer<String> add) {
        class_310 mc = class_310.method_1551();
        class_1657 player = mc != null ? mc.field_1724 : null;
        class_1792.class_9635 ctx = class_1792.class_9635.field_51353;
        if (player != null && player.method_37908() != null) {
            ctx = class_1792.class_9635.method_59528(player.method_37908());
        }

        for (class_1836 tipType : new class_1836[]{class_1836.field_41070, class_1836.field_41071}) {
            try {
                List<class_2561> lines = stack.method_7950(ctx, player, tipType);
                if (lines == null) continue;
                for (class_2561 line : lines) {
                    if (line != null) add.accept(line.getString());
                }
            } catch (Exception ignored) {}
        }
    }

    private static boolean isDefaultItemModel(class_1799 stack, class_2960 modelId) {
        try {
            class_2960 defaultId = stack.method_7909().method_57347().method_58695(class_9334.field_54199, null);
            return defaultId != null && defaultId.equals(modelId);
        } catch (Exception e) {
            return "minecraft:netherite_sword".equals(modelId.toString());
        }
    }

    /** Lore line is exactly (or mostly) a tier tag like "MYTHIC" with no sword name. */
    private static void mapLoreTierTag(String plain, java.util.function.Consumer<String> add) {
        if (plain == null || plain.isBlank() || isIgnoredTooltipLine(plain)) return;
        String lower = plain.toLowerCase(Locale.ROOT).trim();
        if (!lower.contains(" ")) {
            String cit = LORE_TIER_TO_CIT.get(lower);
            if (cit != null) add.accept(cit);
        }
        for (var entry : LORE_TIER_TO_CIT.entrySet()) {
            if (lower.contains(entry.getKey())) {
                add.accept(entry.getValue());
            }
        }
    }

    private static void scanCustomData(String raw, java.util.function.Consumer<String> add) {
        if (raw == null || raw.isBlank()) return;
        String lower = raw.toLowerCase(Locale.ROOT);
        for (var entry : LORE_TIER_TO_CIT.entrySet()) {
            if (lower.contains(entry.getKey())) {
                add.accept(entry.getValue());
            }
        }
        String[] known = { "noob_sword", "good_sword", "pro_sword", "perfect_sword", "warden_sword", "hippo_sword", "bubble_coral" };
        for (String token : known) {
            if (lower.contains(token)) {
                add.accept(humanizeToken(token));
            }
        }
    }

    private static String humanizeToken(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String t = raw.replace('\\', '/');
        int slash = t.lastIndexOf('/');
        if (slash >= 0) t = t.substring(slash + 1);
        if (t.endsWith(".png")) t = t.substring(0, t.length() - 4);
        String[] parts = t.split("[_\\-]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? raw : sb.toString();
    }
}
