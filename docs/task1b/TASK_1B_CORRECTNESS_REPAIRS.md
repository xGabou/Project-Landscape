# Task 1B correctness repairs (in progress)

Comparator: Task 0 `e9dd8841a1b4a95e4bb2b23e084d40abbc1bea70` and accepted
Task 1A `c773b8b48b229e7150f54b00ef0a33973c491d0e`.
Task 1A evidence is immutable. New runs and relational correctness checks live here.
No terrain golden is being established and no geography/climate/biome redesign is in scope.

## Verification protocol

Run the real Forge `runReproductionClient` harness before and after each fix, plus
`compileJava classes jar build compileReproductionJava reproductionClasses`.
Archive the complete JSON observations separately, including lossless FULL block snapshots.
`scripts/task1b/Verify-Repairs.ps1` evolves the relevant reproduction checks into
correctness assertions and compares independently generated raw/filtered fixtures,
whole core-tile hashes and real chunk exact samples/stored quart biomes to Task 1A.
FULL blocks remain observations, not an asserted golden (Task 1A F12).

Each numbered repair is a separate commit. A commit cannot embed its own SHA; resolve
each section's introducing commit with Git history, and the final handoff lists full SHAs.
Toolchain, preset, seeds, coordinates and default tile geometry remain those of Task 1A.

## 01 — Cross-context cell cache identity

Inherited rerun `00-inherited` reproduced all 27 original checks (Forge launch 1m34s).
After `01-after`: A-B-A, B-A-B, both worker orders, and both same-seed/different-context
orders return their own refreshed values: **zero contaminated fields in all six sequences**.
Previously HEIGHT, CONTINENT, EROSION and BIOME_REGION leaked between seed 8675309 and 42.
Same-seed tests give only one context a tile so distinct ownership is observable.

`WorldLookup` owns a final opaque identity object. `CellSampler.Cache2d` compares that
token by reference alongside position; it invalidates before recomputing so a thrown
lookup cannot leave a valid old key. Tokens do not retain worlds/registries/tiles on
long-lived worker threads. New worlds, dimensions or rebuilt generation contexts get
distinct lookup tokens regardless of seed equality. Context mutation is not a supported
version transition; Task 2 must create a new context for changed version/config.

Changed production files: `cell/heightmap/WorldLookup.java`, `densityfunction/CellSampler.java`
(both under `src/main/java/raccoonman/reterraforged/world/worldgen/`). Existing MIT notice
retained at root and provenance headers added to these touched derived files.
Developer changes: `.gitattributes`, `ReproductionSuite.java`, new verifier and this record/evidence.

Compile/classes/jar/build + reproduction classes **PASS, 7s**. Actual Forge full run
**PASS, 1m43s**. Canonical comparisons: 340 raw/filtered pairs, 510 seed-pair rows,
120 whole core-tile digests and 24 real FULL-chunk exact samples/stored quart biomes:
**zero differences** against Task 1A. Finished block hashes are not asserted equivalent:
F12 remains unassigned and will be rerun at the final gate. Seed narrowing is unchanged.

Cost: one object per lookup, one reference per scratch cache, one identity comparison
per fallback/transient density evaluation; no extra generation, per-query allocations
or locks. No trustworthy isolated performance delta measured (Task 1C remains later).
Sampling mode and tile-warmth staleness deliberately remain for Fix 2.
Evidence: `00-inherited`, `00-inherited-verification`, `01-after`,
`01-cross-world-cache/verification.json`, `logs/01-{build,runtime}.log`.
