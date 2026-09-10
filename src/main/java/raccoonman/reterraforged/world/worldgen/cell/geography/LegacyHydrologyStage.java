/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.geography;

import com.gabou.projectlandscape.geography.HydrologyStage;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Levels;
import raccoonman.reterraforged.world.worldgen.cell.rivermap.Rivermap;
import raccoonman.reterraforged.world.worldgen.cell.terrain.populator.VolcanoPopulator;

/** Owns primitive-key map retrieval, per-chunk reuse and carving. Legacy cache implementation is retained. */
public record LegacyHydrologyStage(RiverMapSource source, Levels levels) implements HydrologyStage<Cell,Rivermap> {
    @FunctionalInterface public interface RiverMapSource { Rivermap get(int siteX, int siteZ); }
    public Rivermap resolve(int siteX, int siteZ, Rivermap previous) {
        if(previous!=null && siteX==previous.getX() && siteZ==previous.getZ())return previous;
        return source.get(siteX,siteZ);
    }
    @Override public Rivermap apply(Cell cell, float x, float z, Rivermap previous) {
        Rivermap current=resolve(cell.continentX,cell.continentZ,previous);
        carve(cell,x,z,current);
        return current;
    }
    public void carve(Cell cell, float x, float z, Rivermap rivers) {
        rivers.apply(cell,x,z);
        VolcanoPopulator.modifyVolcanoType(cell,levels);
    }
}
