/* Original Task 0 verification harness. All Rights Reserved.
 * Development source set only; not distributed in the mod jar. */
package baseline.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import raccoonman.reterraforged.RTFCommon;
import raccoonman.reterraforged.registries.RTFRegistries;
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
import raccoonman.reterraforged.data.worldgen.Datapacks;
import raccoonman.reterraforged.data.worldgen.preset.settings.Presets;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;

@Mod("baseline_smoke")
public final class BaselineSmoke {
    private final boolean vanilla = Boolean.getBoolean("task1b.smokeVanilla");
    private final boolean legacyExport = Boolean.getBoolean("task1b.exportLegacyPreset");
    private final boolean geographyV1 = Boolean.getBoolean("task4.smokeV1");
    private String initialV1Manifest;
    private java.util.List<String> initialV1Topology;
    private final boolean existingWorld = System.getProperty("task2.existingWorld") != null;
    private final String world = System.getProperty("task2.existingWorld", "task0-" + System.currentTimeMillis());
    private int stage;
    private int ticks;
    private CompletableFuture<Void> generation;
    private String lastScreen = "";
    private final List<Object> task2Evidence = new ArrayList<>();

    public BaselineSmoke() {
        if (!world.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Smoke world name must be a simple directory label");
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
                RTFCommon.LOGGER.info("TASK0 TITLE_SCREEN modDetected={} world={}", ModList.get().isLoaded(RTFCommon.MOD_ID), world);
                stage = 1;
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(4);
                mc.options.simulationDistance().set(5);
                if (existingWorld) {
                    if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(world).resolve("level.dat"))) throw new IllegalStateException("Existing smoke world missing");
                    stage = 2;
                    mc.createWorldOpenFlows().loadLevel(mc.screen, world);
                    return;
                }
                CreateWorldScreen.openFresh(mc, mc.screen);
            } else if (stage == 1 && mc.screen instanceof CreateWorldScreen create) {
                stage = 2;
                Path root = mc.gameDirectory.toPath().resolve("saves").resolve(world);
                Path pack = root.resolve("datapacks/task0-preset");
                if(geographyV1) {
                    if(vanilla)throw new IllegalArgumentException("V1 and vanilla smoke are separate cases");
                    var selection=com.gabou.projectlandscape.persistence.DevelopmentGeographySelection.path(root);
                    Files.createDirectories(selection.getParent());
                    Files.writeString(selection,com.gabou.projectlandscape.config.MacroGeographySettings.CODEC.encodeStart(
                        com.mojang.serialization.JsonOps.INSTANCE,com.gabou.projectlandscape.config.MacroGeographySettings.defaults()).getOrThrow(false,s->{}).toString(),java.nio.file.StandardOpenOption.CREATE_NEW);
                }
                if (!vanilla && legacyExport) {
                    Files.createDirectories(pack);
                    Datapacks.makePreset(Presets.makeLegacyDefault(), create.getUiState().getSettings().worldgenLoadContext(),
                        root.resolve("export-work"), pack, "Task 0 Legacy Default").run();
                    RTFCommon.LOGGER.info("TASK0 PRESET_EXPORTED {}", pack);
                }
                var config = create.getUiState().getSettings().dataConfiguration();
                var enabled = new ArrayList<>(config.dataPacks().getEnabled());
                if (!vanilla && legacyExport) enabled.add("file/task0-preset");
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
                        int rules = level.registryAccess().registry(RTFRegistries.STRUCTURE_RULE).map(r -> r.size()).orElse(0);
                        if (rules != 0) throw new IllegalStateException("Unexpected RTF rules in vanilla world: " + rules);
                        RTFCommon.LOGGER.info("TASK1B VANILLA_RULES count={}", rules);
                    } else if (state.generatorContext() == null || state.preset() == null) throw new IllegalStateException("ReTerraForged context/preset missing");
                    if (!vanilla && state.generatorContext().generationContext() == null) throw new IllegalStateException("Task 2 world metadata missing");
                    if(geographyV1) {
                        var c=state.generatorContext();var bridge=c.generator.getHeightmap().paBridge();
                        if(bridge==null)throw new AssertionError("V1 development world did not install new geography");
                        String manifest=com.gabou.projectlandscape.persistence.GenerationManifestStore.encode(c.generationContext().manifest());
                        var topology=new java.util.ArrayList<String>();
                        for(int z=-32768;z<=32768;z+=4096)for(int x=-32768;x<=32768;x+=4096)topology.add(bridge.macro().sampleMacro(x,z).toString());
                        if(reopened&&(!manifest.equals(initialV1Manifest)||!topology.equals(initialV1Topology)))throw new AssertionError("V1 save/reopen manifest/topology changed");
                        initialV1Manifest=manifest;initialV1Topology=topology;
                        task2Evidence.add(java.util.Map.of("v1",true,"reopened",reopened,"topologySamples",topology.size(),"manifestStable",true,"topologyStable",true));
                    }
                    task2Evidence.add(java.util.Map.of("phase", reopened ? "reopened" : "initial", "vanilla", vanilla,
                        "existingTask1CWorld", existingWorld, "terraBlender", ModList.get().isLoaded("terrablender"),
                        "metadata", vanilla ? java.util.Map.of("ownership", "foreign_untouched") :
                            com.gabou.projectlandscape.generation.context.GenerationDiagnostics.describe(state.generatorContext().generationContext())));
                    if (vanilla && !reopened) {
                        var reload = server.reloadResources(server.getPackRepository().getSelectedIds());
                        server.managedBlock(reload::isDone);
                        reload.join();
                        task2Evidence.add(java.util.Map.of("vanillaLiveReload", "PASS"));
                    }
                    RTFCommon.LOGGER.info("TASK0 WORLD_LOADED reopened={} seed={} packs={}", reopened, level.getSeed(), server.getPackRepository().getSelectedIds());
                    int offset = reopened ? 2048 : 0;
                    int[][] positions = {{0, 0}, {-129, -129}, {127, 127}, {128, 128}, {1024, -1024}, {-2048, 2048}, {4096, 0}};
                    for (int[] position : positions) {
                        int x = position[0] + offset, z = position[1] + offset;
                        var chunk = level.getChunk(x >> 4, z >> 4);
                        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                        var block = level.getBlockState(new BlockPos(x, y - 1, z));
                        if (block.isAir()) throw new IllegalStateException("Empty generated surface at " + x + "," + z);
                        task2Evidence.add(java.util.Map.of("reopened", reopened, "x", x, "z", z, "height", y,
                            "status", chunk.getStatus().toString(), "biome", level.getBiome(new BlockPos(x,y,z)).unwrapKey().orElseThrow().location().toString()));
                        RTFCommon.LOGGER.info("TASK0 CHUNK reopened={} x={} z={} status={} height={} surface={} biome={}",
                            reopened, x, z, chunk.getStatus(), y, block, level.getBiome(new BlockPos(x, y, z)).unwrapKey());
                    }
                    if (!reopened && !vanilla && legacyExport && !geographyV1) {
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
                else RTFCommon.LOGGER.info("TASK0 PASS: title, normal Project Landscape world, full chunks, save, reopen, additional full chunks; world={}", world);
                Files.writeString(mc.gameDirectory.toPath().resolve("task0-pass.txt"), world + "\n");
                Files.writeString(mc.gameDirectory.toPath().resolve("task2-smoke-evidence.json"),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(task2Evidence));
                mc.stop();
            }
        } catch (Throwable failure) {
            stage = 99;
            RTFCommon.LOGGER.error("TASK0 FAIL", failure);
            mc.stop();
        }
    }
}
