/* Original offline equivalence experiment. All Rights Reserved. */
package baseline.reproduction.performance;

import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.Erosion;
import java.nio.file.Path;
import java.util.*;

/** Both implementations receive identical detached real terrain and preserve droplet order. */
final class ErosionExperiment {
    static void run(GeneratorContext context,Path out)throws Exception {
        var measurements=new ArrayList<Object>();long checked=0;
        for(int[] point:new int[][]{{0,0},{47,48},{-224,-96},{-64,-1172},{64,468},{-64,-3907}}){
            var input=context.cache.provide(point[0],point[1]);int size=input.getBlockSize().total();
            var reference=Erosion.factory(context).apply(size);var candidate=Erosion.factory(context,true).apply(size);
            for(int pass=-2;pass<7;pass++){
                var a=input.snapshot();var b=input.snapshot();long before,referenceNs,candidateNs;
                if((pass&1)==0){
                    before=System.nanoTime();reference.apply(a,point[0],point[1],context.preset.filters().erosion.dropletsPerChunk);referenceNs=System.nanoTime()-before;
                    before=System.nanoTime();candidate.apply(b,point[0],point[1],context.preset.filters().erosion.dropletsPerChunk);candidateNs=System.nanoTime()-before;
                }else{
                    before=System.nanoTime();candidate.apply(b,point[0],point[1],context.preset.filters().erosion.dropletsPerChunk);candidateNs=System.nanoTime()-before;
                    before=System.nanoTime();reference.apply(a,point[0],point[1],context.preset.filters().erosion.dropletsPerChunk);referenceNs=System.nanoTime()-before;
                }
                for(int i=0;i<a.getBacking().length;i++){
                    var ac=a.getBacking()[i];var bc=b.getBacking()[i];
                    for(var field:ac.getClass().getFields()){
                        if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                        Object av=field.get(ac),bv=field.get(bc);
                        if(av instanceof Float af&&bv instanceof Float bf){if(Float.floatToRawIntBits(af)!=Float.floatToRawIntBits(bf))throw new AssertionError("Erosion float "+field+" at "+i);}
                        else if(!Objects.equals(av,bv))throw new AssertionError("Erosion field "+field+" at "+i);
                        checked++;
                    }
                }
                measurements.add(Map.of("tileX",point[0],"tileZ",point[1],"pass",pass,"warmup",pass<0,"referenceNanos",referenceNs,"candidateNanos",candidateNs));
                a.close();b.close();
            }
        }
        WorldgenBenchmark.write(out,"erosion_experiment.json",Map.of("measurements",measurements,"rawFieldComparisons",checked,"exact",true,
                "scope","isolated erosion on identical real finalized snapshots, not FULL generation; all public Cell fields compared, same droplets and mutable height calculations",
                "candidate","precompute terrain/region/river strength once per apply; retain height-dependent modifier and operation association"));
    }
}
