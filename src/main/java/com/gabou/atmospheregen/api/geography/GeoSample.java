/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.api.geography;

import java.util.Objects;

/**
 * Detached immutable finalized surface geography. Elevation is continuous block Y, NOT a final
 * Minecraft block-state/top-solid height; caves, density interpolation and surface features may differ.
 * Sea-relative elevation uses the configured sea-level block Y. Neither elevation is an unknown sentinel.
 */
public record GeoSample(BlockPosition position, double elevationBlockY, double seaRelativeElevationBlocks,
        WaterCategory water, Landform landform, GeographyMetrics metrics, HydrologySample hydrology) {
    public GeoSample {
        Objects.requireNonNull(position); Objects.requireNonNull(water); Objects.requireNonNull(landform);
        Objects.requireNonNull(metrics); Objects.requireNonNull(hydrology);
        if (!Double.isFinite(elevationBlockY) || !Double.isFinite(seaRelativeElevationBlocks))
            throw new IllegalArgumentException("Geography elevations must be finite");
        if (water != hydrology.water()) throw new IllegalArgumentException("Conflicting water classifications");
    }
}
