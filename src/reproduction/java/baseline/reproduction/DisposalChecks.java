/* Original developer correctness tests. All Rights Reserved. */
package baseline.reproduction;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;
import raccoonman.reterraforged.concurrent.Resource;
import raccoonman.reterraforged.concurrent.cache.*;
import raccoonman.reterraforged.concurrent.pool.ArrayPool;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;

final class DisposalChecks {
    static boolean closed(Tile tile) throws Exception {
        return !((Resource<?>)Evidence.field(tile,"cacheResource")).isOpen();
    }
    static ArrayPool.Statistics stats(GeneratorContext c, String pool) throws Exception {
        return ((ArrayPool<?>)Evidence.field(c.generator,pool)).statistics();
    }
    static void awaitReturned(GeneratorContext c) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(stats(c,"cellPool").live()!=0 || stats(c,"chunkPool").live()!=0) {
            if(System.nanoTime()>deadline)throw new AssertionError("Workspace resources did not converge after teardown");
            Thread.sleep(10);
        }
    }
    static void run(Evidence out, Supplier<GeneratorContext> contexts) throws Exception {
        var c=contexts.get();
        Cache<CacheEntry<Tile>> cache=CacheManager.createCache(1,60,20,TimeUnit.SECONDS);
        Tile first=c.generator.generate(0,0).join(); var firstValue=Evidence.cell(first.lookup(0,0),null);
        var entry=cache.computeIfAbsent(0,k->CacheEntry.supply(CompletableFuture.completedFuture(first)));
        Tile next=c.generator.generate(1,0).join();
        var replacement=cache.computeIfAbsent(1,k->CacheEntry.supply(CompletableFuture.completedFuture(next)));
        out.row("disposal","case","capacity eviction without lazy get","firstClosed",closed(first),
            "readerUnchanged",firstValue.equals(Evidence.cell(first.lookup(0,0),null)));
        // A stale entry must not remove a replacement installed under the same key.
        boolean wrongRemoved=cache.remove(1,entry);
        out.row("disposal","case","identity conditional removal","wrongEntryRemoved",wrongRemoved,"replacementPresent",cache.get(1)==replacement);
        cache.close();cache.close();
        out.row("disposal","case","explicit cache shutdown","closed",closed(next),"unregistered",!((List<?>)Evidence.field(CacheManager.class,"CACHES")).contains(cache));
        try {cache.computeIfAbsent(2,k->entry);throw new AssertionError("Closed cache admitted new work");}
        catch(IllegalStateException expected){out.row("disposal","case","closed cache rejects new work","error",Evidence.failure(expected));}

        var pending=new CompletableFuture<Tile>();
        Cache<CacheEntry<Tile>> queued=CacheManager.createCache(1,60,20,TimeUnit.SECONDS);
        queued.computeIfAbsent(0,k->CacheEntry.supply(pending));queued.close();
        var late=c.generator.generate(2,0).join();pending.complete(late);
        out.row("disposal","case","pending completion after shutdown","closed",closed(late));

        var failed=CacheEntry.supply(CompletableFuture.<Tile>failedFuture(new IllegalStateException("controlled failed future")));
        failed.close(); failed.close();
        var cancelled=new CompletableFuture<Tile>();var cancelledEntry=CacheEntry.supply(cancelled);cancelledEntry.close();cancelled.cancel(false);
        out.row("disposal","case","failed and cancelled entry close","failedDone",failed.isDone(),"cancelled",cancelled.isCancelled());

        int accepted=0;
        for(int i=0;i<12;i++)if(c.generator.generate(i,4).cancel(false))accepted++;
        awaitReturned(c);
        out.row("disposal","case","cancelled real tile workspaces","cancelled",accepted,"cells",stats(c,"cellPool"),"chunks",stats(c,"chunkPool"));

        var method=c.generator.getClass().getDeclaredMethod("makeTile",int.class,int.class);method.setAccessible(true);
        Tile old=(Tile)method.invoke(c.generator,0,0);old.close();Tile working=(Tile)method.invoke(c.generator,1,0);
        working.getCellRaw(16,16).height=.75F;old.close();boolean unchanged=working.getCellRaw(16,16).height==.75F;
        working.close();working.close();
        out.row("disposal","case","stale workspace close after pool reuse","unchanged",unchanged,"cells",stats(c,"cellPool"),"chunks",stats(c,"chunkPool"));

        var pool=ArrayPool.of(1,Integer[]::new);var a=pool.get(2);var b=pool.get(2);
        a.close();a.close();var newer=pool.get(2);a.close();boolean safe=newer.isOpen();b.close();newer.close();newer.close();
        out.row("disposal","case","array handles and full pool","staleHandleSafe",safe,"allClosed",!a.isOpen()&&!b.isOpen()&&!newer.isOpen(),"statistics",pool.statistics());
        c.cache.close();awaitReturned(c);
    }
}
