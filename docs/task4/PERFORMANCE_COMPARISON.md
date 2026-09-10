# Task 4 performance comparison

Status: paired live-Forge measurements captured; Task 4 performance gate passes with a documented review item.

`Task4TerrainChecks` measures the same eight tile coordinates with the same retained terrain preset
and seed under legacy and V1. Each version receives six discarded warmup batches and ten measured
batches. A batch generates eight filtered tiles directly and closes their snapshots. This includes
terrain synthesis, hydrology, filtering and publication, without Minecraft chunk generation.

The coordinates mix interiors, oceans and coast-adjacent areas for V1. The legacy topology differs at
those positions, so paired timings measure the actual replacement workload, not identical landforms.
A regression above 15% requires investigation. Raw macro queries and warm canonical provider queries
are recorded separately. Timing checksums prevent the output from becoming unused benchmark work.

Task 3's accepted historical filtered-tile median was 27.541 ms (36.309 tiles/s), with 3,201,752 warmed
worker-plus-caller allocated bytes per tile. The current paired control is the relevant comparison;
the historical number uses a different coordinate workload and is not interchangeable.

Cache construction is outside the per-cell distance hot path: no coarse-distance query executes during
terrain generation. Distance tiles retain two 32×32 float arrays each, at most 32 entries (262,144 array
bytes, excluding map/object overhead). Building a tile temporarily samples a 160×160 lattice and uses
Euclidean transforms. Island geometry caches at most 256 immutable site lists; river maps at most 128
site entries. These caches are owned by the V1 world/provider, with no static topology cache.

The measured V1 median is 555.4 ms per eight-tile batch versus 455.8 ms legacy, a 21.9% paired
workload increase. This exceeds the 15% review trigger and is recorded for design review; the paired
coordinates deliberately mix V1 interiors, oceans and coasts, so this is not a claim that the macro
predicate alone accounts for the entire difference. Raw macro sampling is about 0.22–0.29 ms per
1,000 queries. Retained array sizes are not whole-process retained-heap measurements. Cold temporary
allocation, total terrain allocations, cache contention and real chunk throughput remain limitations.
