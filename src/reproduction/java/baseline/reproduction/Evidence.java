/* Original developer-side measurement code. All Rights Reserved. */
package baseline.reproduction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.*;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.terrain.Terrain;
import raccoonman.reterraforged.world.worldgen.densityfunction.CellSampler;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Heightmap;

/** Observations, never golden expected terrain. Floating values retain their exact bits. */
public final class Evidence {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private final Path directory;
    private final Map<String, List<Map<String, Object>>> tables = new TreeMap<>();
    public Evidence(Path directory) throws Exception { this.directory = directory; Files.createDirectories(directory); }
    public synchronized void row(String table, Object... values) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i + 1]);
        tables.computeIfAbsent(table, k -> new ArrayList<>()).add(row);
    }
    public synchronized void flush() throws Exception {
        for (var entry : tables.entrySet()) Files.writeString(directory.resolve(entry.getKey() + ".json"), JSON.toJson(entry.getValue()) + "\n");
    }
    public void snapshot(String relative, Object value) throws Exception {
        Path file=directory.resolve(relative);Files.createDirectories(file.getParent());
        try(var stream=new java.util.zip.GZIPOutputStream(Files.newOutputStream(file))) {
            stream.write(JSON.toJson(value).getBytes(StandardCharsets.UTF_8));
        }
    }
    public static Map<String, Object> cell(Cell cell, Heightmap heightmap) {
        Map<String, Object> result = new TreeMap<>();
        try {
            for (var f : Cell.class.getFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Object value = f.get(cell);
                if (value instanceof Float v) value = Float.floatToRawIntBits(v);
                else if (value instanceof Terrain t) value = t.getName();
                else if (value instanceof Enum<?> e) value = e.name();
                result.put(f.getName(), value);
            }
        } catch (Exception ex) { throw new RuntimeException(ex); }
        if (heightmap != null) for (var f : CellSampler.Field.values()) result.put("hint_" + f.getSerializedName(), Float.floatToRawIntBits(f.read(cell, heightmap)));
        return result;
    }
    public static List<String> different(Map<String, ?> a, Map<String, ?> b) {
        return a.keySet().stream().filter(k -> !Objects.equals(a.get(k), b.get(k))).sorted().toList();
    }
    public static String hash(Object object) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJson(object).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
    public static String failure(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return failure.getClass().getName() + ": " + failure.getMessage();
    }
    public static String trace(Throwable failure) {
        var text = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(text));
        return text.toString();
    }
    /** Test reflection does not modify algorithms; accesses only explicitly documented internals. */
    public static Object field(Object owner, String name) throws Exception {
        Class<?> type = owner instanceof Class<?> c ? c : owner.getClass();
        while (type != null) {
            try { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner instanceof Class<?> ? null : owner); }
            catch (NoSuchFieldException missing) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    public static void set(Object owner, String name, Object value) throws Exception {
        var f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
}
