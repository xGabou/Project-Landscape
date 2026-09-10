/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.generation.version;
import com.mojang.serialization.Codec;
public enum BaselineClimateAlgorithmVersion {
    LEGACY_RTF_HINTS_V0, PA_BASELINE_V1;
    public static final Codec<BaselineClimateAlgorithmVersion> CODEC=VersionCodecs.of(BaselineClimateAlgorithmVersion.class);
}

