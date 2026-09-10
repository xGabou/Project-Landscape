/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography.terrain;

import com.gabou.projectlandscape.geography.terrain.TerrainMetrics.Stage;

import com.gabou.projectlandscape.api.geography.*;
import com.gabou.projectlandscape.geography.ocean.CoarseDistanceField;
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
        onClose(distances::close);
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
    public com.gabou.projectlandscape.api.geography.MacroGeographyProvider macroProvider(){return bridge.macro();}
    public Map<String,Long> distanceCacheStats(){return distances.cacheStats();}
    public CoarseDistanceField distanceField(){return distances;}
    public void onClose(Runnable action){context.cache.onClose(action);}
    public com.gabou.projectlandscape.climate.CanonicalClimateService climateService(
            com.gabou.projectlandscape.generation.context.WorldGenerationContext generation){return context.canonicalClimate(generation);}
    /** Detached world-aligned surface field, copied from the same canonical filtered tile as sample(). */
    public SurfaceTile snapshotSurfaceTile(int x,int z){
        if(context.cache.isClosed())throw new IllegalStateException("V1 geography context is closed");
        int tx=context.cache.chunkToTile(x>>4),tz=context.cache.chunkToTile(z>>4);
        long measured=Stage.SURFACE_ACQUISITION.start();
        var tile=context.cache.provide(tx,tz);
        Stage.SURFACE_ACQUISITION.end(measured);
        int side=tile.getChunksSize().size()*16,originX=tx*side,originZ=tz*side;
        float[] elevation=new float[side*side],mountains=new float[side*side];
        for(int dz=0;dz<side;dz++)for(int dx=0;dx<side;dx++){
            Cell cell=tile.lookup(originX+dx,originZ+dz);int i=dz*side+dx;
            elevation[i]=cell.height*context.levels.worldHeight;
            mountains[i]=Math.max(0,Math.min(1,cell.mountainChainContribution()+cell.regionalMountainContribution()));
        }
        return new SurfaceTile(originX,originZ,side,context.levels.waterLevel,elevation,mountains);
    }
    public int surfaceTileCoordinate(int blockCoordinate){return context.cache.chunkToTile(blockCoordinate>>4);}
    /** Exact climate-only projection of fields unconditionally overwritten during marine finalization. */
    public double marineSeaRelativeElevation(MacroGeographyProvider.MacroSample sample){
        if(context.cache.isClosed())throw new IllegalStateException("V1 geography context is closed");
        return bridge.marineSeaRelativeElevation(sample);
    }
    /** Additional physical terrain label; existing Task 4 samples retain their original classification. */
    public Landform detailedLandform(int x,int z){
        var sample=sample(x,z);
        if(sample.water()!=WaterCategory.LAND)return sample.landform();
        Cell cell=context.cache.provide(context.cache.chunkToTile(x>>4),context.cache.chunkToTile(z>>4)).lookup(x,z);
        var terrain=cell.terrain;
        // Retained terrain identity is geographic evidence, never the legacy BiomeType hint.
        for(int depth=0;depth<32;depth++){
            String name=terrain.getName();
            if(name.equals("plateau")||name.equals("badlands"))return Landform.PLATEAU;
            if(name.equals("hills"))return Landform.HILLS;
            if(!(terrain.getDelegate() instanceof raccoonman.reterraforged.world.worldgen.cell.terrain.Terrain parent)||parent==terrain)break;
            terrain=parent;
        }
        return sample.landform();
    }
    public static final class SurfaceTile {
        private final int x,z,side,seaLevel;
        private final float[] elevation,mountains;
        private SurfaceTile(int x,int z,int side,int seaLevel,float[] elevation,float[] mountains){this.x=x;this.z=z;this.side=side;this.seaLevel=seaLevel;this.elevation=elevation;this.mountains=mountains;}
        public double seaRelativeElevation(int blockX,int blockZ){return (double)elevation[index(blockX,blockZ)]-seaLevel;}
        public double mountainInfluence(int blockX,int blockZ){return mountains[index(blockX,blockZ)];}
        public long retainedArrayBytes(){return (long)elevation.length*Float.BYTES*2;}
        private int index(int blockX,int blockZ){int dx=blockX-x,dz=blockZ-z;if(dx<0||dz<0||dx>=side||dz>=side)throw new IllegalArgumentException("Point outside canonical surface tile");return dz*side+dx;}
    }
}
