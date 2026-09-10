/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * The applicable permission notice is retained in the repository LICENSE.
 */
package raccoonman.reterraforged.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.registries.RTFRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;

import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DataPackRegistryEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.RegistryBuilder;

//this is only public so the initializer class can call register
//TODO make this non public
public final class RegistryUtil {
    public static <T> void register(Registry<T> registry, String name, T value) {
        // These codecs/features are inherited ReTerraForged serialization identities.
        // Keeping their keys lets an old exported datapack decode without duplicating
        // the same Forge registry object under two names (which Forge rejects).
		getLegacyWritable(registry).register(ResourceKey.create(registry.key(), RTFCommon.legacyDataLocation(name)), value, Lifecycle.stable());
    }

	/** Registers Project Landscape-owned static content. */
	public static <T> void registerProjectLandscape(Registry<T> registry, String name, T value) {
		getWritable(registry).register(RTFRegistries.createKey(registry.key(), name), value, Lifecycle.stable());
	}

	private static final Map<ResourceKey<? extends Registry<?>>, DeferredRegistry.Writable<?>> REGISTERS = new ConcurrentHashMap<>();
	private static final Map<ResourceKey<? extends Registry<?>>, DeferredRegistry.Writable<?>> LEGACY_REGISTERS = new ConcurrentHashMap<>();
	private static final List<DataRegistry<?>> DATA_REGISTRIES = Collections.synchronizedList(new ArrayList<>());

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void register(IEventBus bus) {
		for(DeferredRegistry.Writable<?> registry : REGISTERS.values()) {
			registry.register(bus);
		}
		for(DeferredRegistry.Writable<?> registry : LEGACY_REGISTERS.values()) {
			registry.register(bus);
		}
		
		bus.addListener((DataPackRegistryEvent.NewRegistry event) -> {
			for(DataRegistry registry : DATA_REGISTRIES) {
				event.dataPackRegistry(registry.key(), registry.codec());
			}
		});
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> WritableRegistry<T> getWritable(Registry<T> registry) {
		return (WritableRegistry<T>) REGISTERS.computeIfAbsent(registry.key(), (k) -> {
			return new DeferredRegistry.Writable<>(DeferredRegister.create((ResourceKey) k, RTFCommon.MOD_ID));
		});
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static <T> WritableRegistry<T> getLegacyWritable(Registry<T> registry) {
		return (WritableRegistry<T>) LEGACY_REGISTERS.computeIfAbsent(registry.key(), (k) ->
			new DeferredRegistry.Writable<>(DeferredRegister.create((ResourceKey) k, RTFCommon.LEGACY_DATA_NAMESPACE)));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <T> Registry<T> createRegistry(ResourceKey<? extends Registry<T>> key) {
		if(!key.equals(RTFRegistries.BIOME_MODIFIER_TYPE)) {
			DeferredRegister<T> register = DeferredRegister.create((ResourceKey) key, RTFCommon.MOD_ID);
			register.makeRegistry(() -> {
				return new RegistryBuilder().hasTags();
			});
			REGISTERS.put(key, new DeferredRegistry.Writable<>(register));
		}
		return DeferredRegistry.memoize(key, () -> {
			return GameData.getWrapper(key, Lifecycle.stable());
		});
	}

	public static <T> void createDataRegistry(ResourceKey<? extends Registry<T>> key, Codec<T> codec) {
		DATA_REGISTRIES.add(new DataRegistry<>(key, codec));
	}

	public static <T> void createLegacyDataRegistry(String path, Codec<T> codec) {
		DATA_REGISTRIES.add(new DataRegistry<>(ResourceKey.createRegistryKey(RTFCommon.legacyDataLocation(path)), codec));
	}
	
	private record DataRegistry<T>(ResourceKey<? extends Registry<T>> key, Codec<T> codec) {
	}
}
