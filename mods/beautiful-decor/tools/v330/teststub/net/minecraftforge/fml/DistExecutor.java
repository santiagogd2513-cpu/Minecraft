package net.minecraftforge.fml;

import net.minecraftforge.api.distmarker.Dist;

import java.util.function.Supplier;

/** Pieza de mentira de DistExecutor: el código "de cliente" se ejecuta enseguida. */
public final class DistExecutor {
    private DistExecutor() {
    }

    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {
        if (dist == Dist.CLIENT) {
            toRun.get().run();
        }
    }
}
