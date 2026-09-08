/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.config;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;

/** Version validation selects exactly one section. Planned controls do not modify the legacy backend. */
public record WorldGeographyConfig(Optional<LegacyGeographySettings> legacy, Optional<PlannedGeographySettings> planned) {
    public static final Codec<WorldGeographyConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        ConfigCodecs.optional("legacy", LegacyGeographySettings.CODEC).forGetter(WorldGeographyConfig::legacy),
        ConfigCodecs.optional("planned", PlannedGeographySettings.CODEC).forGetter(WorldGeographyConfig::planned)
    ).apply(i, WorldGeographyConfig::new));
    public WorldGeographyConfig { Objects.requireNonNull(legacy); Objects.requireNonNull(planned); }
}
