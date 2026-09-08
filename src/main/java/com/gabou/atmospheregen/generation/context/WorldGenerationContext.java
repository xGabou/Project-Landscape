/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.generation.context;

import com.gabou.atmospheregen.generation.seed.*;
import com.gabou.atmospheregen.persistence.*;
import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Immutable world/dimension generation environment. Persistent identity is a content identity
 * (same seed/dimension/manifest => same ID), not a unique save-folder UUID. Runtime cache identity
 * is deliberately distinct for separate loads/instances even with equal persistent content.
 */
public final class WorldGenerationContext {
    private final GenerationManifest manifest;
    private final ResourceKey<Level> dimension;
    private final GenerationSeedService seeds;
    private final Object runtimeToken;

    public WorldGenerationContext(GenerationManifest manifest, ResourceKey<Level> dimension) {
        this(manifest, dimension, new Object());
    }
    /** Bind an already context-scoped cache token once; never use a mutable current-world global. */
    public WorldGenerationContext(GenerationManifest manifest, ResourceKey<Level> dimension, Object runtimeToken) {
        this.manifest = Objects.requireNonNull(manifest);
        this.dimension = Objects.requireNonNull(dimension);
        this.runtimeToken = Objects.requireNonNull(runtimeToken);
        if (!dimension.location().equals(manifest.content().dimension())) throw new IllegalArgumentException("Generation context dimension does not match manifest");
        seeds = new NamedSeedService(worldSeed(), dimension.location(), manifest.content().versions());
    }
    public long worldSeed() { return manifest.content().worldSeed(); }
    public ResourceKey<Level> dimension() { return dimension; }
    public GenerationManifest manifest() { return manifest; }
    public GenerationSeedService seeds() { return seeds; }
    public String contextId() { return "atmospheregen:" + manifest.fingerprint().sha256(); }
    public Object runtimeToken() { return runtimeToken; }
}
