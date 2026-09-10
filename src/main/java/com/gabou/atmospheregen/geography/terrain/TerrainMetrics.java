/* Original development telemetry. All Rights Reserved. */
package com.gabou.atmospheregen.geography.terrain;

import java.util.*;
import java.util.concurrent.atomic.LongAdder;

/** Opt-in aggregate diagnostics, no coordinates, world references or retained work. Nested times overlap. */
public final class TerrainMetrics {
    public static final boolean ENABLED=Boolean.getBoolean("task6c.metrics");
    public enum Stage {
        TILE, BATCH, MACRO, TERRAIN_HEIGHT, HYDROLOGY, LEGACY_HINTS, SHORELINE,
        EROSION, SMOOTHING, STEEPNESS, BEACH, CORRECTIONS, SURFACE_ACQUISITION,
        EROSION_NOISE, ISLAND_WAIT, ISLAND_WORK, ISLAND_COMPUTE, ISLAND_INSERT, ISLAND_EVICTION;
        private final LongAdder calls=new LongAdder(),nanos=new LongAdder();
        public long start() { return ENABLED?System.nanoTime():0; }
        public void end(long start) { if(ENABLED){nanos.add(System.nanoTime()-start);calls.increment();} }
    }
    private TerrainMetrics() {}
    public static Map<String,Long> snapshot() {
        Map<String,Long> out=new TreeMap<>();
        for(var stage:Stage.values()) {out.put(stage.name()+"Calls",stage.calls.sum());out.put(stage.name()+"Nanos",stage.nanos.sum());}
        return out;
    }
}
