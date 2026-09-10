/* Original Task 3 developer verification. All Rights Reserved. */
package baseline.reproduction;

import java.nio.file.*;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import com.gabou.projectlandscape.compat.legacy.LegacyRtfGeographyAdapter;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.CellPopulator;
import raccoonman.reterraforged.world.worldgen.cell.terrain.Blender;
import raccoonman.reterraforged.world.worldgen.noise.module.Noises;

/** New measurements protect new output independently from the immutable 24-field V0 cell corpus. */
public final class GeographyChecks {
    public static void run(ServerLevel level,Evidence out) throws Exception {
        var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
        var provider=LegacyRtfGeographyAdapter.forLevel(level);
        var fixture=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("task1c.fixtureFile")))).getAsJsonObject();
        for(var sf:fixture.getAsJsonArray("seedFixtures")) {
            if(sf.getAsJsonObject().get("seed").getAsLong()!=level.getSeed())continue;
            for(var p:sf.getAsJsonObject().getAsJsonArray("points")){
                var point=Evidence.JSON.fromJson(p,ReproductionSuite.Point.class);int x=point.x(),z=point.z();
                var a=provider.sample(x,z);var b=provider.sample(x,z);
                if(!a.equals(b))throw new AssertionError("Canonical public warmth changed");
                Cell c=context.cache.provide(x>>7,z>>7).lookup(x,z);
                if(a.elevationBlockY()!=(double)(c.height*context.levels.worldHeight))throw new AssertionError("Canonical elevation");
                if(a.metrics().slope().isPresent() || a.metrics().localRelief().isPresent() || a.metrics().continentality().isPresent())throw new AssertionError("Unproven physical metric fabricated");
                var signals=a.legacyTerrainSignals().orElseThrow();
                if(signals.landValue()!=c.continentEdge || signals.mountainChainContribution()!=c.mountainChainContribution()
                        || signals.regionalMountainContribution()!=c.regionalMountainContribution())throw new AssertionError("Captured geography transport");
                if(!provider.hydrology().sample(x,z).equals(a.hydrology()))throw new AssertionError("Canonical hydrology");
                out.row("provider_samples","seed",Long.toString(level.getSeed()),"point",point,"context",context.generationContext().contextId(),
                    "geography",Map.of("elevationBlockY",a.elevationBlockY(),"seaRelativeElevationBlocks",a.seaRelativeElevationBlocks(),
                        "water",a.water().name(),"landform",a.landform().name(),"landValue",signals.landValue(),
                        "terrainRegionSelector",signals.terrainRegionSelector(),"slopeAvailable",false,"reliefAvailable",false),
                    "legacyCellStateAndRouterReads",Evidence.cell(c,context.generator.getHeightmap()),
                    "legacyMinecraftParameters",Map.of("erosion",c.erosion,"weirdness",c.weirdness,"temperature",c.temperature,
                        "moisture",c.moisture,"biomeRegion",c.biomeRegionId,"biomeType",c.biome.name()));
                out.row("mountain_samples","point",point,"chainSelector",signals.mountainChainSelector().map(m->m.value()).orElse(null),
                    "chain",signals.mountainChainContribution(),"regional",signals.regionalMountainContribution(),
                    "combined",a.metrics().mountainInfluence().orElseThrow().value());
                out.row("hydrology_samples","point",point,"water",a.hydrology().water().name(),"river",a.hydrology().river(),
                    "lake",a.hydrology().lake(),"wetland",a.hydrology().wetland(),
                    "valleyInfluence",a.hydrology().riverValleyInfluence().map(m->m.value()).orElse(null));
            }
        }
        var retained=provider.sample(127,-129);
        for(int i=0;i<64;i++)context.cache.drop(0,-2);
        var regenerated=provider.sample(127,-129);
        if(!retained.equals(regenerated))throw new AssertionError("New geography fields changed after eviction/recycle");
        Cell copy=new Cell();Cell source=context.cache.provide(0,-2).lookup(127,-129);copy.copyFrom(source);
        if(Float.floatToRawIntBits(copy.mountainChainSelector())!=Float.floatToRawIntBits(source.mountainChainSelector()))throw new AssertionError("Mountain snapshot copy");
        copy.reset();if(!Float.isNaN(copy.mountainChainSelector()) || copy.mountainChainContribution()!=0 || copy.regionalMountainContribution()!=0)throw new AssertionError("Mountain reset");
        for(float selector:new float[]{0.2F,0.55F,0.9F}) {
            int[] calls={0,0};
            CellPopulator lower=(c,x,z)->{calls[0]++;c.height=10;c.regionalMountainContribution(1);};
            CellPopulator upper=(c,x,z)->{calls[1]++;c.height=20;c.regionalMountainContribution(0);};
            Cell c=new Cell();new Blender(Noises.constant(selector),lower,upper,.3F,.8F,.575F).apply(c,0,0);
            float alpha=selector<.3F?0:selector>.8F?1:(selector-.3F)/(.8F-.3F);
            if(c.mountainChainSelector()!=selector || c.mountainChainContribution()!=alpha || c.regionalMountainContribution()!=1-alpha)throw new AssertionError("Captured blend weight");
            if(calls[0]!=(alpha==1?0:1) || calls[1]!=(alpha==0?0:1))throw new AssertionError("Extra terrain graph evaluation");
            out.row("mountain_blend_checks","selector",selector,"chain",alpha,"regional",c.regionalMountainContribution(),"lowerCalls",calls[0],"upperCalls",calls[1]);
        }
        out.row("geography_checks","status","PASS","publicRows",85,"detachedEvictionRegeneration",true,"copyReset",true,
            "slopeRelief","explicitly unavailable; legacy gradient is not slope","pipeline",context.generator.geographyPipeline().getClass().getName());
    }
}
