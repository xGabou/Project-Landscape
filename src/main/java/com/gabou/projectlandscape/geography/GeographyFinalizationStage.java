/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography;

/** Bulk physical filtering after all point writers finish; publication must occur after this returns. */
@FunctionalInterface
public interface GeographyFinalizationStage<T> {
    /** optionalFilters=false is exclusively legacy preview behavior, not canonical finalization. */
    void apply(T tile, boolean optionalFilters);
}
