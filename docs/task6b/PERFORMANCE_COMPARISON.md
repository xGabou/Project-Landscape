# Task 6B performance comparison

The accepted exact optimizations improve all five acquisition scenarios and both
comparable FULL pairs. Cold V1 FULL generation remains far above the historical
+25% overhead goal. Task 6B closes the measured duplicate-work/cache scope;
a dedicated terrain-generation task is recommended before making any broader
performance acceptance claim.

## Three paired acquisition runs

These are cold-plus-warm canonical climate acquisitions, not FULL chunks.
JDK 17.0.17+10, 8 available processors, 6 GiB heap, seed 8675309, fixed preset
and exact climate configuration are held constant. The baseline precedes optimized
in each pair; runs are serialized. All completed pairs remain represented.
No randomized-order or statistical-confidence claim is made with three pairs.

| Scenario | Median baseline s | Median optimized s | Median paired reduction | Paired range |
|---|---:|---:|---:|---:|
| sequential_generation | 17.997 | 11.606 | 31.9931% | 29.77–35.53% |
| spawn_expansion | 13.197 | 8.652 | 31.3008% | 26.95–35.77% |
| scattered_generation | 114.347 | 65.669 | 37.2457% | 29.71–42.57% |
| interleaved_regions | 39.035 | 23.807 | 36.2310% | 30.28–42.66% |
| climate_heavy | 8.347 | 5.593 | 31.7852% | 29.32–36.13% |

Median paired percentages are computed within each pair, not from the two median
times. Every persisted output matches all seven raw-double fields. The authoritative
[paired comparison](evidence/paired_comparison.json) retains individual runs,
ranges and sample standard deviations. Historical first-pair evidence remains
under `acquisition-pair-01/`; all three pairs are under `acquisition/`.

## Original 12-chunk FULL corpus

Each row measures new FULL requests and their prerequisite neighboring generation.
All 12 are new in each run. Output hashing and canonical validation are excluded
from the timers. Median, p95, min, max and sample SD below are **milliseconds**;
total is seconds. Throughput is requested new chunks per second, not the number
of all neighboring chunks actually generated.

| Run | Version | Total s | Median ms | p95 ms | Min ms | Max ms | Sample SD ms | Chunks/s |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| baseline-original | V1 | 216.893 | 1535.469 | 52061.602 | 95.196 | 52061.602 | 22211.211 | 0.05533 |
| baseline-original | legacy | 9.076 | 647.573 | 2176.418 | 83.329 | 2176.418 | 725.422 | 1.32223 |
| baseline-repeat | V1 | 211.196 | 1514.902 | 50042.382 | 108.866 | 50042.382 | 21528.128 | 0.05682 |
| baseline-repeat | legacy | 8.590 | 611.028 | 2185.204 | 70.376 | 2185.204 | 696.627 | 1.39697 |
| baseline-extended | V1 | 211.890 | 1602.484 | 50317.644 | 88.842 | 50317.644 | 21646.201 | 0.05663 |
| baseline-extended | legacy | 9.382 | 673.193 | 2281.711 | 73.494 | 2281.711 | 755.262 | 1.27899 |
| optimized-original | V1 | 112.883 | 1266.293 | 29111.115 | 100.582 | 29111.115 | 11489.594 | 0.10630 |
| optimized-original | legacy | 8.166 | 580.688 | 2057.188 | 70.418 | 2057.188 | 666.755 | 1.46959 |
| optimized-extended | V1 | 146.423 | 1509.445 | 35444.508 | 106.831 | 35444.508 | 14972.794 | 0.08195 |
| optimized-extended | legacy | 8.669 | 631.841 | 2105.570 | 71.893 | 2105.570 | 689.155 | 1.38432 |
| optimized-24 | V1 | 129.273 | 1566.470 | 30424.013 | 98.529 | 30424.013 | 12881.055 | 0.09283 |
| optimized-24 | legacy | 9.070 | 580.274 | 2029.540 | 101.929 | 2029.540 | 667.568 | 1.32306 |
| optimized-48 | V1 | 114.849 | 1636.111 | 28320.234 | 94.331 | 28320.234 | 11430.546 | 0.10449 |
| optimized-48 | legacy | 8.471 | 603.412 | 2066.833 | 99.831 | 2066.833 | 666.316 | 1.41661 |

Nearest-rank p95 for 12 or 14 observations equals the maximum. This is a descriptive
tail of this small fixed corpus, not an estimate of the population p95. The very
low median relative to the total demonstrates why median-only reporting is
misleading here. Exact values and process CPU totals are in
[full_chunk_performance.json](evidence/full_chunk_performance.json).

