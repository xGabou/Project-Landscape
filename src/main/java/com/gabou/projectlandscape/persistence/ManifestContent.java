/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.persistence;

import com.gabou.projectlandscape.config.*;
import com.gabou.projectlandscape.generation.version.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** The complete fingerprint payload; schema is versions.schemaVersion (not mod version). */
public record ManifestContent(GenerationVersions versions, long worldSeed, ResourceLocation dimension,
        WorldGeographyConfig geography, BaselineClimateConfig baselineClimate, BiomeResolverConfig biomeResolver,
        Map<String, GenerationFingerprint> data, Optional<GenerationFingerprint> biomeCatalog) {
    /** Decimal string preserves all 64 bits in JSON tooling as well as Java. */
    public static final Codec<Long> SEED_CODEC = Codec.STRING.comapFlatMap(value -> {
        try { return DataResult.success(Long.parseLong(value)); }
        catch (NumberFormatException invalid) { return DataResult.error(() -> "worldSeed must be a signed 64-bit decimal string"); }
    }, Object::toString);
    public static final Codec<ManifestContent> CODEC = RecordCodecBuilder.create(i -> i.group(
        GenerationVersions.CODEC.fieldOf("versions").forGetter(ManifestContent::versions),
        SEED_CODEC.fieldOf("worldSeed").forGetter(ManifestContent::worldSeed),
        ResourceLocation.CODEC.fieldOf("dimension").forGetter(ManifestContent::dimension),
        WorldGeographyConfig.CODEC.fieldOf("geography").forGetter(ManifestContent::geography),
        BaselineClimateConfig.CODEC.fieldOf("baselineClimate").forGetter(ManifestContent::baselineClimate),
        BiomeResolverConfig.CODEC.fieldOf("biomeResolver").forGetter(ManifestContent::biomeResolver),
        Codec.unboundedMap(Codec.STRING, GenerationFingerprint.CODEC).fieldOf("data").forGetter(ManifestContent::data),
        ConfigCodecs.optional("biomeCatalog", GenerationFingerprint.CODEC).forGetter(ManifestContent::biomeCatalog)
    ).apply(i, ManifestContent::new));
    public ManifestContent {
        Objects.requireNonNull(versions); Objects.requireNonNull(dimension); Objects.requireNonNull(geography);
        Objects.requireNonNull(baselineClimate); Objects.requireNonNull(biomeResolver); Objects.requireNonNull(biomeCatalog);
        data = Collections.unmodifiableSortedMap(new TreeMap<>(data));
    }
    public CanonicalJson canonical() { return CanonicalJson.of(CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow(false, s -> {})); }
    public void validate() {
        boolean legacy = versions.geography() == GeographyAlgorithmVersion.LEGACY_RTF_V0;
        if (legacy) {
            if (!versions.equals(GenerationVersions.legacy()) || geography.legacy().isEmpty() || geography.planned().isPresent()
                    || baselineClimate.planned().isPresent() || biomeResolver.planned().isPresent() || biomeCatalog.isPresent())
                throw new IllegalArgumentException("LEGACY_RTF_V0 requires frozen legacy settings and legacy hints/resolver, with no inactive planned controls/catalog");
            if (!geography.legacy().get().effectivePreset().tree().isJsonObject()) throw new IllegalArgumentException("Legacy effectivePreset must be an object");
        } else if (geography.legacy().isPresent() || geography.planned().isEmpty()) {
            throw new IllegalArgumentException("PA_GEOGRAPHY_V1 metadata requires planned settings, never a legacy fallback");
        }
        if(versions.equals(GenerationVersions.geographyV1())||versions.equals(GenerationVersions.climateV1())||versions.equals(GenerationVersions.planned())) {
            var settings=geography.planned().orElseThrow();
            var macro=settings.macro().orElseThrow(()->new IllegalArgumentException("Functional PA_GEOGRAPHY_V1 requires frozen Task 4 macro settings"));
            if(settings.continentScaleBlocks()!=macro.continentScaleBlocks() || settings.minimumMajorOceanWidthBlocks()!=macro.minimumMajorOceanWidthBlocks())
                throw new IllegalArgumentException("V1 macro scale/width disagree with outer settings");
            boolean climateV1=versions.equals(GenerationVersions.climateV1());
            boolean resolverV1=versions.equals(GenerationVersions.planned());
            if(resolverV1) { if(!baselineClimate.planned().isPresent()||!biomeResolver.planned().isPresent()||biomeCatalog.isEmpty()) throw new IllegalArgumentException("Task 6 V1 requires frozen climate, resolver, and biome catalog settings"); }
            else if(climateV1!=baselineClimate.planned().isPresent()||biomeResolver.planned().isPresent()||biomeCatalog.isPresent())
                throw new IllegalArgumentException(climateV1?"Task 5 V1 requires frozen baseline climate settings and legacy resolver":"Task 4 V1 requires legacy hints/resolver and no planned climate/catalog");
        }
        if (data.isEmpty()) throw new IllegalArgumentException("Generation data fingerprints are required; an untracked world is not a valid manifest");
    }
}
