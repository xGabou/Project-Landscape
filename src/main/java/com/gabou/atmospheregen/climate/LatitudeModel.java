/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.climate;

import com.gabou.atmospheregen.config.BaselineClimateConfig;

/** World-Z latitude with asymptotic pole clamping and no repeating bands. */
public final class LatitudeModel {
    private final double equatorZ, scale;
    public LatitudeModel(BaselineClimateConfig.Planned config) { equatorZ=config.equatorZ(); scale=config.latitudeScaleBlocks(); }
    public double degrees(int z) { return degrees((double)z); }
    public double degrees(double z) {
        double normalized=(z-equatorZ)/scale;
        return 90.0 * normalized / (1.0 + Math.abs(normalized));
    }
    public double absoluteDegrees(double z) { return Math.abs(degrees(z)); }
}
