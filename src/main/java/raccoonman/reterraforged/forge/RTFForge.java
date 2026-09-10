/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.forge;

import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.metadata.PackMetadataGenerator;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.client.data.RTFLanguageProvider;
import raccoonman.reterraforged.client.data.RTFTranslationKeys;
import raccoonman.reterraforged.platform.RegistryUtil;

@Mod(RTFCommon.MOD_ID)
public class RTFForge {

    public RTFForge() {
    	RTFCommon.bootstrap();

    	IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

    	if (FMLEnvironment.dist == Dist.CLIENT) {
    		modBus.addListener(RTFForgeClient::registerPresetEditors);
    	}
    	modBus.addListener(RTFForge::gatherData);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(com.gabou.atmospheregen.climate.ClimateDebugCommand::register);

		RegistryUtil.register(modBus);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(RTFForge::unloadLevel);
    }

    private static void unloadLevel(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            var state = (RTFRandomState)(Object)level.getChunkSource().randomState();
            var context = state.generatorContext();
            if (context != null && context.cache != null) context.cache.close();
        }
    }
    
    private static void gatherData(GatherDataEvent event) {
    	boolean includeClient = event.includeClient();
    	DataGenerator generator = event.getGenerator();
    	PackOutput output = generator.getPackOutput();
    	
    	generator.addProvider(includeClient, new RTFLanguageProvider.EnglishUS(output));
    	generator.addProvider(includeClient, PackMetadataGenerator.forFeaturePack(output, Component.translatable(RTFTranslationKeys.METADATA_DESCRIPTION)));
    }
}
