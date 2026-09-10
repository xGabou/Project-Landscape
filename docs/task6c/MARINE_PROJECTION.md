# Exact marine climate projection

Retain the climate-only non-land projection. The canonical terrain pipeline itself
is unchanged: every actual terrain tile still runs the original ordered filters
and shoreline finalization. The 24,000-block profile, 256-block step, and sample
coordinates are unchanged. Land samples still acquire canonical finalized tiles.

The untruncated retained FULL trace contains 65,274 unique profile coordinates
in 463 owning tiles: 31,913 marine (48.8908%), 33,361 land. Of 226 tiles containing
marine samples, nine also contain land samples. Thus at most 217/463 (46.8683%)
profile tile generations are avoidable. Other chunk-generation consumers may
still require those tiles. The 11,711 samples within 384 blocks of shore overlap
the land/marine partition; they must not be added as a third partition.

Eligibility is exactly `!macro.land()`. Finalization unconditionally replaces
height using the same macro marine depth, selects shallow/deep terrain from shelf
fraction, resets the selector to canonical NaN, resets both mountain contributions
to +0, sets riverMask=1 and erosionMask=false. This includes coastal water and
inland seas. Island land is ineligible. The climate adapter consumes only
sea-relative elevation, mountain influence, water classification, and two
macro-only distance fields. The latter remain unchanged calls to the existing
distance provider. Terrain and hydrology consumers still use the full provider.

One shared `marineHeight` helper supplies finalization and projected elevation.
The cast to float, float multiplication by world height, widening to double, and
sea-level subtraction retain their original order. There is no new cache or
retained state. The provider checks context disposal before projection.

The expanded production experiment checks 117,189 marine cells, including 16,384
inland-sea cells and 51,653 cells within 384 blocks of shoreline. It includes
continental coastal, oceanic and archipelago island-edge tiles, negative tile
boundaries and near-limit terrain. Exact fields: raw height/elevation, all three
mountain fields, terrain label, river/erosion masks, public elevation/landform,
and complete ClimateGeography.Sample against a detached full-tile surface.
No epsilon comparison is used. The fixture generation is in reproduction code.

The first harness attempt incorrectly expected selector zero instead of NaN.
The second exposed the pre-existing coarse-distance halo domain limitation near
±30 million. Both failed logs remain in build. Near-limit raw terrain fields are
checked, but complete climate inputs are compared only inside ±29,980,000; this
does not expand or change the existing distance provider's supported domain.
The JSON's older `no production shortcut yet` scope string is stale in run 04:
that run also checks the production projection against independent full surfaces.

Production acquisition versus smoothing + IslandModel improves sequential 9.85%,
spawn 13.25%, scattered 40.33%, interleaved 32.58%, climate-heavy 12.83%.
Every output climate double matches raw bits. These are fixed-corpus single-run
stage deltas, not population confidence estimates. Final FULL/regression gates
remain separate. See `evidence/marine` and `evidence/marine_acquisition.json`.
