/* Original Project Atmosphere companion adapter. All Rights Reserved.
 * Calls the MIT-derived legacy backend; no inherited implementation is relicensed here. */
package com.gabou.atmospheregen.compat.legacy;

import com.gabou.atmospheregen.api.geography.*;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.terrain.Terrain;

/** Thin read adapter only: no Heightmap extraction, climate model or new geographic calculations. */
public final class LegacyRtfGeographyAdapter implements GeographyProvider {
    private final GeneratorContext legacy;
    private final int heightScale;
    private final int seaLevel;
    private LegacyRtfGeographyAdapter(GeneratorContext legacy) {
        if (legacy.generationContext() == null || legacy.cache == null) throw new IllegalStateException("Canonical legacy provider requires persisted world metadata and a tile cache");
        legacy.generationContext().manifest().content().versions().requireFunctionalBackend();
        this.legacy = legacy;
        this.heightScale = legacy.levels.worldHeight;
        this.seaLevel = legacy.levels.waterLevel;
    }
    public static LegacyRtfGeographyAdapter forLevel(ServerLevel level) {
        return new LegacyRtfGeographyAdapter(((RTFRandomState)(Object)level.getChunkSource().randomState()).requireGeneratorContext("create canonical geography provider"));
    }
    @Override public GeoSample sample(int x, int z) {
        Cell cell = canonical(x, z);
        WaterCategory water = water(cell.terrain);
        // Same float multiplication as legacy density height scaling, then widened (not a new height algorithm).
        double elevation = cell.height * heightScale;
        return new GeoSample(new BlockPosition(x, z), elevation, elevation - seaLevel, water, landform(cell.terrain),
            GeographyMetrics.unknown(), hydrology(cell, water));
    }
    public HydrologyProvider hydrology() { return (x, z) -> sample(x, z).hydrology(); }
    /** Dimensionless legacy hints only; not an implementation of BaselineClimateProvider. */
    public LegacyClimateHints climateHints(int x, int z) {
        Cell cell = canonical(x, z);
        return new LegacyClimateHints(cell.temperature, cell.moisture, cell.biomeRegionId, cell.biome.name());
    }
    public record LegacyClimateHints(float temperatureParameter, float moistureParameter, float biomeRegion, String legacyBiomeType) {}

    private Cell canonical(int x, int z) {
        if (legacy.cache.isClosed()) throw new IllegalStateException("Cannot sample canonical geography after context shutdown: " + legacy.generationContext().contextId());
        var tile = legacy.cache.provide(legacy.cache.chunkToTile(x >> 4), legacy.cache.chunkToTile(z >> 4));
        var source = tile.lookup(x, z);
        if (source == null) throw new IllegalStateException("Owning canonical tile has no cell at " + x + "," + z + "; no direct fallback permitted");
        Cell detached = new Cell(); detached.copyFrom(source); return detached;
    }
    private static WaterCategory water(Terrain terrain) {
        if (terrain.isDeepOcean()) return WaterCategory.DEEP_OCEAN;
        if (terrain.isShallowOcean()) return WaterCategory.SHALLOW_OCEAN;
        if (terrain.isRiver()) return WaterCategory.RIVER;
        if (terrain.isLake()) return WaterCategory.LAKE;
        if (terrain.isWetland()) return WaterCategory.WETLAND;
        if (terrain.isCoast()) return WaterCategory.COAST;
        return terrain.isOverground() ? WaterCategory.LAND : WaterCategory.UNKNOWN;
    }
    private static Landform landform(Terrain terrain) {
        if (terrain.isMountain()) return Landform.MOUNTAIN;
        if (terrain.isDeepOcean() || terrain.isShallowOcean()) return Landform.OCEAN;
        if (terrain.isCoast()) return Landform.COAST;
        if (terrain.isRiver()) return Landform.VALLEY;
        if (terrain.isFlat()) return Landform.PLAINS;
        // No name guessing: legacy highland includes plateaus and hills, which this boundary leaves OTHER.
        return Landform.OTHER;
    }
    private static HydrologySample hydrology(Cell cell, WaterCategory water) {
        Optional<Metric> influence = Float.isFinite(cell.riverMask) && cell.riverMask >= 0 && cell.riverMask <= 1
            ? Optional.of(new Metric(1.0F - cell.riverMask, Metric.Quality.LEGACY_HEURISTIC, 1)) : Optional.empty();
        return new HydrologySample(water, cell.terrain.isRiver(), cell.terrain.isLake(), cell.terrain.isWetland(), influence, Optional.empty());
    }
}
