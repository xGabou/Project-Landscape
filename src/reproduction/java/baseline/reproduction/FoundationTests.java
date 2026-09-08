/* Original development tests. All Rights Reserved. */
package baseline.reproduction;
import com.gabou.atmospheregen.config.*;
import com.gabou.atmospheregen.generation.version.*;
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
    }
}
