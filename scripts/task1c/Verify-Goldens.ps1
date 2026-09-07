# Original Task 1C verification. All Rights Reserved. Never rewrites Task 1A/1B.
param([string]$EvidenceRoot="$PSScriptRoot/../../docs/task1c/evidence",[switch]$FreezeManifest,[string]$Candidate)
$ErrorActionPreference='Stop'
function Table($path){$rows=Get-Content -LiteralPath $path -Raw | ConvertFrom-Json;return $rows}
function EqualFields($a,$b){
    if(@($a.PSObject.Properties).Count -ne @($b.PSObject.Properties).Count){return $false}
    foreach($p in $a.PSObject.Properties){if($null -eq $b.PSObject.Properties[$p.Name] -or $p.Value -cne $b.($p.Name)){return $false}}
    return $true
}
function Json($path,$value){[IO.File]::WriteAllText([IO.Path]::GetFullPath($path),($value|ConvertTo-Json -Depth 80)+"`n",[Text.UTF8Encoding]::new($false))}
$canonical=@(Table "$EvidenceRoot/golden-24/canonical_geography.json")
$direct=@(Table "$EvidenceRoot/golden-24/legacy_direct_samples.json")
$comparison=@(Table "$EvidenceRoot/golden-24/cached_direct_comparison.json")
$identity=@(Table "$EvidenceRoot/golden-24/identity.json")[0]
$fixtures=@(Table "$EvidenceRoot/golden-24/fixtures.json")
$prior=@(Table "$PSScriptRoot/../../docs/task1b/evidence/final-repeat/cached_uncached.json")
$index=@{};foreach($r in $canonical){$index["$($r.seed):$($r.point.x):$($r.point.z)"]=$r.fields}
$rawIndex=@{};foreach($r in $direct){$rawIndex["$($r.seed):$($r.point.x):$($r.point.z)"]=$r.fields}
$failures=[Collections.Generic.List[string]]::new()
if($Candidate){
    foreach($tableName in @('canonical_geography','legacy_direct_samples','biome_hints')){
        $expected=@(Table "$EvidenceRoot/golden-24/$tableName.json");$actual=@(Table "$Candidate/$tableName.json")
        if($expected.Count -ne $actual.Count){$failures.Add("$tableName fixture count changed")}
        for($i=0;$i -lt [Math]::Min($expected.Count,$actual.Count);$i++){
            $a=$actual[$i];$e=$expected[$i]
            if($a.seed -ne $e.seed -or $a.point.x -ne $e.point.x -or $a.point.z -ne $e.point.z -or !(EqualFields $a.fields $e.fields)){$failures.Add("$tableName changed row $i")}
        }
    }
}
foreach($r in $prior){
    $key="$($r.seed):$($r.point.x):$($r.point.z)"
    if(!(EqualFields $r.filtered $index[$key])){$failures.Add("Canonical changed: $key")}
    if(!(EqualFields $r.direct $rawIndex[$key])){$failures.Add("Direct changed: $key")}
}
$legacy=@($comparison|Where-Object {$_.seed -in @('8675309','4303642605','42','-987654321')})
$fields=[ordered]@{};foreach($r in $legacy){foreach($field in $r.differentFields){if(!$fields.Contains($field)){$fields[$field]=0};$fields[$field]++}}
if($legacy.Count -ne 340 -or @($legacy|Where-Object {$_.differentFields.Count -gt 0}).Count -ne 340){$failures.Add('Original 340-row diagnostic changed')}
foreach($pair in @(@('gradient',340),@('height',319),@('heightErosion',176),@('sediment',96),@('terrain',6))){if($fields[$pair[0]] -ne $pair[1]){$failures.Add("Diagnostic count changed: $($pair[0])")}}
$collision=@(Table "$EvidenceRoot/golden-24/seed_collision.json")
if($collision.Count -ne 85 -or @($collision|Where-Object {$_.differentFields.Count -ne 0}).Count){$failures.Add('Legacy-only seed collision changed')}
$tiles=@();foreach($name in @('golden-24','golden-2','golden-48','golden-repeat')){if(Test-Path "$EvidenceRoot/$name/tile_digests.json"){$tiles+=@(Table "$EvidenceRoot/$name/tile_digests.json")}}
$groups=$tiles|Group-Object seed,tileX,tileZ
foreach($g in $groups){if(@($g.Group.sha256|Select-Object -Unique).Count -ne 1){$failures.Add("Scheduling changed: $($g.Name)")}}
$priorTiles=@(Table "$PSScriptRoot/../../docs/task1b/evidence/final-repeat/generation_order.json")
foreach($r in $priorTiles){$current=@($tiles|Where-Object {$_.seed -eq '8675309' -and $_.tileX -eq $r.tileX -and $_.tileZ -eq $r.tileZ})[0];if($r.digest.hash -ne $current.sha256){$failures.Add('Accepted tile digest changed')}}
$summary=[ordered]@{schemaVersion=1;comparator=$identity.comparator;backend='LEGACY_RTF_V0';configurationFingerprint=$identity.configurationFingerprint;
    canonicalRows=$canonical.Count;fieldsPerRow=@($canonical[0].fields.PSObject.Properties).Count;biomeHintRows=@(Table "$EvidenceRoot/golden-24/biome_hints.json").Count;
    acceptedCanonicalRowsCompared=$prior.Count;acceptedDirectRowsCompared=$prior.Count;canonicalDifferences=@($failures|Where-Object {$_ -like 'Canonical*'}).Count;
    legacyDiagnosticRows=$legacy.Count;legacyDiagnosticDifferingRows=@($legacy|Where-Object {$_.differentFields.Count}).Count;legacyDiagnosticFields=$fields;
    seedCollisionRows=$collision.Count;seedCollisionFields=34;tileDigests=$tiles.Count;distinctSeedTiles=@($groups).Count;workerCounts=@($tiles.workers|Sort-Object -Unique);
    candidateCompared=$(if($Candidate){Split-Path $Candidate -Leaf}else{$null});failures=$failures.ToArray();status=$(if($failures.Count){'FAIL'}else{'PASS'})}
