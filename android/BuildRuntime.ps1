[CmdletBinding()]
param(
    [string]$ToolsRoot = (Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'),
    [string]$SdkRoot,
    [string]$NdkRoot,
    [string]$CMake,
    [string]$Ninja,
    [int]$Jobs = 6
)
$ErrorActionPreference = 'Stop'
if ($Jobs -lt 1) { throw 'Jobs must be positive.' }
if (!$SdkRoot) { $SdkRoot = Join-Path $ToolsRoot 'android-sdk' }
if (!$NdkRoot) { $NdkRoot = Join-Path $SdkRoot 'ndk/28.2.13676358' }
if (!$CMake) { $CMake = Join-Path $ToolsRoot 'w64devkit/bin/cmake.exe' }
if (!$Ninja) { $Ninja = Join-Path $ToolsRoot 'w64devkit/bin/ninja.exe' }
$repo = Split-Path $PSScriptRoot -Parent
$work = Join-Path $repo '.android-work'
$source = Join-Path $work 'rexglue'
$build = Join-Path $work 'build-rex-arm64'
$pin = 'c94f5ebdcb3c9d1a460ca48e04f9758448f8d518'
$gamePatch = Join-Path $repo 'patches/rexglue-local-changes.patch'
$androidPatch = Join-Path $PSScriptRoot 'rexglue-android.patch'
$performancePatch = Join-Path $PSScriptRoot 'rexglue-performance.patch'
$mobilePatch = Join-Path $PSScriptRoot 'rexglue-mobile.patch'
$shaderPatch = Join-Path $PSScriptRoot 'rexglue-shader-opt.patch'
foreach ($path in @($CMake, $Ninja, "$NdkRoot/build/cmake/android.toolchain.cmake", $gamePatch, $androidPatch, $performancePatch, $mobilePatch, $shaderPatch)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Missing prerequisite: $path. No software was installed." }
}
function Invoke-Checked([string]$Tool, [string[]]$Arguments) {
    & $Tool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed with exit code $LASTEXITCODE" }
}
New-Item -ItemType Directory -Path $work -Force | Out-Null
if (!(Test-Path -LiteralPath "$source/.git")) {
    if (Test-Path -LiteralPath $source) { throw 'Source destination exists and is not a Git checkout.' }
    Invoke-Checked git @('clone', '--filter=blob:none', '--no-checkout', 'https://github.com/rexglue/rexglue-sdk.git', $source)
    Invoke-Checked git @('-C', $source, '-c', 'core.longpaths=true', 'checkout', '--detach', $pin)
}
$head = (& git -C $source rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $head -ne $pin) { throw "ReXGlue must be at pinned commit $pin. Existing checkout was preserved." }
& git -C $source apply --reverse --check $androidPatch 2>$null
if ($LASTEXITCODE -ne 0) {
    Invoke-Checked git @('-C', $source, 'apply', '--check', $gamePatch)
    Invoke-Checked git @('-C', $source, 'apply', '--whitespace=nowarn', $gamePatch)
    Invoke-Checked git @('-C', $source, 'apply', '--check', $androidPatch)
    Invoke-Checked git @('-C', $source, 'apply', $androidPatch)
}
& "$PSScriptRoot/ApplyPerformancePatches.ps1" -Source $source
$modules = @('cli11', 'libmspack', 'FFmpeg', 'tomlplusplus', 'simde', 'xxHash', 'spdlog', 'fmt', 'utfcpp', 'imgui', 'sdl3', 'vulkan-headers', 'vulkan-memory-allocator', 'spirv-headers', 'spirv-tools', 'glslang', 'o1heap', 'inja')
Invoke-Checked git (@('-C', $source, '-c', 'core.longpaths=true', 'submodule', 'update', '--init', '--depth', '1', '--jobs', "$Jobs", '--') + @($modules | ForEach-Object { "thirdparty/$_" }))
Invoke-Checked $CMake @('-S', $source, '-B', $build, '-G', 'Ninja', "-DCMAKE_MAKE_PROGRAM=$Ninja", "-DCMAKE_TOOLCHAIN_FILE=$NdkRoot/build/cmake/android.toolchain.cmake", '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-26', '-DANDROID_STL=c++_shared', '-DCMAKE_BUILD_TYPE=Release', '-DREXGLUE_USE_D3D12=OFF', '-DREXGLUE_USE_VULKAN=ON', '-DREXGLUE_ENABLE_TRACY=OFF', '-DREXGLUE_ENABLE_PERF_COUNTERS=OFF', '-DREXGLUE_BUILD_TESTS=OFF', '-DCMAKE_EXPORT_COMPILE_COMMANDS=ON')
Invoke-Checked $CMake @('--build', $build, '--target', 'rexruntime', 'rexgpu-xenos', '--parallel', "$Jobs")
Write-Output "Runtime and GPU: $source/out/linux-arm64 (Android arm64-v8a; inherited output directory name)."
Write-Output "Compilation database: $build/compile_commands.json"
