/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.geography;

import java.util.Objects;
import java.util.Optional;

/**
 * Truthfully named V0 geographic diagnostics; not future physical continentality or climate.
 * Land/region values are dimensionless legacy selectors, NOT distances or globally unique IDs.
 * Site is the existing corrected continent center used for river-map lookup (not a connected landmass).
 * Mountain contributions are height-blend population weights captured before carving/filtering,
 * not ridge geometry or the fraction of final elevation caused by uplift. Selector is unknown in
 * ocean-only branches where the mountain graph was never evaluated. Support is one sampled block.
 * Erosion delta/sediment are accumulated legacy normalized height changes, converted to block units;
 * erosion delta is nonpositive removal, not the final elevation or Minecraft erosion parameter.
 */
public record LegacyTerrainSignals(double landValue, BlockPosition continentSite,
        double terrainRegionSelector, double terrainRegionEdge,
        Optional<Metric> mountainChainSelector, double mountainChainContribution,
        double regionalMountainContribution, double erosionRemovalDeltaBlocks, double sedimentBlocks) {
    public LegacyTerrainSignals {
        Objects.requireNonNull(continentSite); Objects.requireNonNull(mountainChainSelector);
        if(!Double.isFinite(landValue) || !Double.isFinite(terrainRegionSelector) || !Double.isFinite(terrainRegionEdge)
                || !Double.isFinite(mountainChainContribution) || !Double.isFinite(regionalMountainContribution)
                || !Double.isFinite(erosionRemovalDeltaBlocks) || !Double.isFinite(sedimentBlocks))
            throw new IllegalArgumentException("Legacy geographic signals must be finite");
        if(mountainChainContribution<0 || mountainChainContribution>1 || regionalMountainContribution<0 || regionalMountainContribution>1)
            throw new IllegalArgumentException("Mountain population contributions must be in [0,1]");
    }
}
