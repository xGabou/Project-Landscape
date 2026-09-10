/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.persistence;

import com.gabou.projectlandscape.config.*;
import com.gabou.projectlandscape.generation.version.GenerationVersions;
import com.gabou.projectlandscape.compat.legacy.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;

/** Explicit per-save developer selection. No public preset/default change, no existing-world migration. */
public final class DevelopmentGeographySelection {
    private DevelopmentGeographySelection() {}
    public static Path path(Path dimensionDirectory){return dimensionDirectory.resolve("data/atmospheregen/development_geography_v1.json");}
    public static Optional<GenerationManifest> requested(Path directory,ServerLevel level,ChunkGenerator generator,GeneratorContext terrain) {
        Path file=path(directory);if(!Files.exists(file))return Optional.empty();
        try {
            var macro=MacroGeographySettings.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(Files.readString(file))).getOrThrow(false,s->{});
            var data=new TreeMap<>(LegacyGenerationData.capture(level,generator));
            var retained=LegacyPresetSnapshot.capture(terrain.preset);
            data.put("retained_terrain_effective_preset",GenerationFingerprint.of(retained.effectivePreset()));
            data.put("retained_tile_geometry",GenerationFingerprint.of(CanonicalJson.of(new com.google.gson.JsonPrimitive("tile=3;border="+retained.borderChunks()))));
            var settings=new PlannedGeographySettings(macro.continentScaleBlocks(),macro.minimumMajorOceanWidthBlocks(),
                terrain.preset.terrain().general.terrainRegionSize,1000,0,Optional.of(macro));
            return Optional.of(GenerationManifest.create(new ManifestContent(GenerationVersions.geographyV1(),level.getSeed(),level.dimension().location(),
                new WorldGeographyConfig(Optional.empty(),Optional.of(settings)),new BaselineClimateConfig(Optional.empty()),new BiomeResolverConfig(Optional.empty()),data,Optional.empty())));
        }catch(IOException failure){throw new UncheckedIOException("Cannot read V1 developer geography selection "+file,failure);}
    }
}
