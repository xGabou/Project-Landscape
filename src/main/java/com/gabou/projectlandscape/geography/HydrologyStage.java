/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography;

/**
 * Owns network retrieval/reuse/application. R is backend-private per-chunk traversal state;
 * null starts a traversal. Never store mutable traversal state on a shared stage instance.
 */
@FunctionalInterface
public interface HydrologyStage<W extends GeographyWorkspace, R> {
    R apply(W workspace, float x, float z, R previous);
}
