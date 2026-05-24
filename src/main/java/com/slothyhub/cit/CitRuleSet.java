package com.slothyhub.cit;

import com.slothyhub.SlothyHubMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Find the first matching rule for an item.
     *
     * @param itemId   namespaced item ID (e.g. "minecraft:diamond_sword")
     * @param displayName item display name, or null if unset
     * @return the first matching rule, or null if none applies
     */
    public CitRule findMatch(String itemId, String displayName) {
        List<CitRule> candidates = byItem.getOrDefault(itemId, Collections.emptyList());
        for (CitRule rule : candidates) {
            if (matches(rule, displayName)) return rule;
        }
        return null;
    }

    private boolean matches(CitRule rule, String displayName) {
        if (!rule.nameMatcher.isBlank()) {
            if (displayName == null) return false;
            String pattern = rule.nameMatcher;
            if (pattern.startsWith("regex:")) {
                try {
                    if (!displayName.matches(pattern.substring(6))) return false;
                } catch (Exception e) {
                    return false;
                }
            } else if (pattern.startsWith("ipattern:")) {
                if (!displayName.equalsIgnoreCase(pattern.substring(9))) return false;
            } else if (pattern.startsWith("pattern:")) {
                if (!displayName.equals(pattern.substring(8))) return false;
            } else {
                if (!displayName.equals(pattern)) return false;
            }
        }
        // NBT / damage / stack conditions are evaluated in CitItemRenderer
        return true;
    }

    public List<CitRule> getRules() { return rules; }
    public List<CitRule> allRules() { return rules; }
    public boolean isEmpty() { return rules.isEmpty(); }

    /** First item id of a rule (convenience for texture picker). */
    public static String firstItem(CitRule r) {
        return r.items.isEmpty() ? null : r.items.get(0);
    }
}
