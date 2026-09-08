/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.generation.seed;

import com.gabou.atmospheregen.generation.version.GenerationVersions;

/** Serialized names are permanent seed-domain IDs; enum ordinal and declaration order are never used. */
public enum SeedDomain {
    CONTINENT("continent", Kind.GEOGRAPHY),
    OCEAN("ocean", Kind.GEOGRAPHY),
    TERRAIN_REGIONS("terrain_regions", Kind.GEOGRAPHY),
    MOUNTAIN_CHAINS("mountain_chains", Kind.GEOGRAPHY),
    HYDROLOGY("hydrology", Kind.GEOGRAPHY),
    SURFACE("surface", Kind.GEOGRAPHY),
    BASELINE_TEMPERATURE("baseline_temperature", Kind.CLIMATE),
    BASELINE_PRECIPITATION("baseline_precipitation", Kind.CLIMATE),
    BIOME_SPATIAL_SELECTION("biome_spatial_selection", Kind.BIOME);

    private enum Kind { GEOGRAPHY, CLIMATE, BIOME }
    private final String id;
    private final Kind kind;
    SeedDomain(String path, Kind kind) { this.id = "atmospheregen:" + path; this.kind = kind; }
    public String id() { return id; }
    public String algorithmVersion(GenerationVersions versions) {
        return switch (kind) {
            case GEOGRAPHY -> versions.geography().name();
            case CLIMATE -> versions.baselineClimate().name();
            case BIOME -> versions.biomeResolver().name();
        };
    }
}
