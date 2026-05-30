package com.slothyhub.cit;

import com.slothyhub.compat.McVersion;
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
import net.minecraft.class_9323;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 1.20.5+ CIT name resolution via data components. */
final class CitStackNamesModern {

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

    private static final Method STACK_GET = resolveStackGet();
    private static final Method DEFAULT_GET_OR = resolveDefaultGetOr();

    private CitStackNamesModern() {}

    static List<String> resolve(class_1799 stack) {
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
            if (raw != null) mapLoreTierTag(CitItemRenderer.stripFormatting(raw), add, seen);
        };

        try {
            class_2561 renamed = stack.method_65130();
            if (renamed != null) addWithTier.accept(renamed.getString());

            class_2561 custom = getComponent(stack, class_9334.field_49631);
            if (custom != null) addWithTier.accept(custom.getString());

            class_2561 itemName = getComponent(stack, class_9334.field_50239);
            if (itemName != null) addWithTier.accept(itemName.getString());

            class_2561 display = stack.method_7964();
            if (display != null) addWithTier.accept(display.getString());

            class_9290 lore = getComponent(stack, class_9334.field_49632);
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

            class_9280 cmd = getComponent(stack, class_9334.field_49637);
            if (cmd != null) {
                for (String s : cmd.comp_3356()) {
                    add.accept(s);
                    add.accept(humanizeToken(s));
                }
                for (Float f : cmd.comp_3354()) {
                    if (f != null) add.accept(String.valueOf(f.intValue()));
                }
            }

            class_2960 itemModel = McVersion.atLeast("1.21.4")
                ? getComponent(stack, class_9334.field_54199) : null;
            if (itemModel != null && !isDefaultItemModel(stack, itemModel)) {
                add.accept(itemModel.toString());
                add.accept(itemModel.method_12832());
                add.accept(humanizeToken(itemModel.method_12832()));
            }

            class_9279 customData = getComponent(stack, class_9334.field_49628);
            if (customData != null && !customData.method_57458()) {
                scanCustomData(customData.toString(), add);
                scanCustomData(customData.method_57461().toString(), add);
            }
        } catch (Throwable ignored) {}

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
            } catch (Throwable ignored) {}
        }
    }

    private static boolean isDefaultItemModel(class_1799 stack, class_2960 modelId) {
        try {
            class_2960 defaultId = defaultGetOr(
                stack.method_7909().method_57347(), class_9334.field_54199, null);
            return defaultId != null && defaultId.equals(modelId);
        } catch (Throwable e) {
            return "minecraft:netherite_sword".equals(modelId.toString());
        }
    }

    private static void mapLoreTierTag(String plain, java.util.function.Consumer<String> add, LinkedHashSet<String> seen) {
        if (plain == null || plain.isBlank() || isIgnoredTooltipLine(plain)) return;
        if (hasNamedSword(seen)) return;

        String lower = plain.toLowerCase(Locale.ROOT).trim();
        if (lower.contains(" ")) return;

        String cit = LORE_TIER_TO_CIT.get(lower);
        if (cit != null && seen.add(cit.toLowerCase(Locale.ROOT))) {
            add.accept(cit);
        }
    }

    private static boolean hasNamedSword(LinkedHashSet<String> seen) {
        for (String s : seen) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.endsWith(" sword") && !lower.equals("netherite sword")) {
                return true;
            }
        }
        return false;
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

    @SuppressWarnings("unchecked")
    private static <T> T getComponent(class_1799 stack, net.minecraft.class_9331<? extends T> type) {
        if (stack == null || type == null) return null;
        try {
            return stack.method_57381(type);
        } catch (Throwable ignored) {}
        if (STACK_GET != null) {
            try {
                return (T) STACK_GET.invoke(stack, type);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T defaultGetOr(class_9323 defaults, net.minecraft.class_9331<? extends T> type, T fallback) {
        if (defaults == null || type == null || DEFAULT_GET_OR == null) return fallback;
        try {
            return (T) DEFAULT_GET_OR.invoke(defaults, type, fallback);
        } catch (ReflectiveOperationException e) {
            return fallback;
        }
    }

    private static Method resolveStackGet() {
        for (String name : new String[]{"method_57381", "method_57824", "method_58694"}) {
            try {
                Method m = class_1799.class.getMethod(name, net.minecraft.class_9331.class);
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Method resolveDefaultGetOr() {
        for (String name : new String[]{"method_58695", "method_57830", "method_57379"}) {
            try {
                Method m = class_9323.class.getMethod(name, net.minecraft.class_9331.class, Object.class);
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
