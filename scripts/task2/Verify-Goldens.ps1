# Original Task 2 comparator gate. All Rights Reserved. Accepted Task 1C data is read-only.
param([string]$CandidateRoot="$PSScriptRoot/../../docs/task2/evidence",[switch]$PrimaryOnly,
    [string]$PrimarySuite='golden-24',[string]$OutputPrefix='')
$ErrorActionPreference='Stop'
$reference=(Resolve-Path "$PSScriptRoot/../../docs/task1c/evidence").Path
function Rows($path){return @(Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)}
function WriteJson($path,$value){[IO.File]::WriteAllText([IO.Path]::GetFullPath($path),($value|ConvertTo-Json -Depth 70)+"`n",[Text.UTF8Encoding]::new($false))}
function FieldsEqual($a,$b){
    if(@($a.PSObject.Properties).Count -ne @($b.PSObject.Properties).Count){return $false}
    foreach($p in $a.PSObject.Properties){if($null -eq $b.PSObject.Properties[$p.Name] -or $p.Value -cne $b.($p.Name)){return $false}}
    return $true
}
$failures=[Collections.Generic.List[string]]::new()
$counts=[ordered]@{}
foreach($table in @('canonical_geography','legacy_direct_samples','biome_hints','minecraft_biomes','seed_collision','cached_direct_comparison')){
    $expected=Rows "$reference/golden-24/$table.json";$actual=Rows "$CandidateRoot/$PrimarySuite/$table.json"
    $counts[$table]=$actual.Count
    if($expected.Count -ne $actual.Count){$failures.Add("$table row count changed")}
    for($i=0;$i -lt [Math]::Min($expected.Count,$actual.Count);$i++){
        $e=$expected[$i];$a=$actual[$i]
        if($table -eq 'seed_collision'){
            if($a.differentFields.Count -ne 0 -or !(FieldsEqual $e.a $a.a) -or !(FieldsEqual $e.b $a.b)){$failures.Add("Legacy collision changed row $i")}
        } elseif($a.seed -ne $e.seed -or $a.point.x -ne $e.point.x -or $a.point.z -ne $e.point.z){$failures.Add("$table fixture identity changed row $i")}
        elseif($table -eq 'minecraft_biomes'){if($a.biome -cne $e.biome){$failures.Add("Minecraft biome changed row $i")}}
        elseif($table -eq 'cached_direct_comparison'){if(($a.differentFields -join ',') -cne ($e.differentFields -join ',')){$failures.Add("Direct semantic differences changed row $i")}}
        elseif(!(FieldsEqual $e.fields $a.fields)){$failures.Add("$table fields changed row $i")}
    }
}
$tileResults=@();$names=@($PrimarySuite);if(!$PrimaryOnly){$names+=@('golden-2','golden-48','golden-repeat','golden-tb')}
foreach($name in $names){
    $actual=Rows "$CandidateRoot/$name/tile_digests.json"
    $referenceName=if($name -eq $PrimarySuite){'golden-24'}else{$name}
    $expected=Rows "$reference/$referenceName/tile_digests.json"
    if($actual.Count -ne $expected.Count){$failures.Add("$name tile count changed")}
    $lookup=@{};foreach($r in $expected){$lookup["$($r.seed):$($r.tileX):$($r.tileZ):$($r.order)"]=$r.sha256}
    $changed=0;foreach($r in $actual){if($lookup["$($r.seed):$($r.tileX):$($r.tileZ):$($r.order)"] -cne $r.sha256){$changed++}}
    if($changed){$failures.Add("$name has $changed changed tile digests")}
    $tileResults += [ordered]@{name=$name;count=$actual.Count;changed=$changed;workers=@($actual.workers|Sort-Object -Unique)}
}
if(!$PrimaryOnly){
    foreach($suite in @('golden-repeat','golden-tb')){foreach($table in @('canonical_geography','legacy_direct_samples','biome_hints','minecraft_biomes')){
        $referenceSuite=if($suite -eq 'golden-repeat'){'golden-24'}else{$suite}
        $expected=Rows "$reference/$referenceSuite/$table.json";$actual=Rows "$CandidateRoot/$suite/$table.json"
        if($actual.Count -ne $expected.Count){$failures.Add("$suite $table row count changed")}
        for($i=0;$i -lt [Math]::Min($actual.Count,$expected.Count);$i++){
            if($table -eq 'minecraft_biomes'){$equal=$actual[$i].biome -ceq $expected[$i].biome}else{$equal=FieldsEqual $actual[$i].fields $expected[$i].fields}
            if(!$equal -or $actual[$i].seed -ne $expected[$i].seed -or $actual[$i].point.x -ne $expected[$i].point.x -or $actual[$i].point.z -ne $expected[$i].point.z){$failures.Add("$suite $table changed row $i")}
        }
    }}
}
$diagnostic=Rows "$CandidateRoot/$PrimarySuite/cached_direct_comparison.json"
$diagnostic=@($diagnostic | Where-Object {$_.seed -in @('8675309','4303642605','42','-987654321')})
$fieldCounts=[ordered]@{};foreach($r in $diagnostic){foreach($field in $r.differentFields){if(!$fieldCounts.Contains($field)){$fieldCounts[$field]=0};$fieldCounts[$field]++}}
if($diagnostic.Count -ne 340 -or @($diagnostic|Where-Object {$_.differentFields.Count}).Count -ne 340){$failures.Add('Legacy 340-row direct diagnostic count changed')}
foreach($pair in @(@('gradient',340),@('height',319),@('heightErosion',176),@('sediment',96),@('terrain',6))){if($fieldCounts[$pair[0]] -ne $pair[1]){$failures.Add("Legacy direct field count changed: $($pair[0])")}}
$summary=[ordered]@{schemaVersion=1;fixtureVersion=1;comparator='cd1a0f8415030ba9f5e865d9abd4520e3c3a18ee';backend='LEGACY_RTF_V0';
    canonicalSemantics='LEGACY_FILTERED_CANONICAL';directSemantics='LEGACY_DIRECT_APPROXIMATE';
    exactness='signed raw float32 bits and exact identifiers; no epsilon';counts=$counts;tileResults=$tileResults;
    diagnosticRows=@($diagnostic).Count;diagnosticDifferingRows=@($diagnostic|Where-Object {$_.differentFields.Count}).Count;diagnosticFields=$fieldCounts;
    primaryOnly=[bool]$PrimaryOnly;failures=$failures.ToArray();status=$(if($failures.Count){'FAIL'}else{'PASS'})}
WriteJson "$CandidateRoot/${OutputPrefix}legacy_golden_comparison.json" $summary
WriteJson "$CandidateRoot/${OutputPrefix}tile_digest_comparison.json" $tileResults
$summary|ConvertTo-Json -Depth 15
if($failures.Count){throw "$($failures.Count) legacy comparator failures; stop before proceeding"}
