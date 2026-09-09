/* Original Project Atmosphere companion adapter. All Rights Reserved.
 * Calls the MIT-derived legacy backend; no inherited implementation is relicensed here. */
package com.gabou.atmospheregen.compat.legacy;

import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import com.gabou.atmospheregen.api.geography.*;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.terrain.Terrain;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache;
import com.gabou.atmospheregen.generation.context.WorldGenerationContext;

/** Canonical public boundary over the extracted pipeline's finalized tiles; never direct Heightmap sampling. */
public final class LegacyRtfGeographyAdapter implements GeographyProvider {
    private final WorldGenerationContext context;
    private final TileCache tiles;
    private final int heightScale;
    private final int seaLevel;
    private LegacyRtfGeographyAdapter(GeneratorContext legacy) {
        if (legacy.generationContext() == null || legacy.cache == null) throw new IllegalStateException("Canonical legacy provider requires persisted world metadata and a tile cache");
        legacy.generationContext().manifest().content().versions().requireFunctionalBackend();
        this.context = legacy.generationContext();
        this.tiles = legacy.cache;
        if(context.runtimeToken()!=legacy.lookup.samplingIdentity())throw new IllegalStateException("Provider/cache generation context mismatch");
        // TileGenerator publishes only after geographyPipeline.finalizeTile; no separate query algorithm.
        java.util.Objects.requireNonNull(legacy.generator.geographyPipeline());
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
        double mountain = (double)cell.mountainChainContribution() + cell.regionalMountainContribution();
        // Tiny float interpolation rounding can exceed one; this new diagnostic is bounded, not used by terrain.
        var mountainMetric=Optional.of(new Metric(Math.min(1.0,mountain),Metric.Quality.LEGACY_HEURISTIC,1));
        var metrics=new GeographyMetrics(Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),
            Optional.empty(),mountainMetric,Optional.empty());
        var selector=Float.isFinite(cell.mountainChainSelector())
            ? Optional.of(new Metric(cell.mountainChainSelector(),Metric.Quality.LEGACY_HEURISTIC,1)) : Optional.<Metric>empty();
        var signals=new LegacyTerrainSignals(cell.continentEdge,new BlockPosition(cell.continentX,cell.continentZ),
            cell.terrainRegionId,cell.terrainRegionEdge,selector,cell.mountainChainContribution(),cell.regionalMountainContribution(),
            cell.heightErosion*heightScale,cell.sediment*heightScale);
        return new GeoSample(new BlockPosition(x, z), elevation, elevation - seaLevel, water, landform(cell.terrain),
            metrics, hydrology(cell, water),Optional.of(signals));
    }
    public HydrologyProvider hydrology() { return (x, z) -> sample(x, z).hydrology(); }
    /** Dimensionless legacy hints only; not an implementation of BaselineClimateProvider. */
    public LegacyClimateHints climateHints(int x, int z) {
        Cell cell = canonical(x, z);
        return new LegacyClimateHints(cell.temperature, cell.moisture, cell.biomeRegionId, cell.biome.name());
    }
    public record LegacyClimateHints(float temperatureParameter, float moistureParameter, float biomeRegion, String legacyBiomeType) {}

    private Cell canonical(int x, int z) {
        if (tiles.isClosed()) throw new IllegalStateException("Cannot sample canonical geography after context shutdown: " + context.contextId());
        var tile = tiles.provide(tiles.chunkToTile(x >> 4), tiles.chunkToTile(z >> 4));
        var source = tile.lookup(x, z);
        if (source == null) throw new IllegalStateException("Owning canonical tile has no cell at " + x + "," + z + "; no direct fallback permitted");
        // Task 1B publication detached this tile from pooled workspaces. A retained tile reader
        // survives eviction safely. Read it only inside this adapter; returned records contain
        // values, never Cell/Tile references. Avoid an extra mutable Cell allocation per API query.
        return source;
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
        if (terrain == TerrainType.PLATEAU) return Landform.PLATEAU;
        if (terrain == TerrainType.HILLS) return Landform.HILLS;
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
