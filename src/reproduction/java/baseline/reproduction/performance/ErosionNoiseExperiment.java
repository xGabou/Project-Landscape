/* Development-only experiment; no production caching authority. */
package baseline.reproduction.performance;
import raccoonman.reterraforged.world.worldgen.noise.module.*;
import raccoonman.reterraforged.world.worldgen.cell.terrain.Populators;
import raccoonman.reterraforged.world.worldgen.util.Seed;
import raccoonman.reterraforged.data.worldgen.preset.settings.Presets;
import java.nio.file.Path;
import java.util.*;
final class ErosionNoiseExperiment {
    private record Key(int x,int z,int seed){}
    private static final class Input implements Noise {
        final Noise input;final boolean reuse;final Key[] keys=new Key[128];final float[] values=new float[128];
        Set<Key> previous=Set.of(),current=new HashSet<>();int calls;long queries,lattice,duplicates,hits;
        Input(Noise input,boolean reuse){this.input=input;this.reuse=reuse;}
        void begin(){previous=current;current=new HashSet<>();calls=0;queries++;}
        public float compute(float x,float z,int seed){
            if(calls++==0)return input.compute(x,z,seed);
            var key=new Key(Float.floatToRawIntBits(x),Float.floatToRawIntBits(z),seed);lattice++;
            if(previous.contains(key))duplicates++;current.add(key);
            int slot=(int)com.gabou.atmospheregen.geography.continent.MacroSiteField.mix(((long)key.x()<<32)^(key.z()&0xffffffffL)^key.seed())&127;
            if(reuse&&key.equals(keys[slot])){hits++;return values[slot];}
            float value=input.compute(x,z,seed);if(reuse){keys[slot]=key;values[slot]=value;}return value;
        }
        public float minValue(){return input.minValue();}public float maxValue(){return input.maxValue();}
        public Noise mapAll(Noise.Visitor visitor){throw new UnsupportedOperationException("Experiment only");}
        public com.mojang.serialization.Codec<? extends Noise> codec(){throw new UnsupportedOperationException("Experiment only");}
    }
    private static Erosion find(Noise graph)throws Exception {
        if(graph instanceof Erosion e)return e;
        if(graph.getClass().isRecord())for(var c:graph.getClass().getRecordComponents()){
            var method=c.getAccessor();method.setAccessible(true);Object value=method.invoke(graph);if(value instanceof Noise n){var found=find(n);if(found!=null)return found;}
        }
        return null;
    }
    static void run(Path out)throws Exception {
        var settings=Presets.makeLegacyDefault().terrain().mountains;var rows=new ArrayList<Object>();
        for(int kind=1;kind<=3;kind++){
            var seed=new Seed(8675309);var ground=Noises.constant(0);
            Noise graph=(kind==1?Populators.makeMountains(seed,ground,settings,1,true):kind==2?Populators.makeMountains2(seed,ground,settings,1,true):Populators.makeMountains3(seed,ground,settings,1,true)).height();
            var original=Objects.requireNonNull(find(graph));
            for(int pass=-1;pass<3;pass++){
                var inputs=new Input[]{new Input(original.input(),false),new Input(original.input(),true)};
                float[] expected=new float[6*160*160];long[] nanos=new long[2];
                for(int order=0;order<2;order++){
                    int which=order;var in=inputs[which];
                    var model=new Erosion(in,original.seed(),original.octaves(),original.strength(),original.gridSize(),original.amplitude(),original.lacunarity(),original.distanceFallOff(),original.blendMode());
                    int i=0;long start=System.nanoTime();
                    for(int[] origin:new int[][]{{0,0},{-144,-144},{6128,6240},{-28672,-12288},{-8192,-150000},{29980000,-29980000}})
                        for(int z=0;z<160;z++)for(int x=0;x<160;x++){
                            in.begin();float v=model.compute(origin[0]+x,origin[1]+z,0);
                            if(which==0)expected[i]=v;else if(Float.floatToRawIntBits(expected[i])!=Float.floatToRawIntBits(v))throw new AssertionError("Noise raw bits");i++;
                        }
                    nanos[which]=System.nanoTime()-start;
                }
                var a=inputs[0];var b=inputs[1];rows.add(Map.of("mountainGraph",kind,"pass",pass,"samples",a.queries,"latticeValuesComputedReference",a.lattice,
                    "duplicatesFromPreviousSample",a.duplicates,"bounded128Hits",b.hits,"referenceNanos",nanos[0],"candidateNanos",nanos[1],"rawBitsExact",true));
            }
        }
        WorldgenBenchmark.write(out,"erosion_noise_experiment.json",Map.of("rows",rows,"capacity",128,
            "scope","Isolated real three mountain Erosion input graphs; unwarped row-major 160x160 input-coordinate scan at six origins. Records input lattice coordinate bits including octave scaling. Not a production tile-worker trace. Wrapper includes diagnostic set allocation in both timings; fixed reference-first order. No production candidate accepted by this result."));
    }
}
