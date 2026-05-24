package com.slothyhub.cit;

import com.slothyhub.SlothyHubMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
     * @param path      the resource path (used in logs and as an ID)
     * @param input     the raw .properties data
     * @return          the parsed rule, or null on error / unsupported type
     */
    public static CitRule parse(String path, InputStream input) {
        Properties props = new Properties();
        try {
            props.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException e) {
            SlothyHubMod.LOGGER.warn("CIT: failed to read {}: {}", path, e.getMessage());
            return null;
        }

        // Only handle "item" type (texture replacement) for now
        String type = props.getProperty("type", "item");
        if (!type.equalsIgnoreCase("item") && !type.equalsIgnoreCase("items")) {
            SlothyHubMod.LOGGER.debug("CIT: skipping unsupported type '{}' in {}", type, path);
            return null;
        }

        // Items this rule applies to
        String itemsRaw = props.getProperty("items", props.getProperty("matchItems", ""));
        if (itemsRaw.isBlank()) {
            SlothyHubMod.LOGGER.warn("CIT: no 'items' in {}", path);
            return null;
        }
        List<String> items = List.of(itemsRaw.trim().split("\\s+"));

        // Texture / model overrides
        String texture = props.getProperty("texture", props.getProperty("texture.bow", ""));
        String model   = props.getProperty("model", "");

        // Conditions
        String name         = props.getProperty("name",      "");
        String damage       = props.getProperty("damage",    "");
        String damageMask   = props.getProperty("damageMask","");
        String stackSize    = props.getProperty("stackSize", props.getProperty("stack", ""));
        List<CitRule.NbtCondition> nbt = parseNbtConditions(props);

        // OptiFine CIT uses nbt.display.Name= for name-based matching.
        // Promote it to the nameMatcher field so existing match logic handles it.
        if (name.isBlank()) {
            for (CitRule.NbtCondition c : nbt) {
                if ("display.Name".equalsIgnoreCase(c.path()) && !c.pattern().isBlank()) {
                    name = c.pattern();
                    break;
                }
            }
        }

        // Build the rule ID from its path
        String id = path;

        return new CitRule(id, items, texture, model, name, damage, damageMask, stackSize, nbt);
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
}