Json "$EvidenceRoot/golden_verification.json" $summary
if($FreezeManifest -and !$failures.Count){
    $file="$EvidenceRoot/fixture_manifest.json";if(Test-Path $file){throw 'Fixture manifest already frozen; refusing overwrite'}
    Json $file ([ordered]@{schemaVersion=1;fixtureVersion=1;backend='LEGACY_RTF_V0';comparator=$identity.comparator;configurationFingerprint=$identity.configurationFingerprint;
        identityReference='golden-24/identity.json';canonicalSemantics='LEGACY_FILTERED_CANONICAL';directSemantics='LEGACY_DIRECT_APPROXIMATE';
        exactness='Float raw signed IEEE754 int bits; strings and integral values exact. No epsilon.';
        fieldSchema=@($canonical[0].fields.PSObject.Properties|ForEach-Object {[ordered]@{name=$_.Name;encoding=$(if($_.Name -in @('continentX','continentZ')){'int32'}elseif($_.Name -eq 'erosionMask'){'boolean'}elseif($_.Name -in @('biome','terrain')){'stable name'}else{'float32 raw signed int bits'})}});
        seedFixtures=$fixtures;defectFixtures='../../task1/evidence/full-24-a; ../../task1b/evidence (immutable reproductions, not generic goldens)';
        tileCore='128x128; 160x160 with halo; exponent 3 / border 1 chunk / batch 6';
        collisionPolicy='Expected only for LEGACY_RTF_V0. Future backend divergence assertion inactive until Task 2.'})
}
$summary|ConvertTo-Json -Depth 10
if($failures.Count){throw "$($failures.Count) verification failures"}
