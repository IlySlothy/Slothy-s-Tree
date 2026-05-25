package com.slothyhub.legacy;

import com.slothyhub.SlothyHubMod;
import com.slothyhub.compat.McVersion;
import net.fabricmc.api.ClientModInitializer;

public final class LegacyCitMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (McVersion.below("1.21.8")) {
            SlothyHubMod.LOGGER.info("SlothyHub Legacy CIT loaded for MC {}", McVersion.current());
        }
    }
}