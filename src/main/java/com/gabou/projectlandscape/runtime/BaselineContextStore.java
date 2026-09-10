package com.gabou.projectlandscape.runtime;

import java.util.LinkedHashMap;
import java.util.Optional;
import com.gabou.projectlandscape.api.climate.ClimateBaseline;
import com.gabou.projectlandscape.climate.ClimateGeography;

/** Bounded detached observations of ALREADY requested canonical climate. No lookup performs generation. */
public final class BaselineContextStore implements AutoCloseable {
    public static final int REGION_SIZE = 2000;
    public static final int CAPACITY = 4096;
    public record Context(int blockX, int blockZ, double latitudeDegrees, ClimateBaseline baseline,
            ClimateGeography.Sample geography) { }
    private final LinkedHashMap<Long, Context> observations = new LinkedHashMap<>();
    private boolean closed;
    public static boolean isRepresentative(int x, int z) {
        return Math.floorMod(x, REGION_SIZE) == 0 && Math.floorMod(z, REGION_SIZE) == 0;
    }
    public synchronized void record(Context value) {
        if (closed) return; // A completing generation flight may outlive its disposed observer.
        // Only the fixed north-west grid corner can represent a region. Arrival order
        // cannot select a different climate. If generation never requests it, stay empty.
        if (!isRepresentative(value.blockX(), value.blockZ())) return;
        long key = ClimateRegion.at(value.blockX(), value.blockZ(), REGION_SIZE).key();
        if (observations.containsKey(key)) return;
        if (observations.size() == CAPACITY) observations.remove(observations.keySet().iterator().next());
        observations.put(key, value);
    }
    public synchronized Optional<Context> sample(int x, int z) {
        if (closed) throw new IllegalStateException("Baseline context disposed");
        return Optional.ofNullable(observations.get(ClimateRegion.at(x, z, REGION_SIZE).key()));
    }
    @Override public synchronized void close() { closed = true; observations.clear(); }
    public synchronized void clear() { observations.clear(); }
}
