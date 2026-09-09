/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.generation.cache;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.LongFunction;

/** Instance-scoped exact-key LRU. Loaders run outside the monitor; completed values hold no futures. */
public final class ExactCache<V> implements AutoCloseable {
    private record Flight<V>(Thread owner, CompletableFuture<V> result) {}
    private final int capacity;
    private final LinkedHashMap<Long,V> completed = new LinkedHashMap<>(16,.75f,true);
    private final HashMap<Long,Flight<V>> pending = new HashMap<>();
    private boolean closed;
    private long hits, misses, waits, evictions, failures;

    public ExactCache(int capacity) {
        if(capacity<1)throw new IllegalArgumentException("Positive cache capacity required");
        this.capacity=capacity;
    }

    /** Loaders must not recursively enter this same cache. Different caches may compose. */
    public V get(long key, LongFunction<V> loader) {
        Flight<V> flight; boolean owner;
        synchronized(this) {
            for(;;) {
                requireOpen();
                V value=completed.get(key);
                if(value!=null){hits++;return value;}
                flight=pending.get(key);
                if(flight!=null){
                    if(flight.owner()==Thread.currentThread())throw new IllegalStateException("Recursive exact cache load");
                    waits++;owner=false;break;
                }
                if(pending.size()<capacity){
                    flight=new Flight<>(Thread.currentThread(),new CompletableFuture<>());
                    pending.put(key,flight);misses++;owner=true;break;
                }
                try {wait();} catch(InterruptedException interrupted) {
                    Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted waiting for cache capacity",interrupted);
                }
            }
        }
        if(owner) {
            try {
                V value=Objects.requireNonNull(loader.apply(key),"Exact cache loader returned null");
                synchronized(this) {
                    if(pending.remove(key,flight)&&!closed) {
                        if(completed.size()>=capacity){completed.remove(completed.keySet().iterator().next());evictions++;}
                        completed.put(key,value);
                    }
                    notifyAll();
                }
                flight.result().complete(value);
            } catch(Throwable failure) {
                synchronized(this){pending.remove(key,flight);failures++;notifyAll();}
                flight.result().completeExceptionally(failure);
            }
        }
        try {return flight.result().join();}
        catch(CompletionException failed) {
            if(failed.getCause() instanceof RuntimeException runtime)throw runtime;
            if(failed.getCause() instanceof Error error)throw error;
            throw failed;
        }
    }

    public synchronized Map<String,Long> stats() {
        return Map.of("entries",(long)completed.size(),"capacity",(long)capacity,"hits",hits,"misses",misses,
                "waits",waits,"evictions",evictions,"failures",failures,"inFlight",(long)pending.size());
    }

    public void clear(){discard(false);}
    @Override public void close(){discard(true);}
    private void discard(boolean closing) {
        List<Flight<V>> abandoned;
        synchronized(this) {
            closed|=closing;completed.clear();abandoned=List.copyOf(pending.values());pending.clear();notifyAll();
        }
        for(var flight:abandoned)flight.result().completeExceptionally(new IllegalStateException("Exact cache cleared or disposed during load"));
    }
    private void requireOpen(){if(closed)throw new IllegalStateException("Generation cache is disposed");}
}
