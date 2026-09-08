/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography;

/**
 * Internal mutable generation storage's geography-only view, NOT a public sample or lease.
 * Backends mutate their concrete workspace in batches; no wrapper/immutable record per cell.
 * Normalized elevations use the backend's existing scale. No biome/climate parameters belong here.
 */
public interface GeographyWorkspace {
    float normalizedElevation();
    float continentLandValue();
    float terrainRegionSelector();
    /** Signed accumulated physical removal delta, not Minecraft's erosion parameter. */
    float normalizedErosionDelta();
    float normalizedSediment();
}
