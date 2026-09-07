# Original Task 1C evidence indexing. All Rights Reserved.
param([string]$Root="$PSScriptRoot/../../docs/task1c/evidence")
$ErrorActionPreference='Stop'
$Root=(Resolve-Path $Root).Path
function ReadJson($file){$v=Get-Content $file -Raw|ConvertFrom-Json;return $v}
function Json($file,$value){[IO.File]::WriteAllText($file,($value|ConvertTo-Json -Depth 60)+"`n",[Text.UTF8Encoding]::new($false))}
$comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee'
$identity=@(ReadJson "$Root/golden-24/identity.json")[0]
$standalone=@(ReadJson "$Root/golden-repeat/canonical_geography.json")
$tb=@(ReadJson "$Root/golden-tb/canonical_geography.json")
$geoDifferences=0
for($i=0;$i -lt $standalone.Count;$i++){
    foreach($p in $standalone[$i].fields.PSObject.Properties){if($p.Value -cne $tb[$i].fields.($p.Name)){$geoDifferences++;break}}
}
$minecraft=@(ReadJson "$Root/golden-repeat/minecraft_biomes.json")
$tbMinecraft=@(ReadJson "$Root/golden-tb/minecraft_biomes.json")
$biomeDifferences=@(for($i=0;$i -lt $minecraft.Count;$i++){if($minecraft[$i].biome -ne $tbMinecraft[$i].biome){$i}}).Count
$smokes=@();foreach($name in @('standalone','tb','vanilla')){
    $log=Get-Content "$Root/logs/smoke-$name.log" -Raw
    $smokes+=[ordered]@{profile=$name;passMarker=(Get-Content "$Root/smoke-$name-pass.txt" -Raw).Trim();gradleSuccess=$log.Contains('BUILD SUCCESSFUL');
        createWorldLoaded=([regex]::Matches($log,'TASK0 WORLD_LOADED reopened=false')).Count;reopenedWorldLoaded=([regex]::Matches($log,'TASK0 WORLD_LOADED reopened=true')).Count;
        fullChunkObservations=([regex]::Matches($log,'TASK0 CHUNK reopened=')).Count;saveObservations=([regex]::Matches($log,'TASK0 SAVED reopened=')).Count;
        log="logs/smoke-$name.log";scope='Automated title/create/generate/save/reopen/generate-more; not manual visual terrain inspection'}
}
$buildLog=Get-Content "$Root/logs/build-datagen.log" -Raw
$jar=(Resolve-Path "$PSScriptRoot/../../build/libs/reterraforged-forge-1.20.1-0.0.6.jar").Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
try{$entries=@($zip.Entries.FullName)}finally{$zip.Dispose()}
$productionDiff=@(git -C "$PSScriptRoot/../.." diff $comparator --name-only -- src/main src/generated)
$summary=[ordered]@{schemaVersion=1;fixtureVersion=1;comparator=$comparator;configurationFingerprint=$identity.configurationFingerprint;
    verification=ReadJson "$Root/golden_verification.json";terraBlender=[ordered]@{enabled=$true;version='3.0.1.10';geographyRows=$tb.Count;geographyDifferentRows=$geoDifferences;biomeKeyRows=$tbMinecraft.Count;biomeKeyDifferences=$biomeDifferences;note='Only TerraBlender, no third-party biome packs; zero differences is not a broad compatibility claim.'};
    build=[ordered]@{tasks=@('compileJava','classes','jar','build','reproductionClasses','runData');success=$buildLog.Contains('BUILD SUCCESSFUL');artifact=[IO.Path]::GetFileName($jar);
        artifactBytes=(Get-Item $jar).Length;artifactSHA256=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant();
        developerClassesInJar=@($entries|Where-Object {$_ -match '^baseline/|task1a-observers'});requiredEntries=@('META-INF/mods.toml','META-INF/accesstransformer.cfg','META-INF/LICENSE','reterraforged-common.mixins.json','reterraforged-forge.mixins.json')|ForEach-Object {[ordered]@{entry=$_;present=$_ -in $entries}}};
    runtimeSmoke=$smokes;productionSourceChanges=$productionDiff;task2Started=$false;
    fullVarianceReference='full_chunk_variance.json';performanceReference='benchmark_summary.json';
    knownLimitations=@('Legacy 32-bit seed narrowing','Filtered/direct semantic difference','Legacy halo/partition dependence','Unproven downstream FULL-block variance cause','Dormant invalid noise definitions guarded at density evaluation','Exact retained-cache byte size and isolated total FULL-chunk allocations not measured')}
Json "$Root/final_summary.json" $summary
if($productionDiff.Count -or !$summary.build.success -or $summary.build.developerClassesInJar.Count -or @($summary.build.requiredEntries|Where-Object {!$_.present}).Count -or @($smokes|Where-Object {!$_.gradleSuccess -or $_.fullChunkObservations -ne 14 -or $_.createWorldLoaded -ne 1 -or $_.reopenedWorldLoaded -ne 1 -or $_.saveObservations -ne 2}).Count){throw 'Final production/build/smoke gate failed'}
$files=@(Get-ChildItem $Root -File -Recurse|Where-Object {$_.Name -ne 'manifest.json'}|Sort-Object FullName|ForEach-Object {
    $relative=$_.FullName.Substring($Root.Length+1).Replace('\','/');$first=($relative -split '/')[0];$idFile="$first/identity.json"
    if(!(Test-Path (Join-Path $Root $idFile))){$idFile='golden-24/identity.json'}
    $role=if($relative -match '^(benchmark-24|pilot-short-queries)/'){'pilot; excluded from denominator'}elseif($relative -match '^variance/|full_chunk_variance'){'downstream diagnostic; not block golden'}elseif($relative -match '/(canonical_geography|legacy_direct_samples|biome_hints|tile_digests|seed_collision|terrain_traits)\.json$'){'exact comparator or explicitly legacy-only semantic fixture'}elseif($relative -match '^logs/|completion|environment|pass.txt'){'provenance/runtime observation; never future behavioral equality'}else{'measurement or fixture metadata'}
    $vanilla=$relative -match 'smoke-vanilla';if($relative -match 'smoke-tb'){$idFile='golden-tb/identity.json'}
    [ordered]@{path=$relative;sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant();bytes=$_.Length;comparator=$comparator;fixtureVersion=1;
        backend=$(if($vanilla){'Vanilla control world; RTF context absent'}else{'LEGACY_RTF_V0'});generatorMode=$first;
        configurationFingerprint=$(if($vanilla){$null}else{$identity.configurationFingerprint});identityReference=$(if($vanilla){'logs/smoke-vanilla.log'}else{$idFile});
        samplingSemanticsReference='fixture_manifest.json and each row operation/semantics; smoke files record real FULL chunks';
        seedReference=$(if($relative -match 'benchmark|allocation|pilot-short'){'8675309'}else{'seed fields in file, identity, or referenced smoke log'});fixtureReference='fixture_manifest.json';role=$role}
})
Json "$Root/manifest.json" ([ordered]@{schemaVersion=1;comparator=$comparator;toolingSourceHead=(git -C "$PSScriptRoot/../.." rev-parse HEAD).Trim();
    note='Every leaf file references identity/settings/seed semantics. Metadata/log hashes ensure archival integrity, not future output equality. Manifest excludes itself.';files=$files})
Write-Output "Indexed $($files.Count) files; production source differences: $($productionDiff.Count); TB geography differences: $geoDifferences; TB biome differences: $biomeDifferences"
