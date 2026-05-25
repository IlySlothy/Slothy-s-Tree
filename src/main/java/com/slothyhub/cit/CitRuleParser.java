package com.slothyhub.cit;

import com.slothyhub.SlothyHubMod;
import net.minecraft.class_2960;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Parses OptiFine/CIT Resewn {@code .properties} files into {@link CitRule} objects.
 *
 * Supports the common subset of CIT properties:
 * type, items, matchItems, texture, model, nbt.*, damage, damageMask, stackSize, name
 */
public final class CitRuleParser {

    private CitRuleParser() {}

    /**
     * Parse a single .properties stream into a CitRule.
     *
     * @param rid       the resource identifier (used as rule id and for PNG resolution)
     * @param input     the raw .properties data
     * @return          the parsed rule, or null on error / unsupported type
     */
    public static CitRule parse(class_2960 rid, InputStream input) {
        Properties props = new Properties();
        try {
            props.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("CIT: failed to read {}: {}", rid, e.getMessage());
            return null;
        }

        // Item + armor piece rules (CIT Resewn / OptiFine compatible subset)
        String type = props.getProperty("type", "item");
        if (!isSupportedType(type)) {
            SlothyHubMod.LOGGER.debug("CIT: skipping unsupported type '{}' in {}", type, rid);
            return null;
        }

        // Items this rule applies to (infer from path when missing — common in armor folders)
        String itemsRaw = props.getProperty("items", props.getProperty("matchItems", ""));
        List<String> items;
        if (itemsRaw.isBlank()) {
            items = inferItemsFromPath(rid, type);
            if (items.isEmpty()) {
                SlothyHubMod.LOGGER.warn("CIT: no 'items' in {}", rid);
                return null;
            }
        } else {
            items = expandItems(itemsRaw, rid, type);
        }

        // Texture / model overrides
        String texture = props.getProperty("texture", props.getProperty("texture.bow", ""));
        if (texture.isBlank()) {
            texture = firstTextureProperty(props);
        }
        String model   = props.getProperty("model", "");

        // Conditions
        String name         = props.getProperty("name",      "");
        String damage       = props.getProperty("damage",    "");
        String damageMask   = props.getProperty("damageMask","");
        String stackSize    = props.getProperty("stackSize", props.getProperty("stack", ""));
        List<CitRule.NbtCondition> nbt = parseNbtConditions(props);

        // OptiFine / CIT Resewn name conditions
        if (name.isBlank()) {
            for (CitRule.NbtCondition c : nbt) {
                if ("display.Name".equalsIgnoreCase(c.path()) && !c.pattern().isBlank()) {
                    name = c.pattern();
                    break;
                }
            }
        }
        if (name.isBlank()) {
            name = props.getProperty("components.minecraft\\:custom_name", "");
            if (name.isBlank())
                name = props.getProperty("components.minecraft:custom_name", "");
        }
        if (name.isBlank()) {
            name = props.getProperty("components.minecraft\\:item_name", "");
            if (name.isBlank())
                name = props.getProperty("components.minecraft:item_name", "");
        }

        // Build stable rule id from the resource identifier
        String id = rid.method_12836() + ":" + rid.method_12832();
        name = normalizeCitName(name, rid, texture);

        return new CitRule(id, items, texture, model, name, damage, damageMask, stackSize, nbt);
    }

