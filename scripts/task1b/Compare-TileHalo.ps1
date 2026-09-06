# Original developer comparison tooling. All Rights Reserved.
param([Parameter(Mandatory=$true)][string]$Before, [Parameter(Mandatory=$true)][string]$After,
      [Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
function Read-Snapshot([string]$dir) {
    $file=[IO.File]::OpenRead((Join-Path $dir 'tile_bounds_halo.json.gz'))
    $gzip=[IO.Compression.GZipStream]::new($file,[IO.Compression.CompressionMode]::Decompress)
    $reader=[IO.StreamReader]::new($gzip)
    try { return ($reader.ReadToEnd() | ConvertFrom-Json) } finally {$reader.Dispose();$gzip.Dispose();$file.Dispose()}
}
$a=Read-Snapshot $Before; $b=Read-Snapshot $After
if($a.Count -ne 25600 -or $b.Count -ne $a.Count) {throw 'Expected the full baseline 160x160 tile'}
$changes=[Collections.Generic.List[object]]::new()
for($i=0;$i -lt $a.Count;$i++) {
    $fields=@($a[$i].psobject.Properties | Where-Object {$_.Value -cne $b[$i].($_.Name)} | ForEach-Object Name)
    if($fields.Count) {
        $x=$i%160;$z=[int][Math]::Floor($i/160)
        $changes.Add([ordered]@{rawX=$x;rawZ=$z;worldX=$x-16;worldZ=$z-16;core=($x-ge16-and$x-lt144-and$z-ge16-and$z-lt144);fields=$fields;before=$a[$i];after=$b[$i]})
    }
}
$summary=[ordered]@{before=$Before;after=$After;cells=$a.Count;changed=$changes.Count;changedCore=@($changes | Where-Object core).Count;changes=$changes}
$dir=New-Item -ItemType Directory -Path $OutputDirectory -Force
[IO.File]::WriteAllText((Join-Path $dir.FullName 'halo_comparison.json'),($summary|ConvertTo-Json -Depth 12).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]$summary | Select-Object cells,changed,changedCore
if($summary.changedCore) {throw 'Core change needs investigation'}
