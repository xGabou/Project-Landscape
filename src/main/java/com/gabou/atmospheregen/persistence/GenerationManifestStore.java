/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.persistence;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** World-storage-lock-owned initialization I/O only. Never called by sample or noise compute. */
public final class GenerationManifestStore {
    public enum ResolutionKind { EXPLICIT_LEGACY_ASSIGNMENT, EXISTING_MANIFEST, EXPLICIT_V1_DEVELOPMENT_ASSIGNMENT, EXPLICIT_V1_CLIMATE_ASSIGNMENT }
    public record Resolution(GenerationManifest manifest, ResolutionKind kind) {}
    private GenerationManifestStore() {}

    public static Path path(Path dimensionDirectory) { return dimensionDirectory.resolve("data/atmospheregen/generation_manifest.json"); }

    /**
     * recognizedLegacy must come from actual RTF preset/router ownership, not a global default.
     * Absence means foreign/unrecognized worldgen: no file => untouched; existing file => error.
     * Caller owns Minecraft's world save lock. Existing files are never overwritten or migrated.
     */
    public static Optional<Resolution> resolve(Path file, Optional<GenerationManifest> recognizedLegacy) throws IOException {
        if (Files.exists(file)) {
            GenerationManifest persisted = read(file);
            persisted.content().versions().requireFunctionalBackend();
            if (recognizedLegacy.isEmpty()) throw new IllegalStateException("Manifest exists for " + persisted.content().dimension()
                + " but this dimension does not own a recognizable RTF preset/router. Restore the matching generator; no newest-version fallback.");
            GenerationManifest expected = recognizedLegacy.get();
            if (!persisted.equals(expected)) throw new IllegalStateException("Generation manifest mismatch in " + persisted.content().dimension()
                + "; changed fields: " + differences(persisted.content().canonical().tree(), expected.content().canonical().tree(), "content")
                + ". Restore matching preset/datapacks/seed. No automatic migration; existing manifest was not overwritten.");
            return Optional.of(new Resolution(persisted, ResolutionKind.EXISTING_MANIFEST));
        }
        if (recognizedLegacy.isEmpty()) return Optional.empty();
        GenerationManifest manifest = recognizedLegacy.get();
        manifest.content().versions().requireFunctionalBackend();
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "generation_manifest-", ".tmp");
        try {
            byte[] bytes = encode(manifest).getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // Same-directory rename, deliberately WITHOUT REPLACE_EXISTING. A competing writer is an error.
            Files.move(temporary, file);
        } finally { Files.deleteIfExists(temporary); }
        return Optional.of(new Resolution(manifest, manifest.content().versions().equals(com.gabou.atmospheregen.generation.version.GenerationVersions.geographyV1())
            ? ResolutionKind.EXPLICIT_V1_DEVELOPMENT_ASSIGNMENT
            : manifest.content().versions().equals(com.gabou.atmospheregen.generation.version.GenerationVersions.climateV1())
                ? ResolutionKind.EXPLICIT_V1_CLIMATE_ASSIGNMENT : ResolutionKind.EXPLICIT_LEGACY_ASSIGNMENT));
    }

    public static String encode(GenerationManifest manifest) {
        return CanonicalJson.of(GenerationManifest.CODEC.encodeStart(JsonOps.INSTANCE, manifest).getOrThrow(false, s -> {})).text();
    }
    public static GenerationManifest read(Path file) throws IOException {
        try {
            return GenerationManifest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                .getOrThrow(false, s -> {});
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Cannot load atmospheregen generation manifest " + file.getFileName()
                + ": " + invalid.getMessage() + ". Restore a supported, matching manifest; defaults will not be substituted.", invalid);
        }
    }
    private static List<String> differences(JsonElement a, JsonElement b, String path) {
        if (a.equals(b)) return List.of();
        if (a.isJsonObject() && b.isJsonObject()) {
            List<String> result = new ArrayList<>();
            Set<String> keys = new TreeSet<>(a.getAsJsonObject().keySet()); keys.addAll(b.getAsJsonObject().keySet());
            for (String key : keys) {
                JsonElement av = a.getAsJsonObject().get(key), bv = b.getAsJsonObject().get(key);
                if (av == null || bv == null) result.add(path + "." + key);
                else result.addAll(differences(av, bv, path + "." + key));
            }
            return result;
        }
        return List.of(path);
    }
}
