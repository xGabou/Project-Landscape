/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.geography;

import com.gabou.projectlandscape.geography.TerrainStage;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.CellPopulator;

/** Existing region and terrain graphs, including ocean/coast height blending and legacy hints. */
public record LegacyTerrainStage(CellPopulator region, CellPopulator terrain, float frequency) implements TerrainStage<Cell> {
    @Override public void apply(Cell cell, float x, float z) {
        region.apply(cell,x,z);
        terrain.apply(cell,x*frequency,z*frequency);
    }
}
