[CmdletBinding()]
param([string]$ToolsRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'), [int]$Jobs = 6)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
$source = Join-Path $repo '.android-work/rexglue'
$build = Join-Path $repo '.android-work/build-codegen-gcc'
$cmake = Join-Path $ToolsRoot 'w64devkit/bin/cmake.exe'
$ninja = Join-Path $ToolsRoot 'w64devkit/bin/ninja.exe'
$gcc = Join-Path $ToolsRoot 'w64devkit/bin/gcc.exe'
$gxx = Join-Path $ToolsRoot 'w64devkit/bin/g++.exe'
foreach ($path in @($cmake, $ninja, $gcc, $gxx)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Existing host tool not found: $path. Nothing was installed." }
}
& "$PSScriptRoot/BuildRuntime.ps1" -ToolsRoot $ToolsRoot -Jobs $Jobs
$ffmpeg = Join-Path $source 'thirdparty/FFmpeg'
$patch = Join-Path $PSScriptRoot 'ffmpeg-mingw.patch'
& git -C $ffmpeg apply --reverse --check $patch 2>$null
if ($LASTEXITCODE -ne 0) {
    & git -C $ffmpeg apply --check $patch
    if ($LASTEXITCODE -ne 0) { throw 'FFmpeg host patch does not match the source.' }
    & git -C $ffmpeg apply $patch
    if ($LASTEXITCODE -ne 0) { throw 'FFmpeg host patch failed.' }
}
$oldPath = $env:PATH
try {
    $env:PATH = "$ToolsRoot\w64devkit\bin;$env:PATH"
    & $cmake -S $source -B $build -G Ninja "-DCMAKE_MAKE_PROGRAM=$ninja" "-DCMAKE_C_COMPILER=$gcc" "-DCMAKE_CXX_COMPILER=$gxx" -DCMAKE_C_FLAGS=-mssse3 -DCMAKE_CXX_FLAGS=-mssse3 -DCMAKE_EXE_LINKER_FLAGS=-static -DCMAKE_SHARED_LINKER_FLAGS=-static -DREXGLUE_ALLOW_GNU_HOST_CODEGEN=ON -DCMAKE_BUILD_TYPE=Release -DREXGLUE_USE_D3D12=OFF -DREXGLUE_USE_VULKAN=ON -DREXGLUE_ENABLE_TRACY=OFF -DREXGLUE_ENABLE_PERF_COUNTERS=OFF -DREXGLUE_BUILD_TESTS=OFF
    if ($LASTEXITCODE -ne 0) { throw 'Host code generator configuration failed.' }
    & $cmake --build $build --target rexglue --parallel $Jobs
    if ($LASTEXITCODE -ne 0) { throw 'Host code generator build failed.' }
    & "$source/out/win-amd64/rexglue.exe" --version
    if ($LASTEXITCODE -ne 0) { throw 'Host code generator smoke test failed.' }
} finally { $env:PATH = $oldPath }
