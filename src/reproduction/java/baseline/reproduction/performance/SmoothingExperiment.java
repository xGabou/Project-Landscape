/* Original offline equivalence experiment. All Rights Reserved. */
package baseline.reproduction.performance;

import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.filter.Smoothing;
import java.nio.file.Path;
import java.util.*;

/** Both implementations receive identical detached real terrain and preserve droplet order. */
final class SmoothingExperiment {
    static void run(GeneratorContext context,Path out)throws Exception {
        var measurements=new ArrayList<Object>();long checked=0;
        for(int[] point:new int[][]{{0,0},{47,48},{-224,-96},{-64,-1172},{64,468},{-64,-3907}}){
            var input=context.cache.provide(point[0],point[1]);int size=input.getBlockSize().total();
            var reference=Smoothing.make(context.preset.filters().smoothing,context.levels);var candidate=new com.gabou.atmospheregen.geography.terrain.ExactSmoothing(reference);
            for(int pass=-2;pass<7;pass++){
                var a=input.snapshot();var b=input.snapshot();long before,referenceNs,candidateNs;
                if((pass&1)==0){
                    before=System.nanoTime();reference.apply(a,point[0],point[1],context.preset.filters().smoothing.iterations);referenceNs=System.nanoTime()-before;
                    before=System.nanoTime();candidate.apply(b,point[0],point[1],context.preset.filters().smoothing.iterations);candidateNs=System.nanoTime()-before;
                }else{
                    before=System.nanoTime();candidate.apply(b,point[0],point[1],context.preset.filters().smoothing.iterations);candidateNs=System.nanoTime()-before;
                    before=System.nanoTime();reference.apply(a,point[0],point[1],context.preset.filters().smoothing.iterations);referenceNs=System.nanoTime()-before;
                }
                for(int i=0;i<a.getBacking().length;i++){
                    var ac=a.getBacking()[i];var bc=b.getBacking()[i];
                    for(var field:ac.getClass().getFields()){
                        if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;
                        Object av=field.get(ac),bv=field.get(bc);
                        if(av instanceof Float af&&bv instanceof Float bf){if(Float.floatToRawIntBits(af)!=Float.floatToRawIntBits(bf))throw new AssertionError("Smoothing float "+field+" at "+i);}
                        else if(!Objects.equals(av,bv))throw new AssertionError("Smoothing field "+field+" at "+i);
                        checked++;
                    }
                }
                measurements.add(Map.of("tileX",point[0],"tileZ",point[1],"pass",pass,"warmup",pass<0,"referenceNanos",referenceNs,"candidateNanos",candidateNs));
                a.close();b.close();
            }
        }
        WorldgenBenchmark.write(out,"smoothing_experiment.json",Map.of("measurements",measurements,"rawFieldComparisons",checked,"exact",true,
                "scope","isolated smoothing on identical real finalized snapshots, not FULL generation; all public Cell fields compared, same in-place scan and float accumulation order",
                "candidate","precompute ordered disk kernel offsets and weights; retain absent-neighbor checks and accumulation order"));
    }
}
