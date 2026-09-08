/* Original development tests. All Rights Reserved. */
package baseline.reproduction;
import com.gabou.atmospheregen.config.*;
import com.gabou.atmospheregen.generation.version.*;
import com.gabou.atmospheregen.generation.seed.*;
import com.mojang.serialization.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
public final class FoundationTests {
    private static <T> void roundtrip(Evidence out,String name,Codec<T> codec,T value) {
        var json=codec.encodeStart(JsonOps.INSTANCE,value).getOrThrow(false,s->{});
        var decoded=codec.parse(JsonOps.INSTANCE,json).getOrThrow(false,s->{});
        if(!value.equals(decoded))throw new AssertionError(name+" round trip");
        out.row("foundation_roundtrip","case",name,"json",json,"equal",true);
    }
    private static void reject(Evidence out,String name,Codec<?> codec,String json) {
        var result=codec.parse(JsonOps.INSTANCE,com.google.gson.JsonParser.parseString(json));
        if(result.error().isEmpty())throw new AssertionError("Accepted invalid "+name);
        out.row("foundation_invalid","case",name,"error",result.error().get().message());
    }
    public static void run(ServerLevel level,Evidence out) throws Exception {
        roundtrip(out,"legacy versions",GenerationVersions.CODEC,GenerationVersions.legacy());
        roundtrip(out,"planned versions",GenerationVersions.CODEC,GenerationVersions.planned());
        roundtrip(out,"planned geography controls",PlannedGeographySettings.CODEC,new PlannedGeographySettings(3000,1000,1200,2000,.5));
        roundtrip(out,"legacy climate config",BaselineClimateConfig.CODEC,new BaselineClimateConfig(Optional.empty()));
        roundtrip(out,"planned climate config",BaselineClimateConfig.CODEC,new BaselineClimateConfig(Optional.of(new BaselineClimateConfig.Planned(100000,.0065,.5,.5))));
        roundtrip(out,"planned resolver config",BiomeResolverConfig.CODEC,new BiomeResolverConfig(Optional.of(new BiomeResolverConfig.Planned(64,1))));
        reject(out,"unknown geography",GeographyAlgorithmVersion.CODEC,"\"FUTURE_UNKNOWN\"");
        reject(out,"missing version fields",GenerationVersions.CODEC,"{}");
        reject(out,"unknown schema",GenerationVersions.CODEC,"{\"schemaVersion\":2,\"geography\":\"LEGACY_RTF_V0\",\"baselineClimate\":\"LEGACY_RTF_HINTS_V0\",\"biomeResolver\":\"LEGACY_MULTINOISE_V0\"}");
        reject(out,"bad planned climate",BaselineClimateConfig.CODEC,"{\"planned\":{\"latitudeScaleBlocks\":0,\"lapseCelsiusPerBlock\":0.0065,\"oceanInfluence\":0.5,\"rainShadowStrength\":0.5}}");
        reject(out,"bad planned resolver",BiomeResolverConfig.CODEC,"{\"planned\":{\"spatialResolutionBlocks\":0,\"fallbackWeight\":-1}}");
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}) {
            var result=ConfigCodecs.finite("test",0,1).parse(JsonOps.INSTANCE,new com.google.gson.JsonPrimitive(value));
            if(result.error().isEmpty())throw new AssertionError("Accepted nonfinite/out of range");
            out.row("foundation_invalid","case","finite range "+value,"error",result.error().get().message());
        }
        GenerationVersions.legacy().requireFunctionalBackend();
        try{GenerationVersions.planned().requireFunctionalBackend();throw new AssertionError("PA geography fallback");}
        catch(UnsupportedOperationException expected){out.row("foundation_invalid","case","unimplemented backend","error",expected.getMessage());}
        seeds(out);
    }
    private static void seeds(Evidence out) throws Exception {
        var dimensions=List.of(new net.minecraft.resources.ResourceLocation("minecraft:overworld"),new net.minecraft.resources.ResourceLocation("minecraft:the_nether"),new net.minecraft.resources.ResourceLocation("minecraft:the_end"));
        long[] seeds={8675309L,4303642605L,42L,-987654321L,0L,Long.MAX_VALUE,-Long.MAX_VALUE};
        for(long seed:seeds) for(var dimension:dimensions) {
            var service=new NamedSeedService(seed,dimension,GenerationVersions.planned());
            var reverse=new ArrayList<>(List.of(SeedDomain.values()));Collections.reverse(reverse);
            for(var domain:reverse) {
                long value=service.seed(domain);
                long async=java.util.concurrent.CompletableFuture.supplyAsync(()->service.seed(domain)).get();
                if(value!=async||value!=new NamedSeedService(seed,dimension,GenerationVersions.planned()).seed(domain))throw new AssertionError("Seed order/thread identity");
                if(value==service.seed(domain,1))throw new AssertionError("Seed salt separation");
                out.row("seed_domains","scheme","atmospheregen:named-seed-v1","seed",Long.toString(seed),"dimension",dimension.toString(),"domain",domain.id(),"version",domain.algorithmVersion(GenerationVersions.planned()),"value",Long.toString(value),"saltOne",Long.toString(service.seed(domain,1)),"orderAndWorkerEqual",true);
            }
        }
        for(var domain:SeedDomain.values()) {
            var a=new NamedSeedService(8675309L,dimensions.get(0),GenerationVersions.planned());
            var b=new NamedSeedService(4303642605L,dimensions.get(0),GenerationVersions.planned());
            if(a.seed(domain)==b.seed(domain))throw new AssertionError("High seed bits lost");
            Set<Long> values=new HashSet<>();for(var dim:dimensions)values.add(new NamedSeedService(8675309L,dim,GenerationVersions.planned()).seed(domain));
            if(values.size()!=3)throw new AssertionError("Dimension seed collision");
            out.row("seed_collision_legacy_vs_new","domain",domain.id(),"legacyIntA",(int)8675309L,"legacyIntB",(int)4303642605L,"newA",Long.toString(a.seed(domain)),"newB",Long.toString(b.seed(domain)),"newDiverges",true,"legacyTerrainProof","Task 1C canonical collision corpus; final Task 2 comparator rerun still required");
            out.row("dimension_seed_separation","domain",domain.id(),"distinctDimensions",3,"distinctValues",values.size());
        }
        var onlyClimateChanged=new GenerationVersions(1,GeographyAlgorithmVersion.PA_GEOGRAPHY_V1,BaselineClimateAlgorithmVersion.LEGACY_RTF_HINTS_V0,BiomeResolverAlgorithmVersion.PA_RESOLVER_V1);
        for(var domain:SeedDomain.values()) {
            long a=new NamedSeedService(42,dimensions.get(0),GenerationVersions.planned()).seed(domain);
            long b=new NamedSeedService(42,dimensions.get(0),onlyClimateChanged).seed(domain);
            boolean climate=domain==SeedDomain.BASELINE_TEMPERATURE||domain==SeedDomain.BASELINE_PRECIPITATION;
            if((a!=b)!=climate)throw new AssertionError("Stage algorithm version isolation");
        }
    }
}
