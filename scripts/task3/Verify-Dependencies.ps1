# Original Task 3 package direction checks. All Rights Reserved.
param([string]$Output='docs/task3/evidence/legacy_dependency_report.json')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$geography=@(Get-ChildItem src/main/java/com/gabou/atmospheregen/geography -Recurse -Filter '*.java')
$forbidden=@($geography | Select-String -Pattern 'com\.gabou\.atmospheregen\.api\.(climate|biome)|Gabou\.|BaselineClimateProvider|ClimateBiomeResolver' | Where-Object {$_.Line.TrimStart() -match '^import '})
if($forbidden.Count){throw "Geography dependency direction violated: $forbidden"}
$api=@(Get-ChildItem src/main/java/com/gabou/atmospheregen/api -Recurse -Filter '*.java')
if($api | Select-String -Pattern '^import Gabou\.') {throw 'Public API exports inherited implementation'}
$stages=@('LegacyContinentStage','LegacyTerrainStage','LegacyHydrologyStage','LegacyGeographyFinalizationStage')
foreach($stage in $stages){if(Select-String "src/main/java/Gabou/reterraforged/world/worldgen/cell/geography/$stage.java" -Pattern '^import .*\.(climate|biome)\.') {throw "$stage imports climate/biome authority"}}
$compat=@(Get-ChildItem src/main/java/Gabou/reterraforged/world/worldgen/cell/geography -Filter '*.java' | Select-String -Pattern '^import .*\.(climate|biome)\.' | ForEach-Object { [ordered]@{file=$_.Filename;import=$_.Line.Trim()} })
$report=[ordered]@{schemaVersion=1;status='PASS';originalGeographyFiles=$geography.Count;forbiddenDependencies=0;publicApiInheritedImports=0;
    deliberateLegacyCompatibilityImports=$compat;remainingDependencies=@('LegacyGeographyFactory constructs Climate at original seed position','LegacyMinecraftParameterAdapter invokes old Climate before filters','ClimateModule passes biome-center land value to LegacyCoastCompatibility','Terrain graph still writes legacy parameter scratch until future router replacement','Legacy hydrology calls existing continent-owned cache through RiverMapSource, never reenters GeographyPipeline')}
[IO.File]::WriteAllText((Join-Path $root $Output),($report|ConvertTo-Json -Depth 20)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output 'PASS: geography/API dependency direction'
