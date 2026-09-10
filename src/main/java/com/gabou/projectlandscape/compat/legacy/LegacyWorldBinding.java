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
        // Phase 1 only claims actual RTF Overworld routers, not mere availability of RTF registry tags.
        if (level.dimension().equals(Level.OVERWORLD) && usesLegacyCells(generator)) {
            var legacy = state.requireGeneratorContext("freeze LEGACY_RTF_V0 manifest before chunk generation");
            if (state.preset() == null) throw new IllegalStateException("RTF router has no identifiable active preset in " + level.dimension().location());
            recognized = Optional.of(GenerationManifest.create(new ManifestContent(GenerationVersions.legacy(), level.getSeed(),
                level.dimension().location(), new WorldGeographyConfig(Optional.of(LegacyPresetSnapshot.capture(legacy.preset)), Optional.empty()),
                new BaselineClimateConfig(Optional.empty()), new BiomeResolverConfig(Optional.empty()),
                LegacyGenerationData.capture(level, generator), Optional.empty())));
            var developer=DevelopmentBiomeSelection.requested(dimensionDirectory,level,generator,legacy)
                .or(()->DevelopmentClimateSelection.requested(dimensionDirectory,level,generator,legacy))
                .or(()->DevelopmentGeographySelection.requested(dimensionDirectory,level,generator,legacy));
            if(developer.isPresent())recognized=developer;
        }
        try {
            GenerationManifestStore.resolve(file, recognized).ifPresent(resolution -> {
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
