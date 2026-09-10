/* Original development integration assertions. All Rights Reserved. */
package baseline.reproduction.biome;

import baseline.reproduction.Evidence;
import com.gabou.projectlandscape.compat.legacy.V1TerraBlenderData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.block.Blocks;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import terrablender.api.SurfaceRuleManager;

final class Task6TerraBlenderChecks {
    static void run(ServerLevel level,Evidence out,boolean reopened)throws Exception{
        var manifest=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext().generationContext().manifest();
        var before=V1TerraBlenderData.noiseSettings(level.registryAccess());
        if(!before.equals(manifest.content().data().get("registry/minecraft:worldgen/noise_settings")))throw new AssertionError("TB fingerprint changed after initialization");
        try{
            SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD,"task6_validation_only",SurfaceRules.state(Blocks.DIAMOND_BLOCK.defaultBlockState()));
            if(before.equals(V1TerraBlenderData.noiseSettings(level.registryAccess())))throw new AssertionError("TB rules omitted from fingerprint");
        }finally{SurfaceRuleManager.removeSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD,"task6_validation_only");}
        if(!before.equals(V1TerraBlenderData.noiseSettings(level.registryAccess())))throw new AssertionError("TB rule fixture not restored");
        out.row("terrablender_fingerprint","reopened",reopened,"stableAfterInitialization",true,"registeredRuleChangeDetected",true,"fixtureRestored",true,"fingerprint",before.sha256());
        out.flush();
    }
}
