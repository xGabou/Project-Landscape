# Original Task 3 measurement metadata. All Rights Reserved.
$ErrorActionPreference='Stop'
$root=(Resolve-Path "$PSScriptRoot/../..").Path
$cpu=Get-CimInstance Win32_Processor
$system=Get-CimInstance Win32_ComputerSystem
$os=Get-CimInstance Win32_OperatingSystem
$result=[ordered]@{schemaVersion=1;acceptedTask2='203dc3c1c2e1d4e09fa911a3d6dc7472664f4ba7';cpu=$cpu.Name;physicalCores=$cpu.NumberOfCores;
    logicalProcessors=$system.NumberOfLogicalProcessors;physicalMemoryBytes=$system.TotalPhysicalMemory;os=$os.Caption;osBuild=$os.BuildNumber;
    powerPlan=(powercfg /getactivescheme);minecraft='1.20.1';forge='47.4.22';java='17.0.17+10';gradle='8.11';forgeGradle='6.0.42';terraBlender='3.0.1.10';
    benchmarkTerraBlenderEnabled=$false;developmentMods=$false;execution='Mapped live client / integrated server, 6 GiB max heap, 24 RTF workers';
    instrumentation='Existing test-only cache observers, nanoTime, ThreadMXBean; portable public API benchmark result escapes via volatile; no profiler';
    environmentReference='benchmark/environment.json'}
[IO.File]::WriteAllText("$root/docs/task3/evidence/machine.json",($result|ConvertTo-Json -Depth 10)+"`n",[Text.UTF8Encoding]::new($false))
