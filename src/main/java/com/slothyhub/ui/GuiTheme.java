package com.slothyhub.ui;

import com.slothyhub.SlothyConfig;

/** SlothyHub client GUI palette — persisted via {@link SlothyConfig}. */
public final class GuiTheme {

    public static final int DEFAULT_BG      = 0xFF0B1410;
    public static final int DEFAULT_PANEL   = 0xFF131D18;
    public static final int DEFAULT_SURFACE = 0xFF1D2F24;
    public static final int DEFAULT_ACCENT  = 0xFF52D47A;
    public static final int DEFAULT_ACCENT_H = 0xFF7DE89C;
    public static final int DEFAULT_DANGER  = 0xFFDE5050;
    public static final int DEFAULT_TEXT    = 0xFFECF5EE;
    public static final int DEFAULT_MUTED   = 0xFF7A9E84;
    public static final int DEFAULT_BORDER  = 0xFF253C2C;
    public static final int DEFAULT_DIM     = 0xFF4A6054;
    public static final int DEFAULT_GOLD    = 0xFFF0C040;

    public record Preset(String id, String label, int bg, int panel, int surface, int accent, int text, int muted, int border) {}

    public static final Preset[] PRESETS = {
        new Preset("forest",  "Forest",  DEFAULT_BG, DEFAULT_PANEL, DEFAULT_SURFACE, DEFAULT_ACCENT, DEFAULT_TEXT, DEFAULT_MUTED, DEFAULT_BORDER),
        new Preset("ocean",   "Ocean",   0xFF0A1218, 0xFF101C28, 0xFF1A3040, 0xFF4AB8E8, 0xFFE8F4FA, 0xFF7AA0B8, 0xFF243848),
        new Preset("ember",   "Ember",   0xFF140C0A, 0xFF201410, 0xFF302018, 0xFFE87840, 0xFFFAEEE8, 0xFFB89078, 0xFF402820),
        new Preset("violet",  "Violet",  0xFF100C18, 0xFF181024, 0xFF241834, 0xFF9B6DFF, 0xFFF0EAFA, 0xFF9888B0, 0xFF302040),
        new Preset("mono",    "Mono",    0xFF0E0E0E, 0xFF161616, 0xFF222222, 0xFFCCCCCC, 0xFFF0F0F0, 0xFF888888, 0xFF333333),
    };

    private GuiTheme() {}

    public static int bg()       { return SlothyConfig.getThemeBg(); }
    public static int panel()    { return SlothyConfig.getThemePanel(); }
    public static int surface()  { return SlothyConfig.getThemeSurface(); }
    public static int accent()   { return SlothyConfig.getThemeAccent(); }
    public static int accentH()  { return Ui.lerpColor(accent(), 0xFFFFFFFF, 0.35f) & 0xFFFFFF | 0xFF000000; }
    public static int danger()   { return DEFAULT_DANGER; }
    public static int text()     { return SlothyConfig.getThemeText(); }
    public static int muted()    { return SlothyConfig.getThemeMuted(); }
    public static int border()   { return SlothyConfig.getThemeBorder(); }
    public static int dim()      { return Ui.lerpColor(muted(), bg(), 0.45f); }
    public static int gold()     { return DEFAULT_GOLD; }

    public static void applyPreset(String id) {
        for (Preset p : PRESETS) {
            if (p.id().equals(id)) {
                SlothyConfig.setTheme(p.bg(), p.panel(), p.surface(), p.accent(), p.text(), p.muted(), p.border(), id);
                Ui.reloadTheme();
                return;
            }
        }
    }

    public static void applyCustom(int bg, int panel, int surface, int accent, int text, int muted, int border) {
        SlothyConfig.setTheme(bg, panel, surface, accent, text, muted, border, "custom");
        Ui.reloadTheme();
    }

    public static void applyCustomLive(int bg, int panel, int surface, int accent, int text, int muted, int border) {
        SlothyConfig.setThemeLive(bg, panel, surface, accent, text, muted, border);
        Ui.reloadTheme();
    }

    /** Read current theme into ARGB array: bg, panel, surface, accent, text, muted, border. */
    public static int[] currentColors() {
        return new int[] { bg(), panel(), surface(), accent(), text(), muted(), border() };
    }

    public static float[] rgbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h = 0f;
        if (delta > 0.0001f) {
            if (max == r) h = ((g - b) / delta) % 6f;
            else if (max == g) h = (b - r) / delta + 2f;
            else h = (r - g) / delta + 4f;
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        float s = max <= 0f ? 0f : delta / max;
        return new float[] { h, s, max };
    }

    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = Math.max(0f, Math.min(1f, s));
        v = Math.max(0f, Math.min(1f, v));
        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        int sector = (int) (h * 6f) % 6;
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        int ri = (int) ((r + m) * 255f);
        int gi = (int) ((g + m) * 255f);
        int bi = (int) ((b + m) * 255f);
        return 0xFF000000 | ri << 16 | gi << 8 | bi;
    }
}
