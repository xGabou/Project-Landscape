/* Original development harness. All Rights Reserved. */
package baseline.reproduction.biome;

import com.gabou.atmospheregen.api.climate.*;
import com.gabou.atmospheregen.api.geography.*;
import com.gabou.atmospheregen.biome.*;
import com.gabou.atmospheregen.climate.*;
import com.gabou.atmospheregen.config.*;
import com.gabou.atmospheregen.generation.seed.*;
import com.gabou.atmospheregen.generation.version.GenerationVersions;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biomes;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Failures are retained as evidence and fail the run; expected results are never emitted as measurements. */
public final class BiomeValidationChecks {
    private static final BaselineClimateConfig.Planned CLIMATE=new BaselineClimateConfig.Planned(100000,0,.0065,.5,1,4096,.85,12000,24000,256,1,.10);
    private static final BiomeResolverConfig.Planned CONFIG=new BiomeResolverConfig.Planned(64,1);
    private static NamedSeedService seeds(long seed){return new NamedSeedService(seed,new ResourceLocation("minecraft","overworld"),GenerationVersions.planned());}
    public static void run(Path out,BiomeCatalog catalog)throws Exception{
        var results=new ArrayList<Map<String,Object>>();
        var resolver=new ClimateBiomeResolver(catalog,seeds(42),CONFIG);
        var model=new BaselineClimateModel(seeds(42),CLIMATE);
        var cases=new ArrayList<Case>();
        for(double t:new double[]{35,25,15,5,-5,-15})for(double rain:new double[]{80,400,900,1800})
            cases.add(new Case("climate_"+t+"_"+rain,geo(WaterCategory.LAND,Landform.PLAINS,100,0,0),model.deriveEvaporationMoisture(t,rain,.3,new WindDirection(-1,0))));
        for(var water:new WaterCategory[]{WaterCategory.SHALLOW_OCEAN,WaterCategory.DEEP_OCEAN,WaterCategory.INLAND_SEA,WaterCategory.COAST,WaterCategory.RIVER,WaterCategory.WETLAND})
            for(double t:new double[]{-10,10,26})cases.add(new Case(water+"_"+t,geo(water,water==WaterCategory.COAST?Landform.COAST:Landform.PLAINS,0,0,0),model.deriveEvaporationMoisture(t,1500,0,new WindDirection(-1,0))));
        for(Case c:cases){
            try{
                var resolution=resolver.explain(c.geography,c.climate);var winner=resolution.winner().descriptor();
                boolean marine=c.geography.water()==WaterCategory.SHALLOW_OCEAN||c.geography.water()==WaterCategory.DEEP_OCEAN||c.geography.water()==WaterCategory.INLAND_SEA;
                boolean valid=marine==winner.traits().contains(BiomeTrait.MARINE);
                if(c.geography.water()==WaterCategory.RIVER)valid&=winner.traits().contains(BiomeTrait.RIVER);
                if(c.geography.water()==WaterCategory.LAND){
                    valid&=winner.temperature().allowed(c.climate.meanTemperatureCelsius());
                    valid&=winner.rainfall().allowed(c.climate.annualRainfallMm());
                    valid&=winner.aridity().allowed(BiomeScorer.aridity(c.climate));
                }
                results.add(Map.of("case",c.name,"passed",valid,"winner",winner.key().toString(),"score",resolution.winner().score()));
            }catch(IllegalStateException failure){results.add(Map.of("case",c.name,"passed",false,"error",failure.getMessage()));}
        }
        var wind=new WindDirection(-1,0);
        var barrier=new Fixture(true);var control=new Fixture(false);
        for(int x:new int[]{-30000000,-4097,-1,0,4096,30000000})for(int z:new int[]{-30000000,-1,0,30000000}){
            double full=model.sample(barrier,x,z).meanTemperatureCelsius(),projected=model.temperatureOnly(barrier,x,z);
            if(Double.doubleToRawLongBits(full)!=Double.doubleToRawLongBits(projected))throw new AssertionError("Temperature projection changed baseline at "+x+","+z);
        }
        results.add(Map.of("case","temperature_projection_exact","passed",true,"coordinates",24));
        List<String> orographic=new ArrayList<>(List.of("case,x,rainfall,temperature,evaporation,ratio,winner,score"));
        double lee=model.sampleWithWind(barrier,0,0,wind).annualRainfallMm();
        double clear=model.sampleWithWind(control,0,0,wind).annualRainfallMm();
        results.add(Map.of("case","Okanagan_rainfall_60_percent","passed",lee<=.6*clear,"leeward",lee,"control",clear));
        for(int x:new int[]{8000,7000,5000,2000,1400,1000,500,0,-2000}){
            var climate=model.sampleWithWind(barrier,x,0,wind);var s=barrier.sample(x,0);
            var g=geo(x>7000?WaterCategory.SHALLOW_OCEAN:WaterCategory.LAND,s.elevationBlocks()>1000?Landform.MOUNTAIN:Landform.PLAINS,s.elevationBlocks(),x,0);
            try{var r=resolver.explain(g,climate);orographic.add("barrier,"+x+","+climate.annualRainfallMm()+","+climate.meanTemperatureCelsius()+","+climate.potentialEvaporationMm()+","+BiomeScorer.aridity(climate)+","+r.winner().key()+","+r.winner().score());}
            catch(IllegalStateException failure){results.add(Map.of("case","orography_"+x,"passed",false,"error",failure.getMessage()));}
        }
        Files.write(out.resolve("orographic_biome_transect.csv"),orographic,StandardCharsets.UTF_8);
        write(out,"semantic_assertions.json",results);
        var ids=new HashSet<String>();
        for(var descriptor:catalog.descriptors())if(!ids.add(descriptor.key().toString()))throw new AssertionError("duplicate descriptor");
        var invalidChecks=new ArrayList<String>();
        var ops=com.mojang.serialization.JsonOps.INSTANCE;
        var base=BiomeDescriptor.CODEC.encodeStart(ops,catalog.descriptors().iterator().next()).getOrThrow(false,s->{}).getAsJsonObject();
        for(String invalid:List.of("invertedRange","preferredOutsideAllowed","unknownTrait","emptyTraits","negativePriority")){
            var data=base.deepCopy();
            switch(invalid){
                case "invertedRange"->data.add("temperatureCelsius",com.google.gson.JsonParser.parseString("[10,5,2,0]"));
                case "preferredOutsideAllowed"->data.add("temperatureCelsius",com.google.gson.JsonParser.parseString("[0,1,20,10]"));
                case "unknownTrait"->data.add("traits",com.google.gson.JsonParser.parseString("[\"NOT_A_TRAIT\"]"));
                case "emptyTraits"->data.add("traits",new com.google.gson.JsonArray());
                case "negativePriority"->data.addProperty("priority",-1);
            }
            if(BiomeDescriptor.CODEC.parse(ops,data).error().isEmpty())throw new AssertionError("Accepted invalid descriptor "+invalid);
            invalidChecks.add(invalid);
        }
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}){
            try{new BiomeDescriptor.Range(value,0,1,2);throw new AssertionError("Nonfinite descriptor range accepted");}
            catch(IllegalArgumentException expected){invalidChecks.add("nonfinite_"+value);}
        }
        write(out,"descriptor_validation.json",Map.of("uniqueDescriptors",ids.size(),"catalogFingerprint",catalog.fingerprint().sha256(),"rejectedInvalidCases",invalidChecks));
        write(out,"determinism.json",determinism(catalog));
        benchmark(out,catalog);
        reachability(out,catalog);
        transects(out,resolver,model);
        long failed=results.stream().filter(row->Boolean.FALSE.equals(row.get("passed"))).count();
        write(out,"validation_summary.json",Map.of("checks",results.size(),"failed",failed,"status",failed==0?"PASS":"FAIL"));
        if(failed>0)throw new AssertionError(failed+" resolver semantic checks failed; see "+out);
    }

    private static void transects(Path out,ClimateBiomeResolver resolver,BaselineClimateModel model)throws Exception{
        for(String axis:List.of("temperature","moisture","elevation")){
            var rows=new ArrayList<String>(List.of("step,temperatureC,rainfallMm,evaporationMm,ratio,elevationBlocks,landform,winner,score,margin"));
            for(int i=0;i<=100;i++){
                double elevation=axis.equals("elevation")?i*20:100;
                ClimateGeography altitudeFixture=(x,z)->new ClimateGeography.Sample(elevation,6000,6000,0,MacroGeographyProvider.MarineClass.LAND);
                double temperature=axis.equals("temperature")?35-i*.65:axis.equals("elevation")?model.temperatureOnly(altitudeFixture,0,60000):22;
                double rain=axis.equals("moisture")?2500-i*24.5:900;
                Landform form=axis.equals("elevation")?(elevation>800?Landform.MOUNTAIN:elevation>300?Landform.HILLS:Landform.PLAINS):Landform.PLAINS;
                var c=model.deriveEvaporationMoisture(temperature,rain,.3,new WindDirection(-1,0));
                var g=geo(WaterCategory.LAND,form,elevation,0,0);
                var r=resolver.explain(g,c);
                rows.add(i+","+temperature+","+rain+","+c.potentialEvaporationMm()+","+BiomeScorer.aridity(c)+","+elevation+","+form+","+r.winner().key()+","+r.winner().score()+","+r.second().map(s->r.winner().score()-s.score()).orElse(1.0));
            }
            Files.write(out.resolve("synthetic_"+axis+"_transect.csv"),rows,StandardCharsets.UTF_8);
        }
    }

    private static void benchmark(Path out,BiomeCatalog catalog)throws Exception{
        var resolver=new ClimateBiomeResolver(catalog,seeds(42),CONFIG);
        var model=new BaselineClimateModel(seeds(42),CLIMATE);
        var climate=model.deriveEvaporationMoisture(12,900,.3,new WindDirection(-1,0));
        GeoSample[] points=new GeoSample[256];
        for(int i=0;i<points.length;i++)points[i]=geo(WaterCategory.LAND,Landform.PLAINS,100,i*137-4096,i*211-4096);
        for(var g:points)if(!resolver.select(g,climate).equals(resolver.explain(g,climate).winner().key()))throw new AssertionError("Debug/hot path disagreement");
        long checksum=0;
        for(int i=0;i<100000;i++)checksum+=resolver.select(points[i&255],climate).hashCode();
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        if(bean.isThreadAllocatedMemorySupported()&&!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().getId();
        List<Map<String,Object>> timings=new ArrayList<>();
        for(int batch=0;batch<7;batch++){
            long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
            for(int i=0;i<100000;i++)checksum+=resolver.select(points[i&255],climate).hashCode();
            long elapsed=System.nanoTime()-start,allocated=bean.getThreadAllocatedBytes(thread)-before;
            timings.add(Map.of("batch",batch,"queries",100000,"elapsedNs",elapsed,"allocatedBytes",allocated));
        }
        write(out,"resolver_performance.json",Map.of("scope","resolver only; preconstructed synthetic climate/geography","runs",timings,"checksum",checksum,"hotDebugExactPoints",points.length));
    }

    private static List<Map<String,Object>> determinism(BiomeCatalog catalog)throws Exception{
        var output=new ArrayList<Map<String,Object>>();
        String reference=null;
        for(int workers:new int[]{1,2,8})for(boolean reverse:new boolean[]{false,true}){
            var resolver=new ClimateBiomeResolver(catalog,seeds(42),CONFIG);
            var model=new BaselineClimateModel(seeds(42),CLIMATE);
            String[] values=new String[96];var pool=Executors.newFixedThreadPool(workers);
            try{
                var tasks=new ArrayList<Callable<Void>>();
                for(int n=0;n<values.length;n++){
                    final int i=reverse?values.length-1-n:n;
                    tasks.add(()->{var c=model.deriveEvaporationMoisture(12,900,.3,new WindDirection(-1,0));var g=geo(WaterCategory.LAND,Landform.PLAINS,100,i*137-4096,i*211-4096);values[i]=resolver.explain(g,c).winner().key().toString();return null;});
                }
                for(var future:pool.invokeAll(tasks))future.get();
            }finally{pool.shutdown();}
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n",values).getBytes(StandardCharsets.UTF_8)));
            if(reference==null)reference=hash;
            if(!reference.equals(hash))throw new AssertionError("worker/order determinism");
            output.add(Map.of("workers",workers,"reverse",reverse,"sha256",hash));
        }
        return output;
    }
    private static void reachability(Path out,BiomeCatalog catalog)throws Exception{
        var resolver=new ClimateBiomeResolver(catalog,seeds(42),CONFIG);
        var model=new BaselineClimateModel(seeds(42),CLIMATE);
        var reached=new TreeMap<String,Object>();int uncovered=0;
        for(var form:new Landform[]{Landform.PLAINS,Landform.HILLS,Landform.PLATEAU,Landform.MOUNTAIN,Landform.COAST})
            for(double elevation:new double[]{0,100,500,1000,1800})
                for(double temperature=-30;temperature<=40;temperature+=2)
                    for(double rain=0;rain<=4000;rain+=100){
                        var c=model.deriveEvaporationMoisture(temperature,rain,.3,new WindDirection(-1,0));
                        for(int region=0;region<4;region++){
                            var g=geo(WaterCategory.LAND,form,elevation,region*8192-16384,region*4096-8192);
                            try{var id=resolver.select(g,c).toString();reached.putIfAbsent(id,Map.of("temperatureC",temperature,"rainfallMm",rain,"evaporationMm",c.potentialEvaporationMm(),"elevationBlocks",elevation,"landform",form,"x",g.position().x(),"z",g.position().z()));}
                            catch(IllegalStateException gap){uncovered++;}
                        }
                    }
        for(var w:new WaterCategory[]{WaterCategory.SHALLOW_OCEAN,WaterCategory.DEEP_OCEAN,WaterCategory.INLAND_SEA,WaterCategory.COAST,WaterCategory.RIVER,WaterCategory.WETLAND})
            for(var form:new Landform[]{Landform.PLAINS,Landform.COAST,Landform.MOUNTAIN})for(double t=-10;t<=30;t+=2){
                var c=model.deriveEvaporationMoisture(t,1800,0,new WindDirection(-1,0));
                try{String id=resolver.select(geo(w,form,0,0,0),c).toString();reached.putIfAbsent(id,Map.of("temperatureC",t,"rainfallMm",1800,"water",w,"landform",form));}
                catch(IllegalStateException gap){uncovered++;}
            }
        List<String> missing=catalog.descriptors().stream().map(d->d.key().toString()).filter(id->!reached.containsKey(id)).sorted().toList();
        write(out,"unreachable_descriptors.json",Map.of("scope","controlled climate and terrain search; absence is not a mathematical impossibility proof","reached",reached,"notReached",missing,"uncoveredTestCombinations",uncovered,"mushroomFields","excluded: non-climatic mechanism not implemented"));
    }
    private static GeoSample geo(WaterCategory w,Landform form,double elevation,int x,int z){
        var hydro=new HydrologySample(w,w==WaterCategory.RIVER,w==WaterCategory.LAKE,w==WaterCategory.WETLAND,Optional.empty(),Optional.empty());
        var metrics=new GeographyMetrics(Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.of(new Metric(form==Landform.MOUNTAIN?1:0,Metric.Quality.MODELLED,1)),Optional.empty());
        return new GeoSample(new BlockPosition(x,z),elevation+63,elevation,w,form,metrics,hydro);
    }
    private record Case(String name,GeoSample geography,ClimateBaseline climate){}
    private record Fixture(boolean barrier) implements ClimateGeography{
        public Sample sample(int x,int z){return new Sample(barrier&&x>=700&&x<=1400?1800:100,Math.max(0,7000-x),Math.max(0,7000-x),0,x>7000?MacroGeographyProvider.MarineClass.MAJOR_OCEAN:MacroGeographyProvider.MarineClass.LAND);}
    }
    private static void write(Path out,String name,Object data)throws Exception{Files.writeString(out.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(data),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
}
