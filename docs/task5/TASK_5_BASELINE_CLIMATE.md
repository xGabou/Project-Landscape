# Phase 1 Task 5 — deterministic baseline climate

Task 5 adds `atmospheregen:baseline_climate_v1` for `PA_GEOGRAPHY_V1`. It is a read-only geography-to-climate layer. It is not runtime weather and it does not select or mutate biomes.

## Architecture

`com.gabou.atmospheregen.climate` contains `BaselineClimateModel`, `PaBaselineClimateProvider`, `ClimateGeographyAdapters`, `LatitudeModel`, `PrevailingWindModel`, and `ClimateBreakdown`. The provider is world/context scoped, immutable to callers, thread-safe through a bounded synchronized LRU, and deterministic independent of cache warmth. `ClimateGeography` is a narrow read-only input of block elevation, coarse coast/ocean distances, mountain influence, and marine class.

`ClimateBaseline` reports mean temperature (°C), annual rainfall (model-equivalent mm/year), ecological moisture index (0..1; not relative humidity), potential evaporation (model-equivalent mm/year), rain shadow (0..1), and a normalized prevailing wind vector.

## Model

Latitude is world-Z based: `absLatitude = 90 * |(z-equatorZ)/scale| / (1 + |(z-equatorZ)/scale|)`. Temperature is the sum of latitude tendency (`28 - 42*(absLatitude/90)^1.15`), negative altitude lapse, ocean moderation toward `17 - 0.20*absLatitude`, continental cooling, and low-amplitude domain-seeded regional variation. Altitude uses actual sea-relative block elevation; the default lapse is `0.0065 °C/block` (6.5 °C per model kilometre, explicitly a calibrated Minecraft scale).

Prevailing winds are coherent latitude bands with deterministic, low-amplitude regional perturbation. Marine exposure distinguishes major ocean, inland sea, coastal water, and land. Continentality is only applied to land and grows with coast distance, so the ocean moderation and continental cooling have separate responsibilities.

Rainfall uses a bounded 24,000-block, 256-block-step upwind transect. Marine samples seed moisture by class; land traversal decays it. Positive elevation rises consume remaining moisture and add orographic precipitation. Rain shadow accumulates from that depletion and recovers exponentially over 12,000 blocks. This is a bounded geography profile, not a recursive climate query. Potential evaporation is a documented temperature/dryness proxy; moisture index is rainfall divided by rainfall plus evaporation plus one.

The debug command `/geo climate <x> <z>` reports the final values and the explainable component breakdown. Climate is not connected to biome authority, surface blocks, structures, or PA runtime weather.

## Versioning and configuration

`GenerationVersions.climateV1()` is `(PA_GEOGRAPHY_V1, PA_BASELINE_V1, LEGACY_MULTINOISE_V0)`. The manifest requires planned baseline settings for this tuple and fingerprints them. Legacy worlds continue to use `LEGACY_RTF_HINTS_V0`; they do not instantiate this provider. Active controls are latitude scale/equator, lapse, ocean and continentality strength, wind band scale, orographic strength, shadow recovery, profile distance/step, evaporation strength, and regional variation. Nonfinite, nonpositive, out-of-range, and step-greater-than-distance values are rejected.

Developer testing can request the climate tuple with `data/atmospheregen/development_climate_v1.json`; the existing world-binding path persists and validates the manifest and installs the same V1 geography backend. Normal preset selection is unchanged.

## Seed domains

The model uses named 64-bit domains `BASELINE_TEMPERATURE`, `BASELINE_PRECIPITATION`, and `BASELINE_WIND`; no sequential allocation or global seed truncation is used. Narrowing is confined to inherited noise boundaries. The collision pair `8675309` and `4303642605` produces distinct V1 climate digests.

## Scope and limitations

The provider currently remains an offline/developer climate authority; Task 6 will consume it for biome eligibility. Hydrology is input only as future extension: no local river microclimate is applied. Snow policy, seasonal climate, full drainage, exact block-resolution distance, and climate-driven terrain are intentionally deferred.
