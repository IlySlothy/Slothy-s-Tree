package com.slothyhub.cit;

import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_9279;
import net.minecraft.class_9334;

import java.util.Locale;

final class CitRuleConditions {

    private CitRuleConditions() {}

    static boolean matchesStack(CitRule rule, class_1799 stack) {
        if (rule == null || stack == null || stack.method_7960()) return false;

        if (!rule.damage.isBlank() && !matchesIntRange(rule.damage, stack.method_7919())) {
            return false;
        }
        if (!rule.stackSize.isBlank() && !matchesIntRange(rule.stackSize, stack.method_7947())) {
            return false;
        }
        for (CitRule.NbtCondition condition : rule.nbtConditions) {
            if (condition == null || condition.path().isBlank() || condition.pattern().isBlank()) continue;
            if ("display.Name".equalsIgnoreCase(condition.path())) continue;
            if (!matchesNbtCondition(stack, condition)) return false;
        }
        return true;
    }

    private static boolean matchesNbtCondition(class_1799 stack, CitRule.NbtCondition condition) {
        String path = condition.path().toLowerCase(Locale.ROOT);
        String pattern = condition.pattern();
        String value = readNbtPath(stack, path);
        if (value == null) return false;
        return matchesPattern(pattern, value);
    }

    private static String readNbtPath(class_1799 stack, String path) {
        try {
            if (path.startsWith("display.")) {
                String sub = path.substring("display.".length());
                if ("name".equals(sub)) {
                    class_2561 name = stack.method_65130();
                    if (name == null) name = stack.method_7964();
                    return name != null ? name.getString() : null;
                }
            }
            class_9279 data = stack.method_57381(class_9334.field_49628);
            if (data != null && !data.method_57458()) {
                return data.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    static boolean matchesPattern(String pattern, String value) {
        if (pattern == null || value == null) return false;
        String plain = CitItemRenderer.stripFormatting(value);
        String p = pattern.trim();
        if (p.startsWith("regex:")) {
            try {
                return plain.matches(p.substring(6));
            } catch (Exception e) {
                return false;
            }
        }
        if (p.startsWith("ipattern:")) {
            String want = CitItemRenderer.stripFormatting(p.substring(9));
            return plain.toLowerCase(Locale.ROOT).contains(want.toLowerCase(Locale.ROOT));
        }
        if (p.startsWith("pattern:")) {
            return plain.equals(CitItemRenderer.stripFormatting(p.substring(8)));
        }
        return plain.equalsIgnoreCase(CitItemRenderer.stripFormatting(p));
    }

    static int nameMatchScore(CitRule rule, String displayName, int nameIndex) {
        if (rule.nameMatcher.isBlank()) {
            return nameIndex * 10_000;
        }
        if (displayName == null) return -1;

        String plain = CitItemRenderer.stripFormatting(displayName);
        String pattern = rule.nameMatcher.trim();
        int base = nameIndex * 10_000;

        if (pattern.startsWith("regex:")) {
            try {
                if (plain.matches(pattern.substring(6))) return base + 200;
            } catch (Exception ignored) {}
            return -1;
        }
        if (pattern.startsWith("pattern:")) {
            return plain.equals(CitItemRenderer.stripFormatting(pattern.substring(8))) ? base : -1;
        }

        String want = pattern.startsWith("ipattern:")
            ? CitItemRenderer.stripFormatting(pattern.substring(9))
            : CitItemRenderer.stripFormatting(pattern);
        if (want.isBlank()) return base;

        String lower = plain.toLowerCase(Locale.ROOT);
        String wantLower = want.toLowerCase(Locale.ROOT);
        if (lower.equals(wantLower)) return base;
        if (lower.contains(wantLower)) return base + 500 + Math.max(0, 200 - wantLower.length());
        if (wantLower.length() >= 6 && wantLower.contains(lower) && !isGenericToken(lower)) {
            return base + 900;
        }
        return -1;
    }

    private static boolean isGenericToken(String lower) {
        return lower.equals("sword") || lower.equals("netherite") || lower.equals("minecraft")
            || lower.startsWith("minecraft:") || lower.endsWith("_sword");
    }

    private static boolean matchesIntRange(String spec, int value) {
        String trimmed = spec.trim();
        if (trimmed.isEmpty()) return true;
        if (trimmed.endsWith("%")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        try {
            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-", 2);
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                return value >= min && value <= max;
            }
            return value == Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return true;
        }
    }
}