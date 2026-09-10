/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
/** Legacy is empty; planned settings are the frozen V1 baseline-climate controls. */
public record BaselineClimateConfig(Optional<Planned> planned) {
    public static final Codec<BaselineClimateConfig> CODEC=ConfigCodecs.optional("planned",Planned.CODEC).codec().xmap(BaselineClimateConfig::new,BaselineClimateConfig::planned);
    public BaselineClimateConfig {java.util.Objects.requireNonNull(planned);}
    public record Planned(double latitudeScaleBlocks,double equatorZ,double lapseCelsiusPerBlock,
            double oceanInfluence,double continentalityStrength,double windBandScaleBlocks,
            double orographicStrength,double rainShadowRecoveryDistance,double climateProfileDistance,
            double climateProfileStep,double evaporationStrength,double regionalVariationStrength) {
        /** Source-compatible defaults used by the Task 2 foundation fixtures. */
        public Planned(double latitudeScaleBlocks,double lapseCelsiusPerBlock,double oceanInfluence,double rainShadowStrength) {
            this(latitudeScaleBlocks,0,lapseCelsiusPerBlock,oceanInfluence,1,4096,orographicFromLegacy(rainShadowStrength),4096,24000,256,1,rainShadowStrength*0.25);
        }
        public static final Codec<Planned> CODEC=RecordCodecBuilder.create(i->i.group(
            ConfigCodecs.finite("latitudeScaleBlocks",1,30000000).fieldOf("latitudeScaleBlocks").forGetter(Planned::latitudeScaleBlocks),
            ConfigCodecs.finite("equatorZ",-30000000,30000000).fieldOf("equatorZ").forGetter(Planned::equatorZ),
            ConfigCodecs.finite("lapseCelsiusPerBlock",0,1).fieldOf("lapseCelsiusPerBlock").forGetter(Planned::lapseCelsiusPerBlock),
            ConfigCodecs.finite("oceanInfluence",0,1).fieldOf("oceanInfluence").forGetter(Planned::oceanInfluence),
            ConfigCodecs.finite("continentalityStrength",0,2).fieldOf("continentalityStrength").forGetter(Planned::continentalityStrength),
            ConfigCodecs.finite("windBandScaleBlocks",1,30000000).fieldOf("windBandScaleBlocks").forGetter(Planned::windBandScaleBlocks),
            ConfigCodecs.finite("orographicStrength",0,1).fieldOf("orographicStrength").forGetter(Planned::orographicStrength),
            ConfigCodecs.finite("rainShadowRecoveryDistance",1,30000000).fieldOf("rainShadowRecoveryDistance").forGetter(Planned::rainShadowRecoveryDistance),
            ConfigCodecs.finite("climateProfileDistance",1,30000000).fieldOf("climateProfileDistance").forGetter(Planned::climateProfileDistance),
            ConfigCodecs.finite("climateProfileStep",1,30000000).fieldOf("climateProfileStep").forGetter(Planned::climateProfileStep),
            ConfigCodecs.finite("evaporationStrength",0,4).fieldOf("evaporationStrength").forGetter(Planned::evaporationStrength),
            ConfigCodecs.finite("regionalVariationStrength",0,1).fieldOf("regionalVariationStrength").forGetter(Planned::regionalVariationStrength)
        ).apply(i,Planned::new));
        public Planned {
            ConfigCodecs.check("latitudeScaleBlocks",latitudeScaleBlocks,1,30000000);ConfigCodecs.check("equatorZ",equatorZ,-30000000,30000000);
            ConfigCodecs.check("lapseCelsiusPerBlock",lapseCelsiusPerBlock,0,1);ConfigCodecs.check("oceanInfluence",oceanInfluence,0,1);
            ConfigCodecs.check("continentalityStrength",continentalityStrength,0,2);ConfigCodecs.check("windBandScaleBlocks",windBandScaleBlocks,1,30000000);
            ConfigCodecs.check("orographicStrength",orographicStrength,0,1);ConfigCodecs.check("rainShadowRecoveryDistance",rainShadowRecoveryDistance,1,30000000);
            ConfigCodecs.check("climateProfileDistance",climateProfileDistance,1,30000000);ConfigCodecs.check("climateProfileStep",climateProfileStep,1,30000000);
            if(climateProfileStep>climateProfileDistance)throw new IllegalArgumentException("climateProfileStep must not exceed climateProfileDistance");
            ConfigCodecs.check("evaporationStrength",evaporationStrength,0,4);ConfigCodecs.check("regionalVariationStrength",regionalVariationStrength,0,1);
        }
        private static double orographicFromLegacy(double shadow){ConfigCodecs.check("rainShadowStrength",shadow,0,1);return shadow;}
    }
}
