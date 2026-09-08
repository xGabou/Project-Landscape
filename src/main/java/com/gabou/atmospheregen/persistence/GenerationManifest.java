/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.persistence;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Immutable self-checking manifest. A valid checksum never implies that an algorithm is implemented. */
public record GenerationManifest(ManifestContent content, GenerationFingerprint fingerprint) {
    private static final Codec<Pair<ManifestContent, GenerationFingerprint>> FIELDS = RecordCodecBuilder.create(i -> i.group(
        ManifestContent.CODEC.fieldOf("content").forGetter(Pair::getFirst),
        GenerationFingerprint.CODEC.fieldOf("fingerprint").forGetter(Pair::getSecond)
    ).apply(i, Pair::of));
    public static final Codec<GenerationManifest> CODEC = FIELDS.comapFlatMap(pair -> {
        try { return DataResult.success(new GenerationManifest(pair.getFirst(), pair.getSecond())); }
        catch (IllegalArgumentException invalid) { return DataResult.error(() -> invalid.getMessage()); }
    }, value -> Pair.of(value.content, value.fingerprint));
    public GenerationManifest {
        content.validate();
        if (!GenerationFingerprint.of(content.canonical()).equals(fingerprint))
            throw new IllegalArgumentException("Manifest fingerprint mismatch: content was changed or corrupted; restore matching world generation data, do not regenerate with defaults");
    }
    public static GenerationManifest create(ManifestContent content) { return new GenerationManifest(content, GenerationFingerprint.of(content.canonical())); }
}
