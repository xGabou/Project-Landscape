/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package baseline.reproduction.climate;

import com.gabou.atmospheregen.api.climate.*;
import com.gabou.atmospheregen.api.geography.MacroGeographyProvider;
import com.gabou.atmospheregen.climate.*;
import com.gabou.atmospheregen.config.*;
import com.gabou.atmospheregen.generation.seed.NamedSeedService;
import com.gabou.atmospheregen.generation.version.GenerationVersions;
import com.gabou.atmospheregen.geography.continent.PaMacroGeography;
import com.gabou.atmospheregen.geography.ocean.CoarseDistanceField;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Offline Task 5 climate fixtures and V1 macro survey. No biome or weather code is involved. */
public final class ClimateSurvey {
    private static final long[] SEEDS={8675309,4303642605L,42,-987654321,0,Long.MAX_VALUE,Long.MIN_VALUE+1,-1,1,12345,67890,314159,271828,20260908,-424242,987654321012345L};
    private static final BaselineClimateConfig.Planned CONFIG=new BaselineClimateConfig.Planned(100000,0,.0065,.5,1,4096,.85,12000,24000,256,1,.10);
    private static final com.gabou.atmospheregen.generation.version.GenerationVersions VERSION=GenerationVersions.climateV1();
    private static NamedSeedService seeds(long seed){return new NamedSeedService(seed,new ResourceLocation("minecraft","overworld"),VERSION);}
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);if(Files.exists(out))throw new IllegalArgumentException("Refusing existing climate survey directory: "+out);Files.createDirectories(out);
        write(out,"configuration.json",CONFIG);synthetic(out);v1Survey(out);determinism(out);write(out,"completion.json",Map.of("status","PASS","version",VERSION,"biomesImplemented",false,"runtimeWeatherImplemented",false));
    }
    private static void synthetic(Path out)throws Exception {
        var model=new BaselineClimateModel(seeds(42),CONFIG);var flat=new Fixture(false,false);var barrier=new Fixture(true,false);var two=new Fixture(true,true);
        List<String> temp=new ArrayList<>(List.of("case,temperatureCelsius,latitudeDegrees"));
        for(int z:new int[]{0,30000,60000,-30000,-60000}){var b=model.breakdown(flat,0,z);temp.add("latitude_"+z+","+b.baseline().meanTemperatureCelsius()+","+b.breakdown().latitudeDegrees());}
        var low=model.sample(flat,0,0);var high=model.sample(new Elevation(flat,1000),0,0);temp.add("altitude_low,"+low.meanTemperatureCelsius()+",0");temp.add("altitude_high,"+high.meanTemperatureCelsius()+",0");writeLines(out,"synthetic_temperature.csv",temp);
        var wind=new WindDirection(-1,0);var windward=model.breakdownWithWind(barrier,2000,0,wind);var lee=model.breakdownWithWind(barrier,0,0,wind);var control=model.breakdownWithWind(flat,0,0,wind);
        check(windward.baseline().annualRainfallMm()>lee.baseline().annualRainfallMm(),"windward must be wetter than lee");check(lee.baseline().annualRainfallMm()<=control.baseline().annualRainfallMm()*.60,"Okanagan 60% barrier target");
        var removed=model.breakdownWithWind(flat,0,0,wind);check(removed.baseline().annualRainfallMm()>lee.baseline().annualRainfallMm(),"barrier removal recovery");
        writeLines(out,"synthetic_orographic.csv",List.of("case,rainfallMm,shadow,orographicRainfall,maximumRise",row("windward",windward),row("leeward",lee),row("unobstructed_control",control),row("barrier_removed",removed)));
        var reversed=model.breakdownWithWind(barrier,0,0,new WindDirection(1,0));check(Math.abs(reversed.baseline().annualRainfallMm()-lee.baseline().annualRainfallMm())>1,"wind reversal must change rainfall");writeLines(out,"wind_reversal.csv",List.of("case,rainfallMm,evaporationMm,moistureIndex,rainShadow",csv("forward",lee.baseline()),csv("reversed",reversed.baseline())));
        var second=model.breakdownWithWind(two,0,0,wind);check(second.baseline().annualRainfallMm()<control.baseline().annualRainfallMm(),"second range cannot restore full ocean moisture");writeLines(out,"multi_range.csv",List.of("case,rainfallMm,shadow",csv("one_range",lee.baseline()),csv("two_ranges",second.baseline())));
        var inland=model.sample(new Fixture(false,false,MacroGeographyProvider.MarineClass.INLAND_SEA),0,0);writeLines(out,"rainfall_fixtures.csv",List.of("case,rainfallMm,evaporationMm,moistureIndex,rainShadow",csv("flat",low),csv("inland_sea",inland)));
    }
    private static String row(String n,BaselineClimateModel.Result r){return n+","+r.baseline().annualRainfallMm()+","+r.baseline().rainShadow()+","+r.breakdown().orographicRainfall()+","+r.breakdown().maximumBarrierRiseBlocks();}
    private static String csv(String n,ClimateBaseline b){return n+","+b.annualRainfallMm()+","+b.potentialEvaporationMm()+","+b.ecologicalMoistureIndex()+","+b.rainShadow();}
    private static void v1Survey(Path out)throws Exception {List<String> rows=new ArrayList<>(List.of("seed,x,z,latitude,temperatureC,rainfallMm,evaporationMm,moistureIndex,rainShadow,windX,windZ,marineClass"));
        List<String> summary=new ArrayList<>(List.of("seed,temperatureMin,temperatureMax,temperatureMean,rainfallMin,rainfallMax,rainfallMean,evaporationMean,moistureMean,rainShadowMean,windChanges"));
        for(long seed:SEEDS){var macro=new PaMacroGeography(seeds(seed),com.gabou.atmospheregen.config.MacroGeographySettings.defaults());var input=ClimateGeographyAdapters.macroOnly(macro,new CoarseDistanceField(macro));var provider=new PaBaselineClimateProvider(input,seeds(seed),CONFIG);double tmin=1e9,tmax=-1e9,ts=0,rmin=1e9,rmax=-1e9,rs=0,es=0,ms=0,ss=0;int n=0,changes=0;WindDirection prev=null;
            for(int z=-32768;z<=32768;z+=4096)for(int x=-32768;x<=32768;x+=4096){var c=provider.sample(x,z);var m=macro.sampleMacro(x,z);var w=c.prevailingWind().orElseThrow();rows.add(seed+","+x+","+z+","+((double)z/100000*90)+","+c.meanTemperatureCelsius()+","+c.annualRainfallMm()+","+c.potentialEvaporationMm()+","+c.ecologicalMoistureIndex()+","+c.rainShadow()+","+w.x()+","+w.z()+","+m.waterBody());tmin=Math.min(tmin,c.meanTemperatureCelsius());tmax=Math.max(tmax,c.meanTemperatureCelsius());ts+=c.meanTemperatureCelsius();rmin=Math.min(rmin,c.annualRainfallMm());rmax=Math.max(rmax,c.annualRainfallMm());rs+=c.annualRainfallMm();es+=c.potentialEvaporationMm();ms+=c.ecologicalMoistureIndex();ss+=c.rainShadow();if(prev!=null&&Math.abs(prev.x()-w.x())>.2)changes++;prev=w;n++;}
            summary.add(seed+","+tmin+","+tmax+","+ts/n+","+rmin+","+rmax+","+rs/n+","+es/n+","+ms/n+","+ss/n+","+changes);}
        writeLines(out,"climate_transects.csv",rows);writeLines(out,"climate_distribution.csv",summary);
    }
    private static void determinism(Path out)throws Exception {var macro=new PaMacroGeography(seeds(8675309),com.gabou.atmospheregen.config.MacroGeographySettings.defaults());var input=ClimateGeographyAdapters.macroOnly(macro,new CoarseDistanceField(macro));var p=new PaBaselineClimateProvider(input,seeds(8675309),CONFIG);int[] order={0,1,2,3,4,5,6,7,8,9};String a=digest(p,order);int[] rev={9,8,7,6,5,4,3,2,1,0};String b=digest(p,rev);p.clear();String c=digest(p,order);if(!a.equals(b)||!a.equals(c))throw new AssertionError("climate order/cache determinism");var otherMacro=new PaMacroGeography(seeds(4303642605L),com.gabou.atmospheregen.config.MacroGeographySettings.defaults());var other=new PaBaselineClimateProvider(ClimateGeographyAdapters.macroOnly(otherMacro,new CoarseDistanceField(otherMacro)),seeds(4303642605L),CONFIG);write(out,"determinism_digests.csv",List.of("case,digest","forward,"+a,"reverse,"+b,"cold,"+c,"collision_pair,"+digest(other,order)));write(out,"seed_divergence.json",new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("legacy","unchanged","v1ClimateDifferent",!a.equals(digest(other,order)))));}
    private static String digest(PaBaselineClimateProvider p,int[] order){long[] values=new long[10];for(int i:order){var c=p.sample(i*509-2000,i*401-1000);values[i]=Double.doubleToRawLongBits(c.meanTemperatureCelsius())^Long.rotateLeft(Double.doubleToRawLongBits(c.annualRainfallMm()),17);}long h=1125899906842597L;for(long value:values){h=31*h+value;}return Long.toUnsignedString(h);}
    private static void write(Path out,String name,Object value)throws Exception{Files.writeString(out.resolve(name),value instanceof String?safe((String)value):new GsonBuilder().setPrettyPrinting().create().toJson(value),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static void writeLines(Path out,String name,List<String> lines)throws Exception{Files.write(out.resolve(name),lines,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    private static String safe(String s){return s;}
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    private record Fixture(boolean barrier,boolean second,MacroGeographyProvider.MarineClass override) implements ClimateGeography {
        Fixture(boolean b,boolean s){this(b,s,MacroGeographyProvider.MarineClass.LAND);} Fixture(boolean barrier,boolean second,MacroGeographyProvider.MarineClass override){this.barrier=barrier;this.second=second;this.override=override;}
        public Sample sample(int x,int z){MacroGeographyProvider.MarineClass water=override;if(water==MacroGeographyProvider.MarineClass.LAND&&x>7000)water=MacroGeographyProvider.MarineClass.MAJOR_OCEAN;double e=100;if(barrier&&x>=700&&x<=1400)e=1800;if(second&&x>=5000&&x<=5700)e=1500;return new Sample(e,Math.max(0,7000-x),Math.max(0,7000-x),0,water);}
    }
    private record Elevation(ClimateGeography base,double amount) implements ClimateGeography {public Sample sample(int x,int z){var s=base.sample(x,z);return new Sample(s.elevationBlocks()+amount,s.coastDistanceBlocks(),s.oceanDistanceBlocks(),s.mountainInfluence(),s.marineClass());}}
}