The original paired V1 reduction is **47.9544%** (216.892802 → 112.8832562 s).
The repeat comparable 12-point pair improves **30.6699%**
(211.1963163 → 146.4225125 s). The additional baseline's first 12 total
211.8897673 s, supporting baseline stability.

## Extended 14-point coverage and high-worker observations

The two appended points are (-7969, -8369), verified MOUNTAIN/stony peaks, and
(-1734, -8369), coastal-margin forest with signed macro shoreline profile
+55.51114444556572 blocks. These classifications come from canonical output
evidence and the retained Task 4 fixtures. They are not inferred from biome labels
alone. The original adjacent mountain point (6144, 6240) remains included.

| Run | V1 total s | Legacy total s | V1 chunks/s | V1 max/p95 ms |
|---|---:|---:|---:|---:|
| baseline-extended | 247.782 | 11.284 | 0.05650 | 50317.644 |
| optimized-extended | 169.834 | 10.462 | 0.08243 | 35444.508 |
| optimized-24 | 152.367 | 10.654 | 0.09188 | 30424.013 |
| optimized-48 | 134.659 | 10.192 | 0.10397 | 28320.234 |

The like-for-like extended pair improves **31.4583%**
(247.7816504 → 169.8338428 s). Its frozen baseline was run after the earlier
series to close missing coverage; it is a contemporary fixed-corpus comparison,
not a randomized experiment. No 12-point baseline total is compared to a 14-point
optimized total.

In this one extended series 24 workers take 152.367 s and 48 take 134.659 s,
both faster than the 169.834 s eight-worker repeat. Forty-eight was not slower
than 24 in these recordings. However, the original 8-worker common-12 run
(112.883 s) is slightly faster than the 48-worker common-12 result (114.849 s).
Single runs and the eight-worker variance do not justify a preferred maximum
worker setting or a reliable scaling curve.

## Variance and tail attribution

The two optimized eight-worker common-12 totals differ by **33.5392563 s**
(**29.7115%** relative to the faster run); sample SD is 23.7158356 s.
Baseline common-12 totals range from 211.1963 to 216.8928 s. Both optimized
runs are retained; neither is discarded as an outlier.

The main workload signal is broad cold terrain acquisition. Per-request terrain
generation counts correlate with wall time at Pearson r = 0.984–1.000 across
the seven observed runs. These correlations describe this corpus only.
The optimized eight-worker extended run provides concrete examples:

| Index | Block X/Z | Canonical landform / surface biome | Tile generations | FULL seconds |
|---|---|---|---:|---:|
| 0 | (8192, 0) | OCEAN / deep_lukewarm_ocean | 16 | 2.294 |
| 1 | (8208, 0) | OCEAN / deep_lukewarm_ocean | 0 | 0.131 |
| 2 | (8224, 0) | OCEAN / deep_lukewarm_ocean | 0 | 0.131 |
| 3 | (8240, 0) | OCEAN / deep_lukewarm_ocean | 0 | 0.107 |
| 4 | (6128, 6240) | PLAINS / plains | 404 | 19.072 |
| 5 | (6144, 6240) | MOUNTAIN / stony_peaks | 4 | 0.438 |
| 6 | (-28672, -12288) | OTHER / plains | 641 | 35.445 |
| 7 | (-28656, -12288) | OTHER / plains | 0 | 0.121 |
| 8 | (-8192, -150000) | PLAINS / old_growth_pine_taiga | 631 | 29.107 |
| 9 | (8192, 60000) | OTHER / windswept_hills | 482 | 24.549 |
| 10 | (-8192, -500000) | OTHER / grove | 514 | 34.303 |
| 11 | (0, -2000000) | OCEAN / deep_frozen_ocean | 16 | 0.725 |
| 12 | (-7969, -8369) | MOUNTAIN / stony_peaks | 12 | 1.032 |
| 13 | (-1734, -8369) | OTHER / forest | 472 | 22.379 |

Cold land requests cause 404–641 terrain generations for an exact canonical
climate profile plus required neighbors. Adjacent requests cause zero to four.
Point geography alone does not determine cost: the verified mountain extension
takes about 1.03 s while the coastal-margin forest takes 22.38 s. The prior
mountain point follows nearby acquisition and takes about 0.44 s. First-time
climate profiles trigger acquisition, but climate equations and resolver scoring
barely appear in the execution samples.

Tile counts for the comparable slow points are effectively unchanged between
optimized runs: 404, about 642, 631, 482 and 514. Broad acquisition is expensive
in both runs; the slower repeat is not explained by substantially more generated
tiles. Process CPU for common-12 increases from 485.578 to 620.141 s while
average process CPU/wall remains about 4.3/4.2. This supports increased execution
cost for a similar workload rather than a new large monitor stall.

