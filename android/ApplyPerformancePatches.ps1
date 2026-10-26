[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Source)
$ErrorActionPreference='Stop'
& git -C $Source apply --reverse --check (Join-Path $PSScriptRoot 'rexglue-vfs-file-links.patch') 2>$null
if ($LASTEXITCODE -eq 0) { return }
foreach ($name in @('rexglue-performance.patch', 'rexglue-mobile.patch', 'rexglue-shader-opt.patch', 'rexglue-vulkan-debug-utils.patch', 'rexglue-renderpasses.patch', 'rexglue-skate-runtime.patch', 'rexglue-vfs-file-links.patch')) {
    $patch=Join-Path $PSScriptRoot $name
    & git -C $Source apply --reverse --check $patch 2>$null
    if ($LASTEXITCODE -eq 0) { continue }
    & git -C $Source apply --check $patch
    if ($LASTEXITCODE -ne 0) { throw "Patch does not match SDK: $name" }
    & git -C $Source apply $patch
    if ($LASTEXITCODE -ne 0) { throw "Patch failed: $name" }
}
