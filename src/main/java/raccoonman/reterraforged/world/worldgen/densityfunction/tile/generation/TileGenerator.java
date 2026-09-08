/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.world.worldgen.densityfunction.tile.generation;

import java.util.concurrent.CompletableFuture;
import com.gabou.atmospheregen.geography.GeographyPipeline;

import raccoonman.reterraforged.concurrent.ThreadPools;
import raccoonman.reterraforged.concurrent.pool.ArrayPool;
import raccoonman.reterraforged.world.worldgen.WorldFilters;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Heightmap;
import raccoonman.reterraforged.world.worldgen.cell.rivermap.Rivermap;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Size;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile.Chunk;

public class TileGenerator {
	private Heightmap heightmap;
	private WorldFilters filters;
	private final GeographyPipeline<Cell, Rivermap, Tile> geography;
	private ArrayPool<Cell> cellPool;
	private ArrayPool<Chunk> chunkPool;
	private int tileChunks;
	private int tileBorder;
	private Size tileSizeBlocks;
	private Size tileSizeChunks;
	private int batchSize;
	private int batchCount;
	
	public TileGenerator(Heightmap heightmap, WorldFilters filters, int tileChunks, int tileBorder, int batchCount) {
		this.heightmap = heightmap;
		this.filters = filters;
		// Retain the real filter slot for failure-injection/lifecycle tests. No per-cell lambda.
		this.geography = new GeographyPipeline<>(heightmap.continentStage(), heightmap.terrainStage(),
			heightmap.hydrologyStage(), heightmap.legacyParameters(), (tile, optional) -> this.filters.apply(tile, optional));
		this.cellPool = ArrayPool.of(100, (length) -> {
			Cell[] cells = new Cell[length];
			for(int i = 0; i < cells.length; i++) {
				cells[i] = new Cell();
			}
			return cells;
		});
		this.chunkPool = ArrayPool.of(100, Chunk[]::new);
		this.tileChunks = tileChunks;
		this.tileBorder = tileBorder;
		this.tileSizeBlocks = Size.blocks(tileChunks, tileBorder);
		this.tileSizeChunks = Size.chunks(tileChunks, tileBorder);
		this.batchSize = getBatchSize(batchCount, this.tileSizeChunks);
		this.batchCount = batchCount;
	}
	
	public Heightmap getHeightmap() {
		return this.heightmap;
	}
	
	public GeographyPipeline<Cell, Rivermap, Tile> geographyPipeline() { return this.geography; }

	public CompletableFuture<Tile> generate(int tileX, int tileZ) {
		Tile tile = this.makeTile(tileX, tileZ);
		CompletableFuture<?>[] futures = new CompletableFuture<?>[this.batchCount * this.batchCount];
		try {
		for (int batchZ = 0; batchZ < this.batchCount; batchZ++) {
			for (int batchX = 0; batchX < this.batchCount; batchX++) {
				int chunkX = batchX * this.batchSize;
				int chunkZ = batchZ * this.batchSize;
				futures[batchX * this.batchCount + batchZ] = CompletableFuture.runAsync(() -> {
			        int maxX = Math.min(this.tileSizeChunks.total(), chunkX + this.batchSize);
			        int maxZ = Math.min(this.tileSizeChunks.total(), chunkZ + this.batchSize);
		            for (int cZ = chunkZ; cZ < maxZ; cZ++) {
		            	for (int cX = chunkX; cX < maxX; cX++) {
			            	Chunk chunk = tile.getChunkWriter(cX, cZ);
			            	
			                Rivermap rivers = null;
	                    	for (int dz = 0; dz < 16; dz++) {
	                    		for (int dx = 0; dx < 16; dx++) {
		                    		int worldX = chunk.getBlockX() + dx;
		                    		int worldZ = chunk.getBlockZ() + dz;
		                    		Cell cell = chunk.getCell(dx, dz);
		                    		
			                        rivers = this.geography.generatePoint(cell, worldX, worldZ, rivers, true);
			                    }
			                }
			            }
			        }
				}, ThreadPools.WORLD_GEN);
	        }
	    }
		} catch (Throwable failure) { return this.finish(tile, futures, true, failure); }
		return this.finish(tile, futures, true, null);
	}
	
