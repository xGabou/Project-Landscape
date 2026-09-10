# Phase 2A runtime climate contract

Phase 1 reference: b9e27263075ab8274c9206bb837e92c768c56a2e. Phase 2B is not implemented.

## Boundaries

PA API namespace: net.Gabou.projectatmosphere.api.climate; RuntimeClimateBridge.API_VERSION=1. GeographicClimate is detached baseline context. RegionalClimateObservation is an observation of existing active server atmospheric state. A single exclusive Session owns one live Overworld identity. Missing active state returns Optional.empty. Samples never acquire chunks, generate terrain or forecasts, advance weather, or scan the registry.

Landscape RuntimeClimateProvider / RuntimeClimateSample remain distinct from ClimateBaseline. Production reflection is confined to compat/projectatmosphere/PaRuntimeClimateProvider; no PA classes are bundled. Without PA, static generation and baseline climate remain available and dynamic climate is disabled. Unsupported APIs and incompatible persisted history disable runtime with an explicit diagnostic.

## Units and authority

Temperature: RegionAtmosphereState.getTemperature(), Celsius. Precipitation: getRainIntensity(), nonnegative dimensionless intensity, never mm/hour or mm/year. Humidity: getHumidity(), normalized fraction that PA permits up to the exact widened `(double) 1.2f` ceiling (1.2000000476837158). Wind: getWind(), base m/s and radians; X=-speed*sin(angle), Z=speed*cos(angle). Pressure: getPressure(), hPa. Timestamp: ServerLevel.getGameTime(), observation time rather than PA simulation-update time. Client rendering and weather packets are not history inputs.

Only baseline temperature initializes PA temperature, before existing seasonal/daily/random variation and clamping. Other context fields (latitude, annual rainfall mm/year, sea-relative elevation, coast/ocean distances, marine flag, prevailing wind) are retained for later calibration. No rainfall-to-intensity/humidity conversion is invented.

## Representative context

The sole candidate is each native region's north-west corner, both coordinates multiples of 2000 (floor division handles negatives). Capture accepts it only if generation already requested that exact canonical baseline sample. It is a representative observed point, never a regional average. Missing, evicted or not-yet-observed context returns empty and PA keeps its usual initialization. The store holds at most 4096 detached points, evicts its oldest retained entry, and never acquires terrain on lookup. Availability is not a worldgen compatibility contract or a guarantee of seed-only weather determinism.

## History and lifecycle

Native 2000 x 2000 block regions preserve PA's actual spatial resolution. At most four player-region candidates are examined each server tick. Successful observations are normally at least 200 ticks apart. Only adjacent observations at most 400 ticks apart accrue duration, using the previous observation over that interval. Inactive/player-absent time is missing coverage. A reopened history has no previous sample and cannot interpolate across sessions.

Four windows use PA configured day, season, four seasons, and sixteen seasons. Neutral defaults are 24000, 24000, 96000, 384000 game ticks. These are game-calendar durations. Each window retains at most 17 aggregate buckets; the oldest partial bucket is excluded from snapshots, introducing up to one bucket of boundary uncertainty.

Aggregates: observed ticks, time-weighted temperature and humidity, temperature extrema, precipitationIntensityTicks, wet/dry ticks, and frozen-wet proxy ticks (intensity > 0 and temperature <= 0 C). The proxy is neither snowfall depth nor snow-water equivalent. No per-sample time series is retained.

SavedData name project_climate_runtime_history, schema 1, maximum 4096 regions. At capacity new history regions are refused, preserving old history. Persistence includes save UUID/generation/dimension identity, regionSize, four windowTicks, region coordinates, bounded buckets, count and last observation tick. Wrong schema, identity, region size or calendar requires explicit migration. Preflight rejects incompatible files before Minecraft's SavedData recovery could silently replace them. New saves receive a UUID; reopening reuses it. Existing climate history without its owning UUID is rejected and requires migration, never silent identity replacement. Save UUID lives in data/project_climate_world_id.txt; copying an entire save including UUID constitutes a clone, while independent same-seed worlds differ.

Services are keyed by live ServerLevel identity, start on ServerStartedEvent, tick on Overworld END events, save and close on stop/unload, disconnect history and close PA sessions. Nether/End are disabled. PA's exclusive lease deliberately rejects concurrent atmospheric owners; this is not a multi-world PA rewrite.

## Diagnostics

Operator command: /geo runtimeclimate <x> <z>. Reports provider status, representative context/temperature/rainfall or explicit absence, native region and active observation, history count/age, recent and four-season mean temperature, intensity*ticks, coverage and wet/dry ticks. Missing-region queries remain read-only.

## Cost and limits

Sampling performs a handful of reflection/getter calls and constant-time active-state/key lookups, capped by four candidates/tick. Ordinary baseline cache misses add a fixed-coordinate predicate; only representative misses allocate detached capture context. History updates touch four bounded bucket maps per accepted observation. There is no chunk/whole-world scan. PA's pre-existing forecast initialization and weather simulation costs are separate from bridge query costs.

Approximate retained memory with compressed Java object references: 68 maximum bucket nodes per history region, about 10-11 KiB/region including accumulators/maps/keys; roughly 40-45 MiB at 4096 fully populated regions, plus approximately 2-3 MiB maximum detached baseline context. This is an object-layout estimate, not a measured retained heap. Sparse histories allocate far less; NBT serialization adds temporary allocations. Existing terrain caches and PA simulation memory are excluded.

Limitations: sparse representative coverage; no physical precipitation calibration; only active player regions observed; capped history capacity; coarse temporal boundaries; calendar changes require migration; runtime weather is not seed-only deterministic; one live Overworld atmospheric owner. No anomalies, trends, ecology, suitability, biome transitions, vegetation or surface conversion are added.


## Validation scope

The existing reproduction harness runs an actual Forge 1.20.1 integrated server with Landscape source plus the rebuilt PA Phase 2A jar and PA's required dependencies. PaForecastRadius is a reproduction-only optional mixin: both startup-area and per-region forecast setup sampling radii are bounded to 128 blocks. Region identity and atmospheric/history resolution remain 2000 blocks. PA still produces real forecast curves, regional state and scheduler updates; no fake weather is injected. The production default 10,000-block startup radius is unchanged and was not used for final acceptance.

Startup can precede representative baseline capture. The baseline integration check therefore records natural callback use, then explicitly exercises PA's actual TemperatureGenerator after existing generation has retained context. This initializer probe is separate from, and outside, runtime-query terrain/performance measurements; its result does not replace live atmospheric state. The harness separately checks the callback temperature and missing-context fallback. It does not claim every initial forecast used Landscape baseline.

The client process hosts the Forge server; all runtime/history work executes on Server thread. This is not a separate dedicated-server executable test. Worker access is rejected and client weather is not sampled.
