# Original Task 4 evidence capture. All Rights Reserved.
param([Parameter(Mandatory=$true)][string]$Name)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
if($Name -notmatch '^[a-zA-Z0-9_-]+$'){throw 'Simple evidence label required'}
$run=(Get-Content "$root/run/task1a/task1a-pass.txt" -Raw).Trim()
if($run -notmatch '^reproduction-[0-9]+$'){throw 'Invalid run marker'}
$source="$root/run/task1a/evidence/$run"
$completion=Get-Content "$source/completion.json" -Raw | ConvertFrom-Json
if($completion[-1].status -ne 'PASS'){throw 'Incomplete run'}
$destination="$root/docs/task4/evidence/$Name"
if(Test-Path $destination){throw 'Refusing overwrite'}
New-Item -ItemType Directory -Force "$root/docs/task4/evidence" | Out-Null
Copy-Item -LiteralPath $source -Destination $destination -Recurse
Write-Output "$run -> $Name"
