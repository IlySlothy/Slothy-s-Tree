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
 * Scans OptiFine/CIT Resewn compatible .properties files under
 * assets/NAMESPACE/optifine/cit/ and assets/NAMESPACE/citresewn/cit/.
 */
public final class CitResourceReloadListener implements SimpleSynchronousResourceReloadListener {

    private static final class_2960 ID = class_2960.method_60655("slothyhub", "cit_listener");

    private static final String[] SCAN_PREFIXES = {
        "optifine/cit/", "citresewn/cit/", "mcpatcher/cit/"
    };

    @Override
    public class_2960 getFabricId() { return ID; }

    /**
     * Intermediary name for SimpleSynchronousResourceReloadListener#reload(ResourceManager).
     */
    @Override
    public void method_14491(class_3300 manager) {
        if (!SlothyConfig.isCitEnabled()) {
            CitRuleSet.setActive(new CitRuleSet(List.of()));
            return;
        }
        List<CitRule> rules = new ArrayList<>();
        for (String prefix : SCAN_PREFIXES) {
            try {
                // findResources(String namespace, Predicate<Identifier>) -> Map<Identifier, Resource>
                Map<class_2960, ?> found = manager.method_14488(prefix,
                    id -> id.toString().endsWith(".properties"));
                for (Map.Entry<class_2960, ?> entry : found.entrySet()) {
                    class_2960 id = entry.getKey();
                    Object resource = entry.getValue();
                    try {
                        InputStream in = openResource(resource);
                        if (in != null) {
                            try (in) {
                                CitRule rule = CitRuleParser.parse(id.toString(), in);
                                if (rule != null) rules.add(rule);
                            }
                        }
                    } catch (Exception e) {
                        SlothyHubMod.LOGGER.warn("CIT: could not load {}: {}", id, e.getMessage());
                    }
                }
            } catch (Exception e) {
                SlothyHubMod.LOGGER.debug("CIT: findResources failed for prefix {}: {}", prefix, e.getMessage());
            }
        }
        CitRuleSet.setActive(new CitRuleSet(rules));
    }

    /** Opens a resource's InputStream via reflection (Resource API varies by MC version). */
    private static InputStream openResource(Object resource) {
        if (resource == null) return null;
        for (String name : new String[]{"method_14482", "open", "getInputStream"}) {
            try {
                java.lang.reflect.Method m = resource.getClass().getMethod(name);
                Object result = m.invoke(resource);
                if (result instanceof InputStream is) return is;
            } catch (Exception ignored) {}
        }
        // Try getDeclaredMethods too (protected methods)
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
