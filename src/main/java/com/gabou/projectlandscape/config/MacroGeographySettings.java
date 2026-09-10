/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Immutable V1 controls. No legacy factory reads this record. Distances/depths are in blocks. */
public record MacroGeographySettings(double continentScaleBlocks, double landCoverageTarget,
        double minimumMajorOceanWidthBlocks, double coastlineDetailBlocks, double islandFrequency,
        double archipelagoFrequency, double inlandSeaFrequency, double shelfWidthBlocks,
        double shelfDepthBlocks, double marineDepthInfluence) {
    public static final Codec<MacroGeographySettings> CODEC=RecordCodecBuilder.create(i->i.group(
        Codec.DOUBLE.fieldOf("continentScaleBlocks").forGetter(MacroGeographySettings::continentScaleBlocks),
        Codec.DOUBLE.fieldOf("landCoverageTarget").forGetter(MacroGeographySettings::landCoverageTarget),
        Codec.DOUBLE.fieldOf("minimumMajorOceanWidthBlocks").forGetter(MacroGeographySettings::minimumMajorOceanWidthBlocks),
        Codec.DOUBLE.fieldOf("coastlineDetailBlocks").forGetter(MacroGeographySettings::coastlineDetailBlocks),
        Codec.DOUBLE.fieldOf("islandFrequency").forGetter(MacroGeographySettings::islandFrequency),
        Codec.DOUBLE.fieldOf("archipelagoFrequency").forGetter(MacroGeographySettings::archipelagoFrequency),
        Codec.DOUBLE.fieldOf("inlandSeaFrequency").forGetter(MacroGeographySettings::inlandSeaFrequency),
        Codec.DOUBLE.fieldOf("shelfWidthBlocks").forGetter(MacroGeographySettings::shelfWidthBlocks),
        Codec.DOUBLE.fieldOf("shelfDepthBlocks").forGetter(MacroGeographySettings::shelfDepthBlocks),
        Codec.DOUBLE.fieldOf("marineDepthInfluence").forGetter(MacroGeographySettings::marineDepthInfluence)
    ).apply(i,MacroGeographySettings::new));
    public MacroGeographySettings {
        ConfigCodecs.check("continentScaleBlocks",continentScaleBlocks,8192,131072);
        ConfigCodecs.check("landCoverageTarget",landCoverageTarget,0.15,0.60);
        ConfigCodecs.check("minimumMajorOceanWidthBlocks",minimumMajorOceanWidthBlocks,256,32768);
        ConfigCodecs.check("coastlineDetailBlocks",coastlineDetailBlocks,0,512);
        ConfigCodecs.check("islandFrequency",islandFrequency,0,1);
        ConfigCodecs.check("archipelagoFrequency",archipelagoFrequency,0,1);
        ConfigCodecs.check("inlandSeaFrequency",inlandSeaFrequency,0,1);
        ConfigCodecs.check("shelfWidthBlocks",shelfWidthBlocks,32,4096);
        ConfigCodecs.check("shelfDepthBlocks",shelfDepthBlocks,2,48);
        ConfigCodecs.check("marineDepthInfluence",marineDepthInfluence,0.1,1.5);
        double radius=continentScaleBlocks*Math.sqrt(landCoverageTarget/Math.PI);
        double available=(continentScaleBlocks-minimumMajorOceanWidthBlocks)/2;
        if(radius*1.10+continentScaleBlocks*0.02+coastlineDetailBlocks>available)
            throw new IllegalArgumentException("continentScaleBlocks/landCoverageTarget/coastlineDetailBlocks leave insufficient clearance for minimumMajorOceanWidthBlocks; increase scale or reduce coverage/detail/width");
        if(shelfWidthBlocks*2>minimumMajorOceanWidthBlocks)
            throw new IllegalArgumentException("shelfWidthBlocks must be <= half minimumMajorOceanWidthBlocks to retain a deep marine corridor");
        double shorelineMargin=continentScaleBlocks/2-(radius*1.10+continentScaleBlocks*0.02+coastlineDetailBlocks);
        if(shelfWidthBlocks*2>shorelineMargin)
            throw new IllegalArgumentException("shelfWidthBlocks leaves insufficient space for a complete shelf break before macro-cell boundaries; reduce shelf width, coverage or coastline detail");
    }
    public static MacroGeographySettings defaults() {return new MacroGeographySettings(16384,0.44,1000,128,0.30,0.16,0.20,384,18,1);}
}
