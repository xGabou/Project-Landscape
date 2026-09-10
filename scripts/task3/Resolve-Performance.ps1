# Original Task 3 paired performance report. All Rights Reserved.
param([string]$Candidate='benchmark-resumed',[string]$Reference='benchmark-task2-reference-stable',
    [string]$PublicCandidate='public-provider-stable',[string]$PublicReference='public-provider-reference-stable')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$evidence="$root/docs/task3/evidence"
function Json($path){Get-Content -LiteralPath $path -Raw | ConvertFrom-Json}
function WriteJson($path,$value){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 50)+"`n",[Text.UTF8Encoding]::new($false))}
function Median($values){$s=@($values|Sort-Object);if(!$s.Count){throw 'Empty measurement'};return $s[[Math]::Floor(($s.Count-1)/2)]}
function Summary($name){
    & ./scripts/task1c/Summarize-Benchmarks.ps1 -Run "$evidence/$name" -Output "$evidence/$name-summary.json" | Out-Null
    $s=Json "$evidence/$name-summary.json"
    $s.identityReference="$name/identity.json";$s.benchmarkRunReference="$name/benchmark_runs.json"
    $s.environmentReference="$name/environment.json";$s.machineReference='machine.json'
    # The shared summarizer discovers a sibling candidate allocation probe. It is
    # not a measurement of accepted Task 2 and must not appear in a control summary.
    if($name -like 'benchmark-task2-reference*'){$s.separateAllocationProbe=$null}
    WriteJson "$evidence/$name-summary.json" $s
    return $s
}
$before=Summary $Reference;$after=Summary $Candidate
$rows=@($after.operations|ForEach-Object {
    $op=$_;$old=$before.operations|Where-Object {$_.operation -eq $op.operation}
    $change=100*($old.throughputUnitsPerSecond.median/$op.throughputUnitsPerSecond.median-1)
    [ordered]@{operation=$op.operation;beforeThroughput=$old.throughputUnitsPerSecond.median;afterThroughput=$op.throughputUnitsPerSecond.median;
        timeChangePercent=$change;medianNs=$op.latencyNs.median;p95Ns=$op.latencyNs.p95;
        referenceRepeatCV=$old.repeatMeanNsPerUnit.coefficientOfVariationPercent;candidateRepeatCV=$op.repeatMeanNsPerUnit.coefficientOfVariationPercent;
        investigateOverTenPercent=($change -gt 10);scope=$op.attribution}
})
$pubBefore=Json "$evidence/$PublicReference/public_provider_benchmark.json"
$pubAfter=Json "$evidence/$PublicCandidate/public_provider_benchmark.json"
$pb=@($pubBefore|Where-Object {!$_.warmup});$pa=@($pubAfter|Where-Object {!$_.warmup})
if($pb.Count -ne 10 -or $pa.Count -ne 10 -or @($pb+$pa|Where-Object {$_.queries -ne 262144}).Count){throw 'Public benchmark protocols differ'}
$public=[ordered]@{reference="$PublicReference/public_provider_benchmark.json";candidate="$PublicCandidate/public_provider_benchmark.json";
    referenceThroughput=(Median $pb.throughput);candidateThroughput=(Median $pa.throughput);
    timeChangePercent=100*((Median $pb.throughput)/(Median $pa.throughput)-1);
    referenceBytesPerQuery=(Median $pb.bytesPerQuery);candidateBytesPerQuery=(Median $pa.bytesPerQuery);
    warmupBatches=20;measuredBatches=10;queriesPerBatch=262144;resultEscapes=$true}
$initial=Json "$evidence/initial_performance_comparison.json"
$failures=@($rows|Where-Object {$_.operation -notlike 'FULL_*' -and $_.investigateOverTenPercent})
$status=if($failures.Count -or $public.timeChangePercent -gt 10 -or $initial.allocation.changePercent -gt 10){'REQUIRES_REVIEW'}else{'PASS_PAIRED_REFERENCE'}
$report=[ordered]@{schemaVersion=2;acceptedTask2='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7';backend='LEGACY_RTF_V0';
    initialHistoricalComparison='initial_performance_comparison.json';initialCandidate='benchmark_summary.json';
    reference="$Reference-summary.json";candidate="$Candidate-summary.json";comparisons=$rows;publicProvider=$public;allocation=$initial.allocation;
    policy='Investigate >10% extraction time/allocation regression outside measured variance. FULL includes downstream Minecraft.';
    status=$status}
WriteJson "$evidence/performance_comparison.json" $report
$rows|ForEach-Object {[pscustomobject]$_}|Format-Table operation,timeChangePercent
Write-Output "Public query change: $($public.timeChangePercent)%; status: $status"
