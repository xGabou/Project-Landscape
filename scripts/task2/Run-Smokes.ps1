# Original Task 2 smoke/old-save verification. All Rights Reserved.
param([string]$Suffix='')
$ErrorActionPreference='Stop'
if($Suffix -notmatch '^[a-zA-Z0-9_-]*$'){throw 'Invalid evidence suffix'}
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$oldName=(Get-Content 'docs/task1c/evidence/smoke-standalone-pass.txt' -Raw).Trim()
if($oldName -notmatch '^task0-[0-9]+$'){throw 'Unexpected accepted world name'}
$oldPath=Join-Path "$root/run/task0-smoke/saves" $oldName
if(!(Test-Path "$oldPath/level.dat")){throw 'Accepted Task 1C world is unavailable'}
if(Test-Path "$oldPath/data/atmospheregen/generation_manifest.json"){throw 'Task 1C source save already has a manifest; do not silently reuse it'}
$beforeHash=(Get-FileHash "$oldPath/level.dat" -Algorithm SHA256).Hash
$copyName='task2-existing-legacy-'+[Guid]::NewGuid().ToString('N')
$copyPath=Join-Path "$root/run/task0-smoke/saves" $copyName
Copy-Item -LiteralPath $oldPath -Destination $copyPath -Recurse
$cases=@(
    @{name='smoke-standalone';arguments=@()},
    @{name='smoke-tb';arguments=@('-PwithTerraBlender=true')},
    @{name='smoke-vanilla';arguments=@('-PsmokeVanilla=true')},
    @{name='smoke-existing-legacy';arguments=@("-PsmokeExistingWorld=$copyName")}
)
$results=@()
foreach($case in $cases){
    $name=$case.name+$Suffix
    if(Test-Path "docs/task2/evidence/$name.json"){throw "Refusing to overwrite $name evidence"}
    Write-Output "Starting $name"
    $arguments=@('runSmokeClient')+$case.arguments+@('--console=plain')
    $ErrorActionPreference='Continue'
    & "$root/gradlew.bat" @arguments *> "$root/docs/task2/evidence/logs/$name.log"
    $code=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if($code -ne 0){throw "$name failed: $code"}
    Copy-Item -LiteralPath 'run/task0-smoke/task2-smoke-evidence.json' -Destination "docs/task2/evidence/$name.json"
    $marker=(Get-Content 'run/task0-smoke/task0-pass.txt' -Raw).Trim()
    $rows=Get-Content "docs/task2/evidence/$name.json" -Raw | ConvertFrom-Json
    $results += [ordered]@{case=$name;world=$marker;status='PASS';chunkChecks=@($rows|Where-Object {$_.status -eq 'minecraft:full' -or $_.status -eq 'full'}).Count;createOrOpenSaveReopen=$true}
    Write-Output "$name PASS"
}
$unchanged=(Get-FileHash "$oldPath/level.dat" -Algorithm SHA256).Hash -eq $beforeHash
if(!$unchanged -or (Test-Path "$oldPath/data/atmospheregen/generation_manifest.json")){throw 'Original Task 1C world was modified'}
$report=[ordered]@{schemaVersion=1;comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee';results=$results;
    oldWorld=[ordered]@{source=$oldName;copiedTo=$copyName;sourceHadManifest=$false;sourceLevelDatSha256=$beforeHash.ToLowerInvariant();sourceUnchanged=$unchanged;
        copyManifestCreated=(Test-Path "$copyPath/data/atmospheregen/generation_manifest.json")};status='PASS'}
[IO.File]::WriteAllText("$root/docs/task2/evidence/runtime_smoke$Suffix.json",($report|ConvertTo-Json -Depth 20)+"`n",[Text.UTF8Encoding]::new($false))
