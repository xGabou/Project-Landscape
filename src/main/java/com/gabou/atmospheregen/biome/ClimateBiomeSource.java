/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.biome;

import com.gabou.atmospheregen.api.biome.BiomeResolutionContext;
import com.gabou.atmospheregen.climate.*;
import com.gabou.atmospheregen.generation.context.WorldGenerationContext;
import com.gabou.atmospheregen.geography.terrain.PaGeographyProvider;
import com.gabou.atmospheregen.geography.ocean.CoarseDistanceField;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.*;
import java.util.*;
import java.util.stream.Stream;

/** Serializable V1 source definition, rebound to a world context before queries are allowed. */
public final class ClimateBiomeSource extends BiomeSource {
    public static final Codec<ClimateBiomeSource> CODEC=RecordCodecBuilder.create(i->i.group(
            BiomeSource.CODEC.fieldOf("retained").forGetter(s->s.retained),
            Codec.STRING.fieldOf("manifestFingerprint").forGetter(s->s.manifestFingerprint)
    ).apply(i,ClimateBiomeSource::new));
    private final BiomeSource retained;
    private final String manifestFingerprint;
    private final PaGeographyProvider geography;
    private final PaBaselineClimateProvider climateProvider;
    private final CanonicalClimateGeography climateGeography;
    private final BaselineClimateModel climateModel;
    private final ClimateBiomeResolver resolver;
    private final BiomeResolutionContext resolution;
    private final BiomeCatalog catalog;
    private final Map<net.minecraft.resources.ResourceLocation,Holder<Biome>> holders;

    /** Codec decoding does not capture server state. */
    private ClimateBiomeSource(BiomeSource retained,String manifestFingerprint) {
        if(retained instanceof ClimateBiomeSource)throw new IllegalArgumentException("Nested V1 biome source");
        if(!manifestFingerprint.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid V1 manifest fingerprint");
        this.retained=Objects.requireNonNull(retained);
        this.manifestFingerprint=manifestFingerprint;
        geography=null;climateProvider=null;climateGeography=null;climateModel=null;resolver=null;resolution=null;catalog=null;holders=Map.of();
    }
    public ClimateBiomeSource(BiomeSource retained,PaGeographyProvider geography,
            WorldGenerationContext generation,HolderGetter<Biome> biomes) {
        this.retained=Objects.requireNonNull(retained);
        this.geography=Objects.requireNonNull(geography);
        manifestFingerprint=generation.manifest().fingerprint().sha256();
        catalog=new VanillaBiomeCatalog();
        resolution=new BiomeResolutionContext(generation,biomes,Optional.of(catalog.fingerprint()));
        var service=geography.climateService(generation);
        climateGeography=service.geography();
        climateModel=service.model();
        climateProvider=service.provider();
        resolver=new ClimateBiomeResolver(catalog,generation.seeds(),generation.manifest().content().biomeResolver().planned().orElseThrow());
        var resolved=new HashMap<net.minecraft.resources.ResourceLocation,Holder<Biome>>();
        for(var d:catalog.descriptors())resolved.put(d.key(),biomes.getOrThrow(ResourceKey.create(Registries.BIOME,d.key())));
        holders=Map.copyOf(resolved);
    }
    public BiomeSource retained(){return retained;}
    public String manifestFingerprint(){return manifestFingerprint;}
    public com.gabou.atmospheregen.api.climate.ClimateBaseline sampleClimate(int x,int z){return climateProvider.sample(x,z);}
    public Map<String,Long> climateCacheStats(){return climateProvider.cacheStats();}
    public Map<String,Long> surfaceCacheStats(){return climateGeography.cacheStats();}
    @Override protected Codec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){
        if(geography==null)return retained.possibleBiomes().stream();
        return Stream.concat(holders.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue),
                retained.possibleBiomes().stream().filter(ClimateBiomeSource::isCave)
                        .sorted(Comparator.comparing(h->h.unwrapKey().orElseThrow().location().toString()))).distinct();
    }
    @Override public Holder<Biome> getNoiseBiome(int quartX,int quartY,int quartZ,Climate.Sampler sampler){
        if(geography==null)throw new IllegalStateException("V1 biome source must be bound to its manifest before sampling");
        int x=QuartPos.toBlock(quartX),y=QuartPos.toBlock(quartY),z=QuartPos.toBlock(quartZ);
        var g=BiomeGeography.sample(geography,x,z);
        if(y<g.elevationBlockY()-8){
            Holder<Biome> underground=retained.getNoiseBiome(quartX,quartY,quartZ,sampler);
            if(isCave(underground))return underground;
        }
        // Exact Task 5 coordinates. Regional approximations require independent measured error gates.
        if(GeographicBiomeRules.temperatureOnlyEligible(g)){
            var forced=GeographicBiomeRules.temperatureOnlyOverride(g,climateModel.temperatureOnly(climateGeography,x,z));
            if(forced!=null)return holders.get(forced);
        }
        return holders.get(resolver.select(g,climateProvider.sample(x,z)));
    }
    private static boolean isCave(Holder<Biome> holder){return holder.is(Biomes.LUSH_CAVES)||holder.is(Biomes.DRIPSTONE_CAVES)||holder.is(Biomes.DEEP_DARK);}
}
