# Task 6B — Phase 1 performance stabilization

Status: in progress. No acceptance claim is made by the acquisition pilot.

Baseline production commit: `a3140a5d7113137563d275cbaea05a8e94f3b23e`.
The Task 6B branch starts at that commit in an isolated worktree; pre-existing
changes in the main and Task 6 worktrees are untouched.

The generation tuple, preset, filtered terrain, climate coordinates and equations,
resolver, holders, surface rules and structures must remain exact. The Task 6
TerraBlender lifecycle fingerprint fix is part of the baseline.

## Measurement procedure

`gradlew.bat runData -Ptask6bOutput=build/<unique-run> --offline --console=plain`
loads the normal Forge transformations through the data launcher, builds the
production default noise registry, and runs actual canonical filtered tiles. It
does not create a client window or a world. Output directories are immutable.
Plain Java cannot bootstrap this Forge registry without its transformations.

The acquisition corpus uses seed 8675309, default retained preset, V1 macro
settings and the accepted 24,000-block / 256-block-step climate configuration.
There are five fixed coordinate sequences: sequential, dense spawn expansion,
scattered, four interleaved distant regions, and repeated quart X/Z at multiple Y
levels (represented as repeated 2D climate requests in the acquisition harness).
Each sequence starts with fresh context/cache instances. Its second pass repeats
the exact coordinates. Raw bits of all seven climate doubles are retained.

Wall time and process CPU time are measured around calls. Calling-thread
allocation is explicitly distinguished from process-wide allocation sampling.
JFR uses the JDK profile settings and records monitor contention above 1 ms.
Microbenchmarks include initialization; they are pilot measurements until a
proper steady-state repeat is recorded. FULL throughput is measured separately.

`gradlew.bat runReproductionClient -PreproProfile=task6b -PreproProcessors=8
-PwithTerraBlender=true --offline --console=plain` creates V1 then legacy worlds
with the same seed and fixed chunk order. FULL timings exclude output hashing
and include required neighboring chunks. Preexisting FULL chunks are flagged.
Final blocks and stored quart biomes are hashed separately. A client launch is
reserved for this integrated FULL benchmark; acquisition profiling is headless.

The final comparison must distinguish repeated contemporary measurements from
historical Task 1C / Task 4 results. Known feature-order variance requires separate
analysis; it cannot be used to excuse arbitrary output differences.

No Phase 2 functionality is in scope.
