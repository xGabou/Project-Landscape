/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography.terrain;

import com.gabou.atmospheregen.api.geography.*;
import com.gabou.atmospheregen.geography.ocean.CoarseDistanceField;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import java.util.*;

/** Detached canonical terrain plus separately identified macro and coarse-distance diagnostics. */
public final class PaGeographyProvider implements GeographyProvider {
    public record DetailedSample(GeoSample geography,MacroGeographyProvider.MacroSample macro,
            CoarseDistanceField.Distances distances) {}
    private final GeneratorContext context;
    private final PaTerrainBridge bridge;
    private final CoarseDistanceField distances;
    public PaGeographyProvider(GeneratorContext context) {
        bridge=Objects.requireNonNull(context.generator.getHeightmap().paBridge(),"V1 provider requires installed V1 terrain");
        if(context.cache==null)throw new IllegalArgumentException("V1 canonical provider requires a tile cache");
        this.context=context;distances=new CoarseDistanceField(bridge.macro());
    }
    @Override public GeoSample sample(int x,int z) {
        if(context.cache.isClosed())throw new IllegalStateException("V1 geography context is closed");
        Cell cell=context.cache.provide(context.cache.chunkToTile(x>>4),context.cache.chunkToTile(z>>4)).lookup(x,z);
        var macro=bridge.macro().sampleMacro(x,z);
        WaterCategory water=!macro.land()?macro.waterBody()==MacroGeographyProvider.MarineClass.INLAND_SEA?WaterCategory.INLAND_SEA:
            cell.terrain.isDeepOcean()?WaterCategory.DEEP_OCEAN:WaterCategory.SHALLOW_OCEAN:
            cell.terrain.isRiver()?WaterCategory.RIVER:cell.terrain.isLake()?WaterCategory.LAKE:
            cell.terrain.isWetland()?WaterCategory.WETLAND:cell.terrain.isCoast()?WaterCategory.COAST:WaterCategory.LAND;
        var influence=Optional.of(new Metric(Math.max(0,Math.min(1,1-cell.riverMask)),Metric.Quality.LEGACY_HEURISTIC,1));
        var hydro=new HydrologySample(water,cell.terrain.isRiver(),cell.terrain.isLake(),cell.terrain.isWetland(),influence,Optional.empty());
        var metrics=new GeographyMetrics(Optional.of(new Metric(macro.continentality(),Metric.Quality.MODELLED,1)),
            Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),
            Optional.of(new Metric(Math.max(0,Math.min(1,cell.mountainChainContribution()+cell.regionalMountainContribution())),Metric.Quality.LEGACY_HEURISTIC,1)),Optional.empty());
        Landform form=!macro.land()?Landform.OCEAN:cell.terrain.isMountain()?Landform.MOUNTAIN:
            cell.terrain.isRiver()?Landform.VALLEY:cell.terrain.isCoast()?Landform.COAST:cell.terrain.isFlat()?Landform.PLAINS:Landform.OTHER;
        double elevation=cell.height*context.levels.worldHeight;
        return new GeoSample(new BlockPosition(x,z),elevation,elevation-context.levels.waterLevel,water,form,metrics,hydro);
    }
    public DetailedSample sampleDetailed(int x,int z){return new DetailedSample(sample(x,z),bridge.macro().sampleMacro(x,z),distances.sample(x,z));}
    public Map<String,Long> distanceCacheStats(){return distances.cacheStats();}
}
