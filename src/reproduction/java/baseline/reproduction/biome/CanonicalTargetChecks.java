/* Original development harness. All Rights Reserved. */
package baseline.reproduction.biome;

import baseline.reproduction.Evidence;
import com.gabou.projectlandscape.biome.*;
import com.gabou.projectlandscape.climate.*;
import com.gabou.projectlandscape.config.*;
import com.gabou.projectlandscape.generation.seed.*;
import java.util.*;
import java.util.concurrent.*;

final class CanonicalTargetChecks {
    static void run(long seed,Collection<PreparedCanonicalInputs.Point> points,PreparedCanonicalInputs input,
            PaBaselineClimateProvider climate,ClimateBiomeResolver resolver,GenerationSeedService seeds,
            BaselineClimateConfig.Planned config,Evidence out)throws Exception{
        var winners=new LinkedHashMap<PreparedCanonicalInputs.Point,String>();
        long climateNs=0,resolverNs=0;
        for(var p:points){
            long start=System.nanoTime();var c=climate.sample(p.x(),p.z());climateNs+=System.nanoTime()-start;
            var g=input.geography.get(p);
            start=System.nanoTime();var winner=resolver.select(g,c);resolverNs+=System.nanoTime()-start;
            var r=resolver.explain(g,c);
            if(!winner.equals(r.winner().key()))throw new AssertionError("select/explain mismatch");
            winners.put(p,winner.toString());
            out.row("canonical_targeted_samples","seed",Long.toString(seed),"x",p.x(),"z",p.z(),"winner",winner.toString(),
                "traits",r.winner().descriptor().traits().stream().map(Enum::name).sorted().toList(),
                "temperatureC",c.meanTemperatureCelsius(),"rainfallMm",c.annualRainfallMm(),"evaporationMm",c.potentialEvaporationMm(),
                "ratio",BiomeScorer.aridity(c),"rainShadow",c.rainShadow(),"elevationBlockY",g.elevationBlockY(),
                "water",g.water().name(),"landform",g.landform().name(),"winnerScore",r.winner().score(),
                "secondCandidate",r.second().map(s->s.key().toString()).orElse(null),
                "margin",r.second().map(s->r.winner().score()-s.score()).orElse(1.0),"topCandidates",r.candidates().stream().limit(3).map(s->Map.of("key",s.key().toString(),"score",s.score())).toList());
        }
        var reversed=new ArrayList<>(points);Collections.reverse(reversed);
        for(int workers:new int[]{1,2,8}){
            var copy=new PaBaselineClimateProvider(input,seeds,config);
            var reconstructed=new ClimateBiomeResolver(new VanillaBiomeCatalog(),seeds,new BiomeResolverConfig.Planned(64,1));
            var pool=Executors.newFixedThreadPool(workers);
            try{
                List<Callable<Boolean>> jobs=new ArrayList<>();
                for(var p:reversed)jobs.add(()->{
                    var c=copy.sample(p.x(),p.z());
                    return c.equals(climate.sample(p.x(),p.z()))&&c.equals(copy.sample(p.x(),p.z()))
                        &&winners.get(p).equals(reconstructed.select(input.geography.get(p),c).toString());
                });
                for(var f:pool.invokeAll(jobs))if(!f.get())throw new AssertionError("Canonical determinism");
            }finally{pool.shutdown();}
            out.row("canonical_determinism","seed",Long.toString(seed),"workers",workers,"points",points.size(),"exact",true,
                "scope","canonical detached inputs; reconstructed climate/resolver, reverse order, cold/warm climate cache; not reconstructed terrain contexts");
        }
        String digest=Evidence.hash(winners.entrySet().stream().map(e->List.of(e.getKey().x(),e.getKey().z(),e.getValue())).toList());
        out.row("canonical_seed_digests","seed",Long.toString(seed),"digest",digest,"samples",points.size());
        out.row("canonical_performance","seed",Long.toString(seed),"samples",points.size(),"climateEquationsNs",climateNs,
            "resolverSelectNs",resolverNs,"scope","cold climate over prepared exact canonical inputs; acquisition separate, diagnostic explain excluded");
        out.flush();
    }
}
