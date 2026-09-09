/* Original Task 6B development benchmark. All Rights Reserved. */
package baseline.reproduction.task6b;

import baseline.reproduction.Evidence;
import baseline.reproduction.Metrics;
import com.gabou.atmospheregen.biome.ClimateBiomeSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import java.lang.management.ManagementFactory;
import java.util.*;

public final class FullChunkPerformance {
    public static void run(ServerLevel level,Evidence out,int worldIndex)throws Exception {
        var source=level.getChunkSource().getGenerator().getBiomeSource();
        if((source instanceof ClimateBiomeSource)!=(worldIndex==0))throw new AssertionError("Benchmark world version");
        String version=worldIndex==0?"V1":"legacy";
        var os=(com.sun.management.OperatingSystemMXBean)ManagementFactory.getOperatingSystemMXBean();
        var threads=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        try(var recording=new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"))){
            recording.start();
            // Global block coordinates fixed before either version is measured. Categories are observed below.
            int[][] points={{8192,0},{8208,0},{8224,0},{8240,0},{6128,6240},{6144,6240},
                    {-28672,-12288},{-28656,-12288},{-8192,-150000},{8192,60000},{-8192,-500000},{0,-2000000}};
            int index=0;
            for(int[] p:points){
                int cx=p[0]>>4,cz=p[1]>>4;
                boolean preexisting=level.getChunkSource().getChunkNow(cx,cz)!=null;
                var before=Metrics.snapshot();long alloc=allocated(threads),cpu=os.getProcessCpuTime(),start=System.nanoTime();
                var chunk=level.getChunk(cx,cz);
                long wall=System.nanoTime()-start,cpuNs=os.getProcessCpuTime()-cpu,bytes=allocated(threads)-alloc;
                if(chunk.getStatus()!=net.minecraft.world.level.chunk.ChunkStatus.FULL)throw new AssertionError("Expected FULL");
                var blocks=new ArrayList<String>();var biomes=new ArrayList<String>();var pos=new BlockPos.MutableBlockPos();
                for(int z=0;z<16;z++)for(int x=0;x<16;x++)for(int y=level.getMinBuildHeight();y<level.getMaxBuildHeight();y++)
                    blocks.add(chunk.getBlockState(pos.set(cx*16+x,y,cz*16+z)).toString());
                for(int z=0;z<4;z++)for(int x=0;x<4;x++)for(int y=level.getMinBuildHeight()>>2;y<level.getMaxBuildHeight()>>2;y++)
                    biomes.add(chunk.getNoiseBiome(cx*4+x,y,cz*4+z).unwrapKey().orElseThrow().location().toString());
                out.row("task6b_full_chunks","version",version,"index",index++,"blockX",p[0],"blockZ",p[1],"preexistingFull",preexisting,
                        "wallNanos",wall,"processCpuNanos",cpuNs,"liveThreadAllocationDeltaBytes",bytes,"workerCount",Runtime.getRuntime().availableProcessors(),
                        "blocksDigest",Evidence.hash(blocks),"biomesDigest",Evidence.hash(biomes),"beforeCounters",before,"afterCounters",Metrics.snapshot(),
                        "scope","FULL plus prerequisite neighbors; hashing excluded; feature-order variance must be assessed separately");
                out.flush();System.out.println("TASK6B FULL "+version+" "+index+"/"+points.length+" wallMs="+wall/1e6);
            }
            // Repeat X/Z at several Y values as Minecraft quart population does; preserve cave delegation.
            var sampler=level.getChunkSource().randomState().sampler();var winners=new ArrayList<String>();
            long start=System.nanoTime(),cpu=os.getProcessCpuTime(),alloc=allocated(threads);
            for(int pass=0;pass<4;pass++)for(int y=0;y<24;y++)for(int z=0;z<4;z++)for(int x=0;x<4;x++)
                winners.add(source.getNoiseBiome((6128>>2)+x,y,(6240>>2)+z,sampler).unwrapKey().orElseThrow().location().toString());
            out.row("task6b_quart_population","version",version,"queries",winners.size(),"uniqueXZ",16,"wallNanos",System.nanoTime()-start,
                    "processCpuNanos",os.getProcessCpuTime()-cpu,"liveThreadAllocationDeltaBytes",allocated(threads)-alloc,"digest",Evidence.hash(winners));
            recording.stop();
            var path=java.nio.file.Path.of("evidence","task6b-"+version+"-"+System.currentTimeMillis()+".jfr");
            java.nio.file.Files.createDirectories(path.getParent());recording.dump(path);
            out.row("task6b_profiles","version",version,"path",path.toAbsolutePath().toString());out.flush();
        }
        if(source instanceof ClimateBiomeSource climateSource){
            out.row("task6b_cache_metrics","phase","after paired corpus","surface",climateSource.surfaceCacheStats(),"climate",climateSource.climateCacheStats(),"geography",climateSource.surfaceGeographyCacheStats(),"winners",climateSource.surfaceWinnerCacheStats());
            LocalityTrace.enabled=true;
            try{level.getChunk((6128>>4)+3,(6240>>4)+3);}finally{LocalityTrace.flush(out);}
            out.row("task6b_cache_metrics","phase","after locality chunk","surface",climateSource.surfaceCacheStats(),"climate",climateSource.climateCacheStats(),"geography",climateSource.surfaceGeographyCacheStats(),"winners",climateSource.surfaceWinnerCacheStats());out.flush();
        }
    }
    private static long allocated(com.sun.management.ThreadMXBean bean){long sum=0;for(long value:bean.getThreadAllocatedBytes(bean.getAllThreadIds()))if(value>0)sum+=value;return sum;}
}
