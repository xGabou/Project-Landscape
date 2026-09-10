package com.gabou.projectlandscape.runtime;

import java.util.IdentityHashMap;
import java.util.Map;
import com.gabou.projectlandscape.compat.projectatmosphere.PaRuntimeClimateProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;

/** Forge registrations only; runtime state is keyed by live level identity and removed on unload. */
@Mod.EventBusSubscriber(modid=RTFCommon.MOD_ID)
public final class RuntimeClimateLifecycle {
    private static final Map<ServerLevel,RuntimeClimateService> SERVICES=new IdentityHashMap<>();
    private static final Map<ServerLevel,String> STATUS=new IdentityHashMap<>();
    private RuntimeClimateLifecycle() { }
    public static RuntimeClimateService service(ServerLevel level){return SERVICES.get(level);}
    public static String status(ServerLevel level){return STATUS.getOrDefault(level,"runtime climate provider: unavailable; dynamic climate: disabled");}
    public static BaselineContextStore baseline(ServerLevel level) {
        if(level.dimension()!=Level.OVERWORLD)return null;
        var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
        if(context==null||context.generationContext()==null||context.generator.getHeightmap().paBridge()==null)return null;
        var versions=context.generationContext().manifest().content().versions();
        if(versions.baselineClimate()!=com.gabou.projectlandscape.generation.version.BaselineClimateAlgorithmVersion.PA_BASELINE_V1)return null;
        return context.canonicalClimate(context.generationContext()).provider().runtimeContext();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void start(ServerStartedEvent event) {
        ServerLevel level=event.getServer().overworld();BaselineContextStore baseline=baseline(level);
        if(baseline==null)return;
        if(!net.minecraftforge.fml.ModList.get().isLoaded("projectatmosphere")) {
            STATUS.put(level,"runtime climate provider: unavailable (PA absent); baseline climate: available; dynamic climate: disabled");return;
        }
        PaRuntimeClimateProvider provider=null;
        try {
            provider=new PaRuntimeClimateProvider(level,baseline);var p=provider;
            var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext().generationContext();
            // Preflight prevents SavedData's recovery fallback from silently overwriting a future/invalid schema.
            var data=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data");
            var file=data.resolve(ClimateHistory.DATA_NAME+".dat").toFile();
            String identity=ClimateWorldIdentity.loadOrCreate(data,file.exists())+"/"+context.contextId()+"/"+level.dimension().location();
            if(file.exists())ClimateHistory.load(net.minecraft.nbt.NbtIo.readCompressed(file).getCompound("data"),identity,p.regionSize(),p.windowTicks());
            var history=level.getDataStorage().computeIfAbsent(t->ClimateHistory.load(t,identity,p.regionSize(),p.windowTicks()),
                    ()->new ClimateHistory(identity,p.regionSize(),p.windowTicks()),ClimateHistory.DATA_NAME);
            SERVICES.put(level,new RuntimeClimateService(level,p,history,baseline));STATUS.put(level,"runtime climate provider: "+p.source());
        } catch(Exception|LinkageError ex) {
            if(provider!=null)provider.close();
            STATUS.put(level,"runtime climate provider: unavailable ("+ex.getClass().getSimpleName()+": "+ex.getMessage()+"); baseline climate: available; dynamic climate: disabled");
            org.slf4j.LoggerFactory.getLogger("ProjectClimateRuntime").warn("Runtime climate disabled; static generation remains available",ex);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level) {
            var service=SERVICES.get(level);if(service!=null)service.tick();
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void stop(ServerStoppingEvent event) {
        for(var level:java.util.List.copyOf(SERVICES.keySet()))if(level.getServer()==event.getServer())dispose(level);
        STATUS.keySet().removeIf(level->level.getServer()==event.getServer());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)dispose(level);
    }
    private static void dispose(ServerLevel level) {
        RuntimeClimateService service=SERVICES.remove(level);STATUS.remove(level);
        if(service!=null){try{level.getDataStorage().save();}finally{service.close();}}
    }
}
