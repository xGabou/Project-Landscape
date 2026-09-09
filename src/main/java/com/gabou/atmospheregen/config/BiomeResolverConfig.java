/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
/** V1 resolver controls; the legacy MultiNoiseBiomeSource does not read them. */
public record BiomeResolverConfig(Optional<Planned> planned) {
    public static final Codec<BiomeResolverConfig> CODEC=ConfigCodecs.optional("planned",Planned.CODEC).codec().xmap(BiomeResolverConfig::new,BiomeResolverConfig::planned);
    public BiomeResolverConfig {java.util.Objects.requireNonNull(planned);}
    /** Regional scale/strength are active. Spatial resolution, fallback weight and transition
     * softness remain reserved serialized fields, not hot-path approximation/fallback controls.
     * Exact canonical queries and fail-closed coverage deliberately do not read those fields. */
    public record Planned(int spatialResolutionBlocks,int fallbackWeight,double regionalVariationScaleBlocks,
            double regionalVariationStrength,double transitionSoftness) {
        public Planned(int spatialResolutionBlocks,int fallbackWeight) { this(spatialResolutionBlocks,fallbackWeight,4096,0.18,0.12); }
        public static final Codec<Planned> CODEC=RecordCodecBuilder.create(i->i.group(
            ConfigCodecs.integer("spatialResolutionBlocks",1,30000000).fieldOf("spatialResolutionBlocks").forGetter(Planned::spatialResolutionBlocks),
            ConfigCodecs.integer("fallbackWeight",1,1000000).fieldOf("fallbackWeight").forGetter(Planned::fallbackWeight),
            ConfigCodecs.finite("regionalVariationScaleBlocks",1,30000000).fieldOf("regionalVariationScaleBlocks").forGetter(Planned::regionalVariationScaleBlocks),
            ConfigCodecs.finite("regionalVariationStrength",0,1).fieldOf("regionalVariationStrength").forGetter(Planned::regionalVariationStrength),
            ConfigCodecs.finite("transitionSoftness",0,1).fieldOf("transitionSoftness").forGetter(Planned::transitionSoftness)
        ).apply(i,Planned::new));
        public Planned {if(spatialResolutionBlocks<1||spatialResolutionBlocks>30000000||fallbackWeight<1||fallbackWeight>1000000)throw new IllegalArgumentException("Positive bounded biome resolution and weight required");ConfigCodecs.check("regionalVariationScaleBlocks",regionalVariationScaleBlocks,1,30000000);ConfigCodecs.check("regionalVariationStrength",regionalVariationStrength,0,1);ConfigCodecs.check("transitionSoftness",transitionSoftness,0,1);}
    }
}
