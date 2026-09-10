package com.gabou.projectlandscape.runtime;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import raccoonman.reterraforged.RTFCommon;

@Mod.EventBusSubscriber(modid=RTFCommon.MOD_ID)
public final class RuntimeClimateCommand {
    private RuntimeClimateCommand() { }
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("geo").then(Commands.literal("runtimeclimate").requires(s->s.hasPermission(2))
                .then(Commands.argument("x",IntegerArgumentType.integer(-30_000_000,30_000_000))
                .then(Commands.argument("z",IntegerArgumentType.integer(-30_000_000,30_000_000)).executes(c->{
                    var source=c.getSource();var level=source.getLevel();int x=IntegerArgumentType.getInteger(c,"x"),z=IntegerArgumentType.getInteger(c,"z");
                    var service=RuntimeClimateLifecycle.service(level);var baseline=RuntimeClimateLifecycle.baseline(level);
                    source.sendSuccess(()->Component.literal(RuntimeClimateLifecycle.status(level)),false);
                    if(baseline!=null) {
                        var value=baseline.sample(x,z);
                        source.sendSuccess(()->Component.literal(value.map(b->"baseline climate: available; representative="+b.blockX()+","+b.blockZ()
                                +" temperature="+b.baseline().meanTemperatureCelsius()+" C rainfall="+b.baseline().annualRainfallMm()+" mm/year")
                                .orElse("baseline climate: available; this region has no retained observation (no terrain loaded by diagnostic)")),false);
                    } else source.sendSuccess(()->Component.literal("baseline climate: unavailable for this dimension/generation context"),false);
                    if(service==null)return 1;
                    source.sendSuccess(()->Component.literal("region="+ClimateRegion.at(x,z,service.regionSize())+" size="+service.regionSize()+" blocks; "
                            +service.sample(x,z).map(Object::toString).orElse("runtime observation unavailable (inactive/uninitialized)")),false);
                    service.history().region(x,z).ifPresent(h->{
                        long now=level.getGameTime();var recent=h.snapshot(0,now);var annual=h.snapshot(2,now);
                        source.sendSuccess(()->Component.literal("history count="+h.observationCount()+" age="+(now-h.lastTick())+" ticks; recent mean="+recent.meanTemperature()
                                +" C; four-season mean="+annual.meanTemperature()+" C; precipitation="+annual.precipitationIntensityTicks()
                                +" intensity*ticks; observed coverage="+annual.elapsedTicks()+" ticks; wet/dry="+annual.wetTicks()+"/"+annual.dryTicks()),false);
                    });return 1;
                })))));
    }
}