    /**
     * Repair common Texture Builder mistakes: concatenated names in one ipattern field.
     * Falls back to the .properties filename (e.g. perfect_sword → Perfect Sword).
     */
    static String normalizeCitName(String name, class_2960 rid, String texture) {
        if (name == null || name.isBlank()) return name;

        String prefix = "";
        String body = name.trim();
        if (body.regionMatches(true, 0, "ipattern:", 0, 9)) {
            prefix = body.substring(0, 9);
            body = body.substring(9).trim();
        } else if (body.regionMatches(true, 0, "pattern:", 0, 8)) {
            prefix = body.substring(0, 8);
            body = body.substring(8).trim();
        } else if (body.regionMatches(true, 0, "regex:", 0, 6)) {
            return name;
        }

        // Step 1: collapse any exact repetition of a substring (e.g. "Noob SwordNoob Sword" → "Noob Sword").
        // This runs even before the expected-name comparison so things like "FooFooFoo" → "Foo".
        String collapsed = reduceExactRepetition(body);
        if (collapsed != null && !collapsed.equals(body)) {
            SlothyHubMod.LOGGER.warn("CIT: collapsed repeated name in {} ('{}' → '{}')",
                rid, body, collapsed);
            body = collapsed;
        }

        String expected = expectedNameFromRule(rid, texture);
        if (expected == null || expected.isBlank()) return prefix + body;

        String bodyLower = body.toLowerCase(Locale.ROOT);
        String expectedLower = expected.toLowerCase(Locale.ROOT);

        // Exact or clean match — keep as-is
        if (bodyLower.equals(expectedLower)) return prefix + expected;

        // Step 2: if body is a doubled or N-times repeat of expected, just return expected.
        if (isRepetitionOf(bodyLower, expectedLower)) {
            SlothyHubMod.LOGGER.warn("CIT: repaired N-repeat name in {} ('{}' → '{}')",
                rid, body, expected);
            return prefix + expected;
        }

        // Valid compound names like "Good Boots" must not be truncated to filename "Boots".
        // Require a separating space before the expected suffix so "NoobSwordNoob Sword" still falls through.
        if (bodyLower.endsWith(" " + expectedLower) && body.length() <= expected.length() + 12) {
            return prefix + body;
        }
        if (bodyLower.startsWith(expectedLower + " ") || bodyLower.contains(" " + expectedLower)) {
            return prefix + body;
        }

        // Corrupted concat (multiple sword names run together) — use filename-derived name
        if (countEmbeddedSwordNames(bodyLower) > 1) {
            SlothyHubMod.LOGGER.warn("CIT: repaired multi-name field in {} ('{}' → '{}')",
                rid, body, expected);
            return prefix + expected;
        }

        if (bodyLower.contains(expectedLower) && body.length() > expected.length() + 2) {
            // Keep valid tiered names like "Good Boots" when filename only yields "Boots"
            if (!bodyLower.equals(expectedLower) && bodyLower.endsWith(" " + expectedLower)) {
                return prefix + body;
            }
            SlothyHubMod.LOGGER.warn("CIT: repaired corrupted name in {} ('{}' → '{}')",
                rid, body, expected);
            return prefix + expected;
        }

        // Body embeds expected name but has extra junk from another slot's name
        if (looksConcatenated(body, expected)) {
            SlothyHubMod.LOGGER.warn("CIT: repaired concatenated name in {} ('{}' → '{}')",
                rid, body, expected);
            return prefix + expected;
        }

        return prefix + body;
    }

    /**
     * If {@code s} consists of an exact repetition of some prefix of length {@code k}
     * (where 2 ≤ k ≤ len/2 and len is a multiple of k), return that prefix; otherwise
     * return {@code s} unchanged.
     *
     * Examples:
     *   "Noob SwordNoob Sword"            → "Noob Sword"
     *   "Good SwordGood SwordGood Sword"  → "Good Sword"
     *   "Pro SwordPro Sword"              → "Pro Sword"
     *   "Hello"                           → "Hello"
     */
    static String reduceExactRepetition(String s) {
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

    /** Case-insensitive repetition check (body is some integer repeat of unit, k ≥ 1, repeat ≥ 2). */
    private static boolean isRepetitionOf(String bodyLower, String unitLower) {
        if (unitLower == null || unitLower.isEmpty()) return false;
        if (bodyLower.length() < unitLower.length() * 2) return false;
        if (bodyLower.length() % unitLower.length() != 0) return false;
        for (int i = 0; i < bodyLower.length(); i += unitLower.length()) {
            if (!bodyLower.regionMatches(i, unitLower, 0, unitLower.length())) return false;
        }
        return true;
    }

    private static boolean looksConcatenated(String body, String expected) {
        String lower = body.toLowerCase(Locale.ROOT);
        String exp = expected.toLowerCase(Locale.ROOT);
        if (!lower.contains(exp)) return false;
        // "Noob SwordNoob Sword" — same phrase repeated
        if (lower.replace(exp, "").trim().length() > 2 && lower.contains(exp + exp.substring(0, Math.min(3, exp.length())))) {
            return true;
        }
        // Multiple Title Case words run together without matching expected only
        return body.length() > expected.length() + 4 && countEmbeddedSwordNames(lower) > 1;
    }

    private static int countEmbeddedSwordNames(String lower) {
        String[] known = { "noob sword", "good sword", "pro sword", "perfect sword", "warden sword" };
        int n = 0;
        for (String k : known) {
            if (lower.contains(k)) n++;
        }
        return n;
    }

    private static String expectedNameFromRule(class_2960 rid, String texture) {
        String path = rid.method_12832();
        String armorPiece = expectedArmorPieceName(path);
        if (armorPiece != null) return armorPiece;

        String fromFile = baseNameFromPath(path);
        if (fromFile != null && !isArmorSlotFile(fromFile)) return humanizeUnderscore(fromFile);

        if (texture != null && !texture.isBlank()) {
            String t = texture.replace('\\', '/');
            int slash = t.lastIndexOf('/');
            if (slash >= 0) t = t.substring(slash + 1);
            if (!t.isBlank()) return humanizeUnderscore(t);
        }
        return null;
    }

    private static boolean isArmorSlotFile(String baseName) {
        if (baseName == null) return false;
        String lower = baseName.toLowerCase(Locale.ROOT);
        return lower.equals("boots") || lower.equals("helmet")
            || lower.equals("chestplate") || lower.equals("leggings");
    }

    private static String baseNameFromPath(String path) {
        if (path == null || !path.endsWith(".properties")) return null;
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        return file.substring(0, file.length() - ".properties".length());
    }

    /** e.g. optifine/cit/good_armor/boots.properties → "Good Boots" */
    private static String expectedArmorPieceName(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase(Locale.ROOT);
        String slot = armorSlotFromPath(lower);
        if (slot == null) return null;
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return humanizeUnderscore(slot);
        String dir = path.substring(0, slash);
        int prevSlash = dir.lastIndexOf('/');
        String folder = prevSlash >= 0 ? dir.substring(prevSlash + 1) : dir;
        String tier = folder;
        if (tier.endsWith("_armor")) tier = tier.substring(0, tier.length() - "_armor".length());
        else if (tier.endsWith("armor")) tier = tier.substring(0, Math.max(0, tier.length() - 5));
        String tierName = humanizeUnderscore(tier);
        String slotName = humanizeUnderscore(slot);
        if (tierName == null || tierName.isBlank()) return slotName;
        return tierName + " " + slotName;
    }

    private static String humanizeUnderscore(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static List<CitRule.NbtCondition> parseNbtConditions(Properties props) {
        List<CitRule.NbtCondition> out = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("nbt.")) continue;
            String nbtPath = key.substring(4);
            String pattern  = props.getProperty(key, "");
            out.add(new CitRule.NbtCondition(nbtPath, pattern));
        }
        return out;
    }

