package com.slothyhub.builder;

import net.fabricmc.loader.api.FabricLoader;
import com.slothyhub.compat.Identifiers;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3300;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Resolves pack folders and reads from the live {@link class_3300} (server + enabled packs). */
public final class ResourceScanHelper {

    private static final Method FIND_RESOURCES;
    private static final Method GET_RESOURCE;
    private static final Method GET_NAMESPACES;

    private ResourceScanHelper() {}

    public static class_3300 resourceManager() {
        class_310 mc = class_310.method_1551();
        if (mc == null) return null;
        try { return mc.method_1478(); } catch (Throwable ignored) {}
        for (Method m : class_310.class.getMethods()) {
            if (m.getParameterCount() == 0 && class_3300.class.isAssignableFrom(m.getReturnType())) {
                try { return (class_3300) m.invoke(mc); } catch (ReflectiveOperationException ignored) {}
            }
        }
        return null;
    }

    public static InputStream openResource(Object resource) {
        if (resource == null) return null;
        if (resource instanceof java.util.Optional<?> opt) {
            return opt.isPresent() ? openResource(opt.get()) : null;
        }
        if (resource instanceof java.util.List<?> list && !list.isEmpty()) {
            return openResource(list.get(0));
        }
        for (String name : new String[]{"method_14482", "open", "getInputStream"}) {
            try {
                Method m = resource.getClass().getMethod(name);
                Object result = m.invoke(resource);
                if (result instanceof InputStream is) return is;
            } catch (ReflectiveOperationException ignored) {}
        }
        for (Method m : resource.getClass().getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && InputStream.class.isAssignableFrom(m.getReturnType())) {
                try {
                    m.setAccessible(true);
                    return (InputStream) m.invoke(resource);
                } catch (ReflectiveOperationException ignored) {}
            }
        }
        return null;
    }

    /** Opens a stream from a findResources map entry, falling back to the identifier. */
    public static InputStream openMapEntry(class_3300 manager, class_2960 id, Object mapValue) {
        InputStream in = openResource(mapValue);
        if (in != null) return in;
        return openIdentifier(manager, id);
    }

    public static InputStream openIdentifier(class_3300 manager, class_2960 id) {
        if (manager == null || id == null) return null;

        // 1.21.4+: all pack layers for this id (highest priority first)
        try {
            Object listed = manager.method_14489(id);
            if (listed instanceof List<?> resources && !resources.isEmpty()) {
                InputStream in = openResource(resources.get(0));
                if (in != null) return in;
            }
        } catch (Throwable ignored) {}

        // Optional<Resource>
        try {
            Object opt = manager.method_14486(id);
            InputStream in = openResource(opt);
            if (in != null) return in;
        } catch (Throwable ignored) {}

        // ResourceFactory.open(id) when exposed on the manager
        for (Method m : manager.getClass().getMethods()) {
            if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != class_2960.class) continue;
            if (!InputStream.class.isAssignableFrom(m.getReturnType())) continue;
            try {
                return (InputStream) m.invoke(manager, id);
            } catch (ReflectiveOperationException ignored) {}
        }

        if (GET_RESOURCE != null) {
            try {
                return openResource(GET_RESOURCE.invoke(manager, id));
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    public static InputStream openPath(class_3300 manager, String assetPath) {
        if (manager == null || assetPath == null || assetPath.isBlank()) return null;
        String normalized = assetPath.replace('\\', '/');
        if (normalized.startsWith("assets/")) {
            int nsStart = "assets/".length();
            int nsEnd = normalized.indexOf('/', nsStart);
            if (nsEnd < 0) return null;
            String ns = normalized.substring(nsStart, nsEnd);
            String rel = normalized.substring(nsEnd + 1);
            return openIdentifier(manager, Identifiers.of(ns, rel));
        }
        return openIdentifier(manager, Identifiers.of("minecraft", normalized));
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map<class_2960, ?> findResources(class_3300 manager, String namespace,
                                                              Predicate<class_2960> filter) {
        if (manager == null) return java.util.Map.of();
        if (FIND_RESOURCES != null) {
            try {
                Object result = FIND_RESOURCES.invoke(manager, namespace, filter);
                if (result instanceof java.util.Map<?, ?> map) {
                    return (java.util.Map<class_2960, ?>) map;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
        try {
            return manager.method_14488(namespace, filter);
        } catch (Throwable ignored) {
            return java.util.Map.of();
        }
    }

    public static Set<String> namespaces(class_3300 manager) {
        Set<String> out = new LinkedHashSet<>();
        out.add("minecraft");
        out.add("optifine");
        out.add("citresewn");
        if (manager == null) return out;
        if (GET_NAMESPACES != null) {
            try {
                Object result = GET_NAMESPACES.invoke(manager);
                if (result instanceof Iterable<?> it) {
                    for (Object o : it) out.add(String.valueOf(o));
                }
            } catch (ReflectiveOperationException ignored) {}
        } else {
            try {
                out.addAll(manager.method_14487());
            } catch (Throwable ignored) {}
        }
        return out;
    }

    public static List<Path> collectPackRoots(Path resourcePacksDir, Path localPackDir) {
        List<Path> roots = new ArrayList<>();
        if (Files.isDirectory(resourcePacksDir)) {
            try (Stream<Path> entries = Files.list(resourcePacksDir)) {
                for (Path entry : entries.toList()) {
                    collectRootEntry(entry, localPackDir, roots);
                }
            } catch (Exception ignored) {}
        }
        Path gameDir = FabricLoader.getInstance().getGameDir();
        addPackRootsFromDir(gameDir.resolve("server-resource-packs"), roots);
        addPackRootsFromDir(gameDir.resolve("downloads"), roots);
        return roots;
    }

    private static void collectRootEntry(Path entry, Path localPackDir, List<Path> roots) {
        String name = entry.getFileName().toString();
        if (name.startsWith(".")) return;
        if (name.equalsIgnoreCase("slothyhub-local")) {
            addPackRootsFromDir(localPackDir != null ? localPackDir : entry, roots);
            return;
        }
        if (isPackRoot(entry)) roots.add(entry);
    }

    private static void addPackRootsFromDir(Path dir, List<Path> roots) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> entries = Files.list(dir)) {
            for (Path entry : entries.toList()) {
                if (isPackRoot(entry)) roots.add(entry);
            }
        } catch (Exception ignored) {}
    }

    public static boolean isPackRoot(Path p) {
        String n = p.getFileName().toString();
        if (n.startsWith(".")) return false;
        if (Files.isDirectory(p)) return Files.exists(p.resolve("pack.mcmeta"));
        return n.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    static {
        Method find = null;
        Method get = null;
        Method ns = null;
        for (Method m : class_3300.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == String.class && Predicate.class.isAssignableFrom(p[1])
                && java.util.Map.class.isAssignableFrom(m.getReturnType())) {
                find = m;
            }
            if (p.length == 1 && p[0] == class_2960.class && m.getReturnType() != void.class) {
                get = m;
            }
            if (p.length == 0 && Iterable.class.isAssignableFrom(m.getReturnType())
                && m.getName().contains("14487")) {
                ns = m;
            }
        }
        if (find != null) find.setAccessible(true);
        if (get != null) get.setAccessible(true);
        if (ns != null) ns.setAccessible(true);
        FIND_RESOURCES = find;
        GET_RESOURCE = get;
        GET_NAMESPACES = ns;
    }
}
