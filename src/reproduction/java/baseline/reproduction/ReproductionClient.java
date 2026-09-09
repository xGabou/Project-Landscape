/* Original development harness. All Rights Reserved. */
package baseline.reproduction;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.data.worldgen.Datapacks;
import raccoonman.reterraforged.data.worldgen.preset.settings.Presets;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.cell.Cell;

@Mod("task1a_reproduction")
public final class ReproductionClient {
    private final long[] seeds=Arrays.stream(System.getProperty("task1a.seeds").split(",")).mapToLong(Long::parseLong).toArray();
    private final boolean full=System.getProperty("task1a.profile","full").equals("full");
    private final boolean chunksOnly=System.getProperty("task1a.profile","full").equals("chunks");
    private final String task1c=System.getProperty("task1a.profile", "full");
    private boolean task1cRepeat() { return task1c.equals("benchmark") || task1c.equals("variance"); }
    private final String run="reproduction-"+System.currentTimeMillis();
    private int stage,worldIndex; private long start=System.nanoTime();
    private Evidence out; private CompletableFuture<Void> work;
    private boolean disabledBootstrapSucceeded;
    private boolean foundationReopened;
    private boolean task6Reopened;
    private String task6Digest;
    private com.gabou.atmospheregen.biome.ClimateBiomeSource task6bDisposedSource;
    private com.gabou.atmospheregen.generation.context.WorldGenerationContext previousFoundationContext;
    private com.gabou.atmospheregen.api.geography.GeographyProvider previousFoundationProvider;
    private List<raccoonman.reterraforged.world.worldgen.GeneratorContext> worldContexts=List.of();
    public ReproductionClient(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;
        Minecraft mc=Minecraft.getInstance();
        try{
            if(out==null)out=new Evidence(mc.gameDirectory.toPath().resolve("evidence").resolve(run));
            if(System.nanoTime()-start>30L*60*1_000_000_000L)throw new IllegalStateException("Harness timeout at stage "+stage);
            if(stage==0&&mc.screen!=null&&mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")&&mc.getOverlay()==null)mc.setScreen(new TitleScreen());
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                stage=1; mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);
                CreateWorldScreen.openFresh(mc,mc.screen);
            }else if(stage==1&&mc.screen instanceof CreateWorldScreen screen){
                stage=2;String name=run+"-seed"+worldIndex;
                Path root=mc.gameDirectory.toPath().resolve("saves").resolve(name), pack=root.resolve("datapacks/reproduction-preset");Files.createDirectories(pack);
                if((task1c.equals("task6-biomes") || task1c.equals("task6b")) && worldIndex==0) {
                    Files.createDirectories(root.resolve("data/atmospheregen"));
                    Files.writeString(root.resolve("data/atmospheregen/development_biomes_v1.json"),"{\"climate\":{\"latitudeScaleBlocks\":100000,\"equatorZ\":0,\"lapseCelsiusPerBlock\":0.0065,\"oceanInfluence\":0.5,\"continentalityStrength\":1,\"windBandScaleBlocks\":4096,\"orographicStrength\":0.85,\"rainShadowRecoveryDistance\":12000,\"climateProfileDistance\":24000,\"climateProfileStep\":256,\"evaporationStrength\":1,\"regionalVariationStrength\":0.1},\"resolver\":{\"spatialResolutionBlocks\":64,\"fallbackWeight\":1,\"regionalVariationScaleBlocks\":4096,\"regionalVariationStrength\":0.18,\"transitionSoftness\":0.12}}");
                }
                var selected = Presets.makeLegacyDefault();
                if (System.getProperty("task1a.presetFile") != null) {
                    var json = com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("task1a.presetFile"))));
                    selected = raccoonman.reterraforged.data.worldgen.preset.settings.Preset.DIRECT_CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow(false, RTFCommon.LOGGER::error);
                }
                if(worldIndex==3&&full)selected.miscellaneous().customBiomeFeatures=false;
                Datapacks.makePreset(selected,screen.getUiState().getSettings().worldgenLoadContext(),root.resolve("export"),pack,"Task 1A selected preset").run();
                if(worldIndex==0&&full){
                    var disabled=Presets.makeLegacyDefault();disabled.miscellaneous().customBiomeFeatures=false;
                    try{Datapacks.makePreset(disabled,screen.getUiState().getSettings().worldgenLoadContext(),root.resolve("disabled-work"),root.resolve("disabled-pack"),"Disabled custom features reproduction").run();
                        disabledBootstrapSucceeded=true;
                        out.row("vegetation_disabled","bootstrap","success","loadAttempted",false,"note","Separate disabled-preset world follows the three comparator worlds");
                    }catch(Throwable ex){out.row("vegetation_disabled","bootstrap","failed","error",Evidence.failure(ex),"exceptionTree",Evidence.trace(ex),"loadAttempted",false,"reason","No complete pack available after bootstrap failure");}
                }
                var config=screen.getUiState().getSettings().dataConfiguration();var enabled=new ArrayList<>(config.dataPacks().getEnabled());enabled.add("file/reproduction-preset");
                var data=new WorldDataConfiguration(new DataPackConfig(enabled,List.of()),config.enabledFeatures());
                var settings=new LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),data);
                mc.createWorldOpenFlows().createFreshLevel(name,settings,new WorldOptions(currentSeed(),true,false),
                    registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
            }else if(stage==2&&mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()!=null){
                stage=3;var server=mc.getSingleplayerServer();
                work=server.submit(()->{
                    try{
                        out.row("observer_counters", "phase", "before_suite", "seed", currentSeed(), "counters", Metrics.snapshot());
                        if(task1c.equals("stage-baseline") || task1c.equals("stage-extraction")) {
                            StageBaseline.run(server.overworld(),out,task1c.equals("stage-extraction"));
                        } else if(task1c.equals("task4-terrain")) {
                            baseline.reproduction.geography.Task4TerrainChecks.run(server.overworld(),out);
                        } else if(task1c.equals("task6-canonical")) {
                            baseline.reproduction.biome.CanonicalBiomeSurvey.run(server.overworld(),out);
                        } else if(task1c.equals("public-provider-benchmark")) {
                            PublicProviderBenchmark.run(server.overworld(),out);
                        } else if(task1c.equals("geography")) {
                            GeographyChecks.run(server.overworld(),out);
                        } else if(task1c.equals("foundation")) {
                            FoundationTests.run(server.overworld(),out);
                            var metadata=((RTFRandomState)(Object)server.overworld().getChunkSource().randomState()).generatorContext().generationContext();
                            if(metadata==null)throw new AssertionError("Missing automatic world manifest binding");
                            if(foundationReopened && (!metadata.contextId().equals(previousFoundationContext.contextId()) || metadata.runtimeToken()==previousFoundationContext.runtimeToken()))throw new AssertionError("Reopened context persistence/isolation");
                            previousFoundationContext=metadata;
                            previousFoundationProvider=com.gabou.atmospheregen.compat.legacy.LegacyRtfGeographyAdapter.forLevel(server.overworld());
                            out.row("world_binding","phase",foundationReopened?"reopened":"created","metadata",com.gabou.atmospheregen.generation.context.GenerationDiagnostics.describe(metadata),"persistedContextStable",true);
                            ((net.minecraft.world.level.storage.PrimaryLevelData)server.getWorldData()).withConfirmedWarning(true);
                            server.saveEverything(false,true,true);
                        } else if(task1c.equals("task6b")) {
                            if(!task6Reopened||worldIndex!=0)baseline.reproduction.task6b.FullChunkPerformance.run(server.overworld(),out,worldIndex);
                            if(Boolean.getBoolean("task6b.final")){
                                if(worldIndex==0){
                                    String digest=baseline.reproduction.biome.Task6RuntimeChecks.run(server.overworld(),out,task6Reopened);
                                    if(task6Reopened&&!digest.equals(task6Digest))throw new AssertionError("Task 6B reopen biome mismatch");task6Digest=digest;
                                    ((net.minecraft.world.level.storage.PrimaryLevelData)server.getWorldData()).withConfirmedWarning(true);
                                    server.saveEverything(false,true,true);
                                }else{
                                    new LegacyBaselineSuite(server.overworld(),out).run("golden",worldIndex);
                                    baseline.reproduction.geography.Task4TerrainChecks.run(server.overworld(),out);
                                    baseline.reproduction.biome.CanonicalBiomeSurvey.run(server.overworld(),out);
                                }
                            }
                        } else if(task1c.equals("task6-biomes")) {
                            String digest=baseline.reproduction.biome.Task6RuntimeChecks.run(server.overworld(),out,task6Reopened);
                            if(task6Reopened&&!digest.equals(task6Digest))throw new AssertionError("Task 6 save/reopen biome mismatch");
                            task6Digest=digest;
                            ((net.minecraft.world.level.storage.PrimaryLevelData)server.getWorldData()).withConfirmedWarning(true);
                            server.saveEverything(false,true,true);
                        } else if(task1c.startsWith("golden") || task1cRepeat() || task1c.equals("allocation")) {
                            new LegacyBaselineSuite(server.overworld(), out).run(task1c,worldIndex);
                        } else if(worldIndex==0&&!chunksOnly)new ReproductionSuite(server.overworld(),out,seeds).run(server.overworld(),full);
                        out.row("observer_counters", "phase", "before_chunks", "seed", currentSeed(), "counters", Metrics.snapshot());
                        if(worldIndex==3&&full)disabledVegetation(server.overworld());
                        else if(full||chunksOnly)chunks(server.overworld());
                        out.row("observer_counters", "phase", "after_chunks", "seed", currentSeed(), "counters", Metrics.snapshot());
                        out.flush();
                    }catch(Exception ex){throw new RuntimeException(ex);}
                });
            }else if(stage==3&&work.isDone()){
                worldContexts=new ArrayList<>();
                for(var level:mc.getSingleplayerServer().getAllLevels()) {
                    var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
                    if(context!=null)worldContexts.add(context);
                }
                work.join();
                if(task1c.equals("task6b") && mc.getSingleplayerServer().overworld().getChunkSource().getGenerator().getBiomeSource() instanceof com.gabou.atmospheregen.biome.ClimateBiomeSource source)task6bDisposedSource=source;
                stage=4;mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());
            }else if(stage==4&&mc.getSingleplayerServer()==null){
                boolean allClosed=true,allUnregistered=true;long live=0;
                for(var context:worldContexts) {
                    allClosed &= context.cache.isClosed();
                    allUnregistered &= !((List<?>)Evidence.field(raccoonman.reterraforged.concurrent.cache.CacheManager.class,"CACHES")).contains(Evidence.field(context.cache,"cache"));
                    DisposalChecks.awaitReturned(context);
                    live += DisposalChecks.stats(context,"cellPool").live()+DisposalChecks.stats(context,"chunkPool").live();
                }
                out.row("disposal","case","actual world unload","seed",currentSeed(),"worldIndex",worldIndex,"contexts",worldContexts.size(),"allClosed",allClosed,"allUnregistered",allUnregistered,"livePooledBorrows",live);
                if(task6bDisposedSource!=null){
                    var source=task6bDisposedSource;
                    if(source.distanceCacheStats().get("entries")!=0 || source.surfaceCacheStats().get("entries")!=0 || source.climateCacheStats().get("entries")!=0 || source.surfaceWinnerCacheStats().get("entries")!=0 || source.surfaceGeographyCacheStats().get("entries")!=0)throw new AssertionError("V1 caches retained after actual world unload");
                    try{source.sampleClimate(0,0);throw new AssertionError("Post-unload climate recreated");}catch(IllegalStateException expected){}
                    try{source.getNoiseBiome(0,80,0,null);throw new AssertionError("Post-unload source recreated");}catch(IllegalStateException expected){}
                    out.row("task6b_cache_disposal","distance",source.distanceCacheStats(),"postCloseQueriesRejected",true,"worldIndex",worldIndex,"reopened",task6Reopened,"surface",source.surfaceCacheStats(),"climate",source.climateCacheStats(),"winners",source.surfaceWinnerCacheStats(),"geography",source.surfaceGeographyCacheStats(),"allEmpty",true);
                    task6bDisposedSource=null;
                }
                worldContexts=List.of();
                if(task1c.equals("task6b")&&Boolean.getBoolean("task6b.final")&&worldIndex==0&&!task6Reopened){
                    task6Reopened=true;stage=2;mc.createWorldOpenFlows().loadLevel(mc.screen,run+"-seed0");return;
                }
                if(task1c.equals("task6-biomes")&&!task6Reopened){
                    task6Reopened=true;stage=2;mc.createWorldOpenFlows().loadLevel(mc.screen,run+"-seed0");return;
                }
                if(task1c.equals("foundation")) {
                    try{previousFoundationProvider.sample(0,0);throw new AssertionError("Provider sampled after shutdown");}catch(IllegalStateException expected){out.row("api_shutdown","reopened",foundationReopened,"rejected",true);}
                    if(!foundationReopened){foundationReopened=true;stage=2;mc.createWorldOpenFlows().loadLevel(mc.screen,run+"-seed0");return;}
                }
                if((full||chunksOnly||task1cRepeat()||task1c.equals("task6b"))&&++worldIndex<(task1c.equals("task6b")?2:task1c.equals("variance")?6:task1cRepeat()?4:full&&disabledBootstrapSucceeded?4:Math.min(3,seeds.length))){stage=0;}else{
                    out.row("completion","status","PASS","kind","reproduction observations, not correctness goldens","run",run);out.flush();
                    Files.writeString(mc.gameDirectory.toPath().resolve("task1a-pass.txt"),run+"\n");stage=99;RTFCommon.LOGGER.info("TASK1A PASS {}",run);mc.stop();
                }
            }
        }catch(Throwable ex){stage=99;RTFCommon.LOGGER.error("TASK1A FAIL",ex);try{out.row("completion","status","FAIL","error",Evidence.failure(ex));out.flush();}catch(Exception ignored){}mc.stop();}
    }
    private long currentSeed(){return task1c.equals("task6b")?8675309L:task1c.equals("variance")?seeds[worldIndex%3]:task1cRepeat()?8675309L:seeds[worldIndex<3?worldIndex:0];}
    private void disabledVegetation(ServerLevel level) {
        var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
        var placed=level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        var custom=placed.keySet().stream().filter(k->k.getNamespace().equals("reterraforged")&&k.getPath().endsWith("_trees")).map(Object::toString).sorted().toList();
        boolean bound=placed.holders().allMatch(h->h.isBound()&&h.value().feature().isBound());
        var biomes=level.registryAccess().registryOrThrow(Registries.BIOME);
        var ordinary=new LinkedHashMap<String,List<String>>();
        for(var key:List.of(net.minecraft.world.level.biome.Biomes.PLAINS,net.minecraft.world.level.biome.Biomes.FOREST,net.minecraft.world.level.biome.Biomes.DARK_FOREST)) {
            var features=biomes.getHolderOrThrow(key).value().getGenerationSettings().features().stream().flatMap(set->set.stream())
                .map(h->h.unwrapKey().orElseThrow().location().toString()).filter(k->k.contains("trees")||k.contains("dark_forest_vegetation")).toList();
            ordinary.put(key.location().toString(),features);
        }
        List<String> statuses=new ArrayList<>();
        for(int[] p:new int[][]{{0,0},{-129,-129},{127,127},{128,128},{-4032,-4096},{-2688,-4096},{-64,-4096},{3392,-3072}})
            statuses.add(level.getChunk(p[0]>>4,p[1]>>4).getStatus().toString());
        out.row("vegetation_disabled","case","actual disabled datapack world","loadAttempted",true,"loaded",true,
            "contextPresent",context!=null,"customTreeKeys",custom,"allPlacedHoldersBound",bound,"ordinaryTreeFeatures",ordinary,"chunkStatuses",statuses);
    }
    private void chunks(ServerLevel level) throws Exception {
        int[][] positions={{0,0},{-129,-129},{127,127},{128,128},{-4032,-4096},{-2688,-4096},{-64,-4096},{3392,-3072}};
        var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
        for(int[] p:positions){long start=System.nanoTime();var chunk=level.getChunk(p[0]>>4,p[1]>>4);
            List<String> blocks=new ArrayList<>(),biomes=new ArrayList<>(),storedBiomes=new ArrayList<>();List<Integer> heights=new ArrayList<>();
            for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                int wx=(p[0]>>4<<4)+x,wz=(p[1]>>4<<4)+z;heights.add(level.getHeight(Heightmap.Types.WORLD_SURFACE,wx,wz));
                for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++){
                    blocks.add(chunk.getBlockState(new BlockPos(wx,y,wz)).toString());
                    if((x&3)==0&&(z&3)==0&&(y&3)==0)biomes.add(level.getBiome(new BlockPos(wx,y,wz)).unwrapKey().orElseThrow().location().toString());
                    if((x&3)==0&&(z&3)==0&&(y&3)==0)storedBiomes.add(chunk.getNoiseBiome(wx>>2,y>>2,wz>>2).unwrapKey().orElseThrow().location().toString());
                }
            }
            Cell cell=new Cell();boolean cached=context.lookup.applyCell(cell,p[0],p[1],false,true);
            Cell direct=new Cell();context.generator.getHeightmap().apply(direct,p[0],p[1],true);
            Cell exact=new Cell();context.lookup.applyCell(exact,p[0],p[1],true,true);
            String blockSnapshot="chunk_blocks/"+level.getSeed()+"_"+(p[0]>>4)+"_"+(p[1]>>4)+".json.gz";
            out.snapshot(blockSnapshot,blocks);
            var rs=level.getChunkSource().randomState();var at=new DensityFunction.SinglePointContext(p[0],20,p[1]);
            out.row("generated_chunks","seed",level.getSeed(),"dimension",level.dimension().location().toString(),"x",p[0],"z",p[1],"status",chunk.getStatus().toString(),
                "surfaceHeights",heights,"blocksSHA256",Evidence.hash(blocks),"biomesSHA256",Evidence.hash(biomes),"postChunkLookup",Evidence.cell(cell,context.generator.getHeightmap()),
                "storedQuartBiomesSHA256",Evidence.hash(storedBiomes),
                "blockSnapshot",blockSnapshot,"minY",level.getMinBuildHeight(),"maxY",level.getMaxBuildHeight(),"blockOrder","z then x then y (y fastest)",
                "postChunkWasCached",cached,"direct",Evidence.cell(direct,context.generator.getHeightmap()),"exact",Evidence.cell(exact,context.generator.getHeightmap()),
                "postChunkVsDirect",Evidence.different(Evidence.cell(cell,null),Evidence.cell(direct,null)),"postChunkVsExact",Evidence.different(Evidence.cell(cell,null),Evidence.cell(exact,null)),
                "barrierNoise",rs.router().barrierNoise().compute(at),"veinToggle",rs.router().veinToggle().compute(at),"finalDensity",rs.router().finalDensity().compute(at),
                "elapsedNsIncludingHashing",System.nanoTime()-start);
        }
    }
}
