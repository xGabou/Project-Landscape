/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.world.worldgen;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderGetter;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Heightmap;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Levels;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.WorldLookup;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.generation.TileGenerator;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;
import raccoonman.reterraforged.world.worldgen.util.Seed;

public class GeneratorContext {
    public Seed seed;
    public Levels levels;
    public Preset preset;
    public HolderGetter<Noise> noiseLookup;
    public TileGenerator generator;
    @Nullable
    public TileCache cache;
    public WorldLookup lookup;
    // Task 2 metadata only. Sampling continues using the existing cheap WorldLookup token.
    private volatile com.gabou.projectlandscape.generation.context.WorldGenerationContext generationContext;
    private com.gabou.projectlandscape.geography.terrain.PaGeographyProvider canonicalGeography;
    private com.gabou.projectlandscape.climate.CanonicalClimateService canonicalClimate;
    private com.gabou.projectlandscape.compat.projectatmosphere.LandscapeForecastService atmosphereForecast;

    public synchronized com.gabou.projectlandscape.geography.terrain.PaGeographyProvider canonicalGeography() {
        if(cache==null||cache.isClosed())throw new IllegalStateException("Canonical geography context is disposed");
        if(canonicalGeography==null)canonicalGeography=new com.gabou.projectlandscape.geography.terrain.PaGeographyProvider(this);
        return canonicalGeography;
    }

    public synchronized com.gabou.projectlandscape.climate.CanonicalClimateService canonicalClimate(
            com.gabou.projectlandscape.generation.context.WorldGenerationContext generation) {
        if(generation!=generationContext||generation==null)throw new IllegalArgumentException("Climate service requires its bound generation context");
        if(cache==null||cache.isClosed())throw new IllegalStateException("Canonical climate context is disposed");
        if(canonicalClimate==null)canonicalClimate=new com.gabou.projectlandscape.climate.CanonicalClimateService(canonicalGeography(),generation);
        return canonicalClimate;
    }

    public synchronized void bindGenerationContext(com.gabou.projectlandscape.generation.context.WorldGenerationContext context) {
        if (this.generationContext != null) throw new IllegalStateException("RTF generation metadata is already bound");
        if (context.runtimeToken() != this.lookup.samplingIdentity()) throw new IllegalArgumentException("Generation metadata must share the owning lookup's cache identity");
        this.generationContext = java.util.Objects.requireNonNull(context);
    }

    @Nullable
    public com.gabou.projectlandscape.generation.context.WorldGenerationContext generationContext() {
        return this.generationContext;
    }

    /** Forecast-only service for optional atmosphere integrations. It never reads the terrain tile cache. */
    @Nullable
    public synchronized com.gabou.projectlandscape.compat.projectatmosphere.LandscapeForecastService atmosphereForecastService() {
        var generation = this.generationContext;
        if (generation == null || this.cache == null || this.cache.isClosed()
                || this.generator.getHeightmap().paBridge() == null) {
            return null;
        }
        if (this.atmosphereForecast == null) {
            this.atmosphereForecast = new com.gabou.projectlandscape.compat.projectatmosphere.LandscapeForecastService(this, generation);
        }
        return this.atmosphereForecast;
    }
    
    public GeneratorContext(Preset preset, HolderGetter<Noise> noiseLookup, int seed, int tileSize, int tileBorder, int batchCount, @Nullable TileCache cache) {
        this.preset = preset;
        this.noiseLookup = noiseLookup;
        this.seed = new Seed(seed);
        this.levels = new Levels(preset.world().properties.terrainScaler(), preset.world().properties.seaLevel);
        this.generator = new TileGenerator(Heightmap.make(this), new WorldFilters(this), tileSize, tileBorder, batchCount);
        this.cache = cache;
        this.lookup = new WorldLookup(this);
    }

    public static GeneratorContext makeCached(Preset preset, HolderGetter<Noise> noiseLookup, int seed, int tileSize, int batchCount, boolean queue) {
        // Frozen legacy semantics: halo is derived from the persisted erosion preset,
        // one chunk below lifetime 32 and two at/above it. This changes filter output;
        // never reinterpret it as runtime performance tuning or silently normalize it.
    	GeneratorContext ctx = makeUncached(preset, noiseLookup, seed, tileSize, Math.min(2, Math.max(1, preset.filters().erosion.dropletLifetime / 16)), batchCount);
    	ctx.cache = new TileCache(tileSize, queue, ctx.generator);
    	ctx.lookup = new WorldLookup(ctx);
    	return ctx;
    }
    
    public static GeneratorContext makeUncached(Preset preset, HolderGetter<Noise> noiseLookup, int seed, int tileSize, int tileBorder, int batchCount) {
    	return new GeneratorContext(preset, noiseLookup, seed, tileSize, tileBorder, batchCount, null);
    }
}
