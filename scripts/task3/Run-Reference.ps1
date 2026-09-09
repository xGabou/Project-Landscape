# Original same-machine Task 2 control runner. All Rights Reserved.
param([string[]]$Profiles=@('public-provider-benchmark','benchmark'))
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$reference=(Resolve-Path "$root/build/task3-task2-reference").Path
if((git -C $reference rev-parse HEAD) -ne '203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7'){throw 'Wrong reference commit'}
if(git -C $reference diff --name-only -- src/main build.gradle gradle.properties){throw 'Reference production differs'}
foreach($profile in $Profiles){
    if($profile -notin @('public-provider-benchmark','benchmark')){throw 'Unsupported control profile'}
    $name=if($profile -eq 'benchmark'){'benchmark-task2-reference'}else{'public-provider-reference'}
    if(Test-Path "$root/docs/task3/evidence/$name"){throw 'Refusing overwrite'}
    Push-Location $reference
    try {
        $ErrorActionPreference='Continue'
        & ./gradlew.bat runReproductionClient "-PreproProfile=$profile" --console=plain *> "$root/docs/task3/evidence/logs/$name.log"
        $code=$LASTEXITCODE
        $ErrorActionPreference='Stop'
        if($code -ne 0){throw "Reference $profile failed: $code"}
    } finally {Pop-Location}
    & "$PSScriptRoot/Collect-Run.ps1" -Name $name -SourceRoot $reference
}
