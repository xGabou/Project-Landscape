/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography.terrain;

import com.gabou.atmospheregen.config.MacroGeographySettings;
import com.gabou.atmospheregen.generation.seed.GenerationSeedService;
import raccoonman.reterraforged.world.worldgen.*;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.*;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.generation.TileGenerator;

/** Initialization-only installation, before context publication/chunk scheduling. Never a live migration. */
public final class PaGeographyInstallation {
    private PaGeographyInstallation() {}
    public static void install(GeneratorContext context,GenerationSeedService seeds,MacroGeographySettings settings,int batchCount,boolean queue) {
        if(context.generationContext()!=null)throw new IllegalStateException("Cannot replace geography in a bound world");
        Heightmap retained=context.generator.getHeightmap();
        if(retained.paBridge()!=null)throw new IllegalStateException("V1 geography already installed");
        var bridge=new PaTerrainBridge(context,retained,seeds,settings);
        var heightmap=new Heightmap(retained.continentStage(),retained.terrainStage(),retained.hydrologyStage(),
            retained.legacyParameters(),retained.levels(),retained.controlPoints(),bridge);
        if(context.cache!=null)context.cache.close();
        int border=Math.min(2,Math.max(1,context.preset.filters().erosion.dropletLifetime/16));
        context.generator=new TileGenerator(heightmap,new WorldFilters(context),3,border,batchCount);
        context.cache=new TileCache(3,queue,context.generator);context.lookup=new WorldLookup(context);
    }
}
