# LEGACY_RTF_V0 performance denominator

Production comparator: `cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee`. No production algorithms changed for this measurement. [benchmark_summary.json](evidence/benchmark_summary.json) contains machine-readable statistics; [benchmark_runs.json](evidence/benchmark-final/benchmark_runs.json) contains individual timed batches and counter deltas. These are measured operations, **not Gradle task elapsed times**.

## Environment and protocol

Intel Core i7-13700K, 16 physical / 24 logical cores, approximately 64 GiB RAM, Windows 11 Pro build 26200, high-performance power-plan GUID `8c5e7fda-e8bf-4a96-9a85-a6e23a8c635c`. Temurin 17.0.17+10, 6 GiB maximum heap, Forge 47.4.22 / Minecraft 1.20.1. See [machine.json](evidence/machine.json) and [JVM arguments](evidence/benchmark-final/environment.json). This is a mapped development client with integrated server, normal rendering, and the test-only cache observers enabled. No TerraBlender, PA, or other optional development mods in the timing denominator.

Generation settings and their fingerprint are identical to [the golden baseline](TASK_1C_GOLDEN_BASELINE.md). Timing uses seed **8675309**, 24 workers, tile exponent 3, halo one chunk, batch 6. Measurements do not change tile geometry, seed narrowing, climate or sampling contracts.

Protocol:

1. Proper Forge launch, title screen, export/reload Legacy Default datapack, live registry-backed generation context.
2. Point queries: three discarded warmup repetitions, five measured repetitions per operation; 65,536 calls per repetition. Coordinates `x=(i*37)&127`, `z=(i*71)&127`, in owning tile (0,0), already loaded. One reusable Cell is reset before each call. Each call is timed; every 16th duration is retained (4,096 observations/repetition). This is a fixed hot-region workload, not a uniform geographic survey. Throughput includes loop/timer overhead; latency observations are systematically thinned and should not be interpreted as an all-world distribution.
3. Tile/cold exact: one discarded five-tile warmup repetition, three measured five-tile repetitions; tiles `(80..84, -80)`. Each repetition constructs a fresh context **outside timing**. Only the first tile of each context has cold workspace-pool allocation; subsequent tiles reuse workspace arrays. Cold cache means absent tile entries, not a cold JVM or cold OS page cache. Direct tile mode bypasses TileCache; isolated contexts use `queue=false`.
4. Real chunks: four fresh same-seed worlds, first discarded as warmup, next three measured. Each workload synchronously requests eight FULL chunks, with prerequisites generated normally. Existing spawn generation and world creation are outside timing. Chunk origin formulas are recorded in code and batch details:
   - cold exploration: `(1024+i,1024+i)`;
   - sequential adjacent: `(2048+i,2048)`;
   - scattered: `(3072+64*i,3072)`;
   - warmed owning terrain: `(4096+i,4096)`, owning RTF tile preloaded outside timing; Minecraft chunks and other neighboring tiles are not all preloaded.
5. Hashing, fixture discovery, field serialization, snapshot writing and pool-report construction occur outside timed intervals. Entry observers use LongAdder counters and nanoTime; their overhead is **not subtracted**. Serialization happens only after timed loops. No debugger or sampling profiler is attached by the harness.

The four chunk workloads use **different regions**. Do not interpret their throughput differences as a controlled A/B effect of prewarming or order. They are four fixed future-comparison workloads. Returned FULL chunks were not already FULL at the start of their individual request; neighboring partial status work is expected, especially for adjacent chunks.

All reported percentiles use nearest rank. Point latency pools contain 20,480 retained samples per operation. Tile latency has 15 samples; chunk latency has 24. Their p95 is coarse; p99 is deliberately omitted below 100 samples. Repetition variance is computed from per-repetition mean time per unit, with sample standard deviation, separately from within-run latency variation.

## Measured results

Throughput is the median **timed-batch** throughput, not the reciprocal of median individual latency. The distinction matters for chunks whose first request generates many prerequisites.

| Operation | Median latency | p95 latency | Median throughput | Repetition-mean CV |
|---|---:|---:|---:|---:|
| Raw Heightmap point | 2.700 µs | 3.201 µs | 366,707 queries/s | 0.67% |
| Direct approximate point | 2.700 µs | 3.201 µs | 371,678 queries/s | 0.63% |
| Canonical warm exact point | 0.100 µs | 0.200 µs | 6,219,253 queries/s | 1.48% |
| Opportunistic cached point | 0.100 µs | 0.200 µs | 7,003,655 queries/s | 8.79% |
| Warm tile lookup + cell copy | 0.100 µs | 0.200 µs | 6,262,398 queries/s | 2.19% |
| Filtered tile generation | 25.531 ms | 26.912 ms | 39.17 tiles/s | 1.69% |
| Cold TileCache generation | 24.853 ms | 25.569 ms | 40.24 tiles/s | 0.83% |
| Cold exact point (generates tile) | 24.713 ms | 34.592 ms | 40.46 queries/s | 4.42% |
| FULL cold exploration | 119.997 ms | 712.840 ms | 4.61 chunks/s | 2.96% |
| FULL sequential adjacent | 59.187 ms | 571.801 ms | 8.10 chunks/s | 1.18% |
| FULL scattered | 493.397 ms | 647.051 ms | 1.96 chunks/s | 1.86% |
| FULL warmed owning terrain | 71.195 ms | 1,723.172 ms | 3.50 chunks/s | 0.73% |

