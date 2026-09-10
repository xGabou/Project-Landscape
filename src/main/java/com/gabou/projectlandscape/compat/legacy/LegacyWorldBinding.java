/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.compat.legacy;

import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import com.gabou.projectlandscape.config.*;
import com.gabou.projectlandscape.generation.context.WorldGenerationContext;
import com.gabou.projectlandscape.generation.version.GenerationVersions;
import com.gabou.projectlandscape.persistence.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import raccoonman.reterraforged.world.worldgen.densityfunction.CellSampler;

/** Binds metadata after RandomState initialization and before ChunkMap publishes or schedules chunks. */
public final class LegacyWorldBinding {
    private LegacyWorldBinding() {}

    public static void bind(ServerLevel level, Path dimensionDirectory, ChunkGenerator generator, RTFRandomState state) {
        Path file = GenerationManifestStore.path(dimensionDirectory);
        Optional<GenerationManifest> recognized = Optional.empty();
        GenerationManifestStore.ResolutionKind creationKind = null;
        // Project Landscape owns every new Overworld router supplied by its built-in default data.
        if (level.dimension().equals(Level.OVERWORLD) && usesLegacyCells(generator)) {
            var legacy = state.requireGeneratorContext("freeze LEGACY_RTF_V0 manifest before chunk generation");
            if (state.preset() == null) throw new IllegalStateException("RTF router has no identifiable active preset in " + level.dimension().location());
            if (java.nio.file.Files.exists(file)) {
                GenerationManifest persisted;
                try { persisted = GenerationManifestStore.read(file); }
                catch (IOException failure) { throw new UncheckedIOException("Cannot read persisted generation manifest for " + level.dimension().location(), failure); }
                if (persisted.content().worldSeed() != level.getSeed() || !persisted.content().dimension().equals(level.dimension().location()))
                    throw new IllegalStateException("Persisted generation manifest does not belong to this Overworld");
                boolean legacyManifest = persisted.content().versions().equals(GenerationVersions.legacy());
                if (legacyManifest != state.usesLegacyData())
                    throw new IllegalStateException("Persisted generation manifest/backend namespace mismatch; restore the matching Project Landscape or legacy data");
                recognized = Optional.of(persisted);
            } else if (state.usesLegacyData()) {
                recognized = Optional.of(legacyManifest(level, generator, legacy));
                creationKind = GenerationManifestStore.ResolutionKind.EXPLICIT_LEGACY_ASSIGNMENT;
            } else {
                recognized = Optional.of(plannedManifest(level, generator, legacy));
                var developer=DevelopmentBiomeSelection.requested(dimensionDirectory,level,generator,legacy)
                    .or(()->DevelopmentClimateSelection.requested(dimensionDirectory,level,generator,legacy))
                    .or(()->DevelopmentGeographySelection.requested(dimensionDirectory,level,generator,legacy));
                if(developer.isPresent()) {
                    recognized=developer;
                    var versions = developer.get().content().versions();
                    creationKind = versions.equals(GenerationVersions.geographyV1())
                        ? GenerationManifestStore.ResolutionKind.EXPLICIT_V1_DEVELOPMENT_ASSIGNMENT
                        : versions.equals(GenerationVersions.climateV1())
                            ? GenerationManifestStore.ResolutionKind.EXPLICIT_V1_CLIMATE_ASSIGNMENT
                            : GenerationManifestStore.ResolutionKind.EXPLICIT_V1_BIOME_ASSIGNMENT;
                } else creationKind = GenerationManifestStore.ResolutionKind.AUTOMATIC_PROJECT_LANDSCAPE_DEFAULT;
            }
        }
        try {
            GenerationManifestStore.resolve(file, recognized, creationKind).ifPresent(resolution -> {
                var legacy = state.requireGeneratorContext("bind persisted generation metadata");
                if(resolution.manifest().content().versions().equals(GenerationVersions.geographyV1()) || resolution.manifest().content().versions().equals(GenerationVersions.climateV1()) || resolution.manifest().content().versions().equals(GenerationVersions.planned())) {
                    var config=raccoonman.reterraforged.config.PerformanceConfig.read(raccoonman.reterraforged.config.PerformanceConfig.DEFAULT_FILE_PATH)
                        .getOrThrow(false,s->{});
                    com.gabou.projectlandscape.geography.terrain.PaGeographyInstallation.install(legacy,
                        new com.gabou.projectlandscape.generation.seed.NamedSeedService(level.getSeed(),level.dimension().location(),GenerationVersions.geographyV1()),
                        resolution.manifest().content().geography().planned().orElseThrow().macro().orElseThrow(),config.batchCount(),
                        raccoonman.reterraforged.concurrent.ThreadPools.availableProcessors()>4);
                }
                legacy.bindGenerationContext(new WorldGenerationContext(resolution.manifest(), level.dimension(), legacy.lookup.samplingIdentity()));
                RTFCommon.LOGGER.info("AtmosphereGen {}: {} dimension={} fingerprint={}", resolution.kind(),
                    resolution.manifest().content().versions().geography(), level.dimension().location(), resolution.manifest().fingerprint().sha256());
            });
        } catch (IOException failure) { throw new UncheckedIOException("Cannot persist generation manifest for " + level.dimension().location() + "; world generation must not proceed", failure); }
    }

