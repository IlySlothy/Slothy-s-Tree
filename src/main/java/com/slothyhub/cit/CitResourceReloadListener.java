package com.slothyhub.cit;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.class_2960;
import net.minecraft.class_3300;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fabric resource reload listener — re-parses CIT rules whenever resource packs change.
 *
 * Scans ALL active resource packs for OptiFine / CIT Resewn compatible .properties files.
 * The files live at: assets/<namespace>/optifine/cit/**  (namespace is usually "minecraft")
 *
 * IMPORTANT: ResourceManager.findResources(String namespace, Predicate<Identifier> pathFilter)
 *            The first argument is the NAMESPACE ("minecraft"), NOT a path prefix.
 *            We then filter the identifier paths to find cit/ sub-paths.
 */
public final class CitResourceReloadListener implements SimpleSynchronousResourceReloadListener {

    private static final class_2960 ID = class_2960.method_60655("slothyhub", "cit_listener");

    /** Namespaces to scan — most packs use "minecraft", some use their own. */
    private static final String[] NAMESPACES = { "minecraft", "optifine", "citresewn" };

    /** Path prefixes within a namespace that contain CIT .properties files. */
    private static final String[] CIT_PATH_PREFIXES = {
        "optifine/cit/", "citresewn/cit/", "mcpatcher/cit/", "cit/"
    };

    @Override
    public class_2960 getFabricId() { return ID; }

    @Override
    public void method_14491(class_3300 manager) {
        if (!SlothyConfig.isCitEnabled()) {
            CitRuleSet.setActive(new CitRuleSet(List.of()));
            return;
        }

        List<CitRule> rules = new ArrayList<>();
        int scanned = 0;

        for (String namespace : NAMESPACES) {
            try {
                // findResources(namespace, pathPredicate) -> Map<Identifier, Resource>
                // First arg = namespace ("minecraft"), second = predicate on the full identifier
                Map<class_2960, ?> found = manager.method_14488(namespace, id -> {
                    String path = id.method_12836(); // Identifier#getPath()
                    if (!path.endsWith(".properties")) return false;
                    for (String prefix : CIT_PATH_PREFIXES) {
                        if (path.startsWith(prefix)) return true;
                    }
                    return false;
                });

                for (Map.Entry<class_2960, ?> entry : found.entrySet()) {
                    scanned++;
                    class_2960 rid = entry.getKey();
                    try {
                        InputStream in = openResource(entry.getValue());
                        if (in != null) {
                            try (in) {
                                CitRule rule = CitRuleParser.parse(rid.toString(), in);
                                if (rule != null) rules.add(rule);
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
    }

    /** Opens a Resource's InputStream via reflection (API varies slightly across MC versions). */
    private static InputStream openResource(Object resource) {
        if (resource == null) return null;
        // Try known intermediary/named method names
        for (String name : new String[]{"method_14482", "open", "getInputStream", "getReader"}) {
            try {
                java.lang.reflect.Method m = resource.getClass().getMethod(name);
                Object result = m.invoke(resource);
                if (result instanceof InputStream is) return is;
            } catch (Exception ignored) {}
        }
        // Fallback: any declared no-arg method returning InputStream
        for (java.lang.reflect.Method m : resource.getClass().getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && InputStream.class.isAssignableFrom(m.getReturnType())) {
                try {
                    m.setAccessible(true);
                    return (InputStream) m.invoke(resource);
                } catch (Exception ignored) {}
            }
        }
        return null;
    }
}
