# Original focused stage comparison. All Rights Reserved. Diagnostic, never block goldens.
param([string]$Run="$PSScriptRoot/../../docs/task1c/evidence/variance",[string]$Output="$PSScriptRoot/../../docs/task1c/evidence/full_chunk_variance.json")
$ErrorActionPreference='Stop'
function Blocks($path){
    $f=[IO.File]::OpenRead($path);$gzip=[IO.Compression.GZipStream]::new($f,[IO.Compression.CompressionMode]::Decompress);$r=[IO.StreamReader]::new($gzip)
    try{return ($r.ReadToEnd()|ConvertFrom-Json)}finally{$r.Dispose();$gzip.Dispose();$f.Dispose()}
}
$stages=Get-Content "$Run/variance_stages.json" -Raw|ConvertFrom-Json
$geo=Get-Content "$Run/variance_geography.json" -Raw|ConvertFrom-Json
$rows=[Collections.Generic.List[object]]::new()
foreach($a in @($stages|Where-Object {$_.repeat -lt 3})){
    $b=@($stages|Where-Object {$_.repeat -eq $a.repeat+3 -and $_.chunkX -eq $a.chunkX -and $_.chunkZ -eq $a.chunkZ -and $_.requestedStatus -eq $a.requestedStatus})[0]
    if($null -eq $b){throw 'Missing repeated stage'}
    $count=0;$types=@{};$examples=@();$min=9999;$max=-9999;$ranges=@{}
    if($a.sha256 -ne $b.sha256){
        $left=Blocks "$Run/$($a.snapshot)";$right=Blocks "$Run/$($b.snapshot)";$height=$a.maxY-$a.minY
        if($left.Count -ne $right.Count){throw 'Snapshot lengths differ'}
        for($i=0;$i -lt $left.Count;$i++){
            if($left[$i] -ceq $right[$i]){continue}
            $count++;$key=$left[$i]+' -> '+$right[$i];if(!$types.ContainsKey($key)){$types[$key]=0};$types[$key]++
            $y=$a.minY+($i%$height);$min=[Math]::Min($min,$y);$max=[Math]::Max($max,$y)
            $band=[int][Math]::Floor($y/16)*16;if(!$ranges.ContainsKey($band)){$ranges[$band]=0};$ranges[$band]++
            if($examples.Count -lt 12){$column=[int][Math]::Floor($i/$height);$examples+=[ordered]@{x=($a.chunkX*16)+($column%16);y=$y;z=($a.chunkZ*16)+[int][Math]::Floor($column/16);a=$left[$i];b=$right[$i]}}
        }
    }
    $rows.Add([ordered]@{seed=$a.seed;chunkX=$a.chunkX;chunkZ=$a.chunkZ;requestedStatus=$a.requestedStatus;actualStatusA=$a.actualStatus;actualStatusB=$b.actualStatus;
        requestedStageIsolated=($a.actualStatus -eq $a.requestedStatus -and $b.actualStatus -eq $b.requestedStatus);differentBlocks=$count;
        minChangedY=$(if($count){$min}else{$null});maxChangedY=$(if($count){$max}else{$null});
        transitions=@($types.GetEnumerator()|Sort-Object Name|ForEach-Object {[ordered]@{transition=$_.Name;blocks=$_.Value}});
        yBands=@($ranges.GetEnumerator()|Sort-Object Name|ForEach-Object {[ordered]@{minY=$_.Name;maxY=$_.Name+15;blocks=$_.Value}});examples=$examples})
}
$geography=@();foreach($a in @($geo|Where-Object {$_.repeat -lt 3})){
    $b=@($geo|Where-Object {$_.repeat -eq $a.repeat+3 -and $_.chunkX -eq $a.chunkX -and $_.chunkZ -eq $a.chunkZ})[0]
    $geography+=[ordered]@{seed=$a.seed;chunkX=$a.chunkX;chunkZ=$a.chunkZ;equalBiomes=$a.storedBiomeSHA256 -eq $b.storedBiomeSHA256;
        differentExactFields=@($a.exact.PSObject.Properties|Where-Object {$_.Value -cne $b.exact.($_.Name)}|ForEach-Object Name)}
}
$report=[ordered]@{schemaVersion=1;fixtureVersion=1;comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee';backend='LEGACY_RTF_V0';identityReference='variance/identity.json';
    protocol='Two fresh worlds for each of 8675309 / 4303642605 / 42; request NOISE,SURFACE,CARVERS,FEATURES,FULL sequentially at eight fixed chunks. No changed gamerules.';
    stageCounts=@($rows|Group-Object {$_.requestedStatus}|ForEach-Object {[ordered]@{status=$_.Name;compared=$_.Count;different=@($_.Group|Where-Object {$_.differentBlocks}).Count;isolated=@($_.Group|Where-Object {$_.requestedStageIsolated}).Count;isolatedDifferences=@($_.Group|Where-Object {$_.requestedStageIsolated -and $_.differentBlocks}).Count}});
    chunks=$rows.ToArray();geography=$geography;
    attribution='A request is not an injection-time snapshot: generation of prerequisites/neighbor features can advance or modify this chunk. Actual status is recorded. No conclusive feature-source or scheduler attribution solely from changed block types.';
    disposition='Deferred downstream block-variance issue; canonical RTF samples/tile digests, not finished block equality, define geography comparator.'}
[IO.File]::WriteAllText([IO.Path]::GetFullPath($Output),($report|ConvertTo-Json -Depth 30)+"`n",[Text.UTF8Encoding]::new($false))
$report.stageCounts|ForEach-Object {[pscustomobject]$_}|Format-Table
Write-Output "Exact geography mismatches: $(@($geography|Where-Object {$_.differentExactFields.Count}).Count); biome mismatches: $(@($geography|Where-Object {!$_.equalBiomes}).Count)"
