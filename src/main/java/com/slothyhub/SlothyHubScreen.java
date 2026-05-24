package com.slothyhub;

import net.minecraft.class_332;
import net.minecraft.class_437;

public class SlothyHubScreen extends SlothyHubScreenBase {

    public SlothyHubScreen(class_437 parent) {
        super(parent);
    }

    /** Overrides screen overlay render (not used but required by some MC versions). */
    public void method_25420(class_332 ctx, int mx, int my, float delta) {}

    /** mouseScrolled (MC 1.21.x) — four-arg variant. */
    public boolean method_25403(double mx, double my, double hDelta, double vDelta) {
        return onScrollDelta(vDelta);
    }

    /** mouseScrolled (MC 1.20.x) — three-arg variant. */
    public boolean method_25401(double mx, double my, double vDelta) {
        return onScrollDelta(vDelta);
    }
}
