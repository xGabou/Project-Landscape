/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.concurrent.cache.map;

import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Consumer;

public interface LongMap<T> {
    int size();
    
    void clear();
    
    void remove(long key);
    
    void remove(long key, Consumer<T> ifPreset);

    default boolean remove(long key, T expected, Consumer<T> consumer) {
        throw new UnsupportedOperationException("This map does not support identity-conditional removal");
    }
    
    int removeIf(Predicate<T> predicate);
    
    void put(long key, T value);
    
    T get(long key);
    
    T computeIfAbsent(long key, LongFunction<T> computer);
    
    default <V> V map(long key, LongFunction<T> factory, Function<T, V> mapper) {
        return mapper.apply(this.computeIfAbsent(key, factory));
    }
}
