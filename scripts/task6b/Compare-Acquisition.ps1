param([string]$Baseline='build/task6b-baseline-02',[string]$Optimized='build/task6b-optimized-01',[string]$Output='docs/task6b/evidence')
$ErrorActionPreference='Stop'
function ReadJson($p){Get-Content -LiteralPath $p -Raw | ConvertFrom-Json}
function WriteJson($name,$value){[IO.File]::WriteAllText((Join-Path (Resolve-Path $Output) $name),($value | ConvertTo-Json -Depth 80),[Text.UTF8Encoding]::new($false))}
$rows=@()
foreach($name in @('sequential_generation','spawn_expansion','scattered_generation','interleaved_regions','climate_heavy')){
    $b=ReadJson "$Baseline/$name.json"; $a=ReadJson "$Optimized/$name.json"
    if($b.outputs.Count -ne $a.outputs.Count){throw 'Output count changed'}
    for($i=0;$i -lt $b.outputs.Count;$i++){
        $x=$b.outputs[$i];$y=$a.outputs[$i]
        if($x.x -ne $y.x -or $x.z -ne $y.z -or ($x.rawDoubleBits -join ',') -cne ($y.rawDoubleBits -join ',')){throw "Exact climate mismatch $name $i"}
    }
    $bw=($b.measurements | Measure-Object wallNanos -Sum).Sum; $aw=($a.measurements | Measure-Object wallNanos -Sum).Sum
    $row=[ordered]@{scenario=$name;baselineWallNanos=$bw;optimizedWallNanos=$aw;wallReductionPercent=100*(1-$aw/$bw);baselineProcessCpuNanos=($b.measurements | Measure-Object processCpuNanos -Sum).Sum;optimizedProcessCpuNanos=($a.measurements | Measure-Object processCpuNanos -Sum).Sum;exactClimateComparisons=$a.outputs.Count;allSevenRawDoubleBitsExact=$true;baselineSurface=$b.surfaceCache;optimizedSurface=$a.surfaceCache;baselineClimate=$b.climateCache;optimizedClimate=$a.climateCache}
    $rows+=,$row; WriteJson "$name.json" $row
}
WriteJson 'paired_comparison.json' ([ordered]@{baseline=$Baseline;optimized=$Optimized;methodology='Same machine/JDK17, Forge data launcher, 8 available processors, 6GiB heap, same preset/seed/config/coordinates. Separate fresh JVMs; each workload fresh world context, one cold then one warm pass. JFR profile enabled in both. Not a confidence interval or FULL chunk result.';scenarios=$rows;digestCaveat='Original outputDigest used JVM-dependent Map serialization order; acceptance compares coordinates and all seven raw-double bit values directly.'})
WriteJson 'baseline_benchmark.json' ([ordered]@{source=$Baseline;environment=(ReadJson "$Baseline/completion.json");microbenchmarks=(ReadJson "$Baseline/microbenchmarks.json");productionHead='a3140a5d7113137563d275cbaea05a8e94f3b23e'})
WriteJson 'optimized_benchmark.json' ([ordered]@{source=$Optimized;environment=(ReadJson "$Optimized/completion.json");microbenchmarks=(ReadJson "$Optimized/microbenchmarks.json");productionHead='cee8f11';scope='offline acquisition; runtime FULL evidence separate'})
$rows | ForEach-Object {[pscustomobject]@{Scenario=$_.scenario;Reduction=$_.wallReductionPercent;Exact=$_.allSevenRawDoubleBitsExact}}
