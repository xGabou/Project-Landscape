/* Original portable Task 3 API benchmark. All Rights Reserved.
 * Compiles unchanged against accepted Task 2 and Task 3; no access to new record fields. */
package baseline.reproduction;

import java.lang.management.ManagementFactory;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import com.gabou.atmospheregen.api.geography.GeoSample;
import com.gabou.atmospheregen.compat.legacy.LegacyRtfGeographyAdapter;

/** Result escapes through volatile storage: do not benchmark only an optimized-away elevation read. */
public final class PublicProviderBenchmark {
    private static volatile GeoSample sink;
    public static void run(ServerLevel level,Evidence out) {
        var provider=LegacyRtfGeographyAdapter.forLevel(level);provider.sample(0,0);
        var mx=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        if(!mx.isThreadAllocatedMemoryEnabled())mx.setThreadAllocatedMemoryEnabled(true);
        long tid=Thread.currentThread().getId();int count=65536;
        for(int repeat=-3;repeat<5;repeat++) {
            long[] samples=new long[count/16];long allocated=mx.getThreadAllocatedBytes(tid),start=System.nanoTime();
            for(int i=0;i<count;i++) {
                long before=System.nanoTime();sink=provider.sample((i*37)&127,(i*71)&127);
                long duration=System.nanoTime()-before;if((i&15)==0)samples[i/16]=duration;
            }
            long elapsed=System.nanoTime()-start,bytes=mx.getThreadAllocatedBytes(tid)-allocated;
            Arrays.sort(samples);
            out.row("public_provider_benchmark","repeat",repeat,"warmup",repeat<0,"queries",count,"elapsedNs",elapsed,
                "throughput",count*1_000_000_000.0/elapsed,"callerAllocatedBytes",bytes,"bytesPerQuery",(double)bytes/count,
                "medianNs",samples[samples.length/2],"p95Ns",samples[(int)Math.ceil(samples.length*.95)-1],
                "scope","warm canonical public immutable sample; escapes via volatile; 3 warmup + 5 measured, includes timer overhead",
                "seed",Long.toString(level.getSeed()),"java",System.getProperty("java.runtime.version"));
        }
    }
}
