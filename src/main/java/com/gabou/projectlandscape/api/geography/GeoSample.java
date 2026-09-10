/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.api.geography;

import java.util.Objects;
import java.util.Optional;

/**
 * Detached immutable finalized surface geography. Elevation is continuous block Y, NOT a final
 * Minecraft block-state/top-solid height; caves, density interpolation and surface features may differ.
 * Sea-relative elevation uses the configured sea-level block Y. Neither elevation is an unknown sentinel.
 */
public record GeoSample(BlockPosition position, double elevationBlockY, double seaRelativeElevationBlocks,
        WaterCategory water, Landform landform, GeographyMetrics metrics, HydrologySample hydrology,
        Optional<LegacyTerrainSignals> legacyTerrainSignals) {
    /** Source-compatible constructor for providers without legacy backend diagnostics. */
    public GeoSample(BlockPosition position, double elevationBlockY, double seaRelativeElevationBlocks,
            WaterCategory water, Landform landform, GeographyMetrics metrics, HydrologySample hydrology) {
        this(position,elevationBlockY,seaRelativeElevationBlocks,water,landform,metrics,hydrology,Optional.empty());
    }
    public GeoSample {
        Objects.requireNonNull(position); Objects.requireNonNull(water); Objects.requireNonNull(landform);
        Objects.requireNonNull(metrics); Objects.requireNonNull(hydrology);
        Objects.requireNonNull(legacyTerrainSignals);
        if (!Double.isFinite(elevationBlockY) || !Double.isFinite(seaRelativeElevationBlocks))
            throw new IllegalArgumentException("Geography elevations must be finite");
        if (water != hydrology.water()) throw new IllegalArgumentException("Conflicting water classifications");
    }
}
