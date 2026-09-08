/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.geography;

import com.gabou.atmospheregen.geography.ContinentStage;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.continent.Continent;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;

/** Frozen point preparation plus current continent model. Does not retrieve/apply river networks. */
public record LegacyContinentStage(Continent backend, Noise beachNoise) implements ContinentStage<Cell> {
    @Override public void apply(Cell cell, float x, float z) {
        cell.terrain=TerrainType.FLATS;
        cell.beachNoise=beachNoise.compute(x,z,0);
        backend.apply(cell,x,z);
    }
}
