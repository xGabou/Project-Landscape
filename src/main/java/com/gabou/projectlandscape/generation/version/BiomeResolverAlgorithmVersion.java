/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.generation.version;
import com.mojang.serialization.Codec;
public enum BiomeResolverAlgorithmVersion {
    LEGACY_MULTINOISE_V0, PA_RESOLVER_V1;
    public static final Codec<BiomeResolverAlgorithmVersion> CODEC=VersionCodecs.of(BiomeResolverAlgorithmVersion.class);
}

