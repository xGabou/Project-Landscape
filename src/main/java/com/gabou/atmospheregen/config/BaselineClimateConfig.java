/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
/** Empty for legacy: its noise parameters are NOT physical model climate. Planned settings are inactive. */
public record BaselineClimateConfig(Optional<Planned> planned) {
    public static final Codec<BaselineClimateConfig> CODEC=ConfigCodecs.optional("planned",Planned.CODEC).codec().xmap(BaselineClimateConfig::new,BaselineClimateConfig::planned);
    public BaselineClimateConfig {java.util.Objects.requireNonNull(planned);}
    public record Planned(double latitudeScaleBlocks,double lapseCelsiusPerBlock,double oceanInfluence,double rainShadowStrength) {
        public static final Codec<Planned> CODEC=RecordCodecBuilder.create(i->i.group(
            ConfigCodecs.finite("latitudeScaleBlocks",1,30000000).fieldOf("latitudeScaleBlocks").forGetter(Planned::latitudeScaleBlocks),
            ConfigCodecs.finite("lapseCelsiusPerBlock",0,1).fieldOf("lapseCelsiusPerBlock").forGetter(Planned::lapseCelsiusPerBlock),
            ConfigCodecs.finite("oceanInfluence",0,1).fieldOf("oceanInfluence").forGetter(Planned::oceanInfluence),
            ConfigCodecs.finite("rainShadowStrength",0,1).fieldOf("rainShadowStrength").forGetter(Planned::rainShadowStrength)
        ).apply(i,Planned::new));
        public Planned {ConfigCodecs.check("latitudeScaleBlocks",latitudeScaleBlocks,1,30000000);ConfigCodecs.check("lapseCelsiusPerBlock",lapseCelsiusPerBlock,0,1);ConfigCodecs.check("oceanInfluence",oceanInfluence,0,1);ConfigCodecs.check("rainShadowStrength",rainShadowStrength,0,1);}
    }
}
