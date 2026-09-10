/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography;

/** Regional terrain and continental height blending. Receives unscaled world coordinates. */
@FunctionalInterface
public interface TerrainStage<W extends GeographyWorkspace> {
    void apply(W workspace, float x, float z);
}
