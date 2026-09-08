/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen.cell.geography;

import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.ControlPoints;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;

/**
 * Geographic side effects of old biome classification, not future climate authority.
 * V0 ordering is part of the contract: biome-center mask during the first module pass,
 * submerged correction before any optional second pass, all before tile erosion.
 */
public final class LegacyCoastCompatibility {
    private LegacyCoastCompatibility() {}
    public static void applyBiomeCenterCoast(Cell cell, float biomeCenterLandValue, ControlPoints controlPoints) {
        if(cell.terrain.isOverground() && !cell.terrain.overridesCoast() && biomeCenterLandValue<=controlPoints.coastMarker()) {
            cell.terrain=TerrainType.COAST;
        }
    }
    /** Caller has established height <= water; still applies when biome classification is disabled. */
    public static void applySubmergedCoast(Cell cell) {
        if(cell.terrain==TerrainType.COAST)cell.terrain=TerrainType.SHALLOW_OCEAN;
    }
}
