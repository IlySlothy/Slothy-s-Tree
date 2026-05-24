package com.slothyhub.compat;

import java.util.Collection;
import net.minecraft.class_3283;

public final class VersionCompat {

    private VersionCompat() {}

    public static Collection<String> enabledNames(class_3283 manager) {
        return VersionCompatImpl.enabledNames(manager);
    }

    public static Collection<String> profileIds(class_3283 manager) {
        return VersionCompatImpl.profileIds(manager);
    }

    public static void refresh(class_3283 manager) {
        VersionCompatImpl.refresh(manager);
    }

    public static void setEnabled(class_3283 manager, Collection<String> enabled) {
        VersionCompatImpl.setEnabled(manager, enabled);
    }

    public static boolean hasProfile(class_3283 manager, String id) {
        return VersionCompatImpl.hasProfile(manager, id);
    }
}
