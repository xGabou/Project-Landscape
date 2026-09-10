/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.biome;

import com.gabou.projectlandscape.api.biome.BiomeResolutionContext;
import com.gabou.projectlandscape.api.climate.ClimateBaseline;
import com.gabou.projectlandscape.api.geography.GeoSample;
import com.gabou.projectlandscape.config.BiomeResolverConfig;
import com.gabou.projectlandscape.generation.seed.*;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import java.util.*;

/** Registry-independent ranking with a separate registry-holder integration boundary. */
public final class ClimateBiomeResolver implements com.gabou.projectlandscape.api.biome.ClimateBiomeResolver {
    private final BiomeCatalog catalog;
    private final BiomeResolverConfig.Planned config;
    private final BiomeRegionalVariation variation;
    private final BiomeDescriptor[] candidates;
    private final Map<ResourceLocation,BiomeDescriptor> byId;
    private static final Set<BiomeTrait> PHYSICAL=Set.of(BiomeTrait.MARINE,BiomeTrait.RIVER,
            BiomeTrait.COASTAL,BiomeTrait.WETLAND,BiomeTrait.ALPINE);

    public ClimateBiomeResolver(BiomeCatalog catalog,GenerationSeedService seeds,BiomeResolverConfig.Planned config) {
        this.catalog=Objects.requireNonNull(catalog);
        this.config=Objects.requireNonNull(config);
        variation=new BiomeRegionalVariation(seeds.seed(SeedDomain.BIOME_SPATIAL_SELECTION),config.regionalVariationScaleBlocks());
        var sorted=new ArrayList<>(catalog.descriptors());
        sorted.sort(Comparator.comparingInt(BiomeDescriptor::priority).reversed().thenComparing(d->d.key().toString()));
        var ids=new HashMap<ResourceLocation,BiomeDescriptor>();
        for(var descriptor:sorted)
            if(ids.put(descriptor.key(),descriptor)!=null)
                throw new IllegalArgumentException("Duplicate biome descriptor "+descriptor.key());
        byId=Map.copyOf(ids);
        candidates=sorted.stream().filter(d->Collections.disjoint(d.traits(),PHYSICAL)).toArray(BiomeDescriptor[]::new);
    }

    @Override public Holder<Biome> resolve(GeoSample geography,ClimateBaseline climate,BiomeResolutionContext context) {
        return catalog.holder(select(geography,climate),context);
    }

    /** Normal path: no temporary candidate collections, sorting, or score records. */
    public ResourceLocation select(GeoSample geography,ClimateBaseline climate) {
        var forced=GeographicBiomeRules.override(geography,climate);
        if(forced!=null){find(forced);return forced;}
        BiomeDescriptor best=null;
        double bestScore=Double.NEGATIVE_INFINITY;
        for(var descriptor:candidates) {
            if(!GeographicBiomeRules.eligible(descriptor,geography))continue;
            double base=BiomeScorer.score(descriptor,geography,climate);
            if(!Double.isFinite(base))continue;
            double score=variedScore(base,regional(descriptor,geography));
            if(score>bestScore){bestScore=score;best=descriptor;}
        }
        if(best==null)throw noCandidate(geography,climate);
        return best.key();
    }

    /** Verbose allocations are confined to diagnostics. */
    public Resolution explain(GeoSample geography,ClimateBaseline climate) {
        var forced=GeographicBiomeRules.override(geography,climate);
        if(forced!=null){var scored=new Scored(find(forced),1,1,0);return new Resolution(scored,List.of(scored),true,"physical geography");}
        var scores=new ArrayList<Scored>();
        for(var descriptor:candidates) {
            if(!GeographicBiomeRules.eligible(descriptor,geography))continue;
            double base=BiomeScorer.score(descriptor,geography,climate);
            if(!Double.isFinite(base))continue;
            double regional=regional(descriptor,geography);
            scores.add(new Scored(descriptor,variedScore(base,regional),base,regional));
        }
        scores.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparing(Comparator.comparingInt((Scored s)->s.descriptor().priority()).reversed())
                .thenComparing(s->s.key().toString()));
        if(scores.isEmpty())throw noCandidate(geography,climate);
        return new Resolution(scores.get(0),List.copyOf(scores),false,"Model B climate suitability");
    }

    private double variedScore(double base,double regional){return base*(1+config.regionalVariationStrength()*.08*regional);}
    private double regional(BiomeDescriptor d,GeoSample g){return variation.sample(g.position().x(),g.position().z(),d.key().hashCode());}
    private BiomeDescriptor find(ResourceLocation id){
        var d=byId.get(id);if(d==null)throw new IllegalStateException("Catalog missing "+id);return d;
    }
    private static IllegalStateException noCandidate(GeoSample g,ClimateBaseline c){return new IllegalStateException("No eligible biome: "+g.position()+" climate="+c);}
    public record Scored(BiomeDescriptor descriptor,double score,double climateScore,double regionalVariation){
        public ResourceLocation key(){return descriptor.key();}
    }
    public record Resolution(Scored winner,List<Scored> candidates,boolean geographicOverride,String explanation){
        public Optional<Scored> second(){return candidates.size()>1?Optional.of(candidates.get(1)):Optional.empty();}
    }
}
