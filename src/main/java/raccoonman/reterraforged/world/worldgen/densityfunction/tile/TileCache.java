/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.world.worldgen.densityfunction.tile;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import raccoonman.reterraforged.concurrent.cache.Cache;
import raccoonman.reterraforged.concurrent.cache.CacheEntry;
import raccoonman.reterraforged.concurrent.cache.CacheManager;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.generation.TileGenerator;
import raccoonman.reterraforged.world.worldgen.util.PosUtil;

public class TileCache implements TileFactory, AutoCloseable {
	private int tileSize;
	private boolean queue;
	private Cache<CacheEntry<Entry>> cache;
	private TileGenerator generator;
    private final java.util.List<Runnable> disposalActions = new java.util.ArrayList<>();
	
	public TileCache(int tileSize, boolean queue, TileGenerator generator) {
		this.tileSize = tileSize;
		this.queue = queue;
		this.cache = CacheManager.createCache(256, 60L, 20L, TimeUnit.SECONDS);
		this.generator = generator;
	}
	
	public TileGenerator getGenerator() {
		return this.generator;
	}

	@Override
	public void close() {
        java.util.List<Runnable> actions;
        synchronized(this) {
            this.cache.close();
            actions=java.util.List.copyOf(this.disposalActions);this.disposalActions.clear();
        }
        for(Runnable action:actions)action.run();
    }

    /** Context-owned detached services share the terrain cache's disposal boundary. */
    public void onClose(Runnable action) {
        synchronized(this) {
            if(!this.cache.isClosed()){this.disposalActions.add(java.util.Objects.requireNonNull(action));return;}
        }
        action.run();
    }

	public boolean isClosed() { return this.cache.isClosed(); }
	
	@Nullable
	public Tile provideIfPresent(int tileX, int tileZ) {
		@Nullable
		CacheEntry<Entry> entry = this.cache.get(PosUtil.pack(tileX, tileZ));
		return entry != null ? entry.get().tile : null;
	}

	@Nullable
	public Tile provideAtChunkIfPresent(int chunkX, int chunkZ) {
		return this.provideIfPresent(this.chunkToTile(chunkX), this.chunkToTile(chunkZ));
	}

	@Override
	public Tile provide(int tileX, int tileZ) {
		return this.computeEntry(tileX, tileZ).get().tile;
	}
	
	@Override
	public void queue(int tileX, int tileZ) {
		if(this.queue) {
			this.computeEntry(tileX, tileZ);
		}
	}

	@Override
	public void drop(int tileX, int tileZ) {
		if (this.cache.isClosed()) return;
		long packedTilePos = PosUtil.pack(tileX, tileZ);
		//TODO i dont think get should be able to return null here
		CacheEntry<Entry> entry = this.cache.getIfOpen(packedTilePos);
		if(entry != null && entry.get().drop()) {
			this.cache.remove(packedTilePos, entry);
		}
	}

	@Override
	public int chunkToTile(int chunkCoord) {
		return chunkCoord >> this.tileSize;
	}
	
	private CacheEntry<Entry> computeEntry(int tileX, int tileZ) {
		return this.cache.computeIfAbsent(PosUtil.pack(tileX, tileZ), (k) -> {
			return CacheEntry.supply(this.generator.generate(tileX, tileZ).thenApply(Entry::new));
		});
	}
	
	private class Entry implements raccoonman.reterraforged.concurrent.cache.SafeCloseable {
		private AtomicInteger refCount;
		private int chunkCount;
		private Tile tile;
		
		public Entry(Tile tile) {
			int tileSize = tile.getChunksSize().size();
			this.refCount = new AtomicInteger();
			this.chunkCount = tileSize * tileSize;
			this.tile = tile;
		}
		
		public boolean drop() {
			if(this.refCount.incrementAndGet() == this.chunkCount) {
				this.tile.close();
				return true;
			} else {
				return false;
			}
		}

		@Override
		public void close() { this.tile.close(); }
	}
}
