package com.slothyhub.compat;

import net.fabricmc.loader.api.FabricLoader;

/** MC version helpers shared across CIT, pack format, and feature gates. */
public final class McVersion {

    private static String cached;

    private McVersion() {}

    public static String current() {
        if (cached == null) {
            cached = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(c -> normalize(c.getMetadata().getVersion().getFriendlyString()))
                .orElse("0");
        }
        return cached;
    }

    public static boolean atLeast(String minimum) {
        return compare(current(), minimum) >= 0;
    }

    public static boolean below(String maximumExclusive) {
        return compare(current(), maximumExclusive) < 0;
    }

    public static String normalize(String version) {
        int dash = version.indexOf('-');
        return dash > 0 ? version.substring(0, dash) : version;
    }

    public static int compare(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int va = i < a.length ? parsePart(a[i]) : 0;
            int vb = i < b.length ? parsePart(b[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9].*", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
