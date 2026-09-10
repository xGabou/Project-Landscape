package baseline.reproduction.performance;
import com.gabou.atmospheregen.runtime.*;
import net.minecraft.nbt.*;
import java.nio.file.*;
import java.util.*;
public final class RuntimeHistoryChecks {
    private static int checks;
    private static void check(boolean c,String n){if(!c)throw new AssertionError(n);checks++;}
    private static void rejects(Runnable r){try{r.run();throw new AssertionError("Expected rejection");}catch(IllegalArgumentException|IllegalStateException expected){checks++;}}
    private static RuntimeClimateSample sample(long t,double c,double rain){return new RuntimeClimateSample(t,c,rain,.7,2,-3,1013.25);}
    private static String worldId(Path data,boolean existingHistory) {
        try {
            var method=Class.forName("com.gabou.atmospheregen.runtime.ClimateWorldIdentity").getDeclaredMethod("loadOrCreate",Path.class,boolean.class);
            method.setAccessible(true);return (String)method.invoke(null,data,existingHistory);
        }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
    }
    public static void main(String[] args)throws Exception {
        long[] windows={24000,24000,96000,384000};
        check(new RuntimeClimateSample(0,0,0,(double)1.2f,0,0,1013).humidityFraction()==(double)1.2f,"PA float humidity ceiling");
        var contexts=new BaselineContextStore();
        contexts.record(new BaselineContextStore.Context(4,4,0,null,null));
        check(contexts.sample(4,4).isEmpty(),"non-representative completion ignored");
        contexts.record(new BaselineContextStore.Context(0,0,0,null,null));
        contexts.record(new BaselineContextStore.Context(8,8,0,null,null));
        check(contexts.sample(8,8).orElseThrow().blockX()==0,"fixed representative independent of completion order");
        contexts.record(new BaselineContextStore.Context(-2000,-2000,0,null,null));
        check(contexts.sample(-1,-1).orElseThrow().blockX()==-2000,"negative representative");
        contexts.close();
        check(ClimateRegion.at(-1,-2000,2000).equals(new ClimateRegion(-1,-1)),"negative boundaries");
        var h=new ClimateHistory("worldA/overworld",2000,windows);
        h.observe(0,0,sample(100,10,2));h.observe(0,0,sample(300,20,0));h.observe(0,0,sample(500,-5,1));
        var region=h.region(0,0).orElseThrow();var s=region.snapshot(0,500);
        check(s.elapsedTicks()==400&&s.meanTemperature()==15,"time weighting");
        check(s.precipitationIntensityTicks()==400&&s.wetTicks()==200&&s.dryTicks()==200,"precipitation index units");
        check(!h.observe(0,0,sample(500,99,0)),"duplicate time");check(!h.observe(0,0,sample(499,99,0)),"reverse time");
        h.observe(0,0,sample(2000,99,0));check(region.snapshot(0,2000).elapsedTicks()==400,"missing gap");
        var file=Path.of(args[0]);Files.createDirectories(file.getParent());
        var identityRoot=Files.createTempDirectory(file.getParent(),"save-identity-");
        String worldA=worldId(identityRoot.resolve("a"),false),worldB=worldId(identityRoot.resolve("b"),false);
        check(!worldA.equals(worldB),"independent same-seed saves have separate UUIDs");
        check(worldA.equals(worldId(identityRoot.resolve("a"),true)),"save UUID reused on reopen");
        rejects(()->worldId(identityRoot.resolve("missing"),true));
        var saved=h.save(new CompoundTag());NbtIo.writeCompressed(saved,file.toFile());
        var reopened=ClimateHistory.load(NbtIo.readCompressed(file.toFile()),"worldA/overworld",2000,windows);
        check(reopened.save(new CompoundTag()).equals(saved),"compressed NBT roundtrip");
        reopened.observe(0,0,sample(2200,10,0));check(reopened.region(0,0).orElseThrow().snapshot(0,2200).elapsedTicks()==400,"no cross-session interpolation");
        check(reopened.region(2000,0).isEmpty(),"region isolation");
        check(new ClimateHistory("worldB/overworld",2000,windows).region(0,0).isEmpty(),"world isolation");
        rejects(()->ClimateHistory.load(saved,"worldB/overworld",2000,windows));rejects(()->ClimateHistory.load(saved,"worldA/nether",2000,windows));
        rejects(()->ClimateHistory.load(saved,"worldA/overworld",512,windows));rejects(()->ClimateHistory.load(saved,"worldA/overworld",2000,new long[]{24000,48000,192000,768000}));
        var future=saved.copy();future.putInt("schema",2);rejects(()->ClimateHistory.load(future,"worldA/overworld",2000,windows));
        for(int i=1;i<5000;i++)h.observe(i*2000,0,sample(2000,0,0));
        check(h.regionCount()==ClimateHistory.MAX_REGIONS,"region capacity");check(h.region(0,0).orElseThrow().observationCount()==4,"old history retained at capacity");
        var rolling=new ClimateHistoryRegion(windows);for(long t=0;t<800000;t+=200)rolling.observe(sample(t,12,.4));
        check(rolling.snapshot(3,800000).elapsedTicks()<=384000,"rolling bound");check(rolling.snapshot(0,900000).elapsedTicks()==0,"expiry without updates");
        h.close();rejects(()->h.observe(0,0,sample(3000,0,0)));rejects(()->h.region(0,0));
        check(h.save(new CompoundTag()).getInt("schema")==1,"detached post-close serialization");
        rejects(()->sample(0,Double.NaN,0));rejects(()->sample(0,0,-1));
        var report=Map.of("status","PASS","checks",checks,"compressedPersistence",true,"worldIsolation",true,"boundedWindows",true);
        Files.writeString(file.resolveSibling("history_checks.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");System.out.println(report);
    }
}
