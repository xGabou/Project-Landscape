# Original Task 2 developer tooling. All Rights Reserved. Never overwrites accepted evidence.
param([Parameter(Mandatory=$true)][string]$Name,[string]$SourceRoot)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
if($Name -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Evidence name must be a simple directory label'}
if(!$SourceRoot){$SourceRoot=$root}else{$SourceRoot=(Resolve-Path $SourceRoot).Path}
$run=(Get-Content "$SourceRoot/run/task1a/task1a-pass.txt" -Raw).Trim()
if($run -notmatch '^reproduction-[0-9]+$'){throw 'Invalid run marker'}
$source=Join-Path "$SourceRoot/run/task1a/evidence" $run
$completion=Get-Content "$source/completion.json" -Raw | ConvertFrom-Json
if($completion[-1].status -ne 'PASS'){throw 'Incomplete run'}
$destination=Join-Path "$root/docs/task2/evidence" $Name
if(Test-Path $destination){throw "Refusing to overwrite evidence: $destination"}
Copy-Item -LiteralPath $source -Destination $destination -Recurse
$provenance=[ordered]@{schemaVersion=1;fixtureVersion=1;task='Task 2';run=$run;sourceHead=(git -C $SourceRoot rev-parse HEAD);
    sourceDirty=@(git -C $SourceRoot status --porcelain);comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee';
    acceptedTask1C='d2164f25cd06aafcae15cc15f9f4babe977d4bdb';fixtureManifest='../../../task1c/evidence/fixture_manifest.json';
    fixtureManifestSha256=(Get-FileHash "$root/docs/task1c/evidence/fixture_manifest.json" -Algorithm SHA256).Hash.ToLowerInvariant();
    settingsReference='../../../task1c/evidence/golden-24/identity.json';backend='LEGACY_RTF_V0'}
[IO.File]::WriteAllText("$destination/provenance.json",($provenance|ConvertTo-Json -Depth 20)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output "$run -> $Name"
