/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.persistence;

import com.gabou.projectlandscape.compat.legacy.LegacyGenerationData;
import com.gabou.projectlandscape.compat.legacy.LegacyPresetSnapshot;
import com.gabou.projectlandscape.config.*;
import com.gabou.projectlandscape.generation.version.GenerationVersions;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;

/** Explicit developer-only selection for geography plus baseline climate V1. */
public final class DevelopmentClimateSelection {
    private DevelopmentClimateSelection() {}
    public static Path path(Path directory) { return directory.resolve("data/atmospheregen/development_climate_v1.json"); }
    public static Optional<GenerationManifest> requested(Path directory, ServerLevel level, ChunkGenerator generator, GeneratorContext terrain) {
        Path file=path(directory); if(!Files.exists(file)) return Optional.empty();
        try {
            var json=JsonParser.parseString(Files.readString(file));
            var climate=BaselineClimateConfig.Planned.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow(false,s->{});
            var macro=MacroGeographySettings.defaults();
            var data=new TreeMap<>(LegacyGenerationData.capture(level,generator));
            var retained=LegacyPresetSnapshot.capture(terrain.preset);
            data.put("retained_terrain_effective_preset",GenerationFingerprint.of(retained.effectivePreset()));
            data.put("retained_tile_geometry",GenerationFingerprint.of(CanonicalJson.of(new com.google.gson.JsonPrimitive("tile=3;border="+retained.borderChunks()))));
            var settings=new PlannedGeographySettings(macro.continentScaleBlocks(),macro.minimumMajorOceanWidthBlocks(),
                terrain.preset.terrain().general.terrainRegionSize,1000,0,Optional.of(macro));
            return Optional.of(GenerationManifest.create(new ManifestContent(GenerationVersions.climateV1(),level.getSeed(),level.dimension().location(),
                new WorldGeographyConfig(Optional.empty(),Optional.of(settings)),new BaselineClimateConfig(Optional.of(climate)),
                new BiomeResolverConfig(Optional.empty()),data,Optional.empty())));
        } catch(IOException failure) { throw new UncheckedIOException("Cannot read V1 developer climate selection "+file,failure); }
    }
}
