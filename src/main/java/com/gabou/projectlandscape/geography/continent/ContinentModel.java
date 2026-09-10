/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.projectlandscape.geography.continent;

/** Broad continental field; positive profile means continental land. Profile is not nearest distance. */
public interface ContinentModel {
    record ContinentSample(MacroSiteField.Site site,double shorelineProfileBlocks,double interiorStrength) {}
    ContinentSample sample(double x,double z);
}
