/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
/** Planned controls have no effect on the legacy MultiNoiseBiomeSource. */
public record BiomeResolverConfig(Optional<Planned> planned) {
    public static final Codec<BiomeResolverConfig> CODEC=ConfigCodecs.optional("planned",Planned.CODEC).codec().xmap(BiomeResolverConfig::new,BiomeResolverConfig::planned);
    public BiomeResolverConfig {java.util.Objects.requireNonNull(planned);}
    public record Planned(int spatialResolutionBlocks,int fallbackWeight) {
        public static final Codec<Planned> CODEC=RecordCodecBuilder.create(i->i.group(
            ConfigCodecs.integer("spatialResolutionBlocks",1,30000000).fieldOf("spatialResolutionBlocks").forGetter(Planned::spatialResolutionBlocks),
            ConfigCodecs.integer("fallbackWeight",1,1000000).fieldOf("fallbackWeight").forGetter(Planned::fallbackWeight)
        ).apply(i,Planned::new));
        public Planned {if(spatialResolutionBlocks<1||spatialResolutionBlocks>30000000||fallbackWeight<1||fallbackWeight>1000000)throw new IllegalArgumentException("Positive bounded biome resolution and weight required");}
    }
}
