package com.slothyhub.ui;

import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.Identifiers;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2960;
import net.minecraft.class_310;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Registers GUI textures that must be bound at runtime (DrawHelper cannot rely on loose PNG paths alone). */
public final class GuiAssets {

    private static class_2960 logoId;
    private static int logoW = 16;
    private static int logoH = 16;
    private static boolean logoReady;

    private GuiAssets() {}

    public static void init() {
        class_310 mc = class_310.method_1551();
        if (mc != null) mc.execute(GuiAssets::loadLogo);
        else loadLogo();
    }

    public static boolean hasLogo() {
        return logoReady && logoId != null;
    }

    public static class_2960 logoId() {
        return logoId;
    }

    public static int logoWidth() {
        return logoW;
    }

    public static int logoHeight() {
        return logoH;
    }

    private static void loadLogo() {
        if (logoReady) return;
        try {
            var container = FabricLoader.getInstance().getModContainer(SlothyHubMod.MOD_ID).orElse(null);
            if (container == null) return;

            InputStream stream = null;
            for (Path root : container.getRootPaths()) {
                Path icon = root.resolve("assets/slothyhub/icon.png");
                if (Files.isRegularFile(icon)) {
                    stream = Files.newInputStream(icon);
                    break;
                }
            }
            if (stream == null) return;

            try (InputStream in = stream) {
                class_1011 img = class_1011.method_4309(in);
                logoW = img.method_4307();
                logoH = img.method_4323();
                logoId = Identifiers.of(SlothyHubMod.MOD_ID, "gui/sloth_badge");
                class_1043 tex = DrawHelper.createNativeTexture("slothyhub_gui_logo", img);
                class_310 mc = class_310.method_1551();
                if (tex != null && mc != null && mc.method_1531() != null) {
                    DrawHelper.registerDynamicTexture(logoId, tex, img);
                    logoReady = true;
                }
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.debug("GuiAssets: could not load logo: {}", e.getMessage());
        }
    }
}
