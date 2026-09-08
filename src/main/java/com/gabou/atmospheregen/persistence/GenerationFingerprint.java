/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.persistence;

import com.mojang.serialization.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** SHA-256 of canonical UTF-8 data. This is a reproducibility checksum, not authentication. */
public record GenerationFingerprint(String sha256) {
    public static final Codec<GenerationFingerprint> CODEC = Codec.STRING.comapFlatMap(value ->
        value.matches("[0-9a-f]{64}") ? DataResult.success(new GenerationFingerprint(value))
            : DataResult.error(() -> "Expected 64 lowercase hexadecimal SHA-256 characters"), GenerationFingerprint::sha256);
    public GenerationFingerprint {
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid SHA-256 fingerprint");
    }
    public static GenerationFingerprint of(CanonicalJson value) { return bytes(value.text().getBytes(StandardCharsets.UTF_8)); }
    public static GenerationFingerprint bytes(byte[] value) {
        try { return new GenerationFingerprint(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value))); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
