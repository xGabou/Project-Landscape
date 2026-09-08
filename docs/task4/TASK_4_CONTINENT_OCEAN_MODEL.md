# Task 4 continent/ocean model — implementation in progress

Starting HEAD: `a9b2f288d990f466da9fd79ed8e0a8550dec190d`. The isolated checkout was clean.
The original workspace's uncommitted namespace migration is outside this worktree.
Comparator: `LEGACY_RTF_V0`. No acceptance result is inferred from the Task 3 archives.

## Measurement definitions (before model implementation/tuning)

`MacroGeographyProvider` is the production macro-only predicate, distinct from canonical filtered
`GeographyProvider`. Surveys must name which contract they measure. Macro surveys do not erode terrain.
`TopologyMeasurements` consumes that predicate and never duplicates the land algorithm.

Windows use inclusive lattice points and report origin, extent and resolution. Four-neighbor land
components report sample area, bounding extents, covariance-based major axis (four standard deviations),
boundary censoring and all intersecting site IDs. A site is not a connected continent. Coastline uses
mid-edge marching squares; ambiguous diagonal cells contribute two separate segments. Length and
component topology are resolution dependent. Inland seas are reported separately from open marine water.

Corridors are declared by the model before measurement, using an ordered opposing site pair, centerline,
normal and required width. Sample nine cross-sections and three orientations (normal and ±0.12 radians),
search at 32 blocks, then refine detected coastline crossings to one block. Report failed endpoint
searches, island obstructions, each violation and coordinates, and quantiles of valid sections.
The crossing tolerance is two blocks; finite cross-sections cannot prove a global minimum.
Sub-grid islands can escape a coarse scan and require separate construction clearance accounting.

Planned construction: bounded continental regions in seeded macro cells with explicitly reserved
marine boundary strips. Coast detail and islands must obey the same strip exclusion. The configured
minimum concerns this marine separation network; bays, straits, inland seas and freshwater have no such
minimum. Width measurement must identify actual opposing continental land, not just report strip size.

## Acceptance result

The final 16-seed survey uses a 65,536 × 65,536 block window at 128-block resolution, translated-window
checks, 64-block coastline refinements, and an 8,192-block distance halo. Land fraction is 43.11–44.60%
(mean 43.78%), inland sea is 0.213% mean, and all primary windows contain 16 major sampled components.
Measured declared corridor widths are 2,990–5,039 blocks (p10 3,810, median 4,414, p90 5,039), with
zero invalid sections or reserved-strip intrusions. Mean land beyond 500/1000/2000 sampled marine
blocks is 84.91/68.41/40.87%; maximum sampled interior distance is 5,862–6,000 blocks. Evidence is
under `docs/task4/evidence/`.

Task 5 climate, biome resolution and runtime ecology are outside scope. The regular macro-cell lattice
is a documented limitation, distance values are coarse estimates, sub-grid islands can be missed by
surveys, freshwater remains a terrain/hydrology classification, and the retained river backend is not
a drainage solver.
