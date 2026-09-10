/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.generation.seed;

import com.gabou.projectlandscape.generation.version.GenerationVersions;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * SHA-256 named-seed-v1. Big-endian full 64-bit seed and salt; strings are UTF-8 with
 * unsigned-compatible 32-bit byte lengths. The first eight digest bytes, big endian,
 * form the signed long result. No sequential allocation, mutable digest, or registry order.
 * Bind streams at stage construction, not in the per-cell hot path.
 */
public final class NamedSeedService implements GenerationSeedService {
    private final long worldSeed;
    private final ResourceLocation dimension;
    private final GenerationVersions versions;

    public NamedSeedService(long worldSeed, ResourceLocation dimension, GenerationVersions versions) {
        this.worldSeed = worldSeed;
        this.dimension = Objects.requireNonNull(dimension);
        this.versions = Objects.requireNonNull(versions);
    }

    @Override public long seed(SeedDomain domain, long salt) {
        Objects.requireNonNull(domain);
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        string(digest, "atmospheregen:named-seed-v1");
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(worldSeed).array());
        string(digest, dimension.toString());
        string(digest, domain.id());
        string(digest, domain.algorithmVersion(versions));
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(salt).array());
        return ByteBuffer.wrap(digest.digest()).getLong();
    }

    private static void string(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
