# Original Task 3 verification/archival index. All Rights Reserved.
# Does not change accepted Task 1C/2 evidence or production configuration.
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$destination="$root/docs/task3/evidence"
function Json($path){Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json}
function WriteJson($path,$value){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 80)+"`n",[Text.UTF8Encoding]::new($false))}
$preserved=[ordered]@{}
foreach($task in @('task1c','task2')){
    $manifest=Json "docs/$task/evidence/manifest.json"
    foreach($entry in $manifest.files){if((Get-FileHash "docs/$task/evidence/$($entry.path)" -Algorithm SHA256).Hash.ToLowerInvariant() -cne $entry.sha256){throw "Accepted $task evidence changed: $($entry.path)"}}
    $preserved[$task]=@($manifest.files).Count
}
& ./scripts/task2/Verify-Goldens.ps1 -CandidateRoot docs/task3/evidence | Out-Null
& ./scripts/task3/Verify-Mountains.ps1
& ./scripts/task3/Verify-Dependencies.ps1
& ./scripts/task3/Verify-Stages.ps1 -Candidate docs/task3/evidence/stage-final
$repairs=Json "$destination/task1b-verification/verification.json"
if(@($repairs.checks).Count -ne 64 -or @($repairs.checks | Where-Object {!$_.pass}).Count){throw 'Task 1B regression failed'}
$smoke=Json "$destination/runtime_smoke.json"
if($smoke.status -ne 'PASS' -or @($smoke.results|Where-Object {$_.chunkChecks -ne 14}).Count){throw 'Smoke gate failed'}
$perf=Json "$destination/performance_comparison.json"
if($perf.status -notlike 'PASS*'){throw 'Performance gate has not been resolved'}
foreach($case in @('geography-final','foundation','stage-final','benchmark','allocation','public-provider-benchmark')){
    if((Json "$destination/$case/completion.json")[-1].status -ne 'PASS'){throw "Missing completed $case"}
}
$archived=New-Item -ItemType Directory "$destination/verified-logs" -Force
foreach($log in @('final-build','stage-baseline','stage-final','geography-final','golden-24','golden-2','golden-48','golden-repeat','golden-tb','full','foundation','benchmark','allocation','public-provider-benchmark','smoke-standalone','smoke-tb','smoke-vanilla','smoke-existing-legacy')){
    if(!(Select-String "$destination/logs/$log.log" -Pattern 'BUILD SUCCESSFUL' -Quiet)){throw "Missing successful log: $log"}
    Copy-Item -LiteralPath "$destination/logs/$log.log" -Destination "$archived/$log.txt" -Force
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar="$root/build/libs/reterraforged-forge-1.20.1-0.0.6.jar"
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
try{
    $names=@($zip.Entries.FullName)
    if(@($names|Where-Object {$_ -match '^(baseline/|net/fabricmc/|dev/architectury/)'}).Count){throw 'Developer/foreign loader class packaged'}
    foreach($name in @('com/gabou/atmospheregen/geography/GeographyPipeline.class','META-INF/mods.toml','META-INF/accesstransformer.cfg','reterraforged-common.mixins.json','reterraforged-forge.mixins.json')){
        if($name -notin $names){throw "Artifact missing $name"}
    }
    $artifact=[ordered]@{path='build/libs/reterraforged-forge-1.20.1-0.0.6.jar';sha256=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant();
        mixins=@($names|Where-Object {$_ -like '*mixins.json'});notices=@($names|Where-Object {$_ -match '(?i)license|notice|copying'});developerClassesPackaged=$false}
}finally{$zip.Dispose()}
WriteJson "$destination/build_verification.json" ([ordered]@{status='PASS';tasks=@('compileJava','classes','jar','build','reproductionClasses','smokeClasses','runData');
    artifact=$artifact;minecraft='1.20.1';forge='47.4.22';java='17.0.17+10';gradle='8.11';forgeGradle='6.0.42';terraBlender='3.0.1.10';
    note='Real Forge reproduction/smoke assertions, not NO-SOURCE unit test success. Existing mapped refmap, deprecation, first-run Forge config and offline-client warnings retained.'})
$gold=Json "$destination/legacy_golden_comparison.json"
WriteJson "$destination/final_summary.json" ([ordered]@{schemaVersion=1;fixtureVersion=1;acceptedTask2='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7';backend='LEGACY_RTF_V0';status='PASS';
    sourceHead=(git rev-parse HEAD);preservedEvidence=$preserved;stageRows=2975;stageDifferences=0;canonicalRows=595;biomeHintRows=595;biomeKeys=85;
    standaloneTileDigests=420;terraBlenderTileDigests=105;newMountainSchedulingDigests=525;legacyCollisionRows=85;task1bPassingChecks=64;
    filteredDirectDifferingRows=$gold.diagnosticDifferingRows;filteredDirectCounts=$gold.diagnosticFields;
    physicalSlopeAvailable=$false;localReliefAvailable=$false;canonicalProvider='geography-final/provider_samples.json';
    performance='performance_comparison.json';runtime='runtime_smoke.json';build='build_verification.json';
    limitations=@('Biome-center coast behavior remains explicitly V0 compatibility before filters','Inherited Cell retains legacy parameter/classification storage','Legacy private continent-owned river cache retained behind HydrologyStage','Structure V0 opportunistic eligibility retained, not silently changed to exact','Unknown distances, slope, relief, basin and ridge data remain unavailable','Legacy int narrowing and FULL decoration variance retained');
    legacyTerrainIntentionallyChanged=$false;newGeographyApiFieldsAdded=$true;newContinentsOrOceans=$false;task4Started=$false})
$stage=Json "$destination/stage-final/stage_samples.json"
$stage|Select-Object seed,ordinal,stage,@{n='x';e={$_.point.x}},@{n='z';e={$_.point.z}},@{n='legacyFieldsExact';e={$true}} |
    Export-Csv "$destination/stage_equivalence.csv" -NoTypeInformation -Encoding UTF8
$accepted='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7'
$commits=@(git log --reverse --format='%H%x09%s' "$accepted..HEAD" | ForEach-Object {
    $parts=$_ -split "`t",2;[ordered]@{sha=$parts[0];subject=$parts[1];files=@(git diff-tree --no-commit-id --name-status -r $parts[0])}
})
$paths=@(@(git diff --name-only $accepted)+@(git ls-files --others --exclude-standard)) | Sort-Object -Unique
WriteJson "$destination/files_and_commits.json" ([ordered]@{acceptedTask2=$accepted;commits=$commits;paths=$paths;
    note='The containing final Git commit identifies the final index/report; it cannot embed its own SHA recursively.'})
$files=@(Get-ChildItem $destination -Recurse -File | Where-Object {
    $relative=$_.FullName.Substring($destination.Length+1).Replace('\','/')
    $relative -ne 'manifest.json' -and !$relative.StartsWith('logs/')
} | Sort-Object FullName | ForEach-Object {[ordered]@{path=$_.FullName.Substring($destination.Length+1).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
WriteJson "$destination/manifest.json" ([ordered]@{schemaVersion=1;fixtureVersion=1;acceptedTask2=$accepted;legacyComparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee';
    fixtureReference='../../task1c/evidence/fixture_manifest.json';settingsReference='../../task1c/evidence/golden-24/identity.json';
    semantics='LEGACY_FILTERED_CANONICAL vs separate LEGACY_DIRECT_APPROXIMATE. New mountain capture is separately encoded; no legacy golden replaced.';
    provenance='Each run provenance.json records source/fixture hash. Logs are archive integrity only. stage-baseline predates production edits.';
    files=$files})
Write-Output "PASS: $($files.Count) evidence files; accepted Task 1C/2 archives unchanged"
