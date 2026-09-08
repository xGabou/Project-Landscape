/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.compat.legacy;

import com.gabou.atmospheregen.persistence.*;
import com.google.gson.*;
import com.mojang.serialization.*;
import java.io.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.noise.module.Noise;
import raccoonman.reterraforged.world.worldgen.structure.rule.StructureRule;

/** Initialization-only canonical definition/tag/resource fingerprints. No sampling or noise compute. */
public final class LegacyGenerationData {
    private LegacyGenerationData() {}
    public static Map<String, GenerationFingerprint> capture(ServerLevel level, ChunkGenerator generator) {
        Map<String, GenerationFingerprint> result = new TreeMap<>();
        RegistryAccess access = level.registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        for (var entry : RegistryDataLoader.WORLDGEN_REGISTRIES) {
            String path = entry.key().location().getPath();
            if (path.startsWith("worldgen/") || path.equals("dimension_type")) registry(result, access, ops, entry);
        }
        registry(result, access, ops, RTFRegistries.NOISE, Noise.DIRECT_CODEC);
        registry(result, access, ops, RTFRegistries.STRUCTURE_RULE, StructureRule.DIRECT_CODEC);
        result.put("active_chunk_generator", encode(ChunkGenerator.CODEC, ops, generator, "active chunk generator"));
        // Resolve effective tags rather than pack ordering. All registries are included conservatively.
        JsonObject tags = new JsonObject();
        access.registries().forEach(entry -> {
            JsonObject registryTags = new JsonObject();
            entry.value().getTags().forEach(pair -> {
                JsonArray keys = new JsonArray();
                pair.getSecond().stream().map(h -> h.unwrapKey().orElseThrow().location().toString()).sorted().forEach(keys::add);
                registryTags.add(pair.getFirst().location().toString(), keys);
            });
            tags.add(entry.key().location().toString(), registryTags);
        });
        result.put("resolved_tags", GenerationFingerprint.of(CanonicalJson.of(tags)));
        JsonObject resourceDefinitions = new JsonObject();
        level.getServer().getResourceManager().listResources("", id -> id.getPath().endsWith(".json")
            && (id.getPath().startsWith("worldgen/") || id.getPath().startsWith("dimension/") || id.getPath().startsWith("dimension_type/")
                || id.getPath().startsWith("tags/") || id.getPath().startsWith("biome_modifier/") || id.getPath().startsWith("structure_modifier/")))
            .forEach((id, resource) -> {
                try (Reader reader = resource.openAsReader()) { resourceDefinitions.addProperty(id.toString(), GenerationFingerprint.of(CanonicalJson.of(JsonParser.parseReader(reader))).sha256()); }
                catch (IOException failure) { throw new UncheckedIOException("Cannot fingerprint generation resource " + id, failure); }
            });
        result.put("worldgen_json_resources", GenerationFingerprint.of(CanonicalJson.of(resourceDefinitions)));
        JsonObject templates = new JsonObject();
        level.getServer().getResourceManager().listResources("", id -> id.getPath().endsWith(".nbt")).forEach((id, resource) -> {
            try (InputStream stream = resource.open()) { templates.addProperty(id.toString(), GenerationFingerprint.bytes(stream.readAllBytes()).sha256()); }
            catch (IOException failure) { throw new UncheckedIOException("Cannot fingerprint generation template " + id, failure); }
        });
        result.put("nbt_resources", GenerationFingerprint.of(CanonicalJson.of(templates)));
        try (InputStream stream = LegacyGenerationData.class.getResourceAsStream("/biomes.png")) {
            if (stream == null) throw new IllegalStateException("Missing legacy biome classification table biomes.png");
            result.put("legacy_biome_table", GenerationFingerprint.bytes(stream.readAllBytes()));
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        JsonObject dependencies = new JsonObject();
        // Conservative: unknown external mods may affect worldgen via code not represented in datapacks.
        // The mod's own release number is deliberately NOT a generation algorithm version.
        net.minecraftforge.fml.ModList.get().getMods().stream().filter(mod -> !Set.of("reterraforged", "task1a_reproduction", "baseline_smoke").contains(mod.getModId()))
            .forEach(mod -> dependencies.addProperty(mod.getModId(), mod.getVersion().toString()));
        result.put("external_mod_versions", GenerationFingerprint.of(CanonicalJson.of(dependencies)));
        if (raccoonman.reterraforged.world.worldgen.terrablender.TBCompat.isEnabled())
            result.put("terrablender_effective_config_regions", LegacyTerraBlenderData.capture());
        return Collections.unmodifiableSortedMap(new TreeMap<>(result));
    }
    private static <T> void registry(Map<String, GenerationFingerprint> result, RegistryAccess access, RegistryOps<JsonElement> ops,
            RegistryDataLoader.RegistryData<T> entry) {
        registry(result, access, ops, entry.key(), entry.elementCodec());
    }
    private static <T> void registry(Map<String, GenerationFingerprint> result, RegistryAccess access, RegistryOps<JsonElement> ops,
            ResourceKey<? extends Registry<T>> key, Codec<T> codec) {
        var registry = access.registry(key).orElseThrow(() -> new IllegalStateException("Missing generation registry " + key.location()));
        JsonObject definitions = new JsonObject();
        for (var entry : registry.entrySet()) definitions.add(entry.getKey().location().toString(),
            codec.encodeStart(ops, entry.getValue()).getOrThrow(false, message -> {}));
        result.put("registry/" + key.location(), GenerationFingerprint.of(CanonicalJson.of(definitions)));
    }
    private static <T> GenerationFingerprint encode(Codec<T> codec, RegistryOps<JsonElement> ops, T value, String description) {
        try { return GenerationFingerprint.of(CanonicalJson.of(codec.encodeStart(ops, value).getOrThrow(false, s -> {}))); }
        catch (RuntimeException invalid) { throw new IllegalStateException("Cannot freeze " + description + ": " + invalid.getMessage(), invalid); }
    }
}
