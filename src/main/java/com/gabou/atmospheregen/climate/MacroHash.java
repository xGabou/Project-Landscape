/* Original Project Atmosphere companion architecture. All Rights Reserved. */
package com.gabou.atmospheregen.climate;

/** Small deterministic hash utility kept separate from climate equations. */
final class MacroHash {
    private MacroHash() {}
    static long mix(long x) { x=(x^(x>>>30))*0xbf58476d1ce4e5b9L; x=(x^(x>>>27))*0x94d049bb133111ebL; return x^(x>>>31); }
    static double unit(long x) { return (mix(x)>>>11)*0x1.0p-53; }
}
