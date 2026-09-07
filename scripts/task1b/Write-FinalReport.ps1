# Original Task 1B evidence index. All Rights Reserved.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RunA,[Parameter(Mandatory=$true)][string]$RunB,
      [Parameter(Mandatory=$true)][string[]]$SchedulingRuns,
      [string]$EvidenceDirectory='docs/task1b/evidence')
$ErrorActionPreference='Stop'
function Table([string]$path,[string]$name){return (Get-Content -LiteralPath (Join-Path $path "$name.json") -Raw|ConvertFrom-Json)}
function WriteJson([string]$path,$value){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 40).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))}
$root=(Resolve-Path -LiteralPath $EvidenceDirectory).Path
$commits=@(git log --reverse '--format=%H%x09%s' c773b8b48b229e7150f54b00ef0a33973c491d0e..HEAD | ForEach-Object {
    $parts=$_ -split "`t",2
    [ordered]@{sha=$parts[0];subject=$parts[1];files=@(git diff-tree --no-commit-id --name-status -r $parts[0])}
})
WriteJson (Join-Path $root 'commit_files.json') $commits
$baseline=@(Table 'docs/task1/evidence/full-24-a' 'generation_order')
$hashes=@{}; foreach($row in $baseline){$hashes["$($row.tileX),$($row.tileZ)"]=$row.digest.hash}
$schedule=[Collections.Generic.List[object]]::new()
foreach($run in @($RunA,$RunB)+$SchedulingRuns){
    $rows=@(Table $run 'generation_order')
    $changed=@($rows|Where-Object {$_.digest.hash -cne $hashes["$($_.tileX),$($_.tileZ)"]})
    $schedule.Add([ordered]@{run=$run;rows=$rows.Count;workers=@($rows.workers|Sort-Object -Unique);differentFromTask1A=$changed.Count})
    if($rows.Count -ne 120 -or $changed.Count){throw "Scheduling comparator failed: $run"}
}
$verification=Table (Join-Path $root 'final-verification') 'verification'
if(@($verification.checks|Where-Object {-not $_.pass}).Count){throw 'Final regression failed'}
$collisions=@(Table $RunB 'seed_collisions');$low=@($collisions|Where-Object {$_.sameLow32})
if($low.Count -ne 85 -or @($low|Where-Object {$_.differentFields.Count}).Count){throw 'Legacy narrowing reproduction changed'}
$repeats=Table (Join-Path $root 'final-chunk-repeats') 'chunk_repeats'
$changedChunks=@($repeats.chunks|Where-Object {$_.differentBlocks -gt 0})
$smoke=[Collections.Generic.List[object]]::new()
foreach($name in @('final-smoke-standalone.log','final-smoke-terrablender.log','final-smoke-vanilla.log')){
    $log=Get-Content -LiteralPath (Join-Path $root "logs/$name") -Raw
    $pass=$log.Contains('TASK0 PASS:') -and $log.Contains('WORLD_LOADED reopened=true') -and $log.Contains('BUILD SUCCESSFUL')
    $smoke.Add([ordered]@{log=$name;pass=$pass;fullChunkObservations=([regex]::Matches($log,'TASK0 CHUNK reopened=')).Count;reopened=$log.Contains('WORLD_LOADED reopened=true')})
    if(-not $pass){throw "Smoke failed: $name"}
    if($name -eq 'final-smoke-vanilla.log' -and (([regex]::Matches($log,'TASK1B VANILLA_CONTEXT')).Count -ne 6 -or $log.Contains('file/task0-preset'))){throw 'Vanilla scope verification failed'}
}
$jars=@(Get-ChildItem -LiteralPath build/libs -Filter '*.jar' | Where-Object {$_.Name -notmatch '-(sources|javadoc)\.jar$'})
if($jars.Count -ne 1){throw 'Expected one Forge mod artifact'}
$jarEntries=@(jar tf $jars[0].FullName)
if($LASTEXITCODE -ne 0 -or @($jarEntries|Where-Object {$_ -match '^baseline/'}).Count){throw 'Invalid production jar or packaged development tests'}
foreach($entry in @('META-INF/LICENSE','META-INF/mods.toml','META-INF/accesstransformer.cfg','reterraforged-common.mixins.json','reterraforged-forge.mixins.json')){
    if($jarEntries -notcontains $entry){throw "Missing artifact entry: $entry"}
}
$artifact=[ordered]@{name=$jars[0].Name;bytes=$jars[0].Length;sha256=(Get-FileHash -LiteralPath $jars[0].FullName -Algorithm SHA256).Hash.ToLowerInvariant();developmentClasses=0;requiredMixinManifests=2;licensePresent=$true}
$summary=[ordered]@{
    kind='Task 1B relational correctness checks; not new terrain goldens';verifiedSourceHead=(git rev-parse HEAD).Trim();
    baseline='e9dd8841a1b4a95e4bb2b23e084d40abbc1bea70';task1A='c773b8b48b229e7150f54b00ef0a33973c491d0e';
    finalRun=$RunB;checks=$verification.checks;canonicalComparisons=$verification.comparisons;cachedDirect=$verification.cachedDirect;
    crossWorld=$verification.crossWorld;seedNarrowing=[ordered]@{intentionallyUnchanged=$true;seedA=8675309;seedB=4303642605;equalLow32Rows=$low.Count;differentFields=0};
    scheduling=$schedule;tileGeometry=@(Table $RunB 'legacy_geometry_config');
    fullChunkRepeats=[ordered]@{total=$repeats.chunks.Count;different=$changedChunks.Count;task1AObservation=10;changeInObservedCount=$changedChunks.Count-10;cause='UNPROVEN';changedFixtures=$changedChunks};
    smoke=$smoke;artifact=$artifact;productionRedesignStarted=$false;fixtureGoldenUpdated=$false;
    limitations=@('Finite suite, not proof across every seed/schedule','Published cells remain read-only by internal contract, not immutable Java types','Snapshot copy has allocation cost; final benchmark belongs to Task 1C','Raw dormant noise definitions remain NaN; density boundary fails fast','Seed narrowing and partition-dependent erosion deliberately retained')
}
WriteJson (Join-Path $root 'final_regression_summary.json') $summary
$files=@(Get-ChildItem -LiteralPath $root -Recurse -File | Where-Object {$_.Name -ne 'manifest.json'} | Sort-Object FullName | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($root.Length+1).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
})
WriteJson (Join-Path $root 'manifest.json') ([ordered]@{kind='Task 1B evidence integrity manifest';sourceHead=(git rev-parse HEAD).Trim();files=$files})
Write-Output "Final checks: $($verification.checks.Count); scheduling rows: $(($schedule|ForEach-Object {$_['rows']}|Measure-Object -Sum).Sum); repeated FULL differences: $($changedChunks.Count)/24; evidence files: $($files.Count)"
