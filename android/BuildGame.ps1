[CmdletBinding()]
param([string]$ToolsRoot=(Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'), [string]$Generator, [string]$GameData, [int]$Jobs=8)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$source=Join-Path $repo '.android-work/rexglue'
if (!$Generator) { $Generator=Join-Path $source 'out/win-amd64/rexglue.exe' }
& "$PSScriptRoot/ApplyPerformancePatches.ps1" -Source $source
& "$PSScriptRoot/GenerateGame.ps1" -GameData $GameData -Generator $Generator
$game=Join-Path $repo '.android-work/game'
$build=Join-Path $repo '.android-work/build-game-arm64'
$ndk=Join-Path $ToolsRoot 'android-sdk/ndk/28.2.13676358'
$cmake=Join-Path $ToolsRoot 'w64devkit/bin/cmake.exe'
$ninja=Join-Path $ToolsRoot 'w64devkit/bin/ninja.exe'
Copy-Item -LiteralPath "$repo/game-template/CMakeLists.txt" -Destination "$game/CMakeLists.txt"
foreach ($name in @('main.cpp','fifastreet_app.h','stubs.cpp','native_libc_hooks.cpp','resolution_hooks.cpp')) {
    Copy-Item -LiteralPath "$repo/game-template/src/$name" -Destination "$game/src/$name"
}
& $cmake -S $game -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_TOOLCHAIN_FILE=$ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release "-DREXSDK_DIR=$source" "-DREXGLUE_HOST_CODEGEN_EXECUTABLE=$Generator" "-DREXGLUE_OUTPUT_DIRECTORY=$build/runtime" -DREXGLUE_USE_D3D12=OFF -DREXGLUE_USE_VULKAN=ON -DREXGLUE_ENABLE_TRACY=OFF -DREXGLUE_ENABLE_PERF_COUNTERS=OFF -DREXGLUE_BUILD_TESTS=OFF
if ($LASTEXITCODE -ne 0) { throw 'Android configuration failed' }
& $cmake --build $build --target fifastreet --parallel $Jobs
if ($LASTEXITCODE -ne 0) { throw 'Android compilation failed' }
