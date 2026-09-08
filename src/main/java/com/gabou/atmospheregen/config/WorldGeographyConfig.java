/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.config;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.Objects;
import java.util.Optional;

/** Version validation selects exactly one section. Planned controls do not modify the legacy backend. */
public record WorldGeographyConfig(Optional<LegacyGeographySettings> legacy, Optional<PlannedGeographySettings> planned) {
    private static final Codec<Pair<Optional<LegacyGeographySettings>, Optional<PlannedGeographySettings>>> FIELDS = RecordCodecBuilder.create(i -> i.group(
        ConfigCodecs.optional("legacy", LegacyGeographySettings.CODEC).forGetter(Pair::getFirst),
        ConfigCodecs.optional("planned", PlannedGeographySettings.CODEC).forGetter(Pair::getSecond)
    ).apply(i, Pair::of));
    public static final Codec<WorldGeographyConfig> CODEC = FIELDS.comapFlatMap(pair -> {
        try { return DataResult.success(new WorldGeographyConfig(pair.getFirst(), pair.getSecond())); }
        catch (IllegalArgumentException invalid) { return DataResult.error(() -> invalid.getMessage()); }
    }, value -> Pair.of(value.legacy, value.planned));
    public WorldGeographyConfig {
        Objects.requireNonNull(legacy); Objects.requireNonNull(planned);
        if (legacy.isPresent() == planned.isPresent()) throw new IllegalArgumentException("Select exactly one geography config section: legacy or planned");
    }
}
