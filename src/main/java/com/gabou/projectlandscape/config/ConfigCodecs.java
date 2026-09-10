/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.config;
import com.mojang.serialization.*;
import java.util.Optional;
import java.util.stream.Stream;
public final class ConfigCodecs {
    private ConfigCodecs(){}
    /** Unlike Codec.intRange, invalid input has no partial value to invoke a record constructor with. */
    public static Codec<Integer> integer(String field,int min,int max) {
        return Codec.PASSTHROUGH.comapFlatMap(dynamic->dynamic.asNumber().flatMap(number->{
            try {
                int v=new java.math.BigDecimal(number.toString()).intValueExact();
                return v>=min&&v<=max?DataResult.success(v):DataResult.error(()->field+" must be in ["+min+", "+max+"]; got "+v);
            } catch(NumberFormatException|ArithmeticException invalid) {
                return DataResult.error(()->field+" must be an exact finite integer in ["+min+", "+max+"]; got "+number);
            }
        }),v->new Dynamic<>(JsonOps.INSTANCE,new com.google.gson.JsonPrimitive(v)));
    }
    /** DFU's optionalFieldOf treats malformed present fields as absent. Generation config must not. */
    public static <A> MapCodec<Optional<A>> optional(String name,Codec<A> codec) {
        return new MapCodec<>() {
            @Override public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops,MapLike<T> input) {
                T value=input.get(name);
                return value==null?DataResult.success(Optional.empty()):codec.parse(ops,value).map(Optional::of);
            }
            @Override public <T> RecordBuilder<T> encode(Optional<A> value,DynamicOps<T> ops,RecordBuilder<T> prefix) {
                return value.isEmpty()?prefix:prefix.add(name,codec.encodeStart(ops,value.get()));
            }
            @Override public <T> Stream<T> keys(DynamicOps<T> ops){return Stream.of(ops.createString(name));}
        };
    }
    public static Codec<Double> finite(String field,double min,double max) {
        return Codec.DOUBLE.comapFlatMap(v->Double.isFinite(v)&&v>=min&&v<=max?DataResult.success(v):DataResult.error(()->field+" must be finite in ["+min+", "+max+"]; got "+v),v->v);
    }
    public static void check(String field,double v,double min,double max){if(!Double.isFinite(v)||v<min||v>max)throw new IllegalArgumentException(field+" must be finite in ["+min+", "+max+"]; got "+v);}
}
