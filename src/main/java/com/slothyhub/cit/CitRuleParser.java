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

        String expected = expectedNameFromRule(rid, texture);
        if (expected == null || expected.isBlank()) return name;

        String bodyLower = body.toLowerCase(Locale.ROOT);
        String expectedLower = expected.toLowerCase(Locale.ROOT);

        // Exact or clean match — keep as-is
        if (bodyLower.equals(expectedLower)) return prefix + expected;

        // Corrupted concat (multiple sword names run together) — use filename-derived name
        if (countEmbeddedSwordNames(bodyLower) > 1) {
            SlothyHubMod.LOGGER.warn("CIT: repaired multi-name field in {} ('{}' → '{}')",
                rid, body, expected);
            return prefix + expected;
        }

        if (bodyLower.contains(expectedLower) && body.length() > expected.length() + 2) {
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

        return name;
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
        String fromFile = baseNameFromPath(rid.method_12832());
        if (fromFile != null) return humanizeUnderscore(fromFile);

        if (texture != null && !texture.isBlank()) {
            String t = texture.replace('\\', '/');
            int slash = t.lastIndexOf('/');
            if (slash >= 0) t = t.substring(slash + 1);
            if (!t.isBlank()) return humanizeUnderscore(t);
        }
        return null;
    }

    private static String baseNameFromPath(String path) {
        if (path == null || !path.endsWith(".properties")) return null;
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        return file.substring(0, file.length() - ".properties".length());
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
