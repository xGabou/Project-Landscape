package com.gabou.atmospheregen.runtime;

import java.util.Optional;

/** World-scoped, server-thread only. Empty means unavailable, never baseline substituted as weather. */
public interface RuntimeClimateProvider extends AutoCloseable {
    Optional<RuntimeClimateSample> sample(int blockX, int blockZ);
    int regionSize();
    String source();
    long[] windowTicks();
    @Override void close();
}
