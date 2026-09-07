# Original verification tool. All Rights Reserved.
param([Parameter(Mandatory=$true)][string]$Before,[Parameter(Mandatory=$true)][string]$After,[Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
function Inventory([string]$path) {
    $root=(Resolve-Path -LiteralPath $path).Path
    $result=[ordered]@{}
    Get-ChildItem -LiteralPath $root -Recurse -File | Sort-Object FullName | ForEach-Object {
        $key=$_.FullName.Substring($root.Length+1).Replace('\','/')
        $result[$key]=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }
    return $result
}
$a=Inventory $Before; $b=Inventory $After
$changes=@(@($a.Keys)+@($b.Keys) | Sort-Object -Unique | Where-Object {$a[$_] -cne $b[$_]})
$result=[ordered]@{before=$Before;after=$After;beforeCount=$a.Count;afterCount=$b.Count;changedFiles=$changes;beforeHashes=$a;afterHashes=$b}
$dir=New-Item -ItemType Directory -Path $OutputDirectory -Force
[IO.File]::WriteAllText((Join-Path $dir.FullName 'enabled_pack_comparison.json'),($result|ConvertTo-Json -Depth 8).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))
if($changes.Count){throw 'Enabled preset export differs'}
Write-Output "PASS: $($a.Count) enabled preset files byte-identical"
