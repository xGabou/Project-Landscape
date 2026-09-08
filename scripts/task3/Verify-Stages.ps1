# Original stage-equivalence gate. All Rights Reserved. Never updates the pre-extraction capture.
param([Parameter(Mandatory=$true)][string]$Candidate)
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$before=Get-Content "$root/docs/task3/evidence/stage-baseline/stage_samples.json" -Raw | ConvertFrom-Json
$after=Get-Content "$Candidate/stage_samples.json" -Raw | ConvertFrom-Json
if($before.Count -ne 2975 -or $after.Count -ne $before.Count){throw 'Stage row count changed'}
$counts=[ordered]@{}
for($i=0;$i -lt $before.Count;$i++){
    $a=$after[$i];$b=$before[$i]
    if($a.seed -ne $b.seed -or $a.ordinal -ne $b.ordinal -or $a.stage -cne $b.stage -or $a.point.x -ne $b.point.x -or $a.point.z -ne $b.point.z){throw "Stage fixture identity changed row $i"}
    if(@($a.fields.PSObject.Properties).Count -ne @($b.fields.PSObject.Properties).Count){throw "Field set changed row $i"}
    foreach($property in $b.fields.PSObject.Properties){if($a.fields.($property.Name) -cne $property.Value){throw "Stage mismatch row $i seed=$($a.seed) stage=$($a.stage) field=$($property.Name)"}}
    if(!$counts.Contains($a.stage)){$counts[$a.stage]=0};$counts[$a.stage]++
}
$report=[ordered]@{schemaVersion=1;acceptedTask2='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7';reference='../stage-baseline/stage_samples.json';
    exactness='All 34 legacy fields/reads, raw float bits and stable identifiers; no epsilon';counts=$counts;changed=0;status='PASS'}
[IO.File]::WriteAllText((Join-Path (Resolve-Path $Candidate).Path 'stage_comparison.json'),($report|ConvertTo-Json -Depth 15)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output "PASS: $($after.Count) exact stage rows"