    private static boolean isSupportedType(String type) {
        if (type == null || type.isBlank()) return true;
        String t = type.trim().toLowerCase(Locale.ROOT);
        return t.equals("item") || t.equals("items") || t.equals("armor");
    }

    /** Infer item ids from armor/sword folder layout when {@code items=} is omitted. */
    private static List<String> inferItemsFromPath(class_2960 rid, String type) {
        String path = rid.method_12832().toLowerCase(Locale.ROOT);
        String slot = armorSlotFromPath(path);
        if (slot != null) {
            String material = armorMaterialFromPath(path);
            return List.of(material + "_" + slot);
        }
        if ("armor".equalsIgnoreCase(type)) {
            return List.of();
        }
        return List.of();
    }

    private static String armorSlotFromPath(String path) {
        if (path.endsWith("/boots.properties") || path.endsWith("boots.properties")) return "boots";
        if (path.endsWith("/helmet.properties") || path.endsWith("helmet.properties")) return "helmet";
        if (path.endsWith("/chestplate.properties") || path.endsWith("chestplate.properties")) return "chestplate";
        if (path.endsWith("/leggings.properties") || path.endsWith("leggings.properties")) return "leggings";
        return null;
    }

    private static String armorMaterialFromPath(String path) {
        if (path.contains("leather")) return "leather";
        if (path.contains("chain")) return "chainmail";
        if (path.contains("iron")) return "iron";
        if (path.contains("gold")) return "golden";
        if (path.contains("diamond")) return "diamond";
        // Hypixel-style tier folders use netherite armor with custom textures
        return "netherite";
    }

    private static List<String> expandItems(String itemsRaw, class_2960 rid, String type) {
        String path = rid.method_12832().toLowerCase(Locale.ROOT);
        String slot = armorSlotFromPath(path);
        String[] tokens = itemsRaw.trim().split("\\s+");
        List<String> out = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (token.contains("_") || token.contains(":")) {
                out.add(token);
                continue;
            }
            if (slot != null) {
                out.add(normalizeMaterial(token) + "_" + slot);
            } else {
                out.add(token);
            }
        }
        return out.isEmpty() ? List.of(itemsRaw.trim()) : out;
    }

    private static String normalizeMaterial(String material) {
        return switch (material.toLowerCase(Locale.ROOT)) {
            case "gold" -> "golden";
            case "chain" -> "chainmail";
            default -> material.toLowerCase(Locale.ROOT);
        };
    }

    private static String firstTextureProperty(Properties props) {
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("texture")) continue;
            String value = props.getProperty(key, "").trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }
}
