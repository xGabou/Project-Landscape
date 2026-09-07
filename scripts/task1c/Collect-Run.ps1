# Original developer tooling. All Rights Reserved.
param([Parameter(Mandatory=$true)][string]$Name)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$run=(Get-Content "$root/run/task1a/task1a-pass.txt" -Raw).Trim()
$source=Join-Path "$root/run/task1a/evidence" $run
$completion=Get-Content "$source/completion.json" -Raw | ConvertFrom-Json
if($completion[-1].status -ne 'PASS'){throw 'Incomplete run'}
$destination=Join-Path "$root/docs/task1c/evidence" $Name
if(Test-Path $destination){throw "Refusing to overwrite evidence: $destination"}
Copy-Item -LiteralPath $source -Destination $destination -Recurse
Write-Output "$run -> $Name"
