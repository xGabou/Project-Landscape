# Original extraction budget report. All Rights Reserved.
param([string]$Run='docs/task3/evidence/benchmark',[string]$Output='docs/task3/evidence/performance_comparison.json')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
& ./scripts/task1c/Summarize-Benchmarks.ps1 -Run $Run -Output docs/task3/evidence/benchmark_summary.json | Out-Null
$before=Get-Content docs/task2/evidence/benchmark_repeat_current_summary.json -Raw | ConvertFrom-Json
$after=Get-Content docs/task3/evidence/benchmark_summary.json -Raw | ConvertFrom-Json
# The reused Task 1C arithmetic is unchanged; correct its historical path labels for this archive.
$label=Split-Path $Run -Leaf
$after.identityReference="$label/identity.json"
$after.benchmarkRunReference="$label/benchmark_runs.json"
$after.environmentReference="$label/environment.json"
$after.machineReference='machine.json'
[IO.File]::WriteAllText("$root/docs/task3/evidence/benchmark_summary.json",($after|ConvertTo-Json -Depth 30)+"`n",[Text.UTF8Encoding]::new($false))
$rows=@()
foreach($op in $after.operations){
    $old=@($before.operations | Where-Object {$_.operation -eq $op.operation})[0]
    $rate=$op.throughputUnitsPerSecond.median;$oldRate=$old.throughputUnitsPerSecond.median
    $rows += [ordered]@{operation=$op.operation;beforeThroughput=$oldRate;afterThroughput=$rate;
        timeChangePercent=100*($oldRate/$rate-1);throughputChangePercent=100*($rate/$oldRate-1);
        beforeRepeatCV=$old.repeatMeanNsPerUnit.coefficientOfVariationPercent;afterRepeatCV=$op.repeatMeanNsPerUnit.coefficientOfVariationPercent;
        medianNs=$op.latencyNs.median;p95Ns=$op.latencyNs.p95;investigateOverTenPercent=($oldRate/$rate -gt 1.1)}
}
function Median($values){$s=@($values|Sort-Object);return $s[[Math]::Floor(($s.Count-1)/2)]}
$allocationRows=Get-Content docs/task3/evidence/allocation/allocation_probe.json -Raw | ConvertFrom-Json
$oldAllocationRows=Get-Content docs/task2/evidence/allocation/allocation_probe.json -Raw | ConvertFrom-Json
$allocation=@($allocationRows | Where-Object {!$_.warmup})
$oldAllocation=@($oldAllocationRows | Where-Object {!$_.warmup})
$bytes=Median $allocation.rtfWorkersAndCallerAllocatedBytes;$oldBytes=Median $oldAllocation.rtfWorkersAndCallerAllocatedBytes
$report=[ordered]@{schemaVersion=1;acceptedTask2='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7';backend='LEGACY_RTF_V0';
    reference='../../task2/evidence/benchmark_repeat_current_summary.json';candidate='benchmark_summary.json';comparisons=$rows;
    allocation=[ordered]@{beforeBytesPerTile=$oldBytes;afterBytesPerTile=$bytes;changePercent=100*($bytes/$oldBytes-1);scope='24 RTF workers plus caller; warmed workspace reuse, no renderer';
        publishedCellCaptureOverheadBytes=204800;exactRetainedHeapMeasured=$false};
    policy='Investigate >10% extraction regression outside measured variance. FULL includes downstream Minecraft, not isolated geography.';
    status='MEASURED_REQUIRES_REVIEW'}
[IO.File]::WriteAllText((Join-Path $root $Output),($report|ConvertTo-Json -Depth 30)+"`n",[Text.UTF8Encoding]::new($false))
$rows|ForEach-Object {[pscustomobject]$_}|Format-Table operation,afterThroughput,timeChangePercent,beforeRepeatCV,afterRepeatCV
Write-Output "Allocation $oldBytes -> $bytes ($($report.allocation.changePercent)%)"
