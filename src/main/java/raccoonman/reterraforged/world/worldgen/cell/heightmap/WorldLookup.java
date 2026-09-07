/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.world.worldgen.cell.heightmap;

import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache;

public class WorldLookup {
	// Identity only: retaining this token cannot retain an entire world on a worker thread.
	// A newly constructed lookup (including same-seed contexts) always gets a new token.
	private final Object samplingIdentity = new Object();
	private float waterLevel;
	private float beachLevel;
	private TileCache cache;
	private Heightmap heightmap;
	private Levels levels;
	
	public WorldLookup(GeneratorContext context) {
		this.cache = context.cache;
		this.heightmap = context.generator.getHeightmap();
		this.waterLevel = context.levels.water;
		this.beachLevel = context.levels.water(5);
		this.levels = context.levels;
	}
	
	public Heightmap getHeightmap() {
		return this.heightmap;
	}

	public Object samplingIdentity() {
		return this.samplingIdentity;
	}

	public boolean applyCell(Cell cell, int x, int z, boolean applyClimate) {
		return this.applyCell(cell, x, z, false, applyClimate);
	}

	public boolean applyCell(Cell cell, int x, int z, boolean load, boolean applyClimate) {
		if (load && this.computeAccurate(cell, x, z)) {
			return true;
		}
		if (this.computeCached(cell, x, z)) {
			return true;
		}
		return this.compute(cell, x, z, applyClimate);
	}

	private boolean computeAccurate(Cell cell, int x, int z) {
		if (this.cache == null) {
			throw new IllegalStateException("RTF filtered tile query at " + x + "," + z
					+ " requires a cached generation context; this lookup is direct-only");
		}
		int rx = this.cache.chunkToTile(x >> 4);
		int rz = this.cache.chunkToTile(z >> 4);
		Tile tile = this.cache.provide(rx, rz);
		Cell c = tile.lookup(x, z);
		if (c != null) {
			cell.copyFrom(c);
		}
		return cell.terrain != null;
	}

	private boolean computeCached(Cell cell, int x, int z) {
		Tile tile = this.cachedTile(x, z);
		if (tile != null) {
			Cell c = tile.lookup(x, z);
			if (c != null) {
				cell.copyFrom(c);
			}
			return cell.terrain != null;
		}
		return false;
	}

	/** Current opportunistic source; may join an already queued tile, never queues one. */
	public Tile cachedTile(int x, int z) {
		if (this.cache == null) return null;
		int rx = this.cache.chunkToTile(x >> 4);
		int rz = this.cache.chunkToTile(z >> 4);
		return this.cache.provideIfPresent(rx, rz);
	}

	private boolean compute(Cell cell, int x, int z, boolean applyClimate) {
		this.sampleDirectApproximate(cell, x, z, applyClimate);
		return false;
	}

	/** Legacy unfiltered point path, including its point-only coast adjustment. */
	public void sampleDirectApproximate(Cell cell, int x, int z, boolean applyClimate) {
		if (this.cache != null && this.cache.isClosed()) {
			throw new IllegalStateException("Cannot sample RTF direct approximation at " + x + "," + z + ": generation context is closed");
		}
		this.heightmap.apply(cell, x, z, applyClimate);
		if (cell.terrain == TerrainType.COAST && cell.height > this.waterLevel && cell.height <= this.beachLevel) {
			cell.terrain = TerrainType.BEACH;
		}
	}
}