    private static GenerationManifest legacyManifest(ServerLevel level, ChunkGenerator generator, raccoonman.reterraforged.world.worldgen.GeneratorContext legacy) {
        return GenerationManifest.create(new ManifestContent(GenerationVersions.legacy(), level.getSeed(), level.dimension().location(),
            new WorldGeographyConfig(Optional.of(LegacyPresetSnapshot.capture(legacy.preset)), Optional.empty()),
            new BaselineClimateConfig(Optional.empty()), new BiomeResolverConfig(Optional.empty()),
            LegacyGenerationData.capture(level, generator), Optional.empty()));
    }

    private static GenerationManifest plannedManifest(ServerLevel level, ChunkGenerator generator, raccoonman.reterraforged.world.worldgen.GeneratorContext legacy) {
        var macro = MacroGeographySettings.defaults();
        var settings = new PlannedGeographySettings(macro.continentScaleBlocks(), macro.minimumMajorOceanWidthBlocks(),
            legacy.preset.terrain().general.terrainRegionSize, 1000, 0, Optional.of(macro));
        var climate = new BaselineClimateConfig.Planned(100000, 0, .0065, .5, 1, 4096, .85, 12000, 24000, 256, 1, .10);
        var resolver = new BiomeResolverConfig.Planned(64, 1);
        var data = new java.util.TreeMap<>(LegacyGenerationData.capture(level, generator));
        var retained = LegacyPresetSnapshot.capture(legacy.preset);
        data.put("retained_terrain_effective_preset", GenerationFingerprint.of(retained.effectivePreset()));
        data.put("retained_tile_geometry", GenerationFingerprint.of(CanonicalJson.of(new com.google.gson.JsonPrimitive("tile=3;border=" + retained.borderChunks()))));
        return GenerationManifest.create(new ManifestContent(GenerationVersions.planned(), level.getSeed(), level.dimension().location(),
            new WorldGeographyConfig(Optional.empty(), Optional.of(settings)), new BaselineClimateConfig(Optional.of(climate)),
            new BiomeResolverConfig(Optional.of(resolver)), data, Optional.of(new com.gabou.projectlandscape.biome.VanillaBiomeCatalog().fingerprint())));
    }

    private static boolean usesLegacyCells(ChunkGenerator generator) {
        if (!(generator instanceof NoiseBasedChunkGenerator noise)) return false;
        AtomicBoolean found = new AtomicBoolean();
        noise.generatorSettings().value().noiseRouter().mapAll(function -> {
            if (function instanceof CellSampler.Marker) found.set(true);
            return function;
        });
        return found.get();
    }
}
