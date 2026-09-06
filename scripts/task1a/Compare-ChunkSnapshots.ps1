# Original diagnostic snapshot comparison. All Rights Reserved. No expected terrain values.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RunA, [Parameter(Mandatory=$true)][string]$RunB,
      [Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
function Read-Blocks([string]$path) {
    $file=[IO.File]::OpenRead($path)
    $gzip=[IO.Compression.GZipStream]::new($file,[IO.Compression.CompressionMode]::Decompress)
    $reader=[IO.StreamReader]::new($gzip)
    try { return ($reader.ReadToEnd() | ConvertFrom-Json) } finally {$reader.Dispose();$gzip.Dispose();$file.Dispose()}
}
$rowsA=Get-Content (Join-Path $RunA 'generated_chunks.json') -Raw | ConvertFrom-Json
$rowsB=Get-Content (Join-Path $RunB 'generated_chunks.json') -Raw | ConvertFrom-Json
$rows=[System.Collections.Generic.List[object]]::new()
foreach ($a in $rowsA) {
    $b=@($rowsB | Where-Object {$_.seed -eq $a.seed -and $_.x -eq $a.x -and $_.z -eq $a.z})[0]
    if ($null -eq $b) {throw 'Missing matching chunk'}
    $count=0;$examples=[System.Collections.Generic.List[object]]::new();$transitions=@{};$minChangedY=9999;$maxChangedY=-9999
    if ($a.blocksSHA256 -ne $b.blocksSHA256) {
        $blocksA=Read-Blocks (Join-Path $RunA $a.blockSnapshot)
        $blocksB=Read-Blocks (Join-Path $RunB $b.blockSnapshot)
        if($blocksA.Count -ne $blocksB.Count){throw 'Different block array lengths'}
        $height=$a.maxY-$a.minY
        for($i=0;$i -lt $blocksA.Count;$i++) {
            if($blocksA[$i] -ceq $blocksB[$i]){continue}
            $count++;$transition=$blocksA[$i]+' -> '+$blocksB[$i]
            if(-not $transitions.ContainsKey($transition)){$transitions[$transition]=0};$transitions[$transition]++
            $y=$a.minY+($i%$height);$column=[int][Math]::Floor($i/$height)
            $x=($a.x -shr 4 -shl 4)+($column%16);$z=($a.z -shr 4 -shl 4)+[int][Math]::Floor($column/16)
            $minChangedY=[Math]::Min($y,$minChangedY);$maxChangedY=[Math]::Max($y,$maxChangedY)
            if($examples.Count -lt 20){$examples.Add([ordered]@{x=$x;y=$y;z=$z;a=$blocksA[$i];b=$blocksB[$i]})}
        }
    }
    $rows.Add([ordered]@{seed=$a.seed;x=$a.x;z=$a.z;differentBlocks=$count;minChangedY=$minChangedY;maxChangedY=$maxChangedY;
        equalStoredBiomes=$a.storedQuartBiomesSHA256 -eq $b.storedQuartBiomesSHA256;
        equalBlockBiomes=$a.biomesSHA256 -eq $b.biomesSHA256;
        differentExactCellFields=@($a.exact.psobject.Properties|Where-Object {$_.Value -cne $b.exact.($_.Name)}|ForEach-Object Name);
        transitions=@($transitions.GetEnumerator()|Sort-Object Name|ForEach-Object {[ordered]@{transition=$_.Name;blocks=$_.Value}});examples=$examples})
}
$report=[ordered]@{kind='Repeated live FULL-chunk snapshots, not a golden nor attribution to a specific generation stage';runA=(Split-Path $RunA -Leaf);runB=(Split-Path $RunB -Leaf);chunks=$rows}
$dir=New-Item -ItemType Directory -Path $OutputDirectory -Force
[IO.File]::WriteAllText((Join-Path $dir.FullName 'chunk_repeats.json'),($report|ConvertTo-Json -Depth 15).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))
Write-Output ('Compared '+$rows.Count+' FULL chunks; '+@($rows|Where-Object {$_.differentBlocks -gt 0}).Count+' differ. See chunk_repeats.json; do not bless new goldens.')
