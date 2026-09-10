package baseline.reproduction;

import com.gabou.atmospheregen.runtime.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Actual Forge server, PA state, world save/reopen and unload checks; never simulates fake weather. */
public final class RuntimeIntegrationChecks {
    private static RuntimeClimateService retained;
    private static ServerLevel retainedLevel;
    private static ClimateHistory retainedHistory;
    private static Object retainedSession;
    private static long savedCount;
    public static CompletableFuture<Void> begin(MinecraftServer server,Evidence out,boolean reopened) {
        var level=server.overworld();var service=RuntimeClimateLifecycle.service(level);
        boolean pa=net.minecraftforge.fml.ModList.get().isLoaded("projectatmosphere");
        if(RuntimeClimateLifecycle.service(server.getLevel(net.minecraft.world.level.Level.NETHER))!=null)throw new AssertionError("Nether runtime enabled");
        if(!pa) {
            if(service!=null||RuntimeClimateLifecycle.baseline(level)==null)throw new AssertionError("Static fallback");
            if(server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"geo runtimeclimate 0 0")!=1)throw new AssertionError("Static diagnostic");
            out.row("phase2a_runtime","pa",false,"reopened",reopened,"staticMode",true,"status",RuntimeClimateLifecycle.status(level));
            return CompletableFuture.completedFuture(null);
        }
        if(service==null)throw new AssertionError(RuntimeClimateLifecycle.status(level));
        var p=level.players().get(0);int x=p.getBlockX(),z=p.getBlockZ();
        if(reopened&&service.history().region(x,z).orElseThrow().observationCount()<savedCount)throw new AssertionError("History not restored");
        retained=service;retainedLevel=level;retainedHistory=service.history();
        try { retainedSession=Evidence.field(Evidence.field(service,"provider"),"session"); }
        catch(Exception ex){throw new AssertionError(ex);}
        final long restoredCount=reopened?savedCount:0;
        out.row("phase2a_start","reopened",reopened,"restoredCount",restoredCount,"status",RuntimeClimateLifecycle.status(level));
        try {out.flush();}catch(Exception ex){throw new AssertionError(ex);}
        CompletableFuture<Void> done=new CompletableFuture<>();long start=level.getGameTime();
        Object listener=new Object(){
            @SubscribeEvent public void tick(TickEvent.LevelTickEvent event) {
                if(event.level!=level||event.phase!=TickEvent.Phase.END||level.getGameTime()-start<650)return;
                var ready=service.history().region(x,z);
                if((ready.isEmpty()||ready.get().observationCount()<restoredCount+3||ready.get().snapshot(0,level.getGameTime()).elapsedTicks()==0)
                        && level.getGameTime()-start<6000)return;
                MinecraftForge.EVENT_BUS.unregister(this);
                try {
                    var current=service.sample(x,z).orElseThrow(()->new AssertionError("PA active observation missing"));
                    var h=service.history().region(x,z).orElseThrow();
                    if(h.observationCount()<3||h.snapshot(0,level.getGameTime()).elapsedTicks()==0)throw new AssertionError("History did not accumulate");
                    long before=((Number)Metrics.snapshot().get("tileGenerationCalls")).longValue();
                    long queryStart=System.nanoTime();
                    for(int i=0;i<100;i++)service.sample(x,z).orElseThrow();
                    long queryNs=System.nanoTime()-queryStart;
                    if(server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"geo runtimeclimate "+x+" "+z)!=1)throw new AssertionError("Runtime diagnostic");
                    if(server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"geo runtimeclimate 29000000 29000000")!=1)throw new AssertionError("Missing diagnostic");
                    long after=((Number)Metrics.snapshot().get("tileGenerationCalls")).longValue();
                    if(before!=after)throw new AssertionError("Runtime query generated terrain");
                    var baseline=service.baseline().sample(x,z);
                    if(!reopened&&baseline.isEmpty())throw new AssertionError("No captured baseline context");
                    var api=Class.forName("net.Gabou.projectatmosphere.api.climate.RuntimeClimateBridge");
                    long naturalInitializations=((Number)retainedSession.getClass().getMethod("baselineInitializationCount").invoke(retainedSession)).longValue();
                    long initializationCountAfter=naturalInitializations;
                    if(baseline.isPresent()) {
                        // Exercise PA's REAL initialization after ordinary generation supplied context.
                        // This is explicitly outside runtime-query measurements; it does not replace live weather.
                        var position=p.blockPosition();
                        var biome=level.getBiome(position).unwrapKey().orElseThrow().location();
                        var generator=Class.forName("net.Gabou.projectatmosphere.modules.temperature.util.TemperatureGenerator");
                        float[][] week=(float[][])generator.getMethod("generateWeekForecast",ServerLevel.class,net.minecraft.core.BlockPos.class,net.minecraft.resources.ResourceLocation.class)
                                .invoke(null,level,position,biome);
                        initializationCountAfter=((Number)retainedSession.getClass().getMethod("baselineInitializationCount").invoke(retainedSession)).longValue();
                        if(initializationCountAfter!=naturalInitializations+1||!Float.isFinite(week[0][0]))throw new AssertionError("Real PA temperature initialization missed baseline");
                    }
                    double initialized=((Number)api.getMethod("initialTemperature",ServerLevel.class,int.class,int.class,double.class)
                            .invoke(null,level,x,z,-12345d)).doubleValue();
                    if(initialized!=baseline.map(b->b.baseline().meanTemperatureCelsius()).orElse(-12345d))throw new AssertionError("PA baseline callback");
                    if(((Number)api.getMethod("initialTemperature",ServerLevel.class,int.class,int.class,double.class)
                            .invoke(null,level,29000000,29000000,-12345d)).doubleValue()!=-12345d)throw new AssertionError("Missing context fallback");
                    // A read from another thread must be rejected even though the source object is retained.
                    boolean rejected=CompletableFuture.supplyAsync(()->{try{service.sample(x,z);return false;}catch(IllegalStateException expected){return true;}}).join();
                    if(!rejected)throw new AssertionError("Client/worker authority");
                    savedCount=h.observationCount();
                    server.saveEverything(false,true,true);
                    out.row("phase2a_runtime","pa",true,"reopened",reopened,"serverAuthority",true,"baselineSeparate",true,
                            "baselineTemperature",initialized,"runtimeSample",current,"historyCount",savedCount,"coverageTicks",h.snapshot(0,level.getGameTime()).elapsedTicks(),
                            "runtimeQueryTerrainGenerations",after-before,"grid",service.regionSize(),"saveReopen",reopened,
                            "restoredCount",restoredCount,"naturalBaselineInitializations",naturalInitializations,"sample100Ns",queryNs,"diagnostic",true,
                            "baselineBackedTemperatureGenerator",initializationCountAfter>naturalInitializations,"windowTicks",service.history().save(new net.minecraft.nbt.CompoundTag()).getLongArray("windowTicks"),
                            "baselinePoint",baseline.map(b->b.blockX()+","+b.blockZ()).orElse("unavailable; no terrain acquired"));
                    out.flush();done.complete(null);
                }catch(Throwable ex){done.completeExceptionally(ex);}
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);return done;
    }
    public static void unloaded(Evidence out) throws Exception {
        if(retained==null)return;
        if(RuntimeClimateLifecycle.service(retainedLevel)!=null)throw new AssertionError("Runtime level retained");
        try{retained.sample(0,0);throw new AssertionError("Stale runtime accepted");}catch(IllegalStateException expected){}
        try{retainedHistory.region(0,0);throw new AssertionError("History connected");}catch(IllegalStateException expected){}
        try{retainedSession.getClass().getMethod("sample",int.class,int.class).invoke(retainedSession,0,0);throw new AssertionError("PA session alive");}
        catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof IllegalStateException))throw new AssertionError(expected);}
        catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
        if(Evidence.field(retainedSession,"level")!=null||Evidence.field(retainedSession,"context")!=null)throw new AssertionError("PA retained world references");
        out.row("phase2a_disposal","unregistered",true,"staleAccessRejected",true,"historyDisconnected",true,"paSessionClosed",true,"paWorldReferencesCleared",true);
        retainedHistory=null;retainedSession=null;
        retained=null;retainedLevel=null;
    }
}
