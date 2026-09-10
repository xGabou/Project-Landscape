/* Original Task 6B untimed exact-output gate. All Rights Reserved. */
package baseline.reproduction.task6b;

import baseline.reproduction.Evidence;
import com.gabou.projectlandscape.biome.ClimateBiomeSource;
import com.gabou.projectlandscape.geography.terrain.PaGeographyProvider;
import net.minecraft.server.level.ServerLevel;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import java.util.*;

/** Runs after the entire timed corpus so validation cannot warm later timed V1 requests. */
public final class FullOutputEvidence {
    public static void run(ServerLevel level,Evidence out)throws Exception {
        if(!(level.getChunkSource().getGenerator().getBiomeSource() instanceof ClimateBiomeSource source))return;
        var context=((RTFRandomState)(Object)level.getChunkSource().randomState()).generatorContext();
        var geography=new PaGeographyProvider(context);
        int[][] points={{8192,0},{8208,0},{8224,0},{8240,0},{6128,6240},{6144,6240},
            {-28672,-12288},{-28656,-12288},{-8192,-150000},{8192,60000},{-8192,-500000},{0,-2000000},{-7969,-8369},{-1734,-8369}};
        for(var p:points){
            var g=geography.sample(p[0],p[1]);var climate=source.sampleClimate(p[0],p[1]);
            var tile=context.cache.provide(p[0]>>7,p[1]>>7);var values=new ArrayList<Object>();
            tile.iterate((cell,x,z)->{values.add(Evidence.cell(cell,null));values.add(new int[]{
                Float.floatToRawIntBits(cell.mountainChainSelector()),Float.floatToRawIntBits(cell.mountainChainContribution()),
                Float.floatToRawIntBits(cell.regionalMountainContribution())});});
            var chunk=level.getChunk(p[0]>>4,p[1]>>4);
            long start=System.nanoTime();var loaded=level.getChunk(p[0]>>4,p[1]>>4);long nanos=System.nanoTime()-start;
            if(loaded!=chunk)throw new AssertionError("Loaded chunk identity changed");
            out.row("task6b_full_outputs","blockX",p[0],"blockZ",p[1],"geography",exact(g),"tileDigest",Evidence.hash(values),
                "climateRawBits",List.of(Double.doubleToRawLongBits(climate.meanTemperatureCelsius()),
                Double.doubleToRawLongBits(climate.annualRainfallMm()),Double.doubleToRawLongBits(climate.ecologicalMoistureIndex()),
                Double.doubleToRawLongBits(climate.potentialEvaporationMm()),Double.doubleToRawLongBits(climate.rainShadow()),
                Double.doubleToRawLongBits(climate.prevailingWind().orElseThrow().x()),Double.doubleToRawLongBits(climate.prevailingWind().orElseThrow().z())),
                "macroShorelineProfileBlocks",geography.macroProvider().sampleMacro(p[0],p[1]).shorelineProfileBlocks(),
                "surfaceBiome",source.getNoiseBiome(p[0]>>2,level.getMaxBuildHeight()>>2,p[1]>>2,level.getChunkSource().randomState().sampler()).unwrapKey().orElseThrow().location().toString(),
                "alreadyLoadedWallNanos",nanos);
            out.flush();
        }
    }
    private static Object exact(Object value)throws Exception {
        if(value instanceof Optional<?> optional)return optional.isPresent()?exact(optional.get()):null;
        if(value instanceof Double d)return Double.doubleToRawLongBits(d);
        if(value instanceof Float f)return Float.floatToRawIntBits(f);
        if(value instanceof Enum<?> e)return e.name();
        if(value!=null&&value.getClass().isRecord()){
            var fields=new TreeMap<String,Object>();
            for(var field:value.getClass().getRecordComponents())fields.put(field.getName(),exact(field.getAccessor().invoke(value)));
            return fields;
        }
        return value;
    }
}
