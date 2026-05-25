package com.slothyhub.ui;

import com.slothyhub.PackIconLoader;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.DrawHelper;
import com.slothyhub.compat.Identifiers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    private static boolean logoAttempted;

    private static final String[] LOGO_PATHS = {
        "assets/slothyhub/textures/gui/logo.png",
        "assets/slothyhub/textures/gui/sloth_badge.png",
        "assets/slothyhub/icon.png"
    };

    private GuiAssets() {}

    public static void init() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> client.execute(GuiAssets::loadLogo));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (logoReady || logoAttempted) return;
            if (client == null || client.method_1531() == null) return;
            client.execute(GuiAssets::loadLogo);
        });
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
        if (logoReady || logoAttempted) return;
        logoAttempted = true;
        try {
            String source = null;
            byte[] png = null;
            for (String rel : LOGO_PATHS) {
                png = readLogoBytes(rel);
                if (png != null) {
                    source = rel;
                    break;
                }
            }
            if (png == null) {
                SlothyHubMod.LOGGER.warn("GuiAssets: logo PNG not found (checked {})", String.join(", ", LOGO_PATHS));
                return;
            }
            if (!PackIconLoader.isValidPng(png)) {
                SlothyHubMod.LOGGER.warn("GuiAssets: logo PNG has invalid signature ({})", source);
                return;
            }

            class_1011 img = class_1011.method_4309(new java.io.ByteArrayInputStream(png));
            logoW = img.method_4307();
            logoH = img.method_4323();
            logoId = Identifiers.of(SlothyHubMod.MOD_ID, "gui/logo");
            class_1043 tex = DrawHelper.createNativeTexture("slothyhub_gui_logo", img);
            class_310 mc = class_310.method_1551();
            if (tex != null && mc != null && mc.method_1531() != null) {
                DrawHelper.registerDynamicTexture(logoId, tex, img);
                logoReady = true;
                SlothyHubMod.LOGGER.info("GuiAssets: logo registered from {} ({}x{})", source, logoW, logoH);
            } else {
                SlothyHubMod.LOGGER.warn("GuiAssets: texture manager not ready for logo");
                logoAttempted = false;
            }
        } catch (Exception e) {
            SlothyHubMod.LOGGER.warn("GuiAssets: could not load logo: {}", e.getMessage());
            logoAttempted = false;
        }
    }

    private static byte[] readLogoBytes(String rel) {
        var container = FabricLoader.getInstance().getModContainer(SlothyHubMod.MOD_ID).orElse(null);
        if (container != null) {
            for (Path root : container.getRootPaths()) {
                Path file = root.resolve(rel);
                if (!Files.isRegularFile(file)) continue;
                try {
                    byte[] data = Files.readAllBytes(file);
                    if (PackIconLoader.isValidPng(data)) return data;
                } catch (Exception ignored) {}
            }
        }
        ClassLoader cl = GuiAssets.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(rel)) {
            if (in == null) return null;
            byte[] data = in.readAllBytes();
            if (PackIconLoader.isValidPng(data)) return data;
        } catch (Exception ignored) {}
        return null;
    }
}
