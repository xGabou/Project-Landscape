# Task 6: climate biome resolution (implementation in progress)

Task 6 is not yet accepted. The initial draft exposed a canonical-climate startup cost and contained
invalid survey assertions. Those assertions have been replaced by executed, fail-fast checks.
Runtime create/reopen and controlled catalog reachability now pass. Broad canonical surveys,
full comparator results and total worldgen performance must be recorded before this status can change.

## Resolver contract

The registry-independent catalog uses stable resource locations. `BiomeDescriptor.CODEC` represents
temperature (C), rainfall (model mm/year), rainfall/potential-evaporation ratio, sea-relative
elevation (blocks), semantic traits and stable priority. Every range has four ordered finite values:
allowed minimum, preferred minimum, preferred maximum, allowed maximum. Suitability is one within
the preferred interval and falls linearly to zero at the allowed endpoints. The climate allowed
envelopes are hard exclusions. A descriptor outside them cannot win on another dimension.

The Model B ratio is `rainfall / max(1 mm/year, potentialEvaporation)`. The one-mm floor keeps the
diagnostic quantity finite for zero configured evaporation; it is unrelated to bounded moisture
index. Temperature, raw rainfall and this ratio contribute weights 0.34, 0.28 and 0.28; elevation
preference contributes 0.10. Potential evaporation therefore changes the ratio at equal rainfall.
No additional altitude cooling occurs in the resolver.

`GeographicBiomeRules` applies marine, coast, freshwater, wetland and mountain constraints first.
Wetland geography additionally requires positive temperature, at least 900 model mm/year and ratio
at least 0.9. Badlands descriptors require plateau geography. Highland vegetation requires hills
or mountains. Ordinary scoring cannot override these physical classes.

The normal `select` path traverses prefiltered descriptors without creating score lists. Debug
`explain` exposes sorted candidates, climate score and regional contribution. Ties use descending
priority followed by lexical resource location. Both paths must be tested for exact agreement.

Regional variation uses the Task 2 `BIOME_SPATIAL_SELECTION` domain. Smooth interpolation of hashed
macro lattice nodes produces a coherent field; maximum score perturbation is 0.08 times configured
strength (default 0.18). Variation cannot restore an excluded candidate. No mutable random source,
registry numeric ID or query-order state participates.

## Calibration and catalog

The initial synthetic matrix exposed nine uncovered dry/cold/hot combinations once allowed ranges
became real constraints. Grassland, savanna and cold open-land envelopes were corrected, and
preferred intervals were separated from allowed envelopes. They are model calibrations, not Earth
climate classifications. The measured Okanagan fixture now permits wet-side forest and dry-side
savanna at the same latitude.

Descriptor serialization sorts traits and resource identifiers before canonical hashing. The
result participates in the world manifest. The built-in catalog and codec provide a later datapack
extension boundary; a descriptor resource reload mechanism is not yet implemented. Unknown modded
biomes are excluded. Mushroom fields have no justified non-climatic mechanism and are unavailable.

## Runtime integration

The V1 source wraps the retained source and is explicitly selected by the developer manifest file.
Its registered codec serializes the retained definition and manifest fingerprint. Decoding does
not capture a server; binding checks the manifest and creates world-scoped providers. The resolver
is gated to the Overworld and full V1 version tuple.

Quart coordinates are converted with Minecraft `QuartPos.toBlock`. Below the geographic surface,
only retained lush caves, dripstone caves and deep dark are eligible for delegation. Surface biomes
returned by the retained source cannot silently replace V1 terrestrial authority underground.
Possible-biome enumeration includes these cave holders.

The initial source evaluated long canonical profiles repeatedly. A proposed 64-block nearest-cell
shortcut was removed because it changed climate coordinates without error validation. The current
reuse design copies exact filtered-tile elevation/mountain arrays into bounded detached surface
fields. It preserves all profile sample coordinates and equations. Live verification compares nine
canonical surface inputs and a full climate query exactly. Create/reopen biome digests and codec
roundtrips agree in `reproduction-1788967364037`. See the performance report for the bounded cache
and the distinction between this smoke observation and a paired generation benchmark.

Physical decisions which depend only on temperature use an exact temperature projection and skip
the irrelevant rainfall profile. Twenty-four signed/extreme-coordinate fixtures compare its raw
double output with the full climate model. Ordinary terrestrial and wetland decisions retain full
canonical climate sampling. Legacy sources are not wrapped. V1 surface selection bypasses the
retained MultiNoise/TerraBlender result; the three explicit underground families still delegate.

The only active resolver controls are regional variation scale and strength. The serialized
spatial-resolution, fallback-weight and transition-softness fields remain reserved/inactive.
They do not authorize coordinate snapping, silent fallback biomes or changes to climate equations.

The current built-in catalog has 42 descriptors. Its default content fingerprint is
`0d1fe8454a4e7690dd05563084dd8333b19e1f50836c6a149af3dd5a74c71427`.
Empty traits, unknown traits, inverted/out-of-envelope preferred ranges, nonfinite ranges and
negative priority are rejected by executed tests. Registry-backed binding rejects unresolved holders.

## Outstanding acceptance work

Canonical-terrain patch surveys, actual generated dry/wet-family reachability, quart-scale seamless
transition measurements, runtime surface/structure/cave assertions, full contemporary legacy
comparators, TerraBlender V1 smoke, and performance/memory review remain required. Controlled
descriptor reachability and wide-latitude proxy results are in BIOME_SURVEY.md. No Task 6 completion
claim is made by a macro-proxy survey alone.

No runtime weather, biome evolution, ecological succession or Dynamic Trees integration is part
of this implementation.
