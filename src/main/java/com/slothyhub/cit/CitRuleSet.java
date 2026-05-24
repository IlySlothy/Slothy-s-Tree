package com.slothyhub.cit;

import com.slothyhub.SlothyHubMod;
import net.minecraft.class_1799;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * Find the best matching rule for an item stack (CIT Resewn-style specificity).
     */
    public CitRule findMatch(String itemId, List<String> displayNames, class_1799 stack) {
        List<CitRule> candidates = byItem.getOrDefault(itemId, Collections.emptyList());
        if (candidates.isEmpty()) return null;

        CitRule best = null;
        int bestScore = Integer.MAX_VALUE;

        if (displayNames == null || displayNames.isEmpty()) {
            for (CitRule rule : candidates) {
                if (!rule.nameMatcher.isBlank()) continue;
                if (!CitRuleConditions.matchesStack(rule, stack)) continue;
                return rule;
            }
            return null;
        }

        for (int nameIdx = 0; nameIdx < displayNames.size(); nameIdx++) {
            String name = displayNames.get(nameIdx);
            for (CitRule rule : candidates) {
                if (!CitRuleConditions.matchesStack(rule, stack)) continue;
                int score = CitRuleConditions.nameMatchScore(rule, name, nameIdx);
                if (score < 0) continue;
                score += ruleSpecificityBonus(rule);
                if (score < bestScore) {
                    bestScore = score;
                    best = rule;
                }
            }
        }
        return best;
    }

    public CitRule findMatch(String itemId, List<String> displayNames) {
        return findMatch(itemId, displayNames, null);
    }

    public CitRule findMatch(String itemId, String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return findMatch(itemId, List.of(), null);
        }
        return findMatch(itemId, List.of(displayName), null);
    }

    /** Prefer rules with explicit name patterns and longer ipatterns (more specific). */
    private static int ruleSpecificityBonus(CitRule rule) {
        if (rule.nameMatcher.isBlank()) return 5000;
        String pattern = rule.nameMatcher.trim();
        if (pattern.startsWith("ipattern:") || pattern.startsWith("pattern:")) {
            return Math.max(0, 400 - pattern.length());
        }
        return 200;
    }

    public List<CitRule> getRules() { return rules; }
    public List<CitRule> allRules() { return rules; }
    public boolean isEmpty() { return rules.isEmpty(); }

    /** Deduplicate rules — later entries win (higher resource-pack priority). */
    public static List<CitRule> dedupe(List<CitRule> rules) {
        Map<String, CitRule> unique = new LinkedHashMap<>();
        for (CitRule rule : rules) {
            unique.put(dedupeKey(rule), rule);
        }
        return new ArrayList<>(unique.values());
    }

    private static String dedupeKey(CitRule rule) {
        StringBuilder items = new StringBuilder();
        for (String item : rule.items) {
            if (items.length() > 0) items.append(',');
            items.append(item.contains(":") ? item : "minecraft:" + item);
        }
        return items + "#" + rule.nameMatcher.trim().toLowerCase(Locale.ROOT);
    }

    /** First item id of a rule (convenience for texture picker). */
    public static String firstItem(CitRule r) {
        return r.items.isEmpty() ? null : r.items.get(0);
    }
}
