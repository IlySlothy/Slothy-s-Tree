package com.slothyhub;

import com.slothyhub.cit.CitEngine;
import com.slothyhub.compat.Identifiers;
import com.slothyhub.compat.McVersion;
import com.slothyhub.compat.TickDeltaCompat;
import com.slothyhub.local.LocalPackManager;
import com.slothyhub.ui.Ui;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_3468;
import net.minecraft.class_433;
import net.minecraft.class_437;
import net.minecraft.class_442;
import net.minecraft.class_4185;
import net.minecraft.class_3675.class_307;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class SlothyHubMod implements ClientModInitializer {

    public static final String MOD_ID = "slothyhub";
    public static final Logger LOGGER = LoggerFactory.getLogger("slothyhub");

    private static class_304 openKey;
    private static int lastKillCount = -1;

    @Override
    public void onInitializeClient() {
        SlothyConfig.load();
        Ui.reloadTheme();
        LocalPackManager.init();
        CitEngine.init();
        com.slothyhub.ui.GuiAssets.init();
        PackMcmetaRepair.scanAndFixAsync();
        HeartbeatClient.start();
        // Catalog loads when Texture Builder opens — avoids background HTTP on every launch.

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
            client.execute(PackDownloader::syncAppliedPacks));

        SlothyHubMod.LOGGER.info("SlothyHub loaded. MC {} | pack_format: {}",
            McVersion.current(),
            PackMetaUtil.packFormatForCurrentGame());

        openKey = KeyBindingHelper.registerKeyBinding(
            createKeyBinding("key.slothyhub.open", class_307.field_1668, 72, "category.slothyhub"));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!SlothyConfig.isKillEffectEnabled()) return;
            String plain = message.getString().toLowerCase(java.util.Locale.ROOT);
            if (plain.contains("you killed") || plain.contains("was slain by you")
                || plain.contains("was killed by you") || plain.contains("you have killed")) {
                Ui.onKill();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register((EndTick) client -> {
            if (openKey != null) {
                while (openKey.method_1436()) {
                    if (client.field_1755 == null) {
                        openHub(client);
                    }
                }
                if (SlothyConfig.isKillEffectEnabled() && client.field_1724 != null) {
                    int mobKills    = client.field_1724.method_3143().method_15025(class_3468.field_15419.method_14956(class_3468.field_15414));
                    int playerKills = client.field_1724.method_3143().method_15025(class_3468.field_15419.method_14956(class_3468.field_15404));
                    int total = mobKills + playerKills;
                    if (lastKillCount < 0) {
                        lastKillCount = total;
                    } else if (total > lastKillCount) {
                        for (int i = 0; i < total - lastKillCount; i++) Ui.onKill();
                        lastKillCount = total;
                    }
                } else if (client.field_1724 == null) {
                    lastKillCount = -1;
                }
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            if (!SlothyConfig.isKillEffectEnabled() || !Ui.hasActiveKillFx()) return;
            class_310 mc = class_310.method_1551();
            if (mc.field_1724 != null) {
                int w = mc.method_22683().method_4486();
                int h = mc.method_22683().method_4502();
                float delta = TickDeltaCompat.delta(tickCounter);
                Ui.renderKillEffect(drawContext, w, h, delta);
                Ui.renderTotemPop(drawContext, w, h, delta);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledW, scaledH) -> {
            if (screen instanceof SlothyHubScreenBase slothyScreen) {
                ScreenMouseEvents.beforeMouseScroll(screen).register(
                    (scr, mx, my, hDelta, vDelta) -> slothyScreen.onScrollDelta(vDelta));
            }
            if (screen instanceof TexturePickerScreen textureScreen) {
                ScreenMouseEvents.beforeMouseScroll(screen).register(
                    (scr, mx, my, hDelta, vDelta) -> textureScreen.onScrollDelta(mx, vDelta));
            }
            if (screen instanceof PackLibraryScreen libraryScreen) {
                ScreenMouseEvents.beforeMouseScroll(screen).register(
                    (scr, mx, my, hDelta, vDelta) -> libraryScreen.onScrollDelta(vDelta));
            }
            if (screen instanceof UploadDashboardScreen uploadScreen) {
                ScreenMouseEvents.beforeMouseScroll(screen).register(
                    (scr, mx, my, hDelta, vDelta) -> uploadScreen.onScrollDelta(vDelta));
            }
            if (screen instanceof class_442 titleScreen) {
                class_4185 btn = class_4185.method_46430(
                        class_2561.method_43470("\uD83E\uDD8A SlothyHub"),
                        b -> openHub(client))
                    .method_46434(scaledW / 2 + 104, scaledH / 4 + 96 + 12, 80, 20)
                    .method_46431();
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(titleScreen).add(btn);
            } else if (screen instanceof class_433 pauseScreen) {
                class_4185 btn = class_4185.method_46430(
                        class_2561.method_43470("\uD83E\uDD8A SlothyHub"),
                        b -> openHub(client))
                    .method_46434(scaledW / 2 - 100, scaledH / 4 + 160 + 24, 200, 20)
                    .method_46431();
                net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(pauseScreen).add(btn);
            }
        });
    }

    private static void openHub(class_310 client) {
        class_437 parent = client.field_1755;
        if (SlothyConfig.shouldShowWhatsNew()) {
            client.method_1507(new WhatsNewScreen(new SlothyHubScreen(parent)));
        } else {
            client.method_1507(new SlothyHubScreen(parent));
        }
    }

    private static class_304 createKeyBinding(String id, class_307 type, int code, String category) {
        for (Constructor<?> ctor : class_304.class.getDeclaredConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 4 && p[0] == String.class && p[2] == int.class && p[3] != String.class) {
                try {
                    Class<?> catClass = p[3];
                    Method createM = null;
                    for (Method m : catClass.getDeclaredMethods()) {
                        if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == class_2960.class && m.getReturnType() == catClass) {
                            createM = m; break;
                        }
                    }
                    if (createM != null) {
                        createM.setAccessible(true);
                        Object cat = createM.invoke(null, Identifiers.of("slothyhub", category));
                        ctor.setAccessible(true);
                        return (class_304) ctor.newInstance(id, type, code, cat);
                    }
                } catch (Exception e) {
                    LOGGER.warn("SlothyHub: reflection KeyBinding creation failed", e);
                }
            }
        }
        return new class_304(id, type, code, category);
    }
}