	/** Compatibility name; this is transformed preview geometry, never owning-tile canonical geography. */
	@Deprecated
	public CompletableFuture<Tile> generateZoomed(float centerX, float centerZ, float zoom, boolean applyOptionalFilters) {
		return this.generatePreviewApproximate(centerX,centerZ,zoom,applyOptionalFilters);
	}

	/** Legacy transformed preview grid; optional filtering does not make it canonical world geography. */
	public CompletableFuture<Tile> generatePreviewApproximate(float centerX, float centerZ, float zoom, boolean applyOptionalFilters) {
		Tile tile = this.makeTile(0, 0);
		CompletableFuture<?>[] futures = new CompletableFuture<?>[this.batchCount * this.batchCount];
		try {
        float translateX = centerX - this.tileSizeBlocks.size() * zoom / 2.0F;
        float translateZ = centerZ - this.tileSizeBlocks.size() * zoom / 2.0F;
		for (int batchZ = 0; batchZ < this.batchCount; batchZ++) {
			for (int batchX = 0; batchX < this.batchCount; batchX++) {
				int chunkX = batchX * this.batchSize;
				int chunkZ = batchZ * this.batchSize;
				futures[batchX * this.batchCount + batchZ] = CompletableFuture.runAsync(() -> {
			        int maxX = Math.min(this.tileSizeChunks.total(), chunkX + this.batchSize);
			        int maxZ = Math.min(this.tileSizeChunks.total(), chunkZ + this.batchSize);
			        for (int cZ = chunkZ; cZ < maxZ; cZ++) {
			            for (int cX = chunkX; cX < maxX; cX++) {
			            	Chunk chunk = tile.getChunkWriter(cX, cZ);
			            	
			                Rivermap rivers = null;
	                    	for (int dz = 0; dz < 16; dz++) {
	                    		for (int dx = 0; dx < 16; dx++) {
		                    		float worldX = (chunk.getBlockX() + dx) * zoom + translateX;
		                    		float worldZ = (chunk.getBlockZ() + dz) * zoom + translateZ;
		                    		Cell cell = chunk.getCell(dx, dz);
		                    		
			                        rivers = this.geography.generatePoint(cell, worldX, worldZ, rivers, true);
			                    }
			                }
			            }
			        }
				}, ThreadPools.WORLD_GEN);
	        }
	    }
		} catch (Throwable failure) { return this.finish(tile, futures, applyOptionalFilters, failure); }
		return this.finish(tile, futures, applyOptionalFilters, null);
	}
    
	private CompletableFuture<Tile> finish(Tile workspace, CompletableFuture<?>[] tasks,
			boolean optionalFilters, Throwable submissionFailure) {
		CompletableFuture<Tile> result = new CompletableFuture<>();
		// Cancellation of the public result does not interrupt writers. Drain every
		// accepted batch before returning its arrays, even after partial submission.
		CompletableFuture.allOf(java.util.Arrays.stream(tasks).filter(java.util.Objects::nonNull)
				.toArray(CompletableFuture<?>[]::new)).whenComplete((unused, taskFailure) -> {
			Throwable failure = submissionFailure != null ? submissionFailure : taskFailure;
			Tile snapshot = null;
			try {
				if (failure == null && !result.isCancelled()) {
					this.geography.finalizeTile(workspace, optionalFilters);
					snapshot = workspace.snapshot();
				}
			} catch (Throwable thrown) { failure = thrown; }
			finally { workspace.close(); }
			if (failure != null) result.completeExceptionally(failure);
			else if (snapshot != null && !result.complete(snapshot)) snapshot.close();
		});
		return result;
	}

	private Tile makeTile(int x, int z) {
		var cells = this.cellPool.get(this.tileSizeBlocks.arraySize());
		raccoonman.reterraforged.concurrent.Resource<Chunk[]> chunks = null;
		try {
			chunks = this.chunkPool.get(this.tileSizeChunks.arraySize());
			return new Tile(x, z, this.tileChunks, this.tileBorder, this.tileSizeBlocks, this.tileSizeChunks, cells, chunks);
		} catch (Throwable failure) {
			cells.close();
			if (chunks != null) chunks.close();
			throw failure;
		}
	}
	
    private static int getBatchSize(int batchCount, Size chunkSize) {
        int batchSize = chunkSize.total() / batchCount;
        if (batchSize * batchCount < chunkSize.total()) {
            ++batchSize;
        }
        return batchSize;
    }
}
