package com.slothyhub.cit;

import com.slothyhub.SlothyHubMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compiled set of active CIT rules for the current session.
 * Rebuilt each time resource packs are (re)loaded.
 */
public final class CitRuleSet {

    /** Singleton active rule set — replaced atomically on reload. */
    private static volatile CitRuleSet ACTIVE = new CitRuleSet(Collections.emptyList());

    private final List<CitRule> rules;
    // Fast lookup: item id → list of rules for that item
    private final Map<String, List<CitRule>> byItem;

    public CitRuleSet(List<CitRule> rules) {
        this.rules = List.copyOf(rules);
        Map<String, List<CitRule>> map = new HashMap<>();
        for (CitRule r : rules) {
            for (String item : r.items) {
                String norm = item.contains(":") ? item : "minecraft:" + item;
                map.computeIfAbsent(norm, k -> new ArrayList<>()).add(r);
            }
        }
        map.replaceAll((k, v) -> Collections.unmodifiableList(v));
        this.byItem = Collections.unmodifiableMap(map);
    }

    public static CitRuleSet active() { return ACTIVE; }

    /** Replace the active rule set (called from CitResourceReloadListener). */
    public static void setActive(CitRuleSet ruleSet) {
        ACTIVE = ruleSet;
        SlothyHubMod.LOGGER.info("CIT: loaded {} rules", ruleSet.rules.size());
    }

    /** Items that have at least one CIT rule registered. */
    public boolean hasRulesFor(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        return byItem.containsKey(itemId);
    }

    /**
     * Find the first matching rule for an item.
     *
     * @param itemId   namespaced item ID (e.g. "minecraft:diamond_sword")
     * @param displayNames candidate display strings (custom_name, item_name, lore, etc.)
     * @return the first matching rule, or null if none applies
     */
    public CitRule findMatch(String itemId, List<String> displayNames) {
        List<CitRule> candidates = byItem.getOrDefault(itemId, Collections.emptyList());
        if (candidates.isEmpty()) return null;

        if (displayNames == null || displayNames.isEmpty()) {
            for (CitRule rule : candidates) {
                if (rule.nameMatcher.isBlank()) return rule;
            }
            return null;
        }

        for (String name : displayNames) {
            for (CitRule rule : candidates) {
                if (matches(rule, name)) return rule;
            }
        }
        return null;
    }

    public CitRule findMatch(String itemId, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return findMatch(itemId, List.of());
        }
        return findMatch(itemId, List.of(displayName));
    }

    private boolean matches(CitRule rule, String displayName) {
        if (!rule.nameMatcher.isBlank()) {
            if (displayName == null) return false;
            String plain = CitItemRenderer.stripFormatting(displayName);
            String pattern = rule.nameMatcher.trim();
            if (pattern.startsWith("regex:")) {
                try {
                    if (!plain.matches(pattern.substring(6))) return false;
                } catch (Exception e) {
                    return false;
                }
            } else if (pattern.startsWith("ipattern:")) {
                String want = CitItemRenderer.stripFormatting(pattern.substring(9));
                String lower = plain.toLowerCase(Locale.ROOT);
                String wantLower = want.toLowerCase(Locale.ROOT);
                if (lower.equals(wantLower)) return true;
                // Item name contains pattern: "[RANK] Warden Sword"
                if (!wantLower.isEmpty() && lower.contains(wantLower)) return true;
                // Pattern contains item name: anvil typing "Warden" → "Warden Sword"
                if (lower.length() >= 4 && wantLower.startsWith(lower)) return true;
                if (lower.length() >= 6 && wantLower.contains(lower) && !isGenericToken(lower)) return true;
                return false;
            } else if (pattern.startsWith("pattern:")) {
                if (!plain.equals(CitItemRenderer.stripFormatting(pattern.substring(8)))) return false;
            } else {
                if (!plain.equalsIgnoreCase(CitItemRenderer.stripFormatting(pattern))) return false;
            }
        }
        return true;
    }

    private static boolean isGenericToken(String lower) {
        return lower.equals("sword") || lower.equals("netherite") || lower.equals("minecraft")
            || lower.startsWith("minecraft:") || lower.endsWith("_sword");
    }

    public List<CitRule> getRules() { return rules; }
    public List<CitRule> allRules() { return rules; }
    public boolean isEmpty() { return rules.isEmpty(); }

    /** First item id of a rule (convenience for texture picker). */
    public static String firstItem(CitRule r) {
        return r.items.isEmpty() ? null : r.items.get(0);
    }
}
