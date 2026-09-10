# Task 6B cache architecture

Production owns one canonical `PaGeographyProvider` and one `CanonicalClimateService`
per bound `GeneratorContext`. The service owns `CanonicalClimateGeography`,
`PaBaselineClimateProvider`, and the temperature-only `BaselineClimateModel`;
the provider encapsulates its full-climate model. Both use the identical accepted
configuration. Installation, the biome source, and climate diagnostics acquire this
hierarchy through the context. The canonical geography and climate geography share
one distance field. Test reference providers are explicitly outside these production
ownership counts.

`provider_instance_counts.json` verifies one geography provider, climate provider,
surface cache and distance field, plus identity checks. Services are world-instance
scoped, never stored in a global world cache. Recreation gets fresh instances even
when seed and persistent identity are identical.

`ExactCache<V>` separates a bounded completed-value LRU from a bounded pending-load
map. Exact packed integer coordinate keys preserve negative coordinates. A short
monitor section performs lookup and ownership election; expensive loaders run outside
that monitor. Same-key callers join one pending future. Completed entries retain the
value without a future. Failures remove pending entries and allow retry. Closing
clears completed values, fails pending loads and rejects subsequent use. The existing
capacity policy bounds both maps; `peakInFlight` and `capacityWaits` measure whether
unrelated missing keys actually encounter the pending limit.

| Cache | Bound | Exact key | Work avoided |
|---|---:|---|---|
| Surface fields | 1,024 | owning 128-block tile X/Z | Filtered terrain acquisition and two-array surface extraction |
| Climate baselines | 4,096 | block X/Z | Exact 24,000-block climate profile and climate equations |
| Surface geography | 4,096 | quart X/Z | Canonical physical geography and detailed landform lookup across Y |
| Surface winners | 4,096 | quart X/Z | Surface temperature override or climate/resolver work across Y |
| Coarse distance fields | 32 | owning lattice tile X/Z | Macro lattice classification and Euclidean distance transforms |

Every biome query first obtains its column geography and evaluates retained cave
selection with that query's Y. Only then can a strictly 2D surface winner be returned.
The source reuse regression checks all three retained cave families, multiple Y values,
and direct expected winners with real registry holders. Its repeated-query timing is
a microbenchmark, not FULL chunk throughput.

The distance cache retains its existing synchronized implementation. It has no
pending-load map, so pending-flight counts do not apply. Its map entries and exact
array payload are reported separately from the four `ExactCache` instances.

`TileCache.onClose` registers cleanup at the context's existing disposal boundary.
Closing the terrain cache clears the surface fields, climate results, distances, and
both source caches. Tests deliberately retain Java references after closing and check
payload clearing and rejected queries. Runtime evidence additionally checks actual
world unload and zero live pooled borrows. Neither proves GC unreachability, and
returned pool arrays can remain reachable through a deliberately retained context.

The surface field arrays contain exactly 131,072 primitive bytes per entry, or
134,217,728 bytes (128 MiB) at capacity. The distance arrays add at most 262,144
primitive bytes. Winner values reference existing registry holders; there are no
4,096 newly allocated biome objects or large arrays to charge to this cache.

`cache_memory_summary.json` separates exact primitive payload from estimated JVM
object and map overhead. Under the documented compressed-reference layout, all five
completed caches at their bounds total approximately 130.80 MiB, excluding pending
storage and common services. This is not total world memory. The original 256-entry
terrain cache and 100-array workspace pool are separately accounted for; their
occupancy, expiry, and lifetime differ from detached climate/surface caches.

The cache-layer audit and final production hit/miss/wait/eviction statistics are
recorded in `production_cache_metrics.json`. Climate result reuse must be assessed
both through the shared API and through the source: a winner hit bypasses the climate
cache, so a zero climate-cache hit rate in FULL generation does not imply that exact
climate results have no reuse in acquisition or direct diagnostic access. The
surface field and GeoSample caches hold different values and avoid different work.
No capacity or cache algorithm is changed solely from a synthetic churn benchmark.

## Final production observations

Hit rates use hits / (hits + misses + same-key waits). Counts are cumulative
per world instance. The original run predates peak/capacity telemetry; its missing
values are not silently filled with zero. Comparable snapshots immediately after
the timed corpus show:

| Run | Geography hit rate | Winner hit rate | Surface waits | Surface peak | Climate peak | Geography peak | Winner peak |
|---|---:|---:|---:|---:|---:|---:|---:|
| optimized-original | 98.655901% | 98.675433% | 1233 | not recorded | not recorded | not recorded | not recorded |
| optimized-extended | 98.632413% | 98.656019% | 1087 | 9 | 49 | 5 | 49 |
| optimized-24 | 98.632754% | 98.656387% | 1325 | 9 | 49 | 8 | 49 |
| optimized-48 | 98.631198% | 98.654703% | 1081 | 9 | 49 | 8 | 49 |

All telemetry-enabled runtime snapshots have zero capacity waits and zero load
failures. Pending maps never reach their bounds: observed maxima are 9/1024,
49/4096, 8/4096 and 49/4096 respectively. Climate/winner concurrency need not equal
the terrain pool size: Minecraft callers and composed loads use additional threads.
Generic bounded stress tests are separate from these production observations.

The 48-worker run's final snapshot after the additional untimed locality chunk is:

| Cache | Hits | Misses | Same-key waits | Evictions | Entries before unload | Peak in flight |
|---|---:|---:|---:|---:|---:|---:|
| surface | 591255 | 3710 | 1223 | 2686 | 1024 | 9 |
| climate | 0 | 6062 | 0 | 1966 | 4096 | 49 |
| geography | 2103366 | 29338 | 0 | 25242 | 4096 | 8 |
| winners | 1928442 | 26360 | 0 | 22264 | 4096 | 49 |

Its distance field records 596,112 hits, 76 misses and 32 retained entries
(44 derived evictions before clear), with 262,144 primitive array bytes.
The distance loader retains its existing serialized policy, so no pending-map
or single-flight counter applies. Distance failures are not separately instrumented;
no failed runtime query was observed.

After actual unload the retained source reports distance/surface/climate/geography/
winner entries all zero, all four in-flight counts zero, surface/distance array
bytes zero and post-close queries rejected. Historical cumulative counters remain
available; clearing them is not required for payload disposal. Actual server/world
unload records zero live pooled borrows. Full exact snapshots, including the
8-worker validation phase and reopened world, are in
[runtime_disposal.json](evidence/runtime_disposal.json).

In later JFR recordings the ExactCache monitor has 0.0519804 summed blocked-thread
seconds at 8 workers, 1.099103 at 24 and 1.3222197 at 48. These overlap across
threads and are not wall or CPU percentages. Same-key waits and nested terrain
waits are much larger and separately attributed in the performance report.
Production single-flight is demonstrated by the actual follower counts, but the
counts alone cannot assign a speedup: the former synchronized cache already
deduplicated same-key misses inside each instance. No further cache redesign is
justified by these measurements. IslandModel contention at higher worker counts
belongs to a future terrain/geography investigation.

The precise estimated completed-cache total is 137,154,920 bytes = 130.8011246 MiB.
Exact surface array elements alone are 134,217,728 bytes = 128 MiB; headers and
maps belong to the estimate. Primitive scalar record fields are separately
enumerated in the memory JSON and must not be called array payload. Shared biome
Holder objects are not duplicated per winner entry. Estimates of the unchanged
terrain cache and workspace pool are separate capacity models, not observed heap
occupancy or additional Task 6B allocations.
