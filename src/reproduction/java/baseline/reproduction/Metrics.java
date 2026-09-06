/* Original test-only observers. All Rights Reserved. No production state is written. */
package baseline.reproduction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class Metrics {
    private static final Map<String, LongAdder> COUNTS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Deque<Long>> ENTRY_START = ThreadLocal.withInitial(ArrayDeque::new);
    public static void count(String name) { add(name, 1); }
    private static void add(String name, long amount) { COUNTS.computeIfAbsent(name, k -> new LongAdder()).add(amount); }
    public static void beginEntry() { ENTRY_START.get().push(System.nanoTime()); count("entryGetCalls"); }
    public static void endEntry() { add("entryGetInclusiveNs", System.nanoTime() - ENTRY_START.get().pop()); }
    public static Map<String, Long> snapshot() {
        Map<String, Long> result = new TreeMap<>(); COUNTS.forEach((k,v) -> result.put(k,v.sum())); return result;
    }
}
