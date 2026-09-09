/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.biome;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import java.util.*;

/** Continuous ecological preference with an explicit acceptable envelope. */
public record BiomeDescriptor(net.minecraft.resources.ResourceLocation key, Range temperature, Range rainfall, Range aridity,
        Range elevation, Set<BiomeTrait> traits, int priority) {
    public static final com.mojang.serialization.Codec<BiomeTrait> TRAIT_CODEC=com.mojang.serialization.Codec.STRING.comapFlatMap(name->{
        try{return com.mojang.serialization.DataResult.success(BiomeTrait.valueOf(name));}
        catch(IllegalArgumentException invalid){return com.mojang.serialization.DataResult.error(()->"Unknown biome trait: "+name);}
    },Enum::name);
    public static final com.mojang.serialization.Codec<BiomeDescriptor> CODEC=com.mojang.serialization.codecs.RecordCodecBuilder.create(i->i.group(
        net.minecraft.resources.ResourceLocation.CODEC.fieldOf("biome").forGetter(BiomeDescriptor::key),
        Range.CODEC.fieldOf("temperatureCelsius").forGetter(BiomeDescriptor::temperature),
        Range.CODEC.fieldOf("rainfallMmPerYear").forGetter(BiomeDescriptor::rainfall),
        Range.CODEC.fieldOf("rainfallEvaporationRatio").forGetter(BiomeDescriptor::aridity),
        Range.CODEC.fieldOf("seaRelativeElevationBlocks").forGetter(BiomeDescriptor::elevation),
        TRAIT_CODEC.listOf().comapFlatMap(values->values.isEmpty()
                ?com.mojang.serialization.DataResult.<Set<BiomeTrait>>error(()->"Biome descriptor traits must not be empty")
                :com.mojang.serialization.DataResult.success(Set.copyOf(values)),s->s.stream().sorted().toList()).fieldOf("traits").forGetter(BiomeDescriptor::traits),
        com.gabou.atmospheregen.config.ConfigCodecs.integer("priority",0,1000000).fieldOf("priority").forGetter(BiomeDescriptor::priority)
    ).apply(i,BiomeDescriptor::new));
    public BiomeDescriptor { Objects.requireNonNull(key);Objects.requireNonNull(temperature);Objects.requireNonNull(rainfall);Objects.requireNonNull(aridity);Objects.requireNonNull(elevation);traits=Set.copyOf(traits);if(traits.isEmpty())throw new IllegalArgumentException(key+": descriptor traits must not be empty");if(priority<0)throw new IllegalArgumentException(key+": negative biome priority"); }
    public double score(double value,Range range){return range.score(value);}
    public record Range(double minimum,double preferredMinimum,double preferredMaximum,double maximum) {
        public static final com.mojang.serialization.Codec<Range> CODEC=com.mojang.serialization.Codec.DOUBLE.listOf().comapFlatMap(values->{
            if(values.size()!=4)return com.mojang.serialization.DataResult.error(()->"Range requires [minimum, preferredMinimum, preferredMaximum, maximum]");
            try{return com.mojang.serialization.DataResult.success(new Range(values.get(0),values.get(1),values.get(2),values.get(3)));}
            catch(IllegalArgumentException invalid){return com.mojang.serialization.DataResult.error(()->invalid.getMessage());}
        },r->List.of(r.minimum,r.preferredMinimum,r.preferredMaximum,r.maximum));
        public Range {if(!Double.isFinite(minimum)||!Double.isFinite(preferredMinimum)||!Double.isFinite(preferredMaximum)||!Double.isFinite(maximum)||minimum>preferredMinimum||preferredMinimum>preferredMaximum||preferredMaximum>maximum)throw new IllegalArgumentException("Invalid biome descriptor range");}
        public boolean allowed(double value){return value>=minimum&&value<=maximum;}
        public double score(double value){if(!allowed(value))return 0;if(value>=preferredMinimum&&value<=preferredMaximum)return 1;double d=value<preferredMinimum?preferredMinimum-value:value-preferredMaximum;double span=value<preferredMinimum?preferredMinimum-minimum:maximum-preferredMaximum;return Math.max(0,1-d/Math.max(1e-9,span));}
    }
}
