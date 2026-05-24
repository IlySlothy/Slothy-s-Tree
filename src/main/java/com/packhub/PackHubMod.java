package com.packhub;

import com.packhub.ui.Ui;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.Executors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AfterInit;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.BeforeMouseScroll;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_304;
import net.minecraft.class_310;
import net.minecraft.class_3468;
import net.minecraft.class_4185;
import net.minecraft.class_433;
import net.minecraft.class_442;
import net.minecraft.class_9779;
import net.minecraft.class_3675.class_307;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class PackHubMod implements ClientModInitializer {
   public static final String MOD_ID = "packhub";
   public static final Logger LOGGER = LoggerFactory.getLogger("packhub");
   private static class_304 openScreenKey;
   private static int lastKillCount = -1;
   private static final Method TICK_DELTA_METHOD;

   public PackHubMod() {
   }

   public void onInitializeClient() {
      PackHubConfig.load();
      LOGGER.info("PackHub loaded. Saved URL: {}", PackHubConfig.isConfigured() ? PackHubConfig.getServerUrl() : "(unset)");
      Executors.newSingleThreadExecutor().execute(() -> {
         PackHubConfig.tryAutoDiscover();
         LOGGER.info("PackHub: server URL after auto-discover: {}", PackHubConfig.isConfigured() ? PackHubConfig.getServerUrl() : "(still unset)");
      });
      openScreenKey = KeyBindingHelper.registerKeyBinding(createKeyBinding("key.packhub.open_screen", class_307.field_1668, 80, "category.packhub"));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (openScreenKey != null) {
            while (openScreenKey.method_1436()) {
               if (client.field_1755 == null) {
                  client.method_1507(new PackHubScreen(null));
               }
            }

            if (PackHubConfig.isKillEffectEnabled() && client.field_1724 != null) {
               int mobKills = client.field_1724.method_3143().method_15025(class_3468.field_15419.method_14956(class_3468.field_15414));
               int playerKills = client.field_1724.method_3143().method_15025(class_3468.field_15419.method_14956(class_3468.field_15404));
               int totalKills = mobKills + playerKills;
               if (lastKillCount < 0) {
                  lastKillCount = totalKills;
               } else if (totalKills > lastKillCount) {
                  for (int i = 0; i < totalKills - lastKillCount; i++) {
                     Ui.onKill();
                  }

                  lastKillCount = totalKills;
               }
            } else if (client.field_1724 == null) {
               lastKillCount = -1;
            }
         }
      });
      HudRenderCallback.EVENT.register((HudRenderCallback)(drawContext, tickCounter) -> {
         if (PackHubConfig.isKillEffectEnabled()) {
            class_310 mc = class_310.method_1551();
            if (mc.field_1724 != null) {
               int w = mc.method_22683().method_4486();
               int h = mc.method_22683().method_4502();
               float delta = getTickDeltaCompat(tickCounter);
               Ui.renderKillEffect(drawContext, w, h, delta);
            }
         }
      });
      ScreenEvents.AFTER_INIT
         .register(
            (AfterInit)(client, screen, scaledWidth, scaledHeight) -> {
               if (screen instanceof PackHubScreenBase packScreen) {
                  ScreenMouseEvents.beforeMouseScroll(screen).register((BeforeMouseScroll)(scr, mx, my, hDelta, vDelta) -> packScreen.onScrollDelta(vDelta));
               } else if (screen instanceof PackBuilderScreenBase builderScreen) {
                  ScreenMouseEvents.beforeMouseScroll(screen).register((BeforeMouseScroll)(scr, mx, my, hDelta, vDelta) -> builderScreen.onScrollDelta(vDelta));
               } else if (screen instanceof PackHubSettingsScreenBase settingsScreen) {
                  ScreenMouseEvents.beforeMouseScroll(screen)
                     .register((BeforeMouseScroll)(scr, mx, my, hDelta, vDelta) -> settingsScreen.onScrollDelta(vDelta));
               } else if (screen instanceof FeatherProfilesScreenBase featherScreen) {
                  ScreenMouseEvents.beforeMouseScroll(screen).register((BeforeMouseScroll)(scr, mx, my, hDelta, vDelta) -> featherScreen.onScrollDelta(vDelta));
               } else if (screen instanceof PackEditScreenBase editScreen) {
                  ScreenMouseEvents.beforeMouseScroll(screen).register((BeforeMouseScroll)(scr, mx, my, hDelta, vDelta) -> editScreen.onScrollDelta(vDelta));
               }

               if (screen instanceof class_442 titleScreen) {
                  class_4185 btn = class_4185.method_46430(
                        class_2561.method_43470("\ud83c\udfa8 PackHub"), b -> client.method_1507(new PackHubScreen(titleScreen))
                     )
                     .method_46434(scaledWidth / 2 + 104, scaledHeight / 4 + 96 + 12, 80, 20)
                     .method_46431();
                  Screens.getButtons(titleScreen).add(btn);
               } else if (screen instanceof class_433 pauseScreen) {
                  int btnW = 200;
                  int btnX = scaledWidth / 2 - btnW / 2;
                  int btnY = scaledHeight / 4 + 160 + 24;
                  class_4185 btn = class_4185.method_46430(
                        class_2561.method_43470("\ud83c\udfa8 PackHub"), b -> client.method_1507(new PackHubScreen(pauseScreen))
                     )
                     .method_46434(btnX, btnY, btnW, 20)
                     .method_46431();
                  Screens.getButtons(pauseScreen).add(btn);
               }
            }
         );
   }

   private static float getTickDeltaCompat(Object tickCounter) {
      if (TICK_DELTA_METHOD != null) {
         try {
            return (Float)TICK_DELTA_METHOD.invoke(tickCounter, true);
         } catch (Exception var2) {
         }
      }

      return 0.0F;
   }

   private static class_304 createKeyBinding(String id, class_307 type, int code, String category) {
      for (Constructor<?> ctor : class_304.class.getDeclaredConstructors()) {
         Class<?>[] params = ctor.getParameterTypes();
         if (params.length == 4 && params[0] == String.class && params[2] == int.class && params[3] != String.class) {
            try {
               Class<?> categoryClass = params[3];
               Method createMethod = null;

               for (Method m : categoryClass.getDeclaredMethods()) {
                  if (Modifier.isStatic(m.getModifiers())
                     && m.getParameterCount() == 1
                     && m.getParameterTypes()[0] == class_2960.class
                     && m.getReturnType() == categoryClass) {
                     createMethod = m;
                     break;
                  }
               }

               if (createMethod != null) {
                  createMethod.setAccessible(true);
                  Object cat = createMethod.invoke(null, class_2960.method_60655("packhub", category));
                  ctor.setAccessible(true);
                  return (class_304)ctor.newInstance(id, type, code, cat);
               }
            } catch (ReflectiveOperationException var151) {
               LOGGER.warn("PackHub: reflection KeyBinding creation failed", var151);
            }
         }
      }

      return new class_304(id, type, code, category);
   }

   static {
      Method found = null;

      try {
         for (Method m : class_9779.class.getMethods()) {
            if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class && m.getReturnType() == float.class) {
               found = m;
               break;
            }
         }
      } catch (Exception var5) {
      }

      TICK_DELTA_METHOD = found;
   }
}
