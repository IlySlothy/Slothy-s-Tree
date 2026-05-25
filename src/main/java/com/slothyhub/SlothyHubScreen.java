package com.slothyhub;

import net.minecraft.class_332;

public class SlothyHubScreen extends SlothyHubScreenBase {

    public SlothyHubScreen(net.minecraft.class_437 parent) {
        super(parent);
    }

    /** Overrides screen overlay render (not used but required by some MC versions). */
    public void method_25420(class_332 ctx, int mx, int my, float delta) {}

    /** mouseScrolled — four-arg variant (MC 1.21.x). */
    @Override
    public boolean method_25401(double mx, double my, double hDelta, double vDelta) {
        return onScrollDelta(vDelta);
    }
}
