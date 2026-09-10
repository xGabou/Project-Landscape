/* Original development harness. All Rights Reserved. */
package baseline.reproduction.biome;

import baseline.reproduction.Evidence;
import com.gabou.projectlandscape.api.geography.GeoSample;
import com.gabou.projectlandscape.biome.*;
import com.gabou.projectlandscape.climate.*;
import com.gabou.projectlandscape.config.BaselineClimateConfig;
import com.gabou.projectlandscape.generation.seed.GenerationSeedService;
import com.gabou.projectlandscape.geography.terrain.PaGeographyProvider;
import java.util.*;

/** Survey-only exact point snapshots. Production equations execute unchanged over this input. */
final class PreparedCanonicalInputs implements ClimateGeography {
    record Point(int x,int z) {}
    private final Map<Point,Sample> samples=new HashMap<>();
    final Map<Point,GeoSample> geography=new HashMap<>();
    PreparedCanonicalInputs(PaGeographyProvider provider,GenerationSeedService seeds,
            BaselineClimateConfig.Planned config,Collection<Point> targets,Evidence out)throws Exception {
        var wind=new PrevailingWindModel(seeds,config);
        var requested=new HashSet<>(targets);
        for(var p:targets){
            var w=wind.sample(p.x,p.z);
            // Identical coordinate expression and loop bounds to BaselineClimateModel.profile.
            for(double d=config.climateProfileDistance();d>=0;d-=config.climateProfileStep())
                requested.add(new Point((int)Math.round(p.x-w.x()*d),(int)Math.round(p.z-w.z()*d)));
        }
        var ordered=new ArrayList<>(requested);
        ordered.sort(Comparator.comparingInt((Point p)->provider.surfaceTileCoordinate(p.z))
            .thenComparingInt(p->provider.surfaceTileCoordinate(p.x)).thenComparingInt(Point::z).thenComparingInt(Point::x));
        var targetSet=new HashSet<>(targets);
        var input=new CanonicalClimateGeography(provider);
        var reference=ClimateGeographyAdapters.of(provider,provider.macroProvider(),new com.gabou.projectlandscape.geography.ocean.CoarseDistanceField(provider.macroProvider()));
        long start=System.nanoTime();int completed=0,verified=0;
        for(var p:ordered){
            samples.put(p,input.sample(p.x,p.z));
            if(targetSet.contains(p)){
                geography.put(p,BiomeGeography.sample(provider,p.x,p.z));
                if(!samples.get(p).equals(reference.sample(p.x,p.z)))throw new AssertionError("Canonical input mismatch at "+p);
                verified++;
            }
            if(++completed%500==0)System.out.println("Task6 grouped canonical inputs="+completed+"/"+ordered.size()+" cache="+input.cacheStats());
        }
        var stats=input.cacheStats();
        out.row("canonical_acquisition","targets",targets.size(),"exactInputPoints",samples.size(),
            "elapsedNs",System.nanoTime()-start,"surfaceCache",stats,"exactReferenceChecks",verified,
            "evictions",Math.max(0,stats.get("misses")-stats.get("entries")),
            "arraysPerTile",2,"tileSideBlocks",128,"ordering","tile Z, tile X, block Z, block X",
            "scope","full canonical terrain and exact profile coordinates; point records retained only for this bounded survey");
        out.flush();
        input.clear();
    }
    @Override public Sample sample(int x,int z){
        var s=samples.get(new Point(x,z));
        if(s==null)throw new AssertionError("Unprepared canonical coordinate "+x+","+z);
        return s;
    }
}
