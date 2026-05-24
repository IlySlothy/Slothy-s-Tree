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
}
