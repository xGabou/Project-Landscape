/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.geography;

/** Macro land/site fields; no river-network or climate ownership. Float coordinates preserve legacy zoomed previews. */
@FunctionalInterface
public interface ContinentStage<W extends GeographyWorkspace> {
    void apply(W workspace, float x, float z);
}
