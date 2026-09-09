/* Original Project Atmosphere companion integration. All Rights Reserved. */
package com.gabou.atmospheregen.compat.legacy;

import com.gabou.atmospheregen.persistence.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.levelgen.*;
import raccoonman.reterraforged.mixin.NoiseGeneratorSettingsAccessor;
import terrablender.api.SurfaceRuleManager;

/** V1-only definition fingerprint, independent of TB's late region-type/accessor initialization. */
public final class V1TerraBlenderData {
    private V1TerraBlenderData() {}
    public static GenerationFingerprint noiseSettings(RegistryAccess access) {
        var ops=RegistryOps.create(JsonOps.INSTANCE,access);
        var definitions=new JsonObject();
        for(var entry:access.registryOrThrow(Registries.NOISE_SETTINGS).entrySet()){
            var value=entry.getValue();
            var definition=NoiseGeneratorSettings.DIRECT_CODEC.encodeStart(ops,value).getOrThrow(false,s->{}).getAsJsonObject();
            var stored=((NoiseGeneratorSettingsAccessor)(Object)value).atmospheregen$storedSurfaceRule();
            // Never discard user-authored wrappers: read the stored field, not unwrapped accessor output.
            definition.add("surface_rule",SurfaceRules.RuleSource.CODEC.encodeStart(ops,stored).getOrThrow(false,s->{}));
            var effective=new JsonObject();
            for(var category:SurfaceRuleManager.RuleCategory.values())
                effective.add(category.name(),SurfaceRules.RuleSource.CODEC.encodeStart(ops,
                    SurfaceRuleManager.getNamespacedRules(category,stored)).getOrThrow(false,s->{}));
            definition.add("atmospheregen_terrablender_registered_rules",effective);
            definitions.add(entry.getKey().location().toString(),definition);
        }
        return GenerationFingerprint.of(CanonicalJson.of(definitions));
    }
}
