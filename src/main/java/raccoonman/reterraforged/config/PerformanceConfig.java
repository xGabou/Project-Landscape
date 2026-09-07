/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.config;

import java.nio.file.Path;

import com.mojang.serialization.DataResult;

import raccoonman.reterraforged.concurrent.ThreadPools;
import raccoonman.reterraforged.platform.ConfigUtil;

/**
 * Legacy scheduling settings. Tile geometry is generation semantics, NOT a performance knob.
 * Keep exponent 3 until a versioned backend explicitly owns other geometry. Preset-controlled
 * erosion halo semantics remain in GeneratorContext; they are not overridden here.
 */
public record PerformanceConfig(int tileSize, int batchCount, int threadCount) {
	public static final Path DEFAULT_FILE_PATH = ConfigUtil.rtf("performance_internal.conf");
	
    public static final int LEGACY_TILE_SIZE = 3;
    public static final int MAX_TILE_SIZE = LEGACY_TILE_SIZE;
    public static final int MAX_BATCH_COUNT = 20;
    public static final int MAX_THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;

    public PerformanceConfig {
        if (tileSize != LEGACY_TILE_SIZE) {
            throw new IllegalArgumentException("Legacy tile exponent is fixed at 3; changing geometry requires a generation-version boundary, not performance tuning (requested " + tileSize + ")");
        }
    }

    // Deliberately fixed defaults: do not implement geometry overrides without versioning.
    public static DataResult<PerformanceConfig> read(Path path) {
    	return DataResult.success(makeDefault());
    }
    
    public static PerformanceConfig makeDefault() {
        int tileSize = LEGACY_TILE_SIZE;
    	int batchCount = 6;
    	int threadCount = ThreadPools.availableProcessors();
    	return new PerformanceConfig(tileSize, batchCount, threadCount);
    }
}
