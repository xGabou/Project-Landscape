/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.climate;

import com.gabou.projectlandscape.biome.CanonicalClimateGeography;
import com.gabou.projectlandscape.geography.terrain.PaGeographyProvider;
import com.gabou.projectlandscape.generation.context.WorldGenerationContext;

/** One canonical climate hierarchy per bound GeneratorContext. No static world references. */
public final class CanonicalClimateService {
    private final CanonicalClimateGeography geography;
    private final PaBaselineClimateProvider provider;
    private final BaselineClimateModel model;
    public CanonicalClimateService(PaGeographyProvider source,WorldGenerationContext generation) {
        var config=generation.manifest().content().baselineClimate().planned().orElseThrow();
        geography=new CanonicalClimateGeography(source);
        provider=new PaBaselineClimateProvider(geography,generation.seeds(),config);
        model=new BaselineClimateModel(generation.seeds(),config);
        source.onClose(provider::close);
    }
    public CanonicalClimateGeography geography(){return geography;}
    public PaBaselineClimateProvider provider(){return provider;}
    public BaselineClimateModel model(){return model;}
}
