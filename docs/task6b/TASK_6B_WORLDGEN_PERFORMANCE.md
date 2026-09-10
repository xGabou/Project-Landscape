# Task 6B — Phase 1 worldgen performance stabilization

Task 6B closes the scoped duplicate-work, service-ownership and cache-coordination
work. Generation semantics remain exact. The original goal of V1 FULL generation
within +25% of legacy is **not met**. Cold canonical terrain acquisition warrants
a separate Task 6C. No Task 6C or Phase 2 implementation is included. Phase 2
correctness prerequisites are preserved; accepting Phase 1 performance requires
explicitly deferring the unmet goal.

The final gate record is [final_summary.json](evidence/final_summary.json).
Timings and limitations are in [PERFORMANCE_COMPARISON.md](PERFORMANCE_COMPARISON.md).
Ownership and memory are in [CACHE_ARCHITECTURE.md](CACHE_ARCHITECTURE.md).

## Production implementation

V1 erosion reuses invariant terrain/region/river strength once per filter
application. Height-dependent calculations and floating-point operation association
remain intact. Legacy retains its original modifier path.

Each bound `GeneratorContext` owns one `PaGeographyProvider` and one
`CanonicalClimateService`, including `CanonicalClimateGeography`,
`PaBaselineClimateProvider` and `BaselineClimateModel`. Installation,
the biome source and debug commands share the hierarchy. Verification counts
exactly one geography provider, climate provider, surface cache and distance
field; reference providers used by tests are outside production ownership.

Four instance-scoped `ExactCache` instances use bounded completed-value LRU maps
and per-key pending loads. Loaders execute outside the monitor. Completed values
retain no futures; failed loads retry; closed caches reject access. World
recreation gets fresh instances. `ClimateBiomeSource` caches exact quart-X/Z
geography and surface winners, evaluating retained Y-dependent cave delegation
before consulting the winner. Cached surface results return the same registry
`Holder` instance as direct lookup.

No climate equation, terrain-noise graph, biome ranking, manifest guard, surface
rule or structure rule was weakened. The later production edit adds only
`peakInFlight` and `capacityWaits` telemetry. Resume-session edits concern
harnesses, analysis and documentation.

## Correctness and lifecycle

| Gate | Result | Evidence |
|---|---|---|
| Erosion | 33,177,600 public-field comparisons exact on real finalized snapshots | [erosion](evidence/erosion_production_regression.json) |
| Canonical tiles | Six tiles × five processor requests; optimized/reference/cross-worker exact | [determinism](evidence/canonical_determinism.json) |
| Task 4 | Cold/warm/reverse/reconstructed/worker digest exact | [geography](evidence/geography_regression.json) |
| Task 5 | Temperature, rainfall, evaporation, moisture, orography, shadow, transects and distribution exact | [climate](evidence/climate_regression.json) |
| Task 6 | Descriptors/catalog, assertions, canonical winners and determinism exact | [biomes](evidence/biome_regression.json) |
| Acquisition | All seven climate doubles raw-bit exact across three pairs and five scenarios | [pairs](evidence/paired_comparison.json) |
| FULL | Geography, finalized tiles, raw climate and surface winners exact at 14 points; stored biome digests exact across compared runs | [FULL](evidence/full_output_equivalence.json) |
| Generic concurrency | 1/2/8/24/48 callers; single-flight, churn, retry, isolation and in-flight disposal pass | [cache](evidence/cache_concurrency_telemetry.json) |
| Source | 20 direct Holder-reference checks; nine cave checks covering lush, dripstone and deep dark | [source](evidence/source_reuse.json) |
| Task 1B | Fresh final runtime: 64/64 | [Task 1B](evidence/task1b_regressions.json) |
| Legacy | 595/595 geography, 595/595 hints, 85/85 keys, 420/420 standalone and 105/105 TB tile digests; collision preserved | [legacy](evidence/legacy_comparator.json) |
| TerraBlender | Resolver authority, stable lifecycle fingerprint, rule mutation detection and restoration | [TB](evidence/terrablender_runtime.json) |
| Save/reopen | Biome digest, codec and stored rule fingerprint preserved | [reopen](evidence/save_reopen.json) |
| Actual unload | All five detached caches empty; in-flight and pooled borrows zero; post-close queries rejected | [disposal](evidence/runtime_disposal.json) |

