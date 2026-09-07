/* Original Task 0 verification harness. All Rights Reserved.
 * Development source set only; not distributed in the mod jar. */
package baseline.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.data.worldgen.Datapacks;
import raccoonman.reterraforged.data.worldgen.preset.settings.Presets;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;

@Mod("baseline_smoke")
public final class BaselineSmoke {
    private final boolean vanilla = Boolean.getBoolean("task1b.smokeVanilla");
    private final String world = "task0-" + System.currentTimeMillis();
    private int stage;
    private int ticks;
    private CompletableFuture<Void> generation;
    private String lastScreen = "";

    public BaselineSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            String screen = mc.screen == null ? "null" : mc.screen.getClass().getSimpleName();
            if (!screen.equals(lastScreen)) {
                lastScreen = screen;
                RTFCommon.LOGGER.info("TASK0 SCREEN {} stage={}", screen, stage);
            }
            if (stage == 0 && screen.equals("AccessibilityOnboardingScreen") && mc.getOverlay() == null) {
                mc.setScreen(new TitleScreen());
            }
            if (++ticks > 20 * 600) throw new IllegalStateException("Smoke test timed out at stage " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                RTFCommon.LOGGER.info("TASK0 TITLE_SCREEN modDetected={} world={}", ModList.get().isLoaded("reterraforged"), world);
                stage = 1;
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                mc.options.simulationDistance().set(5);
                CreateWorldScreen.openFresh(mc, mc.screen);
            } else if (stage == 1 && mc.screen instanceof CreateWorldScreen create) {
                stage = 2;
                Path root = mc.gameDirectory.toPath().resolve("saves").resolve(world);
                Path pack = root.resolve("datapacks/task0-preset");
                if (!vanilla) {
                    Files.createDirectories(pack);
                    Datapacks.makePreset(Presets.makeLegacyDefault(), create.getUiState().getSettings().worldgenLoadContext(),
                        root.resolve("export-work"), pack, "Task 0 Legacy Default").run();
                    RTFCommon.LOGGER.info("TASK0 PRESET_EXPORTED {}", pack);
                }
                var config = create.getUiState().getSettings().dataConfiguration();
                var enabled = new ArrayList<>(config.dataPacks().getEnabled());
                if (!vanilla) enabled.add("file/task0-preset");
                var data = new WorldDataConfiguration(new DataPackConfig(enabled, List.of()), config.enabledFeatures());
                var settings = new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), data);
                mc.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(8675309L, true, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
            } else if ((stage == 2 || stage == 5) && mc.level != null && mc.player != null && mc.getSingleplayerServer() != null) {
                boolean reopened = stage == 5;
                stage = reopened ? 6 : 3;
                var server = mc.getSingleplayerServer();
                generation = server.submit(() -> {
                    ServerLevel level = server.overworld();
                    var state = (RTFRandomState) (Object) level.getChunkSource().randomState();
                    if (vanilla) {
                        for (var dimension : server.getAllLevels()) {
                            var foreign = (RTFRandomState)(Object)dimension.getChunkSource().randomState();
                            if (foreign.generatorContext() != null || foreign.requiresGeneratorContext() || foreign.preset() != null)
                                throw new IllegalStateException("RTF unexpectedly claimed vanilla dimension " + dimension.dimension());
                            RTFCommon.LOGGER.info("TASK1B VANILLA_CONTEXT reopened={} dimension={} required=false context=false preset=false", reopened, dimension.dimension().location());
                        }
                        int rules = level.registryAccess().registry(raccoonman.reterraforged.registries.RTFRegistries.STRUCTURE_RULE).map(r -> r.size()).orElse(0);
                        if (rules != 0) throw new IllegalStateException("Unexpected RTF rules in vanilla world: " + rules);
                        RTFCommon.LOGGER.info("TASK1B VANILLA_RULES count={}", rules);
                    } else if (state.generatorContext() == null || state.preset() == null) throw new IllegalStateException("ReTerraForged context/preset missing");
                    RTFCommon.LOGGER.info("TASK0 WORLD_LOADED reopened={} seed={} packs={}", reopened, level.getSeed(), server.getPackRepository().getSelectedIds());
                    int offset = reopened ? 2048 : 0;
                    int[][] positions = {{0, 0}, {-129, -129}, {127, 127}, {128, 128}, {1024, -1024}, {-2048, 2048}, {4096, 0}};
                    for (int[] position : positions) {
                        int x = position[0] + offset, z = position[1] + offset;
                        var chunk = level.getChunk(x >> 4, z >> 4);
                        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                        var block = level.getBlockState(new BlockPos(x, y - 1, z));
                        if (block.isAir()) throw new IllegalStateException("Empty generated surface at " + x + "," + z);
                        RTFCommon.LOGGER.info("TASK0 CHUNK reopened={} x={} z={} status={} height={} surface={} biome={}",
                            reopened, x, z, chunk.getStatus(), y, block, level.getBiome(new BlockPos(x, y, z)).unwrapKey());
                    }
                    if (!reopened && !vanilla) {
                        // Conversion comparator coverage, not a cached/uncached determinism test.
                        var found = new java.util.HashSet<String>();
                        var heightmap = state.generatorContext().generator.getHeightmap();
                        for (int z = -4096; z <= 4096 && found.size() < 4; z += 64) {
                            for (int x = -4096; x <= 4096 && found.size() < 4; x += 64) {
                                Cell cell = new Cell();
                                heightmap.apply(cell, x, z, true);
                                String kind = cell.terrain.isMountain() ? "mountain" : cell.terrain.isRiver() ? "river"
                                    : cell.terrain == TerrainType.COAST || cell.terrain == TerrainType.BEACH ? "coast"
                                    : cell.terrain.getName().contains("plateau") ? "plateau" : "";
                                if (!kind.isEmpty() && found.add(kind)) {
                                    var chunk = level.getChunk(x >> 4, z >> 4);
                                    RTFCommon.LOGGER.info("TASK0 REPRESENTATIVE kind={} x={} z={} terrain={} heightBits={} riverBits={} temperatureBits={} moistureBits={} status={}",
                                        kind, x, z, cell.terrain.getName(), Float.floatToRawIntBits(cell.height), Float.floatToRawIntBits(cell.riverMask),
                                        Float.floatToRawIntBits(cell.temperature), Float.floatToRawIntBits(cell.moisture), chunk.getStatus());
                                }
                            }
                        }
                        if (found.size() != 4) throw new IllegalStateException("Representative coverage missing: " + found);
                    }
                    ((PrimaryLevelData) server.getWorldData()).withConfirmedWarning(true);
                    server.saveEverything(false, true, true);
                    RTFCommon.LOGGER.info("TASK0 SAVED reopened={}", reopened);
                });
            } else if ((stage == 3 || stage == 6) && generation.isDone()) {
                generation.join();
                boolean finished = stage == 6;
                stage = finished ? 7 : 4;
                mc.level.disconnect();
                mc.clearLevel();
                mc.setScreen(new TitleScreen());
            } else if (stage == 4 && mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen) {
                stage = 5;
                mc.createWorldOpenFlows().loadLevel(mc.screen, world);
            } else if (stage == 7 && mc.getSingleplayerServer() == null) {
                stage = 99;
                if (vanilla) RTFCommon.LOGGER.info("TASK0 PASS: title, vanilla world without RTF preset, full chunks, save, reopen, additional full chunks; world={}", world);
                else RTFCommon.LOGGER.info("TASK0 PASS: title, preset export/load, full chunks, save, reopen, additional full chunks; world={}", world);
                Files.writeString(mc.gameDirectory.toPath().resolve("task0-pass.txt"), world + "\n");
                mc.stop();
            }
        } catch (Throwable failure) {
            stage = 99;
            RTFCommon.LOGGER.error("TASK0 FAIL", failure);
            mc.stop();
        }
    }
}
