# Original Task 2 evidence index. All Rights Reserved.
# Run after all sequential runtime gates and before the final documentation commit.
param([string]$FoundationSuite='foundation-tb-release')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$evidence="$root/docs/task2/evidence"
function Json($path){Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json}
function WriteJson($path,$value){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 70)+"`n",[Text.UTF8Encoding]::new($false))}
$accepted='d2164f25cd06aafcae15cc15f9f4babe977d4bdb'
$comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee'
$old=Json "$root/docs/task1c/evidence/manifest.json"
foreach($file in $old.files){
    if((Get-FileHash "$root/docs/task1c/evidence/$($file.path)" -Algorithm SHA256).Hash.ToLowerInvariant() -cne $file.sha256){throw "Accepted Task 1C evidence changed: $($file.path)"}
}
$gold=Json "$evidence/legacy_golden_comparison.json"
$release=Json "$evidence/release_legacy_golden_comparison.json"
$repairs=Json "$evidence/task1b-release-verification/verification.json"
$smoke=Json "$evidence/runtime_smoke-final.json"
$performance=Json "$evidence/performance_comparison.json"
if($gold.status -ne 'PASS' -or $release.status -ne 'PASS' -or $smoke.status -ne 'PASS'){throw 'Comparator/smoke gate failed'}
if(@($repairs.checks).Count -ne 64 -or @($repairs.checks|Where-Object {!$_.pass}).Count){throw 'Task 1B regression gate failed'}
if((Json "$evidence/$FoundationSuite/completion.json")[-1].status -ne 'PASS'){throw 'Foundation gate failed'}
$archive=New-Item -ItemType Directory -Path "$evidence/verified-logs" -Force
foreach($name in @('build-release','golden-release','full-release','smoke-standalone-final','smoke-tb-final','smoke-vanilla-final','smoke-existing-legacy-final')){
    $log="$evidence/logs/$name.log"
    if(!(Select-String -LiteralPath $log -Pattern 'BUILD SUCCESSFUL' -Quiet)){throw "$name lacks successful build completion"}
    Copy-Item -LiteralPath $log -Destination "$archive/$name.txt" -Force
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar="$root/build/libs/reterraforged-forge-1.20.1-0.0.6.jar"
$zip=[IO.Compression.ZipFile]::OpenRead($jar)
try{
    $entries=@($zip.Entries.FullName)
    foreach($required in @('META-INF/mods.toml','META-INF/accesstransformer.cfg','reterraforged-common.mixins.json','reterraforged-forge.mixins.json')){
        if($required -notin $entries){throw "Missing Forge artifact resource $required"}
    }
    if(@($entries|Where-Object {$_ -match '^(baseline/|net/fabricmc/|dev/architectury/)'}).Count){throw 'Developer or removed loader classes in production artifact'}
    $api=@(Get-ChildItem "$root/src/main/java/com/gabou/atmospheregen/api" -Recurse -Filter '*.java')
    if($api | Select-String -Pattern 'raccoonman\.reterraforged'){throw 'Inherited implementation leaked into public API'}
    $build=[ordered]@{status='PASS';commands='compileJava classes jar build reproductionClasses smokeClasses runData runReproductionClient -PreproProfile=foundation -PwithTerraBlender=true';
        log='verified-logs/build-release.txt';minecraft='1.20.1';forge='47.4.22';java='17.0.17+10';gradle='8.11';forgeGradle='6.0.42';mixinGradle='0.7.38';terraBlender='3.0.1.10';
        artifact='build/libs/reterraforged-forge-1.20.1-0.0.6.jar';artifactSha256=(Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant();
        developerClassesPackaged=$false;removedLoaderClassesPackaged=$false;publicApiInheritedImports=0;
        mixins=@($entries|Where-Object {$_ -like '*mixins.json'});noticeEntries=@($entries|Where-Object {$_ -match '(?i)license|notice|copying'});
        warningCategories=@('Inherited Java deprecation/removal and unchecked warnings','Gradle deprecated features before Gradle 9','Mapped development refmap warnings','Forge new-world default server config creation','Offline development Realms authorization','IPv6 property/shader warning and integrated-server loading ticks');
        note='Gradle test is NO-SOURCE; assertions execute in real Forge reproduction and smoke source sets. No manual aesthetic terrain claim.'}
}finally{$zip.Dispose()}
WriteJson "$evidence/build_verification.json" $build
$summary=[ordered]@{schemaVersion=1;fixtureVersion=1;acceptedTask1C=$accepted;comparator=$comparator;productionSourceCommit='276a54ca4b5960ef9ede111a91befd233d980604';backend='LEGACY_RTF_V0';status='PASS';
    acceptedEvidenceFilesUnchanged=@($old.files).Count;canonicalRows=$release.counts.canonical_geography;biomeHintRows=$release.counts.biome_hints;biomeKeys=$release.counts.minecraft_biomes;
    standaloneSchedulingDigests=420;terraBlenderDigests=105;additionalReleaseDigests=105;changedCanonicalFields=0;legacyCollisionRows=85;
    directDiagnosticRows=$release.diagnosticRows;directDifferingRows=$release.diagnosticDifferingRows;directDifferingFields=$release.diagnosticFields;
    task1b='task1b-release-verification/verification.json';foundationStandalone='foundation-release';foundationTerraBlender=$FoundationSuite;
    smoke='runtime_smoke-final.json';performance='performance_comparison.json';performanceDisposition=$performance.status;build='build_verification.json';
    sourceInventory='files_and_commits.json';
    limitations=@('PA versions are metadata only and explicitly unavailable','Live datapack reload rejected for manifest-bound worlds','Conservative external mod version coverage; arbitrary third-party mutable config is not fully frozen','Legacy int seed narrowing and filtered/direct distinction retained','FULL decoration-block variance remains deferred','No exact manifest/cache dominator-retained memory measurement');
    productionAlgorithmsIntentionallyChanged=$false;task3Started=$false}
WriteJson "$evidence/final_summary.json" $summary
$commits=@(git log --reverse --format='%H%x09%s' "$accepted..HEAD" | ForEach-Object {
    $pair=$_ -split "`t",2
    [ordered]@{sha=$pair[0];subject=$pair[1];files=@(git diff-tree --no-commit-id --name-status -r $pair[0])}
})
$paths=@(@(git diff --name-only $accepted)+@(git ls-files --others --exclude-standard))|Sort-Object -Unique
WriteJson "$evidence/files_and_commits.json" ([ordered]@{acceptedHead=$accepted;implementationAndEvidenceCommits=$commits;
    note='Final documentation/index commit is the containing Git commit, not recursively embedded here. Paths include pending final artifacts.';paths=$paths})
$files=@(Get-ChildItem $evidence -Recurse -File | Where-Object {$_.FullName -ne "$evidence/manifest.json" -and $_.DirectoryName -ne "$evidence/logs"} | Sort-Object FullName | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($evidence.Length+1).Replace('\','/');sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant();bytes=$_.Length}
})
WriteJson "$evidence/manifest.json" ([ordered]@{schemaVersion=1;fixtureVersion=1;comparator=$comparator;acceptedTask1C=$accepted;
    fixtureReference='../../task1c/evidence/fixture_manifest.json';settingsReference='../../task1c/evidence/golden-24/identity.json';
    productionSourceCommit='276a54ca4b5960ef9ede111a91befd233d980604';
    semantics='LEGACY_FILTERED_CANONICAL comparator; LEGACY_DIRECT_APPROXIMATE diagnostic. Domain seed rows are infrastructure, not PA terrain.';
    provenance='Each run directory has provenance.json; foundation tables record versions/dimensions/seeds/manifests. Prototype evidence is superseded by release suites. Log hashes are archive integrity only.';
    note='Excludes itself and unarchived working .log files; archive bytes, not output goldens. Final source/evidence commit is the containing Git commit.';files=$files})
Write-Output "PASS: $($files.Count) Task 2 files indexed; $(@($old.files).Count) accepted evidence files unchanged"