Required Task 4 digest:
`c3930838fa0f75d1541c097838f00dfce5707e69a6f6dd12b2ca7b7e0dfdfee6`.

Requested processors 1/2/8/24/48 map to actual terrain workers 2/2/8/24/48 because
RTF has a minimum of two. No true single-terrain-thread execution is claimed.
Offline legacy suites validate tile digests; holder-sensitive observations come
from live Minecraft runtime suites.

The save/reopen run finished after all semantic production optimizations; later
production edits only add counters. [Provenance](evidence/evidence_provenance.json)
records the ordering. The strengthened Holder assertion was rerun separately.
On actual unload, payloads were cleared while source/context references were
deliberately retained. This proves clearing and rejection, with no GC collection
or reachability claim.

FULL block-digest variation also occurs between frozen baseline runs and in
unchanged legacy. Individual feature/block causes remain unisolated. Canonical
fields, raw climate, finalized tiles and stored biome identities provide the exact
semantic gate; the full diagnostic differences remain retained.

## Measurement and provenance

Baseline production: `a3140a5d7113137563d275cbaea05a8e94f3b23e`.
Frozen harness HEAD: `89e34f5251864c8bb7b3041197567e48e7d6a68e`.
Only harness adaptations exist in the frozen checkout, captured in
[frozen_harness_final.patch](evidence/frozen_harness_final.patch); its
`src/main` is unchanged. Provenance records the production source-tree hash.

Acquisition uses JDK 17.0.17+10, Forge's data launcher, 8 processors, 6 GiB heap,
seed 8675309, default retained preset and the accepted 24,000-block climate profile
with 256-block steps. Each scenario starts with fresh services and measures a
cold then identical warm pass. All three ordered baseline/optimized pairs remain
represented by authoritative `Compare-Acquisition.py`. Resume did not rerun them.

FULL uses fresh worlds, V1 followed by legacy in one JVM, with the same seed and
ordered coordinates. Calls reach FULL including required neighbors; timings exclude
hashing and subsequent validation. Preexisting status is recorded. Already-loaded
retrieval is measured separately. The original 12 points remain independently
comparable; two verified mountain/coastal points extend coverage to 14.

JFR uses first-recognized-subsystem attribution from sampled stack leaves.
Sample counts are not exact subsystem CPU time. Blocking/compilation durations
sum across threads and overlap. Later monitors use a 1 ms threshold; profile
parking uses 10 ms in the installed JDK profile. Original recordings retain their historical settings. FULL
recordings include between-request hashing and the quart microbenchmark outside
generation timers.

Finalizers: `Compare-Legacy.py`, `Finalize-Runtime.py`,
`Run-FinalProfiles.ps1`, `Finalize-Analysis.py` and `Finalize-Manifest.py`
under `scripts/task6b/`. They validate and derive reports from retained evidence.
Raw JFRs and original run directories/logs remain local under `build/task6b-*`
and `run/task1a/evidence`; portable summaries and hashes are committed.
Failed/partial attempts remain preserved. Main-worktree `LICENSE`,
`gradle.properties` and `THIRD_PARTY_NOTICES.md` edits are untouched.
Clean-tree confirmation applies to `task6b-performance`.

Task 6B ends here. A dedicated terrain task should investigate the cost and scope
of exact canonical acquisition, terrain noise and filtering. Pending bounds and
monitor measurements do not justify further cache policy changes. Phase 2 feature
work was not started.
