/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen;

import raccoonman.reterraforged.world.worldgen.cell.geography.LegacyGeographyFinalizationStage;

/** Compatibility name for existing internal/filter test consumers. Physical ownership is the stage. */
public class WorldFilters extends LegacyGeographyFinalizationStage {
    public WorldFilters(GeneratorContext context) { super(context); }
}
