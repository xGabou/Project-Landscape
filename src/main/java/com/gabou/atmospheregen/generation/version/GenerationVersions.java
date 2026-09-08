/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.generation.version;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
public record GenerationVersions(int schemaVersion, GeographyAlgorithmVersion geography,
        BaselineClimateAlgorithmVersion baselineClimate, BiomeResolverAlgorithmVersion biomeResolver) {
    public static final Codec<GenerationVersions> CODEC=RecordCodecBuilder.create(i->i.group(
        com.gabou.atmospheregen.config.ConfigCodecs.integer("schemaVersion",1,1).fieldOf("schemaVersion").forGetter(GenerationVersions::schemaVersion),
        GeographyAlgorithmVersion.CODEC.fieldOf("geography").forGetter(GenerationVersions::geography),
        BaselineClimateAlgorithmVersion.CODEC.fieldOf("baselineClimate").forGetter(GenerationVersions::baselineClimate),
        BiomeResolverAlgorithmVersion.CODEC.fieldOf("biomeResolver").forGetter(GenerationVersions::biomeResolver)
    ).apply(i,GenerationVersions::new));
    public GenerationVersions {
        if(schemaVersion!=1)throw new IllegalArgumentException("Unsupported generation schema "+schemaVersion);
        Objects.requireNonNull(geography);Objects.requireNonNull(baselineClimate);Objects.requireNonNull(biomeResolver);
    }
    public static GenerationVersions legacy(){return new GenerationVersions(1,GeographyAlgorithmVersion.LEGACY_RTF_V0,BaselineClimateAlgorithmVersion.LEGACY_RTF_HINTS_V0,BiomeResolverAlgorithmVersion.LEGACY_MULTINOISE_V0);}
    public static GenerationVersions planned(){return new GenerationVersions(1,GeographyAlgorithmVersion.PA_GEOGRAPHY_V1,BaselineClimateAlgorithmVersion.PA_BASELINE_V1,BiomeResolverAlgorithmVersion.PA_RESOLVER_V1);}
    public static GenerationVersions geographyV1(){return new GenerationVersions(1,GeographyAlgorithmVersion.PA_GEOGRAPHY_V1,BaselineClimateAlgorithmVersion.LEGACY_RTF_HINTS_V0,BiomeResolverAlgorithmVersion.LEGACY_MULTINOISE_V0);}
    /** No misleading functional dispatch to an unfinished new generator. */
    public void requireFunctionalBackend() {
        if(!equals(legacy())&&!equals(geographyV1()))throw new UnsupportedOperationException("Generation backend tuple "+this+" is not implemented; climate and resolver V1 remain unavailable. No legacy fallback.");
    }
}
