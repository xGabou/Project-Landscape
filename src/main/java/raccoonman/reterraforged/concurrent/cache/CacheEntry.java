/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.concurrent.cache;

import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;

import raccoonman.reterraforged.concurrent.task.LazyCallable;

public class CacheEntry<T> extends LazyCallable<T> implements ExpiringEntry {
	private volatile long timestamp;
	private Future<T> task;
	private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();

	public CacheEntry(Future<T> task) {
		this.task = task;
		this.timestamp = System.currentTimeMillis();
	}

	@Override
	public T get() {
		this.timestamp = System.currentTimeMillis();
		return super.get();
	}

	@Override
	public boolean isDone() {
		return this.task.isDone();
	}

	@Override
	public long getTimestamp() {
		return this.timestamp;
	}

	@Override
	public void close() {
		if (!this.closed.compareAndSet(false, true)) return;
		// A queued future need not have been read through LazyCallable yet.
		if (this.task instanceof java.util.concurrent.CompletableFuture<T> future) {
			future.thenAccept(this::dispose);
		} else {
			Runnable release = () -> {
				try { this.dispose(this.task.get()); }
				catch (java.util.concurrent.CancellationException | java.util.concurrent.ExecutionException ignored) { }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
			};
			if (this.task.isDone()) release.run();
			else java.util.concurrent.CompletableFuture.runAsync(release);
		}
	}

	private void dispose(T completed) {
		if (completed instanceof SafeCloseable value) {
			value.close();
			return;
		}
		if (completed instanceof AutoCloseable value) {
			try {
				value.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected T create() {
		if (this.task instanceof ForkJoinTask<T> task) {
			return task.join();
		}
		try {
			return this.task.get();
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public static <T> CacheEntry<T> supply(Future<T> task) {
		return new CacheEntry<>(task);
	}
}
