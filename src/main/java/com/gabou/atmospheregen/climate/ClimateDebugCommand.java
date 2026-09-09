/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.climate;

import com.gabou.atmospheregen.api.geography.MacroGeographyProvider;
import com.gabou.atmospheregen.config.BaselineClimateConfig;
import com.gabou.atmospheregen.generation.version.GeographyAlgorithmVersion;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import com.gabou.atmospheregen.geography.terrain.PaGeographyProvider;
import com.gabou.atmospheregen.geography.ocean.CoarseDistanceField;

/** Developer-only readable climate diagnostic; it does not select biomes or run weather. */
public final class ClimateDebugCommand {
    private ClimateDebugCommand() {}
    public static void register(RegisterCommandsEvent event) {
        var z = Commands.argument("z", IntegerArgumentType.integer())
            .requires(source -> source.hasPermission(2))
            .executes(context -> sample(context.getSource(), IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "z")));
        var x = Commands.argument("x", IntegerArgumentType.integer()).then(z);
        event.getDispatcher().register(Commands.literal("geo").then(Commands.literal("climate").then(x)));
    }
    private static int sample(net.minecraft.commands.CommandSourceStack source,int x,int z) {
        net.minecraft.server.level.ServerLevel level = source.getLevel();
        var random=(RTFRandomState)(Object)level.getChunkSource().randomState();GeneratorContext context=random.generatorContext();
        if(context==null||context.generator.getHeightmap().paBridge()==null){source.sendFailure(Component.literal("PA_GEOGRAPHY_V1 is not installed in this world"));return 0;}
        var geography=new PaGeographyProvider(context);MacroGeographyProvider macro=geography.macroProvider();
        var versions=context.generationContext()==null?null:context.generationContext().manifest().content().versions();
        if(versions==null||versions.geography()!=GeographyAlgorithmVersion.PA_GEOGRAPHY_V1){source.sendFailure(Component.literal("/geo climate requires PA_GEOGRAPHY_V1"));return 0;}
        var configured=context.generationContext().manifest().content().baselineClimate().planned().orElseGet(()->new BaselineClimateConfig.Planned(100000,0,.0065,.5));
        var model=new BaselineClimateModel(context.generationContext().seeds(),configured);var adapter=ClimateGeographyAdapters.of(geography,macro,new CoarseDistanceField(macro));
        var result=model.breakdown(adapter,x,z);var b=result.baseline();var d=result.breakdown();
        source.sendSuccess(()->Component.literal("PA_GEOGRAPHY_V1 / "+versions.baselineClimate()+" x="+x+" z="+z+" lat="+d.latitudeDegrees()+" elevContribution="+d.altitudeContributionCelsius()+"C wind="+b.prevailingWind().orElseThrow().x()+","+b.prevailingWind().orElseThrow().z()+" temp="+b.meanTemperatureCelsius()+"C rain="+b.annualRainfallMm()+"mm/y evap="+b.potentialEvaporationMm()+"mm/y moisture="+b.ecologicalMoistureIndex()+" shadow="+b.rainShadow()+" fetch="+d.marineFetch()+" orographic="+d.orographicRainfall()),false);return 1;
    }
}
