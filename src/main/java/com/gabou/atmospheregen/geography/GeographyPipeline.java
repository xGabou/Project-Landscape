/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography;

import java.util.Objects;
import com.gabou.atmospheregen.compat.legacy.LegacyPreFilterCompatibility;

/**
 * Batched geography orchestration. Does not allocate per-cell records, schedule threads, publish tiles,
 * or call future climate/biome services. The V0 compatibility hook is explicitly separate from geography.
 * No PA_GEOGRAPHY_V1 factory/alias is provided; a new backend needs implemented stages of its own.
 */
public final class GeographyPipeline<W extends GeographyWorkspace, R, T> {
    private final ContinentStage<W> continent;
    private final TerrainStage<W> terrain;
    private final HydrologyStage<W, R> hydrology;
    private final LegacyPreFilterCompatibility<W> legacyCompatibility;
    private final GeographyFinalizationStage<T> finalization;

    public GeographyPipeline(ContinentStage<W> continent, TerrainStage<W> terrain,
            HydrologyStage<W, R> hydrology, LegacyPreFilterCompatibility<W> legacyCompatibility,
            GeographyFinalizationStage<T> finalization) {
        this.continent=Objects.requireNonNull(continent); this.terrain=Objects.requireNonNull(terrain);
        this.hydrology=Objects.requireNonNull(hydrology); this.legacyCompatibility=Objects.requireNonNull(legacyCompatibility);
        this.finalization=Objects.requireNonNull(finalization);
    }
    public R generatePoint(W workspace, float x, float z, R previous, boolean classifyLegacyBiomes) {
        continent.apply(workspace,x,z);
        terrain.apply(workspace,x,z);
        R current=hydrology.apply(workspace,x,z,previous);
        legacyCompatibility.apply(workspace,x,z,classifyLegacyBiomes);
        return current;
    }
    public void finalizeTile(T workspace, boolean optionalFilters) { finalization.apply(workspace,optionalFilters); }
    public ContinentStage<W> continent() { return continent; }
    public TerrainStage<W> terrain() { return terrain; }
    public HydrologyStage<W,R> hydrology() { return hydrology; }
    public LegacyPreFilterCompatibility<W> legacyCompatibility() { return legacyCompatibility; }
}
