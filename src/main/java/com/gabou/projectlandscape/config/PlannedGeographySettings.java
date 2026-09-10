/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.config;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
/** Task 2 reserved metadata plus explicit Task 4 macro payload. Terrain/rivers retain the fingerprinted backend preset. */
public record PlannedGeographySettings(double continentScaleBlocks,double minimumMajorOceanWidthBlocks,double terrainScaleBlocks,double mountainScaleBlocks,double riverFrequency,java.util.Optional<MacroGeographySettings> macro) {
    public PlannedGeographySettings(double continentScaleBlocks,double minimumMajorOceanWidthBlocks,double terrainScaleBlocks,double mountainScaleBlocks,double riverFrequency) {
        this(continentScaleBlocks,minimumMajorOceanWidthBlocks,terrainScaleBlocks,mountainScaleBlocks,riverFrequency,java.util.Optional.empty());
    }
    public static final Codec<PlannedGeographySettings> CODEC=RecordCodecBuilder.create(i->i.group(
        ConfigCodecs.finite("continentScaleBlocks",1,30000000).fieldOf("continentScaleBlocks").forGetter(PlannedGeographySettings::continentScaleBlocks),
        ConfigCodecs.finite("minimumMajorOceanWidthBlocks",0,30000000).fieldOf("minimumMajorOceanWidthBlocks").forGetter(PlannedGeographySettings::minimumMajorOceanWidthBlocks),
        ConfigCodecs.finite("terrainScaleBlocks",1,30000000).fieldOf("terrainScaleBlocks").forGetter(PlannedGeographySettings::terrainScaleBlocks),
        ConfigCodecs.finite("mountainScaleBlocks",1,30000000).fieldOf("mountainScaleBlocks").forGetter(PlannedGeographySettings::mountainScaleBlocks),
        ConfigCodecs.finite("riverFrequency",0,1).fieldOf("riverFrequency").forGetter(PlannedGeographySettings::riverFrequency),
        ConfigCodecs.optional("macro",MacroGeographySettings.CODEC).forGetter(PlannedGeographySettings::macro)
    ).apply(i,PlannedGeographySettings::new));
    public PlannedGeographySettings {ConfigCodecs.check("continentScaleBlocks",continentScaleBlocks,1,30000000);ConfigCodecs.check("minimumMajorOceanWidthBlocks",minimumMajorOceanWidthBlocks,0,30000000);ConfigCodecs.check("terrainScaleBlocks",terrainScaleBlocks,1,30000000);ConfigCodecs.check("mountainScaleBlocks",mountainScaleBlocks,1,30000000);ConfigCodecs.check("riverFrequency",riverFrequency,0,1);}
}

