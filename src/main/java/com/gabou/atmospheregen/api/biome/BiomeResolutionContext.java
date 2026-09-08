/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.api.biome;

import com.gabou.atmospheregen.generation.context.WorldGenerationContext;
import com.gabou.atmospheregen.persistence.GenerationFingerprint;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;

/** Frozen registry holder lookup and catalog identity, not mutable biome selection authority. */
public record BiomeResolutionContext(WorldGenerationContext generation, HolderGetter<Biome> biomes,
        Optional<GenerationFingerprint> catalogFingerprint) {
    public BiomeResolutionContext {
        Objects.requireNonNull(generation); Objects.requireNonNull(biomes); Objects.requireNonNull(catalogFingerprint);
        if (!catalogFingerprint.equals(generation.manifest().content().biomeCatalog()))
            throw new IllegalArgumentException("Biome catalog does not match generation manifest");
    }
}
