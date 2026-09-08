/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.geography;

import com.gabou.atmospheregen.compat.legacy.LegacyPreFilterCompatibility;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.climate.Climate;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Levels;
import raccoonman.reterraforged.world.worldgen.biome.Erosion;
import raccoonman.reterraforged.world.worldgen.biome.Weirdness;

/** V0-only Minecraft parameter/classification order, deliberately BEFORE physical tile erosion. */
public record LegacyMinecraftParameterAdapter(Climate climate, Levels levels) implements LegacyPreFilterCompatibility<Cell> {
	public void apply(Cell cell, float x, float z, boolean applyClimate) {
		float riverValleyThreshold = 0.675F;
        if(cell.riverMask < riverValleyThreshold) {
        	cell.erosion = 0.445F;
        	cell.weirdness = 0.34F;
        }
        
        if(cell.terrain.isRiver()) {
            cell.erosion = -0.05F;
            cell.weirdness = -0.03F;
        }
        
        if(cell.terrain.isLake() && cell.height < this.levels.water) {
            cell.erosion = Erosion.LEVEL_4.mid();
            cell.weirdness = -0.03F;
        }
        if(cell.terrain.isWetland()) {
        	cell.erosion = Erosion.LEVEL_6.mid();
        	cell.weirdness = Weirdness.VALLEY.mid();
        }
        
        this.climate.apply(cell, x, z, applyClimate);

        if(cell.riverMask >= riverValleyThreshold && cell.macroBiomeId > 0.5F) { 
        	cell.weirdness = -cell.weirdness;
        }
	}
	
}