Sub-microsecond warm-query figures approach the clock/loop granularity; prefer batched throughput and repeated measurements for regression decisions. The 8.79% opportunistic-query variance is too close to the proposed 10% gate for a single small slowdown to be decisive. Direct/raw comparisons this close do not establish that one implementation is intrinsically faster.

FULL timings include Minecraft density evaluation, surfaces, carvers, features, structures, lighting/status dependencies, and neighboring chunks. They are **not isolated terrain-generation time**. RTF tile generation is separately measured above. Cache entry inclusive wait time is an observer metric and can overlap across threads; do not subtract its sum from wall time or call every entry get a blocking join.

## Cache, allocations and memory

[cache_metrics.json](evidence/benchmark-final/cache_metrics.json) records cache entry occupancy, published-cell count upper bounds, whole-JVM used heap, and both array pools' allocated/borrowed/returned/live/pooled counts. Per-batch counter deltas include cache present hits/misses, provides, queue requests, drops, threshold releases, tile generation submissions, entry gets, initially pending entry gets and inclusive entry-get nanoseconds. Queue **requests** are not necessarily queued tile tasks; generation-call count identifies actual submitted tiles (36 cell batches per tile).

The explicit 64-drop exercise verifies an actual absent entry followed by regeneration. Successful isolated-context shutdown observations have zero live workspace borrows. Published snapshots are detached, nonpooled arrays; a pool return does not mean their cells have been garbage-collected. Occupancy counts include possible pending entries, so `entries * 25,600` is an upper bound on that cache's published-cell count, not an exact retained-byte measurement.

ThreadMXBean process-wide allocation deltas are reported per tile/query/FULL chunk in the summary. They include renderer, client, Minecraft workers and observer/measurement overhead; **do not treat them as isolated generator allocations**. A separate [allocation probe](evidence/allocation/allocation_probe.json) discovers all 24 existing RTF executor workers without replacing the executor. For tile (80,−80), after one warmup, worker-plus-caller allocations in three repetitions were **2,996,952; 2,996,984; 2,996,952 bytes/tile** (median approximately 2.858 MiB). This excludes renderer and other Minecraft chunk workers, but includes measurement/observer allocations and any other RTF work on those workers. Its short-run timing is not substituted for the primary, better-warmed throughput denominator.

No dominator retained-size or heap-dump analysis was performed. Peak **observed process heap** and maximum observed cache occupancy are reported, but exact peak retained cache bytes and isolated total allocations per FULL chunk remain unmeasured. Those limitations are explicit nulls, not zero allocations or evidence of no memory cost. The normal live-client benchmark is not a profiler substitute.

In this run the maximum sampled used heap was **4,603,099,384 bytes** (about 4.287 GiB); maximum sampled cache occupancy was **223 entries**, or at most **5,708,800 published cells** for that cache. These are sampled maxima, not continuously profiled peaks or retained-memory measurements. All 12 isolated tile-context shutdown observations had zero live workspace borrows.

Early `benchmark-24` and `pilot-short-queries` runs are retained as **pilots**, excluded from the denominator. The latter's short loops had high variance. The final longer-batch protocol improves raw/direct repetition CV to below 1%. An initial summary unit-classification error was corrected before publishing the final summary; a cold exact query is one tile-generating query, not 4,096 queries.

## Reproduction

```powershell
.\gradlew.bat runReproductionClient -PreproProfile=benchmark
scripts/task1c/Collect-Run.ps1 -Name <new-benchmark-run>
scripts/task1c/Summarize-Benchmarks.ps1 -Run <new-run> -Output <new-summary.json>
.\gradlew.bat runReproductionClient -PreproProfile=allocation
```

Do not run concurrent Gradle/game instances during measurement. Match machine/power plan, JVM/heap, instrumentation, Forge/mod set, warmup, region, seed and queue policy before comparing. Absolute results from workers=2/48 digest runs are not mixed into this 24-worker performance denominator.

## Future engineering gates — not judgments about future code

- Extraction-only: target no more than about **10%** throughput/allocation regression outside measured variance.
- Complete Phase 1: more than about **25%** added generation time or retained memory requires explicit design review.
- Approximately **2×** generation-time regression is an unacceptable signal.

These budgets require comparable measurements and sufficient precision. This task establishes the denominator, not a new architecture or a reason to accept unmeasured memory growth. The deferred FULL-block variance investigation is in [FULL_CHUNK_VARIANCE.md](FULL_CHUNK_VARIANCE.md); canonical RTF geography, not decorative block equality, is the extraction comparator.
