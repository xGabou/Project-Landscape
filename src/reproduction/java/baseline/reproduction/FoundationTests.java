/* Original development tests. All Rights Reserved. */
package baseline.reproduction;
import com.gabou.atmospheregen.config.*;
import com.gabou.atmospheregen.generation.version.*;
import com.gabou.atmospheregen.generation.seed.*;
import com.gabou.atmospheregen.generation.context.*;
import com.gabou.atmospheregen.persistence.*;
import com.gabou.atmospheregen.compat.legacy.LegacyPresetSnapshot;
import com.mojang.serialization.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
public final class FoundationTests {
    public static void runManifestChecks(ServerLevel level,Evidence out)throws Exception { manifests(level,out); }
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
        reject(out,"fractional integer config",ConfigCodecs.integer("resolution",1,100),"1.5");
        reject(out,"overflowing integer config",ConfigCodecs.integer("resolution",1,100),"4294967297");
        reject(out,"no geography config section",WorldGeographyConfig.CODEC,"{}");
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}) {
            var result=ConfigCodecs.finite("test",0,1).parse(JsonOps.INSTANCE,new com.google.gson.JsonPrimitive(value));
            if(result.error().isEmpty())throw new AssertionError("Accepted nonfinite/out of range");
            out.row("foundation_invalid","case","finite range "+value,"error",result.error().get().message());
        }
        GenerationVersions.legacy().requireFunctionalBackend();
        GenerationVersions.planned().requireFunctionalBackend();
        out.row("foundation_functional","case","V1 geography climate biome tuple","status","accepted");
        seeds(out);
        manifests(level,out);
        api(out);
        binding(level,out);
    }
    private static void binding(ServerLevel level,Evidence out) throws Exception {
        var legacy=((raccoonman.reterraforged.world.worldgen.RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
        var context=legacy.generationContext();
        if(context==null||context.runtimeToken()!=legacy.lookup.samplingIdentity())throw new AssertionError("World context not bound to cache token");
        var emptyObjectHash=GenerationFingerprint.of(new CanonicalJson("{}"));
        for(String key:List.of("nbt_resources","worldgen_json_resources"))if(emptyObjectHash.equals(context.manifest().content().data().get(key)))throw new AssertionError("Default world resource capture is empty: "+key);
        for(var dim:List.of(net.minecraft.world.level.Level.NETHER,net.minecraft.world.level.Level.END)) {
            var other=((raccoonman.reterraforged.world.worldgen.RTFRandomState)(Object)level.getServer().getLevel(dim).getChunkSource().randomState()).generatorContext();
            if(other!=null&&other.generationContext()!=null)throw new AssertionError("Companion forced into foreign dimension");
        }
        var provider=com.gabou.atmospheregen.compat.legacy.LegacyRtfGeographyAdapter.forLevel(level);
        int[][] points={{0,0},{-1,-1},{127,128},{-129,-128},{128,128},{80000,-80000}};
        for(int[] point:points) {
            int x=point[0],z=point[1];var a=provider.sample(x,z);var b=provider.sample(x,z);
            var expected=new raccoonman.reterraforged.world.worldgen.cell.Cell();legacy.lookup.applyCell(expected,x,z,true,true);
            if(!a.equals(b)||a.elevationBlockY()!=(double)(expected.height*legacy.levels.worldHeight))throw new AssertionError("Canonical provider used noncanonical height");
            if(!provider.hydrology().sample(x,z).equals(a.hydrology()))throw new AssertionError("Hydrology semantics");
            out.row("canonical_provider","x",x,"z",z,"elevationBlockY",a.elevationBlockY(),"seaRelativeElevationBlocks",a.seaRelativeElevationBlocks(),"water",a.water().name(),"landform",a.landform().name(),"mountainInfluenceKnown",a.metrics().mountainInfluence().isPresent(),"cacheWarmEqual",true,"filteredHeightEqual",true);
        }
        int x=80000,z=-80000;var before=provider.sample(x,z);
        for(int i=0;i<64;i++)legacy.cache.drop(x>>7,z>>7);
        if(legacy.cache.provideIfPresent(x>>7,z>>7)!=null)throw new AssertionError("Test did not evict tile");
        if(!before.equals(provider.sample(x,z)))throw new AssertionError("Provider changed on eviction/reload");
        var reload=level.getServer().reloadResources(level.getServer().getPackRepository().getSelectedIds());
        if(!reload.isCompletedExceptionally())throw new AssertionError("Live reload bypasses frozen generation manifest");
        out.row("world_binding_checks","canonicalEvictionEqual",true,"netherEndUnbound",true,"reloadRejectedBeforeApplication",true,"runtimeTokenBoundToLegacyLookup",true,"manifest",GenerationManifestStore.encode(context.manifest()));
    }
    private static void api(Evidence out) {
        var metrics=com.gabou.atmospheregen.api.geography.GeographyMetrics.unknown();
        var hydrology=new com.gabou.atmospheregen.api.geography.HydrologySample(com.gabou.atmospheregen.api.geography.WaterCategory.LAND,false,false,false,Optional.empty(),Optional.empty());
        var sample=new com.gabou.atmospheregen.api.geography.GeoSample(new com.gabou.atmospheregen.api.geography.BlockPosition(-1,-129),64,1,hydrology.water(),com.gabou.atmospheregen.api.geography.Landform.PLAINS,metrics,hydrology);
        if(sample.metrics().mountainInfluence().isPresent()||sample.hydrology().segmentIdentity().isPresent())throw new AssertionError("Unknown geography fabricated");
        List<Runnable> invalid=List.of(
            ()->new com.gabou.atmospheregen.api.geography.Metric(Double.NaN,com.gabou.atmospheregen.api.geography.Metric.Quality.MODELLED,1),
            ()->new com.gabou.atmospheregen.api.geography.Metric(1,com.gabou.atmospheregen.api.geography.Metric.Quality.MODELLED,0),
            ()->new com.gabou.atmospheregen.api.climate.WindDirection(0,0),
            ()->new com.gabou.atmospheregen.api.climate.ClimateBaseline(10,-1,.5,1,.5,Optional.empty()));
        for(Runnable check:invalid)try{check.run();throw new AssertionError("Invalid API value accepted");}catch(IllegalArgumentException expected){}
        try{UnavailableGenerationServices.plannedGeography().sample(0,0);throw new AssertionError("Unimplemented geography fallback");}catch(UnsupportedOperationException expected){}
        try{UnavailableGenerationServices.baselineClimate().sample(0,0);throw new AssertionError("RTF hints used as physical climate");}catch(UnsupportedOperationException expected){}
        out.row("api_contracts","unknownMetricsAbsent",true,"invalidValuesRejected",invalid.size(),"unavailableGeographyAndClimateFail",true,"negativeSpatialKey",Long.toString(sample.position().spatialKey()));
    }
    private static void manifests(ServerLevel level,Evidence out) throws Exception {
        var preset=((raccoonman.reterraforged.world.worldgen.RTFRandomState)(Object)level.getChunkSource().randomState()).preset();
        var legacy=LegacyPresetSnapshot.capture(preset);
        var config=new WorldGeographyConfig(Optional.of(legacy),Optional.empty());
        var checksum=GenerationFingerprint.of(legacy.effectivePreset());
        var fixtureRoot=java.nio.file.Path.of(System.getProperty("task1c.fixtureFile")).getParent();
        var identity=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(fixtureRoot.resolve("golden-24/identity.json"))).getAsJsonArray().get(0).getAsJsonObject();
        if(!legacy.effectivePreset().equals(CanonicalJson.of(identity.get("effectivePreset"))))throw new AssertionError("Effective legacy manifest settings changed from Task 1C");
        var content=new ManifestContent(GenerationVersions.legacy(),level.getSeed(),level.dimension().location(),config,
            new BaselineClimateConfig(Optional.empty()),new BiomeResolverConfig(Optional.empty()),Map.of("test:effective_preset",checksum),Optional.empty());
        var manifest=GenerationManifest.create(content);
        var catalog=new com.gabou.atmospheregen.biome.VanillaBiomeCatalog();
        var planned=GenerationManifest.create(new ManifestContent(GenerationVersions.planned(),level.getSeed(),level.dimension().location(),
            new WorldGeographyConfig(Optional.empty(),Optional.of(new PlannedGeographySettings(16384,1000,1200,2000,.5,Optional.of(MacroGeographySettings.defaults())))),
            new BaselineClimateConfig(Optional.of(new BaselineClimateConfig.Planned(100000,.0065,.5,.5))),
            new BiomeResolverConfig(Optional.of(new BiomeResolverConfig.Planned(64,1))),content.data(),Optional.of(catalog.fingerprint())));
        roundtrip(out,"planned V1 manifest",GenerationManifest.CODEC,planned);
        roundtrip(out,"world geography config",WorldGeographyConfig.CODEC,config);
        roundtrip(out,"fingerprint",GenerationFingerprint.CODEC,checksum);
        roundtrip(out,"manifest",GenerationManifest.CODEC,manifest);
        roundtrip(out,"large seed",ManifestContent.SEED_CODEC,Long.MIN_VALUE);
        roundtrip(out,"dimension",net.minecraft.resources.ResourceLocation.CODEC,level.dimension().location());
        var a=new WorldGenerationContext(manifest,level.dimension());var b=new WorldGenerationContext(manifest,level.dimension());
        if(!a.contextId().equals(b.contextId())||a.runtimeToken()==b.runtimeToken())throw new AssertionError("Persistent/runtime context identity");
        try{new WorldGenerationContext(manifest,net.minecraft.world.level.Level.NETHER);throw new AssertionError("Wrong dimension accepted");}
        catch(IllegalArgumentException expected){out.row("foundation_invalid","case","context dimension mismatch","error",expected.getMessage());}
        var ordered=com.google.gson.JsonParser.parseString("{\"b\":2,\"a\":1.0}");
        var reversed=com.google.gson.JsonParser.parseString("{\"a\":1,\"b\":2.0}");
        if(!GenerationFingerprint.of(CanonicalJson.of(ordered)).equals(GenerationFingerprint.of(CanonicalJson.of(reversed))))throw new AssertionError("Unordered fingerprint");
        if(GenerationFingerprint.of(new CanonicalJson("-0.0")).equals(GenerationFingerprint.of(new CanonicalJson("0.0"))))throw new AssertionError("Signed zero fingerprint collision");
        for(double nonfinite:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            var invalid=CanonicalJson.CODEC.parse(JsonOps.INSTANCE,new com.google.gson.JsonPrimitive(nonfinite));
            if(invalid.error().isEmpty())throw new AssertionError("Nonfinite fingerprint value accepted");
            out.row("foundation_invalid","case","nonfinite fingerprint number "+nonfinite,"error",invalid.error().get().message());
        }
        var encoded=com.google.gson.JsonParser.parseString(GenerationManifestStore.encode(manifest)).getAsJsonObject();
        encoded.addProperty("fingerprint","0".repeat(64));reject(out,"changed fingerprint",GenerationManifest.CODEC,encoded.toString());
        reject(out,"malformed geography config",WorldGeographyConfig.CODEC,"{\"legacy\":{\"effectivePreset\":{},\"tileExponent\":4,\"borderChunks\":1}}");
        var directory=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("."),"foundation-manifest-");
        var path=GenerationManifestStore.path(directory);
        var plannedResolution=GenerationManifestStore.resolve(directory.resolve("planned.json"),Optional.of(planned)).orElseThrow();
        if(plannedResolution.kind()!=GenerationManifestStore.ResolutionKind.EXPLICIT_V1_BIOME_ASSIGNMENT)
            throw new AssertionError("V1 manifest resolution kind");
        var pc=planned.content();
        var changedCatalog=GenerationManifest.create(new ManifestContent(pc.versions(),pc.worldSeed(),pc.dimension(),
                pc.geography(),pc.baselineClimate(),pc.biomeResolver(),pc.data(),
                Optional.of(GenerationFingerprint.of(new CanonicalJson("{\"changedCatalog\":true}")))));
        try{GenerationManifestStore.resolve(directory.resolve("planned.json"),Optional.of(changedCatalog));throw new AssertionError("Changed biome catalog accepted");}
        catch(IllegalStateException expected){out.row("foundation_invalid","case","V1 catalog mismatch","error",expected.getMessage());}
        if(!GenerationManifestStore.read(directory.resolve("planned.json")).equals(planned))throw new AssertionError("Catalog mismatch overwrote manifest");
        if(GenerationManifestStore.resolve(path,Optional.empty()).isPresent()||java.nio.file.Files.exists(path))throw new AssertionError("Foreign world assigned defaults");
        var first=GenerationManifestStore.resolve(path,Optional.of(manifest)).orElseThrow();
        var reopen=GenerationManifestStore.resolve(path,Optional.of(manifest)).orElseThrow();
        if(first.kind()!=GenerationManifestStore.ResolutionKind.EXPLICIT_LEGACY_ASSIGNMENT||reopen.kind()!=GenerationManifestStore.ResolutionKind.EXISTING_MANIFEST)throw new AssertionError("Explicit legacy ownership");
        if(!reopen.manifest().equals(manifest))throw new AssertionError("Persistence changed manifest");
        try{GenerationManifestStore.resolve(path,Optional.empty());throw new AssertionError("Manifest on foreign generator accepted");}
        catch(IllegalStateException expected){out.row("foundation_invalid","case","foreign generator with manifest","error",expected.getMessage());}
        var changed=GenerationManifest.create(new ManifestContent(content.versions(),42L,content.dimension(),config,content.baselineClimate(),content.biomeResolver(),content.data(),Optional.empty()));
        try{GenerationManifestStore.resolve(path,Optional.of(changed));throw new AssertionError("Changed world seed accepted");}
        catch(IllegalStateException expected){out.row("foundation_invalid","case","world seed mismatch","error",expected.getMessage());}
        if(!GenerationManifestStore.read(path).equals(manifest))throw new AssertionError("Mismatch overwrote manifest");
        out.row("manifest_roundtrip","fixtureVersion",1,"manifest",GenerationManifestStore.encode(manifest),"roundtrip",true,"contextId",a.contextId(),"separateRuntimeTokens",true,"canonicalOrdering",true,"effectivePresetMatchesTask1C",true);
        out.row("existing_world_resolution","recognition","isolated store test with real active legacy preset; automatic binding is tested separately in world_binding_checks and runtime smoke","foreignWithoutManifest","untouched","first",first.kind().name(),"reopen",reopen.kind().name(),"mismatch","rejected without overwrite");
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
            out.row("seed_collision_legacy_vs_new","domain",domain.id(),"legacyIntA",(int)8675309L,"legacyIntB",(int)4303642605L,"newA",Long.toString(a.seed(domain)),"newB",Long.toString(b.seed(domain)),"newDiverges",true,"legacyTerrainProof","separate golden profile and Verify-Goldens.ps1 check all 85 legacy collision fixtures");
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
