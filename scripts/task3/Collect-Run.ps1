# Original Task 3 evidence archiving. All Rights Reserved. Never overwrites evidence.
param([Parameter(Mandatory=$true)][string]$Name,[string]$SourceRoot)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
if($Name -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Simple evidence label required'}
if(!$SourceRoot){$SourceRoot=$root}else{$SourceRoot=(Resolve-Path $SourceRoot).Path}
$run=(Get-Content "$SourceRoot/run/task1a/task1a-pass.txt" -Raw).Trim()
if($run -notmatch '^reproduction-[0-9]+$'){throw 'Invalid run marker'}
$source="$SourceRoot/run/task1a/evidence/$run"
$completion=Get-Content "$source/completion.json" -Raw | ConvertFrom-Json
if($completion[-1].status -ne 'PASS'){throw 'Incomplete run'}
$destination="$root/docs/task3/evidence/$Name"
if(Test-Path $destination){throw 'Refusing to overwrite evidence'}
Copy-Item -LiteralPath $source -Destination $destination -Recurse
$provenance=[ordered]@{schemaVersion=1;fixtureVersion=1;run=$run;sourceHead=(git -C $SourceRoot rev-parse HEAD);sourceDirty=@(git -C $SourceRoot status --porcelain);
    acceptedTask2='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7';backend='LEGACY_RTF_V0';
    fixtureReference='../../../task1c/evidence/fixture_manifest.json';
    fixtureSha256=(Get-FileHash "$root/docs/task1c/evidence/fixture_manifest.json" -Algorithm SHA256).Hash.ToLowerInvariant()}
[IO.File]::WriteAllText("$destination/provenance.json",($provenance|ConvertTo-Json -Depth 30)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output "$run -> $Name"
