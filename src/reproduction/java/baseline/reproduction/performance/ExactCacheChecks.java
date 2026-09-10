/* Original concurrency and lifecycle regression. All Rights Reserved. */
package baseline.reproduction.performance;

import com.gabou.projectlandscape.generation.cache.ExactCache;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExactCacheChecks {
    public static void main(String[] args)throws Exception {
        Path out=Path.of(args[0]);java.nio.file.Files.createDirectories(out);var results=new ArrayList<Object>();
        for(int workers:new int[]{1,2,8,24,48}){
            var cache=new ExactCache<Long>(8);var pool=Executors.newFixedThreadPool(workers);var loads=new AtomicInteger();
            try {
                var start=new CountDownLatch(1);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
                var futures=new ArrayList<Future<Long>>();
                for(int i=0;i<workers;i++)futures.add(pool.submit(()->{start.await();return cache.get(7,k->{loads.incrementAndGet();entered.countDown();await(release);return k*31;});}));
                start.countDown();if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("Loader did not start");
                release.countDown();for(var f:futures)if(f.get(10,TimeUnit.SECONDS)!=217L)throw new AssertionError("Wrong exact value");
                if(loads.get()!=1)throw new AssertionError("Duplicate load");
                // Concurrent heavy churn; readers hold immutable values across eviction.
                var jobs=new ArrayList<Future<?>>();
                for(int worker=0;worker<workers;worker++){final int w=worker;jobs.add(pool.submit(()->{
                    for(int i=0;i<10000;i++){long key=(i*17L+w*13)%64;long value=cache.get(key,k->k*31);if(value!=key*31)throw new AssertionError("Churn corrupted value");}
                }));}
                for(var job:jobs)job.get(60,TimeUnit.SECONDS);
                if(cache.stats().get("entries")>8||cache.stats().get("inFlight")!=0)throw new AssertionError("Cache bounds");
                results.add(Map.of("workers",workers,"singleFlightLoads",loads.get(),"churnQueries",workers*10000,"stats",cache.stats(),"exact",true));
            } finally {cache.close();pool.shutdownNow();}
        }
        var retry=new ExactCache<Long>(2);try{retry.get(1,k->{throw new IllegalArgumentException("expected");});throw new AssertionError("Missing failure");}catch(IllegalArgumentException expected){}
        if(retry.get(1,k->2L)!=2||retry.stats().get("inFlight")!=0)throw new AssertionError("Poisoned failure");
        var a=new ExactCache<Long>(2);var b=new ExactCache<Long>(2);
        if(a.get(1,k->11L)!=11||b.get(1,k->22L)!=22)throw new AssertionError("Cross-world key contamination");
        a.close();try{a.get(1,k->33L);throw new AssertionError("Reused closed world");}catch(IllegalStateException expected){}
        var recreated=new ExactCache<Long>(2);if(recreated.get(1,k->44L)!=44)throw new AssertionError("Recreated world contamination");
        var pending=new ExactCache<Long>(1);var executor=Executors.newSingleThreadExecutor();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try{
            Future<?> f=executor.submit(()->pending.get(1,k->{entered.countDown();await(release);return 1L;}));
            if(!entered.await(5,TimeUnit.SECONDS))throw new AssertionError("Pending loader");pending.close();release.countDown();
            try{f.get(5,TimeUnit.SECONDS);throw new AssertionError("Disposed pending query returned");}catch(ExecutionException expected){if(!(expected.getCause() instanceof IllegalStateException))throw expected;}
            if(pending.stats().get("entries")!=0||pending.stats().get("inFlight")!=0)throw new AssertionError("Post-disposal repopulation");
        }finally{release.countDown();executor.shutdownNow();}
        WorldgenBenchmark.write(out,"determinism.json",Map.of("workerChecks",results,"failureRetry",true,"worldIsolation",true,"recreatedWorld",true,"inFlightDisposal",true,
                "scope","exact cache concurrency mechanics; real terrain worker determinism is a separate gate"));
        retry.close();b.close();recreated.close();
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
