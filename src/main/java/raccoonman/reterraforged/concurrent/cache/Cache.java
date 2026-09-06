/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.concurrent.cache;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.function.LongFunction;

import raccoonman.reterraforged.concurrent.cache.map.LongMap;

public class Cache<V extends ExpiringEntry> implements AutoCloseable {
	public static final ScheduledExecutorService SCHEDULER = makeScheduler();

	private static ScheduledExecutorService makeScheduler() {
		var executor = new java.util.concurrent.ScheduledThreadPoolExecutor(1, (r) -> {
		Thread thread = new Thread(r);
		thread.setName("CacheScheduler");
		return thread;
		});
		executor.setRemoveOnCancelPolicy(true);
		return executor;
	}
	
    private LongMap<V> map;
    private long lifetimeMS;
    private volatile long timeout;
    private ScheduledFuture<?> poll;
    private volatile boolean closed;
    
    public Cache(int capacity, long expireTime, long pollInterval, TimeUnit unit, IntFunction<LongMap<V>> mapFunc) {
        this.timeout = 0L;
        this.map = mapFunc.apply(capacity);
        this.lifetimeMS = unit.toMillis(expireTime);
        
        long intervalMillis = unit.toMillis(pollInterval);
        this.poll = SCHEDULER.scheduleAtFixedRate(this::poll, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }
    
    public void remove(long key) {
        this.map.remove(key, ExpiringEntry::close);
    }

    public boolean remove(long key, V expected) {
        return this.map.remove(key, expected, ExpiringEntry::close);
    }

    public V getIfOpen(long key) {
        return this.closed ? null : this.map.get(key);
    }
    
    public V get(long key) {
        this.requireOpen();
        return this.map.get(key);
    }
    
    public synchronized V computeIfAbsent(long key, LongFunction<V> func) {
        this.requireOpen();
        return this.map.computeIfAbsent(key, func);
    }
    
    public void poll() {
        if (this.closed) return;
        this.timeout = System.currentTimeMillis() - this.lifetimeMS;
        this.map.removeIf((entry) -> entry.getTimestamp() < this.timeout);
    }

	@Override
	public synchronized void close() {
		if (this.closed) return;
		this.closed = true;
		this.poll.cancel(false);
		try { this.map.clear(); } finally { CacheManager.unregister(this); }
	}

	public boolean isClosed() { return this.closed; }

	private void requireOpen() {
		if (this.closed) throw new IllegalStateException("ReTerraForged cache is closed; generation context has been disposed");
	}
}
