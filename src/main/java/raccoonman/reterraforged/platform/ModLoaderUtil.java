/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * The applicable permission notice is retained in the repository LICENSE.
 */
package raccoonman.reterraforged.platform;

import net.minecraftforge.fml.loading.FMLLoader;

public class ModLoaderUtil {
	public static boolean isLoaded(String modId) {
		return FMLLoader.getLoadingModList().getModFileById(modId) != null;
	}
}
