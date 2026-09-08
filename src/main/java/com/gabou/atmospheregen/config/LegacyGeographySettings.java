/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.config;

import com.gabou.atmospheregen.persistence.CanonicalJson;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/**
 * Opaque immutable backend-specific snapshot, including fields omitted by the old preset codec.
 * Legacy tile exponent 3 and the preset-derived halo are generation semantics, not tuning.
 * Workers/batch count are excluded: the accepted scheduling corpus proved their invariance.
 */
public record LegacyGeographySettings(CanonicalJson effectivePreset, int tileExponent, int borderChunks) {
    public static final Codec<LegacyGeographySettings> CODEC = RecordCodecBuilder.create(i -> i.group(
        CanonicalJson.CODEC.fieldOf("effectivePreset").forGetter(LegacyGeographySettings::effectivePreset),
        ConfigCodecs.integer("legacy tileExponent", 3, 3).fieldOf("tileExponent").forGetter(LegacyGeographySettings::tileExponent),
        ConfigCodecs.integer("legacy borderChunks", 1, 2).fieldOf("borderChunks").forGetter(LegacyGeographySettings::borderChunks)
    ).apply(i, LegacyGeographySettings::new));
    public LegacyGeographySettings {
        Objects.requireNonNull(effectivePreset);
        if (tileExponent != 3 || borderChunks < 1 || borderChunks > 2) throw new IllegalArgumentException("Invalid frozen legacy tile geometry");
    }
}
