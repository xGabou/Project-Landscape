/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * The applicable permission notice is retained in the repository LICENSE.
 */
package raccoonman.reterraforged.platform;

import java.util.concurrent.CompletableFuture;

import net.minecraft.data.registries.RegistriesDatapackGenerator;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

public final class DataGenUtil {
	public static DataProvider createRegistryProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> providerLookup) {
		return new RegistriesDatapackGenerator(output, providerLookup);
	}
}
