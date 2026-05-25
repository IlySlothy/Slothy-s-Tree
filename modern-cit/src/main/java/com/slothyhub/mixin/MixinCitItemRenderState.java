package com.slothyhub.mixin;

import com.slothyhub.SlothyConfig;
import com.slothyhub.SlothyHubMod;
import com.slothyhub.cit.ModernCitItemRenderer;
import net.minecraft.class_10442;
import net.minecraft.class_10444;
import net.minecraft.class_11566;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_1937;
import net.minecraft.class_811;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures the ItemStack being baked into a render state. We hook every public
 * entry point on {@code class_10442} that takes a stack so any caller path
 * (entity render, living-entity render, world render, model bake) primes our
 * cache before {@link net.minecraft.class_10444#method_65604} draws the state.
 *
 * Every @Inject is tagged {@code require = 0} so a mapping shift in 1.21.9+
 * does not crash the mod -- instead each handler logs the first time it fires,
 * so the boot log makes it obvious which entry points are actually bound on
 * the current build. The previous {@code method_65598} hook was removed after
 * the 1.21.11 build confirmed the symbol no longer exists.
 */
@Mixin(class_10442.class)
public abstract class MixinCitItemRenderState {

    private static final int TOTAL_HANDLERS = 3;
    private static final AtomicInteger BOUND = new AtomicInteger(0);
    private static final AtomicBoolean FIRST_65595 = new AtomicBoolean(true);
    private static final AtomicBoolean FIRST_65596 = new AtomicBoolean(true);
    private static final AtomicBoolean FIRST_65597 = new AtomicBoolean(true);

    static {
        SlothyHubMod.LOGGER.info("Modern CIT: MixinCitItemRenderState loaded");
        SlothyHubMod.LOGGER.info(
            "Modern CIT: MixinCitItemRenderState: {} @Inject handlers registered (require=0; misses are tolerated)",
            TOTAL_HANDLERS
        );
    }

    // class_10442.method_65596(class_10444, class_1799, class_811, class_1937, class_11566, I)V
    @Inject(method = "method_65596", at = @At("HEAD"), require = 0)
    private void slothyhub$captureWorldStackPrivate(
        class_10444 renderState,
        class_1799 stack,
        class_811 transformation,
        class_1937 world,
        class_11566 holder,
        int seed,
        CallbackInfo ci
    ) {
        noteFirstCall(FIRST_65596, "method_65596", stack);
        capture(renderState, stack);
    }

    // class_10442.method_65595(class_10444, class_1799, class_811, class_1297)V (entity overload)
    @Inject(method = "method_65595", at = @At("HEAD"), require = 0)
    private void slothyhub$captureEntityStack(
        class_10444 renderState,
        class_1799 stack,
        class_811 transformation,
        class_1297 entity,
        CallbackInfo ci
    ) {
        noteFirstCall(FIRST_65595, "method_65595", stack);
        capture(renderState, stack);
    }

    // class_10442.method_65597(class_10444, class_1799, class_811, class_1309)V (living-entity overload)
    @Inject(method = "method_65597", at = @At("HEAD"), require = 0)
    private void slothyhub$captureLivingStack(
        class_10444 renderState,
        class_1799 stack,
        class_811 transformation,
        class_1309 entity,
        CallbackInfo ci
    ) {
        noteFirstCall(FIRST_65597, "method_65597", stack);
        capture(renderState, stack);
    }

    private static void noteFirstCall(AtomicBoolean flag, String methodName, class_1799 stack) {
        if (flag.compareAndSet(true, false)) {
            int bound = BOUND.incrementAndGet();
            SlothyHubMod.LOGGER.info(
                "Modern CIT: noteStack via {} first call (stack={}) [{}/{} handlers bound]",
                methodName,
                describeStack(stack),
                bound,
                TOTAL_HANDLERS
            );
        }
    }

    private static String describeStack(class_1799 stack) {
        if (stack == null || stack.method_7960()) return "<empty>";
        try {
            return String.valueOf(net.minecraft.class_7923.field_41178.method_10221(stack.method_7909()));
        } catch (Throwable t) {
            return "<unknown>";
        }
    }

    private static void capture(class_10444 renderState, class_1799 stack) {
        if (!SlothyConfig.isCitEnabled()) return;
        ModernCitItemRenderer.noteStack(renderState, stack);
    }
}
