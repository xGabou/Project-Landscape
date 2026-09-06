# Original generated-evidence inventory. All Rights Reserved.
[CmdletBinding()]
param([string]$EvidenceDirectory='docs/task1/evidence')
$ErrorActionPreference='Stop'
$directory=(Resolve-Path -LiteralPath $EvidenceDirectory).Path
$files=@(Get-ChildItem -LiteralPath $directory -Recurse -File | Where-Object {$_.FullName -ne (Join-Path $directory 'manifest.json')} | Sort-Object FullName | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($directory.Length+1).Replace('\','/');bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$manifest=[ordered]@{kind='Reproduction evidence, not correctness goldens';baseline='e9dd8841a1b4a95e4bb2b23e084d40abbc1bea70';files=$files}
[IO.File]::WriteAllText((Join-Path $directory 'manifest.json'),($manifest|ConvertTo-Json -Depth 8).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))
Write-Output ('Inventoried '+$files.Count+' evidence files (manifest excludes itself)')
