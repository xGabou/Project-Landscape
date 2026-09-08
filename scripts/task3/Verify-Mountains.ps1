# Original new-field scheduling assertions, separate from legacy golden digests. All Rights Reserved.
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$all=@();$reference=@{}
foreach($suite in @('golden-24','golden-2','golden-48','golden-repeat','golden-tb')){
    $rows=Get-Content "docs/task3/evidence/$suite/mountain_tile_digests.json" -Raw | ConvertFrom-Json
    if($rows.Count -ne 105){throw "Missing new mountain digest rows $suite"}
    foreach($r in $rows){
        $key="$($r.seed):$($r.tileX):$($r.tileZ)"
        if($reference.ContainsKey($key) -and $reference[$key] -cne $r.sha256){throw "Mountain scheduling mismatch $suite $key $($r.order)"}
        $reference[$key]=$r.sha256
    }
    $all += [ordered]@{suite=$suite;count=$rows.Count;changed=0;workers=@($rows.workers | Sort-Object -Unique)}
}
$report=[ordered]@{schemaVersion=1;status='PASS';newFieldObservations=525;distinctSeedTiles=$reference.Count;
    semantics='Three captured mountain values as raw float bits, core traversal; separate from unchanged 24-field legacy goldens';suites=$all}
[IO.File]::WriteAllText("$root/docs/task3/evidence/mountain_scheduling.json",($report|ConvertTo-Json -Depth 20)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output 'PASS: 525 new mountain field digests invariant'
