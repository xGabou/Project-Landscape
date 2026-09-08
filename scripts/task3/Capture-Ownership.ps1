# Original Task 3 read-only source audit/export. All Rights Reserved.
param([string]$Output='docs/task3/evidence/field_ownership.json')
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
Set-Location $root
$physical=@('height','terrain','continentId','continentEdge','continentX','continentZ','terrainRegionId','terrainRegionEdge','beachNoise')
$hydrology=@('riverMask','erosionMask')
$finalization=@('heightErosion','sediment','gradient')
$hints=@('erosion','weirdness')
$classification=@('biomeRegionId','biomeRegionEdge','macroBiomeId','regionMoisture','regionTemperature','biome','temperature','moisture')
$all=$physical+$hydrology+$finalization+$hints+$classification
$writes=@()
$paths=@('src/main/java/raccoonman/reterraforged/world/worldgen/cell','src/main/java/raccoonman/reterraforged/world/worldgen/densityfunction/tile/filter')
foreach($file in Get-ChildItem $paths -Recurse -Filter '*.java'){
    $path=$file.FullName.Substring($root.Length+1).Replace('\','/')
    $lineNumber=0
    foreach($line in Get-Content $file.FullName -Encoding UTF8){
        $lineNumber++
        if($line.TrimStart().StartsWith('//')){continue}
        $targets=if($file.Name -eq 'Cell.java'){'(?:this|cell\d*|map\.getCellRaw\([^)]*\))'}else{'(?:cell\d*|map\.getCellRaw\([^)]*\))'}
        foreach($match in [regex]::Matches($line,"$targets\.(?<field>$($all -join '|'))\s*(?:\+=|-=|\*=|/=|=(?!=))")){
            $field=$match.Groups['field'].Value
            $owner=if($field -in $hydrology){'HYDROLOGY'}elseif($field -in $hints){'LEGACY MINECRAFT PARAMETER HINT'}elseif($field -in $classification){'LEGACY CLIMATE/BIOME CLASSIFICATION'}elseif($field -in $finalization){'GEOGRAPHY FINALIZATION'}else{'PHYSICAL GEOGRAPHY'}
            if($path -match '/rivermap/' -and $field -in @('height','terrain')){$owner='HYDROLOGY'}
            if($path -match '/filter/' -and $field -in @('height','terrain')){$owner='GEOGRAPHY FINALIZATION'}
            $writes += [ordered]@{file=$path;line=$lineNumber;field=$field;classification=$owner;statement=$line.Trim();
                transportOnly=($file.Name -eq 'Cell.java');activeFilterFamily=($path -notmatch '/cell/filter/');
                legacyCoastCompatibility=($path -match '/climate/' -and $field -eq 'terrain')}
        }
    }
}
$report=[ordered]@{schemaVersion=1;sourceHead=(git rev-parse HEAD);acceptedTask2='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7';
    methodMap='../TASK_3_GEOGRAPHY_EXTRACTION.md';
    scope='Explicit Cell writes in point generation and both filter families; reset/copy are marked transport, inactive cell/filter duplicates are marked separately. Noise/member fields of non-Cell owners are excluded.';
    fieldCount=$all.Count;writes=$writes}
[IO.File]::WriteAllText((Join-Path $root $Output),($report|ConvertTo-Json -Depth 20)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output "$($writes.Count) explicit Cell writes classified across $($all.Count) fields"
