package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.builder.ResourceScanHelper;
import com.slothyhub.compat.Identifiers;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.class_2960;
import net.minecraft.class_3300;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class CitResourceReloadListener implements SimpleSynchronousResourceReloadListener {

    private static final class_2960 ID = Identifiers.of("slothyhub", "cit_listener");

    private static final String[] CIT_PATH_PREFIXES = {
        "optifine/cit/", "citresewn/cit/", "mcpatcher/cit/", "cit/"
    };

    @Override
    public class_2960 getFabricId() { return ID; }

    @Override
    public void method_14491(class_3300 manager) {
        reloadCit(manager);
    }

    /** Parse CIT rules from resource packs (shared by main 1.21.8 and slothyhub-cit on 1.21.9+). */
    public static void reloadCitProperties(class_3300 manager) {
        if (!SlothyConfig.isCitEnabled()) {
            CitRuleSet.setActive(new CitRuleSet(List.of()));
            CitVirtualTextures.clear();
            return;
        }
        if (manager == null) return;

        List<CitRule> rules = new ArrayList<>();
        int scanned = 0;
        Set<String> namespaces = ResourceScanHelper.namespaces(manager);

        for (String namespace : namespaces) {
            try {
                Map<class_2960, ?> found = ResourceScanHelper.findResources(
                    manager, namespace, citPropertiesFilter());

                for (Map.Entry<class_2960, ?> entry : found.entrySet()) {
                    scanned++;
                    class_2960 rid = entry.getKey();
                    try {
                        InputStream in = ResourceScanHelper.openMapEntry(manager, rid, entry.getValue());
                        if (in == null) {
                            SlothyHubMod.LOGGER.warn("CIT: could not open {}", rid);
                            continue;
                        }
                        try (InputStream stream = in) {
                            CitRule rule = CitRuleParser.parse(rid, stream);
                            if (rule != null) {
                                rules.add(rule);
                            } else {
                                SlothyHubMod.LOGGER.warn("CIT: skipped {} (unsupported or invalid rule)", rid);
                            }
                        }
                    } catch (Exception e) {
                        SlothyHubMod.LOGGER.warn("CIT: could not parse {}: {}", rid, e.getMessage());
                    }
                }
            } catch (Exception e) {
                SlothyHubMod.LOGGER.debug("CIT: scan failed for namespace '{}': {}", namespace, e.getMessage());
            }
        }

        CitRuleSet.setActive(new CitRuleSet(rules));
        SlothyHubMod.LOGGER.info("CIT: scanned {} properties files, loaded {} rules.", scanned, rules.size());
        for (CitRule r : rules) {
            SlothyHubMod.LOGGER.info("CIT: rule {} items={} name='{}' texture={}",
                r.id, r.items, r.nameMatcher, r.texture);
        }
    }

    static void reloadCit(class_3300 manager) {
        reloadCitProperties(manager);
        if (!SlothyConfig.isCitEnabled() || manager == null) return;
        scheduleSpriteRebuild(manager);
    }

    /** Atlas stitch finishes after this listener — rebuild sprites on the next client tick. */
    private static void scheduleSpriteRebuild(class_3300 manager) {
        net.minecraft.class_310 mc = net.minecraft.class_310.method_1551();
        CitRuleSet active = CitRuleSet.active();
        Runnable rebuild = () -> {
            class_3300 live = manager != null ? manager : ResourceScanHelper.resourceManager();
            if (live != null) CitVirtualTextures.rebuild(live, active);
        };
        if (mc != null) {
            mc.execute(() -> mc.execute(rebuild));
        } else {
            rebuild.run();
        }
    }

    private static Predicate<class_2960> citPropertiesFilter() {
        return id -> {
            String path = id.method_12832();
            if (!path.endsWith(".properties")) return false;
            for (String prefix : CIT_PATH_PREFIXES) {
                if (path.startsWith(prefix)) return true;
            }
            return false;
        };
    }
}