/* Test orchestration derived from ReTerraForged, Copyright (c) 2023 ReTerraForged,
 * MIT License. See LICENSE. Kept in developer source only; no competing production backend. */
package baseline.reproduction;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.level.ServerLevel;
import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;

/** Frozen Task 2 point ordering; stage observations complement, never replace, final goldens. */
public final class StageBaseline {
    public static void run(ServerLevel level, Evidence out) throws Exception {
        run(level,out,false);
    }
    public static void run(ServerLevel level, Evidence out, boolean staged) throws Exception {
        var preset=((RTFRandomState)(Object)level.getChunkSource().randomState()).preset();
        var noises=level.registryAccess().lookupOrThrow(RTFRegistries.NOISE);
        var fixture=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("task1c.fixtureFile")))).getAsJsonObject();
        out.row("stage_identity","schemaVersion",1,"source","203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7",
            "backend","LEGACY_RTF_V0","fixtureVersion",1,"staged",staged,"configurationFingerprint",fixture.get("configurationFingerprint").getAsString(),
            "semantics","Point stages are unfiltered; finalized is owning filtered tile; exact raw float bits",
            "java",System.getProperty("java.runtime.version"),"dimension",level.dimension().location().toString());
        for(var seedFixture:fixture.getAsJsonArray("seedFixtures")) {
            var sf=seedFixture.getAsJsonObject();long seed=sf.get("seed").getAsLong();
            var context=GeneratorContext.makeCached(preset,noises,(int)seed,3,6,false);
            try {
                var h=context.generator.getHeightmap();var pipeline=context.generator.geographyPipeline();int ordinal=0;
                for(var value:sf.getAsJsonArray("points")) {
                    var p=Evidence.JSON.fromJson(value,ReproductionSuite.Point.class);Cell cell=new Cell();
                    if(staged)pipeline.continent().apply(cell,p.x(),p.z());
                    else {cell.terrain=TerrainType.FLATS;cell.beachNoise=h.beachNoise().compute(p.x(),p.z(),0);h.continent().apply(cell,p.x(),p.z());}
                    capture(out,seed,ordinal,p,"continent",cell,h);
                    if(staged)pipeline.terrain().apply(cell,p.x(),p.z());
                    else {h.region().apply(cell,p.x(),p.z());h.terrain().apply(cell,p.x()*h.terrainFrequency(),p.z()*h.terrainFrequency());}
                    capture(out,seed,ordinal,p,"terrain",cell,h);
                    if(staged)pipeline.hydrology().apply(cell,p.x(),p.z(),null);
                    else h.applyRivers(cell,p.x(),p.z(),h.continent().getRivermap(cell));
                    capture(out,seed,ordinal,p,"hydrology",cell,h);
                    if(staged)pipeline.legacyCompatibility().apply(cell,p.x(),p.z(),true);
                    else h.applyClimate(cell,p.x(),p.z(),true);
                    capture(out,seed,ordinal,p,"legacy_compatibility",cell,h);
                    Cell combined=new Cell();h.apply(combined,p.x(),p.z(),true);
                    if(!Evidence.different(Evidence.cell(cell,h),Evidence.cell(combined,h)).isEmpty())throw new AssertionError("Stage capture changed point order");
                    context.lookup.applyCell(cell.reset(),p.x(),p.z(),true,true);
                    capture(out,seed,ordinal++,p,"finalized",cell,h);
                }
            } finally {context.cache.close();}
        }
    }
    private static void capture(Evidence out, long seed, int ordinal, ReproductionSuite.Point point, String stage, Cell cell,
                                raccoonman.reterraforged.world.worldgen.cell.heightmap.Heightmap h) {
        out.row("stage_samples","seed",Long.toString(seed),"ordinal",ordinal,"point",point,"stage",stage,"fields",Evidence.cell(cell,h));
        out.row("geography_capture","seed",Long.toString(seed),"ordinal",ordinal,"point",point,"stage",stage,
            "chainSelectorBits",Float.floatToRawIntBits(cell.mountainChainSelector()),
            "chainContributionBits",Float.floatToRawIntBits(cell.mountainChainContribution()),
            "regionalContributionBits",Float.floatToRawIntBits(cell.regionalMountainContribution()));
    }
}
