# Original Task 2 performance comparison. All Rights Reserved.
param([string]$Run="$PSScriptRoot/../../docs/task2/evidence/benchmark-final")
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$output="$root/docs/task2/evidence"
& "$root/scripts/task1c/Summarize-Benchmarks.ps1" -Run $Run -Output "$output/benchmark_summary.json"
$before=Get-Content "$root/docs/task1c/evidence/benchmark_summary.json" -Raw|ConvertFrom-Json
$after=Get-Content "$output/benchmark_summary.json" -Raw|ConvertFrom-Json
$comparisons=@()
foreach($new in $after.operations){
    $old=@($before.operations|Where-Object {$_.operation -eq $new.operation})[0]
    $oldRate=$old.throughputUnitsPerSecond.median;$newRate=$new.throughputUnitsPerSecond.median
    $comparisons += [ordered]@{operation=$new.operation;beforeMedianThroughput=$oldRate;afterMedianThroughput=$newRate;
        throughputChangePercent=100*($newRate/$oldRate-1);timePerUnitChangePercent=100*($oldRate/$newRate-1);
        beforeRepeatCVPercent=$old.repeatMeanNsPerUnit.coefficientOfVariationPercent;afterRepeatCVPercent=$new.repeatMeanNsPerUnit.coefficientOfVariationPercent;
        medianLatencyNs=$new.latencyNs.median;p95LatencyNs=$new.latencyNs.p95;
        investigateOverFivePercent=($oldRate/$newRate -gt 1.05);attribution=$new.attribution}
}
$cpu=Get-CimInstance Win32_Processor|Select-Object -First 1
$machine=Get-CimInstance Win32_ComputerSystem
$os=Get-CimInstance Win32_OperatingSystem
$record=[ordered]@{schemaVersion=1;fixtureVersion=1;comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee';backend='LEGACY_RTF_V0';
    reference='../../task1c/evidence/benchmark_summary.json';candidate='benchmark_summary.json';comparisons=$comparisons;
    machine=[ordered]@{cpu=$cpu.Name;physicalCores=$cpu.NumberOfCores;logicalProcessors=$cpu.NumberOfLogicalProcessors;ramBytes=$machine.TotalPhysicalMemory;os=$os.Caption;osBuild=$os.BuildNumber};
    policy='Investigate >5% legacy hot-path regression; no timing from Gradle. FULL latency includes downstream Minecraft, not only RTF.';
    memory=$after.memory;allocationProbe=$after.separateAllocationProbe;status='MEASURED_REQUIRES_REVIEW'}
[IO.File]::WriteAllText("$output/performance_comparison.json",($record|ConvertTo-Json -Depth 30)+"`n",[Text.UTF8Encoding]::new($false))
$comparisons|ForEach-Object {[pscustomobject]$_}|Format-Table operation,throughputChangePercent,timePerUnitChangePercent,beforeRepeatCVPercent,afterRepeatCVPercent
