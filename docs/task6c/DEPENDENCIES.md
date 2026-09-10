# Terrain dependencies and cold acquisition

The default canonical tile is a 128×128 core with a 16-block halo on all sides:
a 160×160 workspace (25,600 cells). The halo is generated independently inside
each workspace; no stage recursively acquires neighboring finalized tiles.
Filter-local read radius is not a proof of independence from earlier writes.

| Stage | Local sample radius | Halo requirement | Writes propagate? | Order dependent? | Long-range dependency |
|---|---|---|---|---|---|
| Base terrain/noise | Coordinate-local output; noise-specific warped/octave lattice neighborhoods | No finalized-neighbor halo; generate raw values across the existing 16-block halo | No cross-cell height writes | Preserve each noise graph's float evaluation order; arbitrary graphs may read cell state | Internal noise scale/warp, not neighboring tile loads |
| Hydraulic erosion | Bilinear 2×2 gradient; brush offsets with squared radius <16; up to 12 unit trajectory steps per default droplet | Existing full 16-block halo; a crop cannot be justified from brush radius alone | Yes, through shared height mutations and subsequent droplets | Yes, droplet traversal and writes | Whole-workspace causal propagation; no neighboring tile acquisition |
| Smoothing | Circular radius 1.8 default; conservative loop radius 2 | Existing halo and identical scan bounds | Yes, later cells read earlier smoothed heights | Yes, Z-major/X-major in-place scan and accumulation | Whole-pass causal propagation inside workspace |
| Steepness | Offsets −1…+2 × radius (default 1) in both axes | Asymmetric 1 before/2 after for local stencil | Writes gradient, not input heights | Arithmetic accumulation fixed; cells independent given finalized heights | Inherits upstream height dependencies |
| Beach detection | Four cardinal offsets at distance 8 | Eight blocks; missing boundary samples use center | Writes labels, reads heights | Preserve existing classification rules | Inherits upstream height dependencies |
| Noise correction | Aligned 4×4 quart neighborhood (≤3 blocks per axis) | Quart-aligned neighborhood inside workspace | Terrain-label mutation | Preserve scan and label-write behavior | No external tile reads |
| Finalization | Coordinate-local macro shoreline classification | None beyond existing filters | Marine overwrites height/terrain/mountain/masks; land retains filtered height | Must follow filters; marine coordinate operations independent | Macro procedural topology only |
| Climate profile | Local point plus 94 upstream samples at d=24000,23744,…,192 | Each land sample owns its own canonical terrain tile; eligible marine samples project exact overwritten fields | Read-only terrain input | Preserve profile sample and accumulation order | 24 km geographic sampling reach across hundreds of owning tiles |

**24 km climate sampling reach is not a 24 km terrain filter dependency radius.**
Each query samples a long sparse line. Wind varies with X/Z, so the many quart
queries needed by FULL and its prerequisite chunks sweep a corridor. Their owning
128-block tiles cover a large region even when target chunks are close together.
The retained untruncated trace has 65,274 unique profile positions, 463 owning
tiles, and exactly 463 surface loads. Thus that burst is real spatial coverage,
not 463 duplicate loads of one tile. Tile counts include prerequisite chunks;
one isolated climate query has only 95 positions and cannot alone explain a
400–640-tile FULL request. The union across FULL's climate queries does.

The marine shortcut removes an exact subset of these terrain dependencies; it
does not crop erosion/smoothing workspaces or shorten climate profiles. Cropped
filter regions, reordered droplets, separable smoothing, altered accumulation,
and arbitrary filter parallelization remain rejected without equivalence proof.
