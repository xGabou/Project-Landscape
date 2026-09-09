/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License. See LICENSE. */
package raccoonman.reterraforged.mixin;

import raccoonman.reterraforged.registries.RTFRegistries;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.ServerLevelData;
import raccoonman.reterraforged.data.worldgen.preset.settings.Preset;

@Mixin(MinecraftServer.class)
class MixinMinecraftServer {

	// Task 2 freeze policy: changing live tags/templates could bypass the persisted generation manifest.
	// Initial/new-world resource loading is unaffected; vanilla-only servers retain normal /reload.
	@Inject(method = "reloadResources", at = @At("HEAD"), cancellable = true)
	private void atmospheregen$guardFrozenGenerationReload(java.util.Collection<String> packs,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> callback) {
		for (ServerLevel level : ((MinecraftServer)(Object)this).getAllLevels()) {
			var state = (RTFRandomState)(Object)level.getChunkSource().randomState();
			var context = state.generatorContext();
			if (context != null && context.generationContext() != null) {
				callback.setReturnValue(java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException(
					"Cannot reload datapacks while a manifest-bound LEGACY_RTF_V0 world is open (" + level.dimension().location()
					+ "). Restart with unchanged generation data; changed data requires explicit versioned migration, not /reload.")));
				return;
			}
		}
	}

	@Inject(
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/biome/Climate$Sampler;findSpawnPosition()Lnet/minecraft/core/BlockPos;"
		),
		method = "setInitialSpawn"
	)
    private static void findSpawnPosition(ServerLevel serverLevel, ServerLevelData serverLevelData, boolean bl, boolean bl2, CallbackInfo callback) {
		RandomState randomState = serverLevel.getChunkSource().randomState();
		Climate.Sampler sampler = randomState.sampler();
		serverLevel.registryAccess().lookup(RTFRegistries.PRESET).flatMap((registry) -> {
			return registry.get(Preset.KEY);
		}).ifPresent((preset) -> {
//			if((Object) randomState instanceof RTFRandomState rtfRandomState && (Object) sampler instanceof RTFClimateSampler rtfClimateSampler) {
//				BlockPos searchCenter = preset.value().world().properties.spawnType.getSearchCenter(rtfRandomState.generatorContext());
//				RTFCommon.LOGGER.info(searchCenter);
////				rtfClimateSampler.setSpawnSearchCenter(searchCenter);
//			} else {
//				throw new IllegalStateException();
//			}
		});
    }
}
