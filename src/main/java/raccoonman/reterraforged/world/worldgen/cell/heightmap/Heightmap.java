/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.heightmap;

import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.CellPopulator;
import raccoonman.reterraforged.world.worldgen.cell.climate.Climate;
import raccoonman.reterraforged.world.worldgen.cell.continent.Continent;
import raccoonman.reterraforged.world.worldgen.cell.geography.*;
import raccoonman.reterraforged.world.worldgen.cell.rivermap.Rivermap;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;

/**
 * Legacy compatibility facade for direct/old consumers. Graph construction and stage ownership
 * live in the frozen legacy backend. This is not canonical public geography.
 */
public record Heightmap(LegacyContinentStage continentStage, LegacyTerrainStage terrainStage,
        LegacyHydrologyStage hydrologyStage, LegacyMinecraftParameterAdapter legacyParameters,
        Levels levels, ControlPoints controlPoints) {
    public Heightmap(CellPopulator terrain, CellPopulator region, Continent continent, Climate climate,
            Levels levels, ControlPoints controlPoints, float terrainFrequency, Noise beachNoise) {
        this(new LegacyContinentStage(continent,beachNoise),new LegacyTerrainStage(region,terrain,terrainFrequency),
            new LegacyHydrologyStage(continent::getRivermap,levels),new LegacyMinecraftParameterAdapter(climate,levels),levels,controlPoints);
    }
    public void apply(Cell cell,float x,float z,boolean applyClimate) {
        applyTerrain(cell,x,z);
        hydrologyStage.apply(cell,x,z,null);
        applyClimate(cell,x,z,applyClimate);
    }
    public void applyTerrain(Cell cell,float x,float z) {
        continentStage.apply(cell,x,z);
        terrainStage.apply(cell,x,z);
    }
    public void applyRivers(Cell cell,float x,float z,Rivermap rivers) { hydrologyStage.carve(cell,x,z,rivers); }
    public void applyClimate(Cell cell,float x,float z,boolean enabled) { legacyParameters.apply(cell,x,z,enabled); }
    public static Heightmap make(GeneratorContext context) { return LegacyGeographyFactory.make(context); }

    // Compatibility accessors: old feature seeds, spawn/preview topology and frozen test orchestration.
    public Continent continent() { return continentStage.backend(); }
    public Climate climate() { return legacyParameters.climate(); }
    public CellPopulator region() { return terrainStage.region(); }
    public CellPopulator terrain() { return terrainStage.terrain(); }
    public float terrainFrequency() { return terrainStage.frequency(); }
    public Noise beachNoise() { return continentStage.beachNoise(); }
}