GC pauses total 1.269 s in the original optimized recording and 1.746 s in the
extended repeat; even the latter's whole-recording pauses cannot explain the
33.54 s common-12 gap. Compiler-thread durations are 12.006 and 17.168 s,
but recordings differ in scope and compiler work overlaps generation. These do
not isolate JIT state as the cause. Fresh worlds start after spawn generation,
so “cold” refers to each requested region, not a pristine JVM or empty global
runtime. Cache eviction, terrain scheduling, JIT/code quality, CPU scheduling
and thermal state were not experimentally separated. Thermal state was not
recorded. Extra feature/block variation exists, but its very small sampled share
does not establish it as the dominant cause.

The defensible conclusion is that terrain execution/acquisition dominates and
FULL throughput is variable; the exact cause of the eight-worker speed difference
remains unresolved. No semantic or cache-policy regression is demonstrated.
A new factorial warmup/CPU-affinity investigation is outside this stabilization
task. Per-point counters and their scope are retained in
[full_tail_analysis.json](evidence/full_tail_analysis.json): counters bracket
requests plus hashing, whereas timing brackets only `getChunk`.

## V1 versus contemporary legacy

The +25% goal is **not met**. These same-JVM comparisons use the same coordinates
and seed but intentionally different version-specific terrain. V1 runs first;
the experiment does not control version-order effects.

| Optimized run | Points | V1 s | Legacy s | V1 time overhead |
|---|---:|---:|---:|---:|
| optimized-original | 12 | 112.883 | 8.166 | 1282.44% |
| optimized-extended | 14 | 169.834 | 10.462 | 1523.29% |
| optimized-24 | 14 | 152.367 | 10.654 | 1330.18% |
| optimized-48 | 14 | 134.659 | 10.192 | 1221.26% |

The 8-worker repeat's common-12 comparison is also retained in the full statistics
(146.4225 versus 8.6685 s). Overall overhead is roughly +1,221% to +1,523% in
the final optimized full-corpus runs, far beyond +25%. Historical Task 1C/Task 4
microbenchmarks are context, not denominators for these contemporary FULL ratios.
The dominant remaining work is terrain noise plus erosion/filtering multiplied by
broad exact acquisition; resolver scoring is not the main cost.

## JFR before and after

The frozen original [before summary](evidence/jfr_before_summary.json) is preserved.
[After summary](evidence/jfr_after_summary.json) includes all seven FULL runs,
both versions, and all acquisition pairs. Full stack/duration summaries reside
under `evidence/profiles/`; original JFR recordings remain at their recorded
local paths.

| Acquisition profile | All samples | Erosion/filtering | Terrain noise | Tile construction |
|---|---:|---:|---:|---:|
| baseline-1 | 13286 | 7346 | 5101 | 761 |
| baseline-2 | 12707 | 3355 | 5528 | 3766 |
| baseline-3 | 14986 | 8006 | 6030 | 881 |
| optimized-1 | 10047 | 4044 | 5321 | 626 |
| optimized-2 | 9158 | 3871 | 4726 | 503 |
| optimized-3 | 11058 | 4357 | 6020 | 612 |

The baseline pair-2 “tile construction” category includes 3,156 samples whose leaf
is `ITerrain.Delegate.erosionModifier`, called by the erosion filter. The existing
leaf-first classifier assigns that leaf to `cell.*`. This explains the anomalously
low 3,355 filtering count; it is not evidence of a faster baseline filter. Counts
are preserved under their original methodology. Different sampled leaf methods,
JIT inlining and stack attribution prevent exact CPU-percentage claims.

| V1 FULL profile | Erosion/filtering samples | Terrain noise samples | Features samples | Resolver samples | GC pauses s | Compiler thread-s |
|---|---:|---:|---:|---:|---:|---:|
| baseline-original | 12532 | 6003 | 30 | 3 | 1.405 | 12.089 |
| baseline-repeat | 12267 | 6472 | 27 | 4 | 1.360 | 11.693 |
| baseline-extended | 14246 | 7095 | 28 | 7 | 1.611 | 13.449 |
| optimized-original | 6292 | 4898 | 25 | 1 | 1.269 | 12.006 |
| optimized-extended | 9861 | 8854 | 24 | 1 | 1.746 | 17.168 |
| optimized-24 | 9831 | 5339 | 35 | 1 | 1.443 | 40.156 |
| optimized-48 | 8022 | 4987 | 30 | 0 | 1.505 | 54.942 |

