/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.world.worldgen.densityfunction;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.SectionPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;
import raccoonman.reterraforged.world.worldgen.biome.Continentalness;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.ControlPoints;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Heightmap;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Levels;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.WorldLookup;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainCategory;
import raccoonman.reterraforged.world.worldgen.densityfunction.CellSampler.Field;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.Tile;
import raccoonman.reterraforged.world.worldgen.noise.NoiseUtil;
import raccoonman.reterraforged.world.worldgen.util.PosUtil;

public record CellSampler(Supplier<WorldLookup> deferredLookup, Field field) implements MarkerFunction.Mapped {
	private static final ThreadLocal<Cache2d> CELL = ThreadLocal.withInitial(Cache2d::new);
	
	@Override
	public double compute(FunctionContext ctx) {
		WorldLookup worldLookup = this.deferredLookup.get();
		Cell cell = CELL.get().getAndUpdate(worldLookup, ctx.blockX(), ctx.blockZ(), true);
		return this.field.read(cell, worldLookup.getHeightmap());
	}

	@Override
	public double minValue() {
		return 0.0F;
	}

	@Override
	public double maxValue() {
		return 1.0F;
	}

	public static class Cache2d {
		private Object lastIdentity;
		private Object lastTileIdentity;
		private boolean lastSampleClimate;
		private long lastPos = Long.MAX_VALUE;
		private Cell cell = new Cell();
		
		public Cell getAndUpdate(WorldLookup lookup, int blockX, int blockZ, boolean sampleClimate) {
			long packedPos = PosUtil.pack(blockX, blockZ);
			// Observe the source once. A second lookup could select a different contract
			// during publication/eviction. Null denotes the legacy direct approximation.
			Tile tile = lookup.cachedTile(blockX, blockZ);
			Object tileIdentity = tile == null ? null : tile.samplingIdentity();
			if(this.lastIdentity != lookup.samplingIdentity() || this.lastPos != packedPos
					|| this.lastTileIdentity != tileIdentity || this.lastSampleClimate != sampleClimate) {
				// Invalidate before sampling: a failed lookup must not leave a valid old key.
				this.lastIdentity = null;
				this.cell.reset();
				if (tile == null) {
					lookup.sampleDirectApproximate(this.cell, blockX, blockZ, sampleClimate);
				} else {
					this.cell.copyFrom(tile.lookup(blockX, blockZ));
				}
				this.lastPos = packedPos;
				this.lastIdentity = lookup.samplingIdentity();
				this.lastTileIdentity = tileIdentity;
				this.lastSampleClimate = sampleClimate;
			}
			return this.cell;
		}
	}
	
	public class CacheChunk implements MarkerFunction.Mapped {
		@Nullable
		private Tile.Chunk chunk;
		private Cache2d cache2d;
		private int chunkX, chunkZ;
		
		public CacheChunk(@Nullable Tile.Chunk chunk, @Nullable Cache2d cache2d, int chunkX, int chunkZ) {
			this.chunk = chunk;
			this.cache2d = cache2d != null ? cache2d : new Cache2d();
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
		}

		@Override
		public double compute(FunctionContext ctx) {
			int blockX = ctx.blockX();
			int blockZ = ctx.blockZ();
			int chunkX = SectionPos.blockToSectionCoord(blockX);
			int chunkZ = SectionPos.blockToSectionCoord(blockZ);
			WorldLookup worldLookup = CellSampler.this.deferredLookup.get();
			Cell cell = (this.chunk != null && this.chunkX == chunkX && this.chunkZ == chunkZ) ? 
				this.chunk.getCell(blockX, blockZ) :
				this.cache2d.getAndUpdate(worldLookup, blockX, blockZ, false);
			return this.structureRiverFix(cell, CellSampler.this.field.read(cell, worldLookup.getHeightmap()));
		}

		@Override
		public double minValue() {
			return CellSampler.this.minValue();
		}

		@Override
		public double maxValue() {
			return CellSampler.this.maxValue();
		}
		
		private float structureRiverFix(Cell cell, float value) {
			if(CellSampler.this.field == Field.HEIGHT) {
				if(cell.riverMask < 0.1F) {
					return value;
				}
			}
			return value;
		}
	}
	
	public record Marker(Field field) implements MarkerFunction {
		public static final Codec<Marker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Field.CODEC.fieldOf("field").forGetter(Marker::field)
		).apply(instance, Marker::new));
		
		@Override
		public KeyDispatchDataCodec<Marker> codec() {
			return new KeyDispatchDataCodec<>(CODEC);
		}

		@Override
		public DensityFunction mapAll(Visitor visitor) {
			return visitor.apply(this);
		}
	}
	
	public enum Field implements StringRepresentable {
		HEIGHT("height") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.height;
			}
		},
		CONTINENT("continent") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return raccoonman.reterraforged.world.worldgen.cell.geography.LegacyMinecraftParameterAdapter.continentalness(cell, heightmap.levels(), heightmap.controlPoints());
			}
		},
		EROSION("erosion") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.erosion;
			}
		},
		WEIRDNESS("weirdness") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.weirdness;
			}
		},
		BIOME_REGION("biome_region") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.biomeRegionId;
			}
		},
		TEMPERATURE("temperature") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.temperature;
			}
		},
		MOISTURE("moisture") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.moisture;
			}
		},
		GRADIENT("gradient") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.gradient;
			}
		},
		HEIGHT_EROSION("height_erosion") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.heightErosion;
			}
		},
		SEDIMENT("sediment") {
			
			@Override
			public float read(Cell cell, Heightmap heightmap) {
				return cell.sediment;
			}
		};

		public static final Codec<Field> CODEC = StringRepresentable.fromEnum(Field::values);
		
		private String name;
		
		private Field(String name) {
			this.name = name;
		}
		
		@Override
		public String getSerializedName() {
			return this.name;
		}
		
		public abstract float read(Cell cell, Heightmap heightmap);
	}
}
