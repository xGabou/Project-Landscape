/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * The applicable permission notice is retained in the repository LICENSE.
 */
package raccoonman.reterraforged.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraftforge.fml.loading.FMLPaths;
import raccoonman.reterraforged.RTFCommon;

public class ConfigUtil {
	public static final Path RTF_CONFIG_PATH = getConfigPath().resolve(RTFCommon.MOD_ID);
	public static final Path LEGACY_CONFIG_PATH = getConfigPath().resolve(RTFCommon.LEGACY_MOD_ID);
	
	public static Path rtf(String path) {
		return RTF_CONFIG_PATH.resolve(path);
	}
	
	public static Path legacy(String path) {
		return LEGACY_CONFIG_PATH.resolve(path);
	}
	public static Path getConfigPath() {
		return FMLPaths.CONFIGDIR.get();
	}
	
	static {
		if(!Files.exists(RTF_CONFIG_PATH)) {
			try {
				Files.createDirectory(RTF_CONFIG_PATH);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
