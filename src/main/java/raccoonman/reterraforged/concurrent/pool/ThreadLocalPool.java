/* Derived from ReTerraForged, Copyright (c) 2023 ReTerraForged, MIT License.
 * See LICENSE for the applicable copyright and permission notice. */
package raccoonman.reterraforged.concurrent.pool;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import raccoonman.reterraforged.concurrent.Resource;

public class ThreadLocalPool<T> {
    private int size;
    private Supplier<T> factory;
    private Consumer<T> cleaner;
    private ThreadLocal<Pool<T>> local;
    
    public ThreadLocalPool(int size, Supplier<T> factory) {
        this(size, factory, t -> {});
    }
    
    public ThreadLocalPool(int size, Supplier<T> factory, Consumer<T> cleaner) {
        this.size = size;
        this.factory = factory;
        this.cleaner = cleaner;
        this.local = ThreadLocal.withInitial(this::createPool);
    }
    
    public Resource<T> get() {
        return this.local.get().retain();
    }
    
    private Pool<T> createPool() {
        return new Pool<>(this.size, this.factory, this.cleaner);
    }
    
    private static class Pool<T> {
        private int size;
        private Supplier<T> factory;
        private Consumer<T> cleaner;
        private List<T> pool;
        private final Thread owner = Thread.currentThread();
        private int index;
        
        private Pool(int size, Supplier<T> factory, Consumer<T> cleaner) {
            this.size = size;
            this.index = size - 1;
            this.factory = factory;
            this.cleaner = cleaner;
            this.pool = new ObjectArrayList<>(size);
            for (int i = 0; i < size; ++i) {
                this.pool.add(factory.get());
            }
        }
        
        private Resource<T> retain() {
            if (this.index > 0) {
                T value = this.pool.remove(this.index);
                --this.index;
                return new PoolResource<>(value, this);
            }
            return new PoolResource<>(this.factory.get(), this);
        }
        
        private void restore(T value) {
            if (this.index + 1 < this.size) {
                this.cleaner.accept(value);
                this.pool.add(value);
                ++this.index;
            }
        }
    }
    
    private static class PoolResource<T> implements Resource<T> {
        private T value;
        private Pool<T> pool;
        private boolean closed;
        
        private PoolResource(T value, Pool<T> pool) {
            this.value = value;
            this.pool = pool;
        }
        
        @Override
        public T get() {
            this.checkThread();
            if (this.closed) throw new IllegalStateException("Cell fallback resource has been closed");
            return this.value;
        }
        
        @Override
        public boolean isOpen() {
            return !this.closed;
        }
        
        @Override
        public void close() {
            this.checkThread();
            if (!this.closed) {
                this.closed = true;
                this.pool.restore(this.value);
            }
        }

        private void checkThread() {
            if (Thread.currentThread() != this.pool.owner) {
                throw new IllegalStateException("Cell fallback resource must be used and released on its borrowing thread");
            }
        }
    }
}
