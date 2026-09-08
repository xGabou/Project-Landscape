/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.generation.version;
import com.mojang.serialization.Codec;
public enum GeographyAlgorithmVersion {
    LEGACY_RTF_V0, PA_GEOGRAPHY_V1;
    public static final Codec<GeographyAlgorithmVersion> CODEC=VersionCodecs.of(GeographyAlgorithmVersion.class);
}

