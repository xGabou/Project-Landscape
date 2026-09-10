/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.geography;

import com.gabou.atmospheregen.geography.terrain.TerrainMetrics.Stage;

import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.WorldErosion;
import com.gabou.atmospheregen.geography.GeographyFinalizationStage;

import java.util.function.IntFunction;

import raccoonman.reterraforged.data.worldgen.preset.settings.FilterSettings;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.BeachDetect;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.Erosion;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.Filterable;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.NoiseCorrection;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.Smoothing;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.Steepness;

/** Frozen whole-tile physical finalization; no climate or biome resolver dependency. */
public class LegacyGeographyFinalizationStage implements GeographyFinalizationStage<Tile> {
    private raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.Filter smoothing;
    private Steepness steepness;
    private BeachDetect beach;
    private NoiseCorrection corrections;
    private FilterSettings settings;
    private WorldErosion<Erosion> erosion;
    private int erosionIterations;
    private int smoothingIterations;
    
    public LegacyGeographyFinalizationStage(GeneratorContext context) {
        this(context, false);
    }

    public LegacyGeographyFinalizationStage(GeneratorContext context, boolean reuseInvariantStrength) {
        IntFunction<Erosion> factory = Erosion.factory(context, reuseInvariantStrength);
        this.settings = context.preset.filters();
        this.beach = BeachDetect.make(context);
        var originalSmoothing = Smoothing.make(context.preset.filters().smoothing, context.levels);
        this.smoothing = reuseInvariantStrength ? new com.gabou.atmospheregen.geography.terrain.ExactSmoothing(originalSmoothing) : originalSmoothing;
        this.steepness = Steepness.make(1, 10.0F, context.levels);
        this.corrections = new NoiseCorrection(context.levels);
        this.erosion = new WorldErosion<>(factory, (e, size) -> e.getSize() == size);
        this.erosionIterations = context.preset.filters().erosion.dropletsPerChunk;
        this.smoothingIterations = context.preset.filters().smoothing.iterations;
    }
    
    public FilterSettings getSettings() {
        return this.settings;
    }
    
    public void apply(Tile tile, boolean optionalFilters) {
        int regionX = tile.getX();
        int regionZ = tile.getZ();
        
        if (optionalFilters) {
            this.applyOptionalFilters(tile, regionX, regionZ);
        }
        this.applyRequiredFilters(tile, regionX, regionZ);
        if(optionalFilters) {
        	this.applyCorrections(tile, regionX, regionZ);
        }
    }
    
    private void applyRequiredFilters(Filterable map, int seedX, int seedZ) {
        long timeSTEEPNESS=Stage.STEEPNESS.start();
        this.steepness.apply(map, seedX, seedZ, 1);
        Stage.STEEPNESS.end(timeSTEEPNESS);
        long timeBEACH=Stage.BEACH.start();
        this.beach.apply(map, seedX, seedZ, 1);
        Stage.BEACH.end(timeBEACH);
    }
    
    private void applyOptionalFilters(Filterable map, int seedX, int seedZ) {
        Erosion erosion = this.erosion.get(map.getBlockSize().total());
        long timeEROSION=Stage.EROSION.start();
        erosion.apply(map, seedX, seedZ, this.erosionIterations);
        Stage.EROSION.end(timeEROSION);
        long timeSMOOTHING=Stage.SMOOTHING.start();
        this.smoothing.apply(map, seedX, seedZ, this.smoothingIterations);
        Stage.SMOOTHING.end(timeSMOOTHING);
    }
    
    public void applyCorrections(Filterable map, int seedX, int seedZ) {
        long timeCORRECTIONS=Stage.CORRECTIONS.start();
        this.corrections.apply(map, seedX, seedZ, 1);
        Stage.CORRECTIONS.end(timeCORRECTIONS);
    }
}
