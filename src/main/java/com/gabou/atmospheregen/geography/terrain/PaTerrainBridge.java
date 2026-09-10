/* Original V1 composition adapter, All Rights Reserved. Composes the retained MIT RTF terrain backend. */
package com.gabou.atmospheregen.geography.terrain;

import com.gabou.atmospheregen.geography.terrain.TerrainMetrics.Stage;

import com.gabou.atmospheregen.api.geography.MacroGeographyProvider.MacroSample;
import com.gabou.atmospheregen.config.MacroGeographySettings;
import com.gabou.atmospheregen.generation.seed.*;
import com.gabou.atmospheregen.geography.*;
import com.gabou.atmospheregen.geography.continent.PaMacroGeography;
import com.gabou.atmospheregen.geography.hydrology.PaRiverGenerator;
import raccoonman.reterraforged.world.worldgen.*;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.continent.Continent;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.*;
import raccoonman.reterraforged.world.worldgen.cell.rivermap.Rivermap;
import raccoonman.reterraforged.world.worldgen.cell.terrain.*;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.util.PosUtil;

/** V1 macro authority over the unchanged regional terrain graph. Canonical filters still run. */
public final class PaTerrainBridge implements Continent {
    private final PaMacroGeography macro;
    private final Heightmap backend;
    private final Levels levels;
    private final PaRiverGenerator rivers;
    public PaTerrainBridge(GeneratorContext context,Heightmap backend,GenerationSeedService seeds,MacroGeographySettings settings) {
        this.backend=backend;levels=context.levels;macro=new PaMacroGeography(seeds,settings);
        rivers=new PaRiverGenerator(this,context,seeds);
    }
    public PaMacroGeography macro(){return macro;}
    public PaRiverGenerator rivers(){return rivers;}
    public GeographyPipeline<Cell,Rivermap,Tile> pipeline(WorldFilters filters) {
        return new GeographyPipeline<>(this::apply,(cell,x,z)->{},this::hydrology,this::legacyHints,(tile,optional)->{
            filters.apply(tile,optional);
            long measured=Stage.SHORELINE.start();
            tile.iterate((cell,x,z)->finalizeShoreline(cell,tile.getBlockX()+x,tile.getBlockZ()+z));
            Stage.SHORELINE.end(measured);
        });
    }
    @Override public void apply(Cell cell,float x,float z) {
        long measured=Stage.MACRO.start();
        var sample=macro.sampleMacro(x,z);var site=macro.sites().at(x,z);
        Stage.MACRO.end(measured);
        cell.resetMountainContributions();cell.terrain=TerrainType.FLATS;
        cell.continentX=(int)site.x();cell.continentZ=(int)site.z();cell.continentId=(float)com.gabou.atmospheregen.geography.continent.MacroSiteField.unit(site.id());
        cell.beachNoise=0;cell.continentEdge=1; // Select only the retained regional land graph.
        if(sample.land()) {
            long terrainTime=Stage.TERRAIN_HEIGHT.start();
            backend.terrainStage().apply(cell,x,z);
            Stage.TERRAIN_HEIGHT.end(terrainTime);
            double alpha=Math.min(1,sample.shorelineProfileBlocks()/384);alpha=alpha*alpha*(3-2*alpha);
            cell.height=(float)(levels.water+(Math.max(cell.height,levels.water+levels.scale(2))-levels.water)*alpha);
            cell.mountainChainContribution((float)(cell.mountainChainContribution()*alpha));
            cell.regionalMountainContribution((float)(cell.regionalMountainContribution()*alpha));
            if(sample.shorelineProfileBlocks()<24)cell.terrain=TerrainType.COAST;
        }else setMarine(cell,sample);
        cell.continentEdge=edge(sample);
    }
    public Rivermap hydrology(Cell cell,float x,float z,Rivermap previous) {
        var sample=macro.sampleMacro(x,z);
        if(!sample.land()||sample.islandClass()!=com.gabou.atmospheregen.api.geography.MacroGeographyProvider.IslandClass.NONE)return previous;
        Rivermap map=previous!=null&&previous.getX()==cell.continentX&&previous.getZ()==cell.continentZ?previous:rivers.map(cell.continentX,cell.continentZ);
        long measured=Stage.HYDROLOGY.start();map.apply(cell,x,z);Stage.HYDROLOGY.end(measured);return map;
    }
    public void legacyHints(Cell cell,float x,float z,boolean enabled) {
        Terrain physical=cell.terrain;
        long measured=Stage.LEGACY_HINTS.start();backend.legacyParameters().apply(cell,x,z,enabled);Stage.LEGACY_HINTS.end(measured);
        // Legacy biome parameters remain temporary. Their biome-center coast mutation has no V1 authority.
        cell.terrain=physical;
    }
    public void applyComplete(Cell cell,float x,float z,boolean enabled) {
        apply(cell,x,z);hydrology(cell,x,z,null);legacyHints(cell,x,z,enabled);
    }
    private void finalizeShoreline(Cell cell,int x,int z) {
        var s=macro.sampleMacro(x,z);
        if(!s.land())setMarine(cell,s);
        else if(!cell.terrain.isRiver()&&!cell.terrain.isLake()&&!cell.terrain.isWetland()) {
            cell.height=Math.max(cell.height,levels.water+levels.scale(1));
            if(s.shorelineProfileBlocks()<24)cell.terrain=TerrainType.BEACH;
            else if(cell.terrain.isCoast())cell.terrain=TerrainType.FLATS;
        }
        cell.continentEdge=edge(s);
    }
    private void setMarine(Cell cell,MacroSample s) {
        cell.height=marineHeight(s);
        cell.terrain=s.shelfFraction()>0.5?TerrainType.SHALLOW_OCEAN:TerrainType.DEEP_OCEAN;
        cell.riverMask=1;cell.erosionMask=false;cell.resetMountainContributions();
    }
    private float marineHeight(MacroSample s){return (float)(levels.water-s.marineDepthBlocks()/levels.worldHeight);}
    /** Same float operations as finalization and detached surface extraction. */
    public double marineSeaRelativeElevation(MacroSample s){
        if(s.land())throw new IllegalArgumentException("Marine projection requires non-land macro classification");
        return (double)(marineHeight(s)*levels.worldHeight)-levels.waterLevel;
    }
    private float edge(MacroSample sample) {
        var c=backend.controlPoints();double d=sample.shorelineProfileBlocks();
        if(d>=0)return (float)(c.coast()+(c.inland()-c.coast())*Math.min(1,d/384));
        return (float)(c.coast()*Math.max(0,1+d/(macro.settings().shelfWidthBlocks()*2)));
    }
    @Override public float getEdgeValue(float x,float z){return edge(macro.sampleMacro(x,z));}
    @Override public long getNearestCenter(float x,float z){var s=macro.sites().at(x,z);return PosUtil.pack((int)s.x(),(int)s.z());}
    @Override public Rivermap getRivermap(int x,int z){return rivers.map(x,z);}
}
