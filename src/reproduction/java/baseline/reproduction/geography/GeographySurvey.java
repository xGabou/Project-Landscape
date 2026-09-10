/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package baseline.reproduction.geography;

import com.gabou.projectlandscape.api.geography.MacroGeographyProvider;
import com.gabou.projectlandscape.config.MacroGeographySettings;
import com.gabou.projectlandscape.generation.seed.NamedSeedService;
import com.gabou.projectlandscape.generation.version.*;
import com.gabou.projectlandscape.geography.continent.*;
import com.gabou.projectlandscape.geography.ocean.CoarseDistanceField;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Real V1 topology survey. Output directories are immutable; runs never overwrite accepted evidence. */
public final class GeographySurvey {
    private static final long[] SEEDS={8675309,4303642605L,42,-987654321,0,Long.MAX_VALUE,Long.MIN_VALUE+1,1,-1,12345,67890,314159,271828,20260908,-424242,987654321012345L};
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final GenerationVersions VERSION=new GenerationVersions(1,GeographyAlgorithmVersion.PA_GEOGRAPHY_V1,
        BaselineClimateAlgorithmVersion.LEGACY_RTF_HINTS_V0,BiomeResolverAlgorithmVersion.LEGACY_MULTINOISE_V0);
    public static PaMacroGeography provider(long seed,MacroGeographySettings settings) {
        return new PaMacroGeography(new NamedSeedService(seed,new ResourceLocation("minecraft","overworld"),VERSION),settings);
    }
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);if(Files.exists(out))throw new IllegalArgumentException("Refusing existing survey directory: "+out);
        Files.createDirectories(out);var settings=MacroGeographySettings.defaults();
        var configuration=MacroGeographySettings.CODEC.encodeStart(JsonOps.INSTANCE,settings).getOrThrow(false,s->{});
        write(out,"configuration.json",configuration);write(out,"config_schema.json",configuration);
        List<Object> fractions=new ArrayList<>(),components=new ArrayList<>(),widths=new ArrayList<>(),interiors=new ArrayList<>(),islands=new ArrayList<>(),shelves=new ArrayList<>(),identity=new ArrayList<>(),translated=new ArrayList<>(),coastRefinement=new ArrayList<>();
        int invalid=0;double minWidth=Double.POSITIVE_INFINITY;List<Double> validWidths=new ArrayList<>();
        for(long seed:SEEDS) {
            long start=System.nanoTime();var p=provider(seed,settings);
            var window=TopologyMeasurements.window(p,-32768,-32768,65536,128);
            fractions.add(Map.of("seed",Long.toString(seed),"land",window.landFraction(),"ocean",window.oceanFraction(),"inlandSea",window.inlandSeaFraction(),"coastlineBlocks",window.marchingSquaresCoastlineBlocks()));
            for(var c:window.components())components.add(Map.of("seed",Long.toString(seed),"component",c));
            var shifted=TopologyMeasurements.window(p,-20423,-56224,65536,128);
            translated.add(Map.of("seed",Long.toString(seed),"window",shifted));
            if(seed==8675309 || seed==42) {
                var fine=TopologyMeasurements.window(p,-32768,-32768,65536,64);
                coastRefinement.add(Map.of("seed",Long.toString(seed),"step64CoastlineBlocks",fine.marchingSquaresCoastlineBlocks(),"step128CoastlineBlocks",window.marchingSquaresCoastlineBlocks()));
                map(out,p,seed);
            }
            for(int gz=-2;gz<2;gz++)for(int gx=-2;gx<2;gx++) {
                var site=p.sites().site(gx,gz);identity.add(Map.of("seed",Long.toString(seed),"site",site));
                for(var island:p.islands().islands(site))islands.add(Map.of("seed",Long.toString(seed),"siteId",Long.toString(site.id()),"island",island));
                for(int axis=0;axis<2;axis++) {
                    if((axis==0&&gx==1)||(axis==1&&gz==1))continue;
                    for(var w:TopologyMeasurements.widths(p,p.oceans().corridor(gx,gz,axis),settings.continentScaleBlocks())) {
                        widths.add(Map.of("seed",Long.toString(seed),"section",w));
                        if(w.violation()||!w.opposingMajorLandFound())invalid++;
                        if(w.opposingMajorLandFound()){minWidth=Math.min(minWidth,w.widthBlocks());validWidths.add(w.widthBlocks());}
                    }
                }
            }
            // A halo preserves the geographic extent for interior distances; targets are marine lattice nodes.
            int n=641,step=128;boolean[] marine=new boolean[n*n];long shelf=0,water=0;
            for(int z=0;z<n;z++)for(int x=0;x<n;x++) {
                var sample=p.sampleMacro(-40960+x*step,-40960+z*step);marine[z*n+x]=!sample.land();
                if(x>=64&&x<=576&&z>=64&&z<=576&&!sample.land()){water++;if(sample.shelfFraction()>0.5)shelf++;}
            }
            double[] distance=CoarseDistanceField.squaredDistance(marine,n,n);long land=0,b500=0,b1000=0,b2000=0;double max=0;
            for(int z=64;z<=576;z++)for(int x=64;x<=576;x++)if(!marine[z*n+x]){
                land++;double d=Math.sqrt(distance[z*n+x])*step;max=Math.max(max,d);
                if(d>500)b500++;if(d>1000)b1000++;if(d>2000)b2000++;
            }
            interiors.add(Map.of("seed",Long.toString(seed),"landSamples",land,"beyond500Fraction",(double)b500/land,"beyond1000Fraction",(double)b1000/land,"beyond2000Fraction",(double)b2000/land,"maximumSampledLatticeDistanceBlocks",max));
            shelves.add(Map.of("seed",Long.toString(seed),"marineSamples",water,"shelfSamples",shelf,"shelfMarineFraction",(double)shelf/water));
            System.out.println("survey seed="+seed+" land="+window.landFraction()+" seconds="+(System.nanoTime()-start)/1e9);
        }
        write(out,"land_ocean_samples.json",fractions);write(out,"continent_components.json",components);
        write(out,"major_ocean_widths.json",widths);write(out,"continental_interior.json",interiors);
        write(out,"island_metrics.json",islands);write(out,"shelf_metrics.json",shelves);write(out,"site_identity.json",identity);
        write(out,"translated_windows.json",translated);write(out,"coastline_refinement.json",coastRefinement);
        validWidths.sort(Double::compare);write(out,"survey_summary.json",Map.of("version",VERSION,"extentBlocks",65536,"stepBlocks",128,"seedCount",SEEDS.length,
            "minimumSampledMajorOceanWidthBlocks",minWidth,"widthP10",quantile(validWidths,0.1),"widthMedian",quantile(validWidths,0.5),"widthP90",quantile(validWidths,0.9),"invalidTopologyCount",invalid));
        determinism(out,settings);adversarial(out);distanceChecks(out,settings);
        if(invalid!=0)throw new AssertionError("Invalid topology sections: "+invalid);
    }
    private static double quantile(List<Double> v,double q){return v.get(Math.max(0,(int)Math.ceil(q*v.size())-1));}
    private static void map(Path out,PaMacroGeography p,long seed)throws Exception {
        var image=new java.awt.image.BufferedImage(1024,1024,java.awt.image.BufferedImage.TYPE_INT_RGB);
        for(int z=0;z<1024;z++)for(int x=0;x<1024;x++) {
            var s=p.sampleMacro(-32768+x*64,-32768+z*64);
            int color=s.land()?0x678e50:s.waterBody()==MacroGeographyProvider.MarineClass.INLAND_SEA?0x486f9e:
                s.shelfFraction()>0.5?0x5da9b6:s.inReservedCorridor()?0x173451:0x28526f;
            if(s.land()&&s.islandClass()!=MacroGeographyProvider.IslandClass.NONE)color=s.islandClass()==MacroGeographyProvider.IslandClass.ARCHIPELAGO?0xeab95b:0xa2bb68;
            image.setRGB(x,z,color);
        }
        javax.imageio.ImageIO.write(image,"png",out.resolve("macro-map-"+seed+".png").toFile());
    }
    private static void write(Path out,String name,Object value)throws Exception{Files.writeString(out.resolve(name),JSON.toJson(value),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static String digest(PaMacroGeography p,List<Integer> order,boolean parallel)throws Exception {
        String[] rows=new String[order.size()];
        var stream=parallel?order.parallelStream():order.stream();
        stream.forEach(i->{int x=(i%129-64)*509-1,z=(i/129-64)*509+1;rows[i]=JSON.toJson(p.sampleMacro(x,z));});
        MessageDigest hash=MessageDigest.getInstance("SHA-256");for(String row:rows)hash.update(row.getBytes(StandardCharsets.UTF_8));return HexFormat.of().formatHex(hash.digest());
    }
    public static void determinism(Path out,MacroGeographySettings settings)throws Exception {
        List<Integer> order=new ArrayList<>();for(int i=0;i<129*129;i++)order.add(i);
        var p=provider(SEEDS[0],settings);String cold=digest(p,order,false),warm=digest(p,order,false);
        Collections.reverse(order);String reverse=digest(p,order,false),restart=digest(provider(SEEDS[0],settings),order,false);
        Map<String,Object> results=new LinkedHashMap<>();results.put("cold",cold);results.put("warm",warm);results.put("reverse",reverse);results.put("reconstructedContext",restart);
        for(int workers:new int[]{1,2,8}) {
            ForkJoinPool pool=new ForkJoinPool(workers);try{String value=pool.submit(()->digest(p,order,true)).get();results.put("workers"+workers,value);if(!cold.equals(value))throw new AssertionError("Worker determinism");}finally{pool.shutdown();}
        }
        String pair=digest(provider(SEEDS[1],settings),order,false);
        write(out,"determinism_digests.json",results);write(out,"seed_divergence.json",Map.of("8675309",cold,"4303642605",pair,"differentTopology",!cold.equals(pair)));
        if(!cold.equals(warm)||!cold.equals(reverse)||!cold.equals(restart)||cold.equals(pair))throw new AssertionError("Macro determinism/collision");
    }
    private static void adversarial(Path out)throws Exception {
        List<Object> results=new ArrayList<>();
        double[][] cases={{16384,.15,1000,128,0.3,.16,384},{65536,.56,1000,128,.3,.16,384},{16384,.44,256,128,.3,.16,128},
            {32768,.25,8000,128,.3,.16,384},{16384,.44,1000,512,.3,.16,256},{16384,.44,1000,128,1,1,384},
            {131072,.44,1000,128,.3,.16,384},{8192,.30,1000,128,.3,.16,384},
            {8192,.60,8000,512,1,1,384},{0,.44,1000,128,.3,.16,384},{16384,Double.NaN,1000,128,.3,.16,384},{16384,.44,1000,128,.3,.16,1000}};
        for(int i=0;i<cases.length;i++) {
            var c=cases[i];boolean expectedValid=i<8;
            try {
                var s=new MacroGeographySettings(c[0],c[1],c[2],c[3],c[4],c[5],.2,c[6],18,1);var p=provider(42,s);
                long failures=TopologyMeasurements.widths(p,p.oceans().corridor(0,0,0),s.continentScaleBlocks()).stream().filter(w->w.violation()||!w.opposingMajorLandFound()).count();
                results.add(Map.of("case",i,"accepted",true,"invalidSections",failures));if(!expectedValid||failures!=0)throw new AssertionError("Adversarial case "+i);
            }catch(IllegalArgumentException rejected){results.add(Map.of("case",i,"accepted",false,"reason",rejected.getMessage()));if(expectedValid)throw rejected;}
        }
        write(out,"adversarial_configs.json",results);
    }
    private static void distanceChecks(Path out,MacroGeographySettings settings)throws Exception {
        var p=provider(42,settings);var field=new CoarseDistanceField(p);List<Object> results=new ArrayList<>();
        for(int x:new int[]{-4097,-4096,-4095,-1,0,1,4095,4096,4097,8192}) {
            var a=field.sample(x,8192);var b=field.sample(x,8192);field.clear();var c=field.sample(x,8192);
            if(!a.equals(b)||!a.equals(c))throw new AssertionError("Distance cache invariance");results.add(Map.of("x",x,"z",8192,"distances",a));
        }
        write(out,"distance_checks.json",results);write(out,"cache_metrics.json",Map.of("distance",field.cacheStats(),"islands",p.islands().cacheStats()));
    }
}
