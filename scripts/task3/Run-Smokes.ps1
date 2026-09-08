# Original Task 3 smoke/old-save verification. All Rights Reserved.
param([string]$Suffix='')
$ErrorActionPreference='Stop'
if($Suffix -notmatch '^[a-zA-Z0-9_-]*$'){throw 'Invalid evidence suffix'}
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$accepted=Get-Content 'docs/task2/evidence/runtime_smoke-final.json' -Raw | ConvertFrom-Json
$oldName=($accepted.results | Where-Object {$_.case -eq 'smoke-standalone-final'}).world
if($oldName -notmatch '^task0-[0-9]+$'){throw 'Unexpected accepted world name'}
$oldPath=Join-Path "$root/run/task0-smoke/saves" $oldName
if(!(Test-Path "$oldPath/level.dat")){throw 'Accepted Task 2 world is unavailable'}
if(!(Test-Path "$oldPath/data/atmospheregen/generation_manifest.json")){throw 'Accepted Task 2 save must have a persisted manifest'}
$manifestBefore=(Get-FileHash "$oldPath/data/atmospheregen/generation_manifest.json" -Algorithm SHA256).Hash
$beforeHash=(Get-FileHash "$oldPath/level.dat" -Algorithm SHA256).Hash
$copyName='task3-existing-legacy-'+[Guid]::NewGuid().ToString('N')
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
    if(Test-Path "docs/task3/evidence/$name.json"){throw "Refusing to overwrite $name evidence"}
    Write-Output "Starting $name"
    $arguments=@('runSmokeClient')+$case.arguments+@('--console=plain')
    $ErrorActionPreference='Continue'
    & "$root/gradlew.bat" @arguments *> "$root/docs/task3/evidence/logs/$name.log"
    $code=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if($code -ne 0){throw "$name failed: $code"}
    Copy-Item -LiteralPath 'run/task0-smoke/task2-smoke-evidence.json' -Destination "docs/task3/evidence/$name.json"
    $marker=(Get-Content 'run/task0-smoke/task0-pass.txt' -Raw).Trim()
    $rows=Get-Content "docs/task3/evidence/$name.json" -Raw | ConvertFrom-Json
    $results += [ordered]@{case=$name;world=$marker;status='PASS';chunkChecks=@($rows|Where-Object {$_.status -eq 'minecraft:full' -or $_.status -eq 'full'}).Count;createOrOpenSaveReopen=$true}
    Write-Output "$name PASS"
}
$unchanged=(Get-FileHash "$oldPath/level.dat" -Algorithm SHA256).Hash -eq $beforeHash
if(!$unchanged -or (Get-FileHash "$oldPath/data/atmospheregen/generation_manifest.json" -Algorithm SHA256).Hash -ne $manifestBefore){throw 'Original Task 2 world was modified'}
if((Get-FileHash "$copyPath/data/atmospheregen/generation_manifest.json" -Algorithm SHA256).Hash -ne $manifestBefore){throw 'Task 2 manifest changed on reopen'}
$report=[ordered]@{schemaVersion=1;acceptedTask2='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7';sourceHead=(git rev-parse HEAD);comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee';results=$results;
    oldWorld=[ordered]@{source=$oldName;copiedTo=$copyName;sourceHadManifest=$true;manifestSha256=$manifestBefore.ToLowerInvariant();manifestUnchanged=$true;sourceLevelDatSha256=$beforeHash.ToLowerInvariant();sourceUnchanged=$unchanged;
        copyManifestCreated=(Test-Path "$copyPath/data/atmospheregen/generation_manifest.json")};status='PASS'}
[IO.File]::WriteAllText("$root/docs/task3/evidence/runtime_smoke$Suffix.json",($report|ConvertTo-Json -Depth 20)+"`n",[Text.UTF8Encoding]::new($false))
