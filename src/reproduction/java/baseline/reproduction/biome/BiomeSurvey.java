/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package baseline.reproduction.biome;

import com.gabou.projectlandscape.api.climate.*;import com.gabou.projectlandscape.api.geography.*;import com.gabou.projectlandscape.biome.*;import com.gabou.projectlandscape.climate.*;import com.gabou.projectlandscape.config.*;import com.gabou.projectlandscape.generation.seed.NamedSeedService;import com.gabou.projectlandscape.generation.version.GenerationVersions;import com.gabou.projectlandscape.geography.continent.PaMacroGeography;import com.gabou.projectlandscape.geography.ocean.CoarseDistanceField;import com.google.gson.GsonBuilder;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.level.biome.Biomes;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.security.MessageDigest;import java.util.*;

/** Offline V1 resolver survey. It never creates or mutates a Minecraft biome source. */
public final class BiomeSurvey {
    private static final long[] SEEDS={8675309,4303642605L,42,-987654321,0,Long.MAX_VALUE,Long.MIN_VALUE+1,-1,1,12345,67890,314159,271828,20260908,-424242,987654321012345L};
    private static final BaselineClimateConfig.Planned CLIMATE=new BaselineClimateConfig.Planned(100000,0,.0065,.5,1,4096,.85,12000,24000,256,1,.10);private static final BiomeResolverConfig.Planned CONFIG=new BiomeResolverConfig.Planned(64,1);private static final GenerationVersions VERSION=GenerationVersions.planned();private static NamedSeedService seeds(long s){return new NamedSeedService(s,new ResourceLocation("minecraft","overworld"),VERSION);}
    public static void main(String[] a)throws Exception {
        Path out=Path.of(a[0]);if(Files.exists(out))throw new IllegalArgumentException("Refusing existing survey directory");
        Files.createDirectories(out);
        var catalog=new VanillaBiomeCatalog();
        write(out,"manifest.json",Map.of("versions",VERSION.toString(),"resolverConfig",CONFIG,
                "catalogFingerprint",catalog.fingerprint().sha256(),"scope","macro-proxy and synthetic validation; runtime acceptance separate"));
        var descriptors=new com.google.gson.JsonArray();
        for(var descriptor:catalog.descriptors())descriptors.add(BiomeDescriptor.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,descriptor).getOrThrow(false,message->{}));
        write(out,"vanilla_biome_catalog.json",descriptors);
        BiomeValidationChecks.run(out,catalog);
        if(Boolean.getBoolean("task6.checksOnly"))return;
        survey(out,catalog);
        extendedLatitude(out,catalog);
        write(out,"survey_completion.json",Map.of("status","COMPLETED","task6Accepted",false,
                "remaining",List.of("canonical terrain survey","runtime and save/reopen","performance","catalog reachability")));
    }

    private static void survey(Path out,BiomeCatalog catalog)throws Exception{List<String> dist=new ArrayList<>(List.of("seed,biome,count")),lat=new ArrayList<>(List.of("latitudeBand,biome,count")),climate=new ArrayList<>(List.of("temperatureBand,aridityBand,biome,count")),margin=new ArrayList<>(List.of("seed,x,z,winner,second,margin,override"));Map<String,Integer> totals=new TreeMap<>(),byLat=new TreeMap<>(),byClimate=new TreeMap<>();Map<String,String> seedDigests=new TreeMap<>();for(long seed:SEEDS){var macro=new PaMacroGeography(seeds(seed),MacroGeographySettings.defaults());var input=ClimateGeographyAdapters.macroOnly(macro,new CoarseDistanceField(macro));var provider=new PaBaselineClimateProvider(input,seeds(seed),CLIMATE);var resolver=new ClimateBiomeResolver(catalog,seeds(seed),CONFIG);Map<String,Integer> local=new TreeMap<>();StringBuilder seedOutput=new StringBuilder();for(int z=-32768;z<=32768;z+=4096)for(int x=-32768;x<=32768;x+=4096){var c=provider.sample(x,z);var g=toGeo(input.sample(x,z),x,z);var r=resolver.explain(g,c);String winner=r.winner().key().toString();seedOutput.append(x).append(',').append(z).append(',').append(winner).append('\n');local.merge(winner,1,Integer::sum);totals.merge(winner,1,Integer::sum);double latitude=Math.abs(new LatitudeModel(CLIMATE).degrees(z));String lb=latitude<30?"low":latitude<60?"mid":"high";byLat.merge(lb+","+winner,1,Integer::sum);String tb=c.meanTemperatureCelsius()<0?"cold":c.meanTemperatureCelsius()<15?"temperate":"warm",ab=c.annualRainfallMm()/Math.max(1,c.potentialEvaporationMm())<.35?"dry":c.annualRainfallMm()/Math.max(1,c.potentialEvaporationMm())<.8?"intermediate":"wet";byClimate.merge(tb+","+ab+","+winner,1,Integer::sum);String second=r.second().map(s->s.key().toString()).orElse("");margin.add(seed+","+x+","+z+","+winner+","+second+","+(r.second().map(s->r.winner().score()-s.score()).orElse(r.winner().score()))+","+r.geographicOverride());}seedDigests.put(Long.toString(seed),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seedOutput.toString().getBytes(StandardCharsets.UTF_8))));for(var e:local.entrySet())dist.add(seed+","+e.getKey()+","+e.getValue());}for(var e:byLat.entrySet()){String[] p=e.getKey().split(",",2);lat.add(p[0]+","+p[1]+","+e.getValue());}for(var e:byClimate.entrySet()){String[] p=e.getKey().split(",",3);climate.add(p[0]+","+p[1]+","+p[2]+","+e.getValue());}writeLines(out,"biome_distribution.csv",dist);writeLines(out,"biome_by_latitude.csv",lat);writeLines(out,"biome_by_climate.csv",climate);writeLines(out,"winner_margin.csv",margin);write(out,"seed_divergence.json",Map.of("macroProxyBiomeDigests",seedDigests,"collisionPairDiffers",!seedDigests.get("8675309").equals(seedDigests.get("4303642605"))));}
    private static void extendedLatitude(Path out,BiomeCatalog catalog)throws Exception {
        List<String> rows=new ArrayList<>(List.of("seed,x,z,latitudeDegrees,temperatureC,rainfallMm,evaporationMm,aridity,marineClass,winner"));
        for(long seed:SEEDS){
            var macro=new PaMacroGeography(seeds(seed),MacroGeographySettings.defaults());
            var input=ClimateGeographyAdapters.macroOnly(macro,new CoarseDistanceField(macro));
            var provider=new PaBaselineClimateProvider(input,seeds(seed),CLIMATE);
            var resolver=new ClimateBiomeResolver(catalog,seeds(seed),CONFIG);
            for(int z:new int[]{-2000000,-500000,-150000,-60000,0,60000,150000,500000,2000000})
                for(int x:new int[]{-8192,0,8192}){
                    var c=provider.sample(x,z);var m=macro.sampleMacro(x,z);
                    var winner=resolver.select(toGeo(input.sample(x,z),x,z),c);
                    rows.add(seed+","+x+","+z+","+new LatitudeModel(CLIMATE).degrees(z)+","+c.meanTemperatureCelsius()+","+c.annualRainfallMm()+","+c.potentialEvaporationMm()+","+BiomeScorer.aridity(c)+","+m.waterBody()+","+winner);
                }
        }
        writeLines(out,"extended_latitude.csv",rows);
    }
    private static GeoSample toGeo(ClimateGeography.Sample s,int x,int z){WaterCategory w=s.marineClass()==MacroGeographyProvider.MarineClass.MAJOR_OCEAN?WaterCategory.DEEP_OCEAN:s.marineClass()==MacroGeographyProvider.MarineClass.INLAND_SEA?WaterCategory.INLAND_SEA:s.marineClass()==MacroGeographyProvider.MarineClass.COASTAL_WATER?WaterCategory.SHALLOW_OCEAN:WaterCategory.LAND;return geo(s.elevationBlocks(),x,z,s.elevationBlocks()>900?Landform.MOUNTAIN:Landform.PLAINS,w);}
    private static GeoSample geo(double e,int x,double z,Landform f){return geo(e,x,z,f,WaterCategory.LAND);}private static GeoSample geo(double e,int x,double z,Landform f,WaterCategory w){var h=new HydrologySample(w,w==WaterCategory.RIVER,false,w==WaterCategory.WETLAND,Optional.empty(),Optional.empty());return new GeoSample(new BlockPosition(x,(int)z),e,e,w,f,new GeographyMetrics(Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.of(new Metric(f==Landform.MOUNTAIN?1:0,Metric.Quality.MODELLED,64)),Optional.empty()),h);}
    private static void write(Path p,String n,Object v)throws Exception{Files.writeString(p.resolve(n),new GsonBuilder().setPrettyPrinting().create().toJson(v),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}private static void writeLines(Path p,String n,List<String> l)throws Exception{Files.write(p.resolve(n),l,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
}
