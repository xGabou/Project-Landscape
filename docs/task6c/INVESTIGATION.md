# Task 6C investigation (in progress)

Baseline: `39c597cb605c051ff0c0f484d65a50ec47120496`. No Phase 2 work.

## Dependencies

- Climate: one local sample plus 94 upstream samples, d=24000,23744,...,192; the loop does **not** reach d=0. Each point requires its own exact elevation. Wind direction varies with query coordinates. Adjacent quart queries therefore sweep a corridor, not a single shared line. FULL generation requests prerequisite chunks as well as the target.
- Canonical surface: 128 x 128 core, owning tile by arithmetic shifts; 16-block halo each side in the default preset gives a 160 x 160 workspace (25,600 cells per load). Surface extraction retains two 128 x 128 float arrays. No terrain-to-terrain neighboring tile recursion: each workspace contains its own halo.
- Raw terrain and macro: coordinate functions, no neighboring finalized tile reads. Noise-domain warps and octave lattice neighborhoods are internal dependencies, not tile acquisition.
- Erosion noise: 3 x 3 candidate sites, each choosing among 3 x 3 neighbors; union is 5 x 5 lattice sites per octave. The existing 25-float cache avoids repeated input noise within an octave, but lattice coordinates/hashes are recomputed, and the cache is cleared at every octave/sample. General input graphs cannot be presumed pure with respect to cell state.
- Hydraulic erosion: bilinear 2 x 2 height/gradient reads, radius-4 erosion brush (strict squared distance <16), up to the configured lifetime (12 default) unit trajectory steps. Droplets read heights modified by earlier droplets. A single-drop radius does not bound whole-pass causal propagation. Cropping the workspace changes seed traversal, trajectories and cumulative mutations.
- Smoothing: rounded loop radius around a circular weighted kernel; original Z-major/X-major in-place scan. Earlier updated neighbors are read later in the same pass. Neither a separable convolution nor a prefix-sum substitution preserves that behavior or float association.
- Steepness: offsets -1..2 in each axis times radius (default 1); no height writes.
- Beach detection: four cardinal height samples at distance 8; boundary absent samples substituted by the center.
- Noise correction: aligned 4 x 4 quart neighborhood; terrain label mutation only.
- Final shoreline: coordinate-local macro classification overwrites marine height and clears mountain contributions after every other filter. This gives a potential exact marine climate projection without tile acquisition; land remains dependent on canonical filtering.
- Coarse distance: independent macro-only 128-block lattice, 32 x 32 core with 64-node halo; never acquires terrain.

## Synchronization and lifecycle

IslandModel's access-order LinkedHashMap, hits/misses, geometry construction and eviction all use the same instance monitor. Lists are already immutable. A cache hit mutates access order, so immutable values alone cannot remove the monitor. Geography generation calls the model repeatedly per cell; up to 48 workers converge on the same monitor. Any proposed shortcut must preserve bounded retention and clear behavior.

Terrain batch workers execute raw geography. Finalization is an allOf completion callback, then snapshot publication. Climate acquisition waits on terrain from outside that raw stage; no raw terrain/filter callback asks climate for another tile. This directed dependency rules out recursive climate/terrain future inversion in the inspected path. Executor queue waits and terrain consumers waiting for batches are different from ExactCache followers waiting for the same key. JFR thresholded parking durations overlap across threads and are not CPU time.

## Measurement

Fresh unmodified baseline acquisition: `evidence/baseline-acquisition-01`. Same five Task 6B scenarios and parameters. Diagnostics enabled only with `-Ptask6cMetrics=true`; nested stage durations overlap. Static aggregate counters retain no world or coordinate references; fixed enum cardinality. Instrumented timings are not used as uninstrumented performance baselines.

Smoothing experiment compares detached identical finalized snapshots, alternating candidate/reference order, two warmup passes and seven measured passes at six fixtures. All 33,177,600 public field comparisons are exact. First measured aggregate: 44.0633 ms reference, 17.6551 ms candidate. This measures smoothing alone, not total generation. No production smoothing change yet.

This is an investigation checkpoint, not Task 6C acceptance or a Phase 2 recommendation.