The original comparable FULL samples fall from 12,532 to 6,292 for filtering and
6,003 to 4,898 for noise. The 14-point baseline versus optimized repeat records
14,246 versus 9,861 filtering and 7,095 versus 8,854 noise samples. Acquisition
now consistently samples noise more than filtering in optimized runs; FULL can
still sample filtering more. Both remain dominant. Corpus size and runtime
variation forbid interpreting sample-count deltas as exact CPU savings.

## Monitor contention, parking and capacity

Later FULL JFRs explicitly record monitor entries over 1 ms. Default profile
ThreadPark threshold is 10 ms in the installed JDK 17 profile. The original run
also used a 10 ms monitor threshold and lacks the later stricter monitor setup;
zero observed events there would not prove zero contention.

| Run | ExactCache monitor events | ExactCache blocked thread-s | ExactCache waits without inner terrain frame, thread-s | Terrain waits inside loaders, thread-s | IslandModel monitor thread-s |
|---|---:|---:|---:|---:|---:|
| optimized-extended | 26 | 0.051980 | 87.399 | 200.133 | 1.458 |
| optimized-24 | 253 | 1.099103 | 89.315 | 175.741 | 28.539 |
| optimized-48 | 128 | 1.322220 | 61.987 | 153.713 | 73.991 |

The ExactCache monitor itself is a small recorded cost. Same-key reuse is real:
the source snapshots count 1,087/1,325/1,081 surface followers after the respective
14-point corpora. Per-key waits can be long while their shared terrain result is
computed. They are coordination on required work, not evidence that loaders hold
the broad monitor. Nested terrain-cache waits must not be called pure ExactCache
follower contention. The finer stack attribution distinguishes them; finite
recorded stacks still limit perfect attribution.

Most “other” parking comes from idle ForkJoinPool workers or executor queues.
It cannot be counted as busy CPU work or directly subtracted from throughput.
IslandModel monitor blocking grows to 28.539 and 73.991 thread-seconds at
24/48 workers, a separate high-concurrency terrain/geography constraint for a
future investigation. The coarse distance field's unchanged lock is small here.

All observed production `capacityWaits` are zero. Peak pending sizes are surface
9/1024, climate 49/4096, geography at most 8/4096, winners 49/4096. Pending maps
never reach capacity in these measurements. Generic small-capacity stress may
exercise capacity blocking; it is not production evidence for a redesign.
Retain ExactCache policy and current bounds.

## Exact outputs, loaded retrieval and limitations

All 14 canonical geography values, finalized tile digests, seven raw climate
fields, surface winners and shoreline profiles match frozen baseline exactly.
Every compared stored V1 and legacy biome digest matches. The differences below
are **block digests**, retained as diagnostics:

| Comparison | V1 differing chunks | Legacy differing chunks | Stored biome differences |
|---|---:|---:|---:|
| baseline-original → baseline-repeat | 2 | 5 | 0 |
| baseline-repeat → baseline-extended | 3 | 3 | 0 |
| baseline-original → optimized-original | 3 | 6 | 0 |
| baseline-extended → optimized-extended | 1 | 5 | 0 |
| optimized-original → optimized-extended | 1 | 4 | 0 |
| optimized-extended → optimized-24 | 5 | 9 | 0 |
| optimized-extended → optimized-48 | 3 | 8 | 0 |

Frozen-repeat and unchanged-legacy differences demonstrate preexisting FULL
block/feature nondeterminism. This does not identify every individual block cause.
No Task 6B attribution is made without isolation.

Already-loaded retrieval after validation measures 0.00035 ms median baseline and
0.00040 ms optimized (14 points each); maxima 0.00050/0.00060 ms. These tiny
single-call intervals are near timer overhead and are not generation throughput.
Chunk object identity is asserted. See [loaded access](evidence/already_loaded_chunks.json).

Surface arrays at capacity are exactly 128 MiB of primitive elements. The five
completed detached caches estimate 137,154,920 bytes (130.8011 MiB) under compressed
reference/layout assumptions. This excludes terrain TileCache, workspace pools,
pending loads, transient workspaces and unmodeled overhead; it is not total JVM
worldgen memory. See the cache report for exact counts and exclusions.

Limitations: small fixed corpora and few repeats; nonrandom version/run ordering;
fresh-region versus already-warmed JVM effects; unresolved execution-cost variance;
sampled/thresholded JFR; no exclusive per-feature timings; known block nondeterminism;
no TPS or total retained-heap measurement; minimum two terrain workers even for a
one-processor request. Task 6B is sufficient to stop its cache/duplicate-work
optimizations. A dedicated terrain task is recommended, without weakening exact
generation or automatically starting Phase 2.
