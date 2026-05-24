package com.slothyhub.kill;

import com.slothyhub.SlothyConfig;
import com.slothyhub.compat.Identifiers;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_3414;
import net.minecraft.class_3417;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolves totem texture + sound from applied resource packs and SlothyHub config. */
public final class KillEffectAssets {

    private static volatile class_2960 cachedTotemTex;
    private static volatile long cacheMs;

    private KillEffectAssets() {}

    public static class_2960 totemTextureId() {
        refreshIfStale();
        return cachedTotemTex != null ? cachedTotemTex : parseId(SlothyConfig.getKillTotemTexture(), "item/totem_of_undying");
    }

    public static void playTotemSound() {
        refreshIfStale();
        // Pack sounds.json overrides apply to the vanilla totem-use event automatically.
        playSound(class_3417.field_14931, 1.0f, 0.8f);
    }

    /** Call after resource reload or pack apply. */
    public static void invalidate() {
        cacheMs = 0;
        cachedTotemTex = null;
    }

    private static void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (now - cacheMs < 3000 && cachedTotemTex != null) return;
        cacheMs = now;

        cachedTotemTex = resolveTotemFromPacks();
    }

    private static class_2960 resolveTotemFromPacks() {
        return parseId(SlothyConfig.getKillTotemTexture(), "item/totem_of_undying");
    }

    private static class_2960 parseId(String raw, String fallbackPath) {
        try {
            if (raw == null || raw.isBlank()) return Identifiers.of("minecraft", fallbackPath);
            if (raw.contains(":")) {
                int c = raw.indexOf(':');
                return Identifiers.of(raw.substring(0, c), raw.substring(c + 1));
            }
            return Identifiers.of("minecraft", raw);
        } catch (Exception e) {
            return Identifiers.of("minecraft", fallbackPath);
        }
    }

    private static volatile Method SOUND_PLAY;
    private static volatile Method SOUND_MASTER;
    private static boolean SOUND_RESOLVED;

    private static void playSound(class_3414 event, float pitch, float volume) {
        resolveSoundMethods();
        if (SOUND_MASTER != null && SOUND_PLAY != null) {
            try {
                Object instance = SOUND_MASTER.invoke(null, event, pitch, volume);
                SOUND_PLAY.invoke(class_310.method_1551().method_1483(), instance);
            } catch (Exception ignored) {}
        }
    }

    private static void resolveSoundMethods() {
        if (SOUND_RESOLVED) return;
        SOUND_RESOLVED = true;
        try {
            Class<?> smClass = class_310.method_1551().method_1483().getClass();
            for (Method m : smClass.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && net.minecraft.class_1113.class.isAssignableFrom(p[0])) {
                    m.setAccessible(true); SOUND_PLAY = m; break;
                }
            }
        } catch (Exception ignored) {}
        try {
            for (String name : new String[]{"tick", "master", "ui"}) {
                try {
                    Method m = net.minecraft.class_1109.class.getMethod(name, class_3414.class, float.class, float.class);
                    if (Modifier.isStatic(m.getModifiers())) { m.setAccessible(true); SOUND_MASTER = m; break; }
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
