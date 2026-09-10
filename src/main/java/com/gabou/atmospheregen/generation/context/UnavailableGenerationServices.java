/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.generation.context;

import com.gabou.atmospheregen.api.biome.ClimateBiomeResolver;
import com.gabou.atmospheregen.api.climate.BaselineClimateProvider;
import com.gabou.atmospheregen.api.geography.GeographyProvider;

/** Explicit unavailable contracts, never a misleading legacy-backed PA implementation. */
public final class UnavailableGenerationServices {
    private UnavailableGenerationServices() {}
    public static GeographyProvider plannedGeography() {
        return (x, z) -> { throw new UnsupportedOperationException("Unbound planned geography has no backend; V1 requires an installed world-scoped PaGeographyProvider"); };
    }
    public static BaselineClimateProvider baselineClimate() {
        return (x, z) -> { throw new UnsupportedOperationException("Physical baseline climate is not implemented; legacy hints are not ClimateBaseline"); };
    }
    public static ClimateBiomeResolver biomeResolver() {
        return (g, c, context) -> { throw new UnsupportedOperationException("Climate biome resolution is not implemented; legacy BiomeSource remains authoritative"); };
    }
}
