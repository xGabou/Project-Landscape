# Original Task 2 timing investigation summary. All Rights Reserved; retains every measured run.
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$evidence="$root/docs/task2/evidence"
$historical=Get-Content "$root/docs/task1c/evidence/benchmark_summary.json" -Raw|ConvertFrom-Json
$reference=Get-Content "$evidence/benchmark_reference_current_summary.json" -Raw|ConvertFrom-Json
$candidate=Get-Content "$evidence/benchmark_repeat_current_summary.json" -Raw|ConvertFrom-Json
$initial=Get-Content "$evidence/performance_comparison.json" -Raw|ConvertFrom-Json
$comparisons=@()
foreach($row in $candidate.operations){
    $old=@($reference.operations|Where-Object {$_.operation -eq $row.operation})[0]
    $historic=@($historical.operations|Where-Object {$_.operation -eq $row.operation})[0]
    $currentTime=100*($old.throughputUnitsPerSecond.median/$row.throughputUnitsPerSecond.median-1)
    $comparisons += [ordered]@{operation=$row.operation;acceptedSourceCurrentThroughput=$old.throughputUnitsPerSecond.median;
        task2RepeatThroughput=$row.throughputUnitsPerSecond.median;timeDeltaVsCurrentReferencePercent=$currentTime;
        timeDeltaVsHistoricalPercent=100*($historic.throughputUnitsPerSecond.median/$row.throughputUnitsPerSecond.median-1);
        referenceRepeatCVPercent=$old.repeatMeanNsPerUnit.coefficientOfVariationPercent;candidateRepeatCVPercent=$row.repeatMeanNsPerUnit.coefficientOfVariationPercent;
        medianNs=$row.latencyNs.median;p95Ns=$row.latencyNs.p95;overFivePercentAgainstCurrentReference=($currentTime -gt 5)}
}
$initial | Add-Member -Force NoteProperty contemporaryComparisons $comparisons
$initial | Add-Member -Force NoteProperty investigation ([ordered]@{
    referenceSource='d2164f25cd06aafcae15cc15f9f4babe977d4bdb';referenceWorktree='build/task2-comparator';
    initialRun='benchmark-final';referenceRun='benchmark-reference-current';repeatRun='benchmark-repeat-current';
    protocol='Same unmodified Task 1C benchmark code, seeds, geometry, 24 workers, 6 GiB heap, mapped client and test observers; sequential processes';
    environments='Same CPU, Windows build, Java 17.0.17+10 and high-performance power-plan GUID; live desktop load not controlled';
    interpretation='Initial >5% slowdowns investigated, not discarded. No >5% median hot-path regression reproduced against contemporary accepted-source measurements. Historical filtered timing differs, within candidate run variation. No particular background process or JIT cause established.';
    allocationInterpretation='RTF worker+caller allocations approximately 2.997 MB/tile, unchanged within measurement overhead. Heap samples are process-wide, not retained metadata size.';
    referenceSummaryCaveat='Shared Task 1C summary helper discovers the sibling Task 2 allocation probe; do not attribute that probe to the fresh reference source.'})
$initial.status=if(@($comparisons|Where-Object {$_.overFivePercentAgainstCurrentReference}).Count){'INVESTIGATE'}else{'PASS_WITH_DOCUMENTED_TIMING_VARIABILITY'}
[IO.File]::WriteAllText("$evidence/performance_comparison.json",($initial|ConvertTo-Json -Depth 40)+"`n",[Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText("$evidence/machine.json",($initial.machine|ConvertTo-Json -Depth 10)+"`n",[Text.UTF8Encoding]::new($false))
$comparisons|ForEach-Object {[pscustomobject]$_}|Format-Table operation,timeDeltaVsCurrentReferencePercent,timeDeltaVsHistoricalPercent
if($initial.status -eq 'INVESTIGATE'){throw 'Performance investigation remains open'}
