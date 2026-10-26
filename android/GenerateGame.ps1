[CmdletBinding()]
param([string]$GameData, [string]$Generator)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$GameData) { $GameData = Join-Path $repo '.android-work/GameData' }
if (!$Generator) { $Generator = Join-Path $repo '.android-work/rexglue/out/win-amd64/rexglue.exe' }
if (!(Test-Path -LiteralPath $Generator)) { throw "Pinned host code generator is not built: $Generator" }
$required = @('default.xex', 'fifadllzf.xex.dll', 'dlc/dlc_FootballCompEng/dlc/FootballCompEng/FootballCompEngzf.xex.dll')
foreach ($relative in $required) {
    $path = Join-Path $GameData $relative
    $stream = [IO.File]::OpenRead($path)
    try {
        $magic = [byte[]]::new(4)
        if ($stream.Read($magic, 0, 4) -ne 4 -or [Text.Encoding]::ASCII.GetString($magic) -ne 'XEX2') { throw "Invalid XEX2 header: $path" }
    } finally { $stream.Dispose() }
}
$game = Join-Path $repo '.android-work/game'
if (!(Test-Path -LiteralPath $game)) { Copy-Item -LiteralPath (Join-Path $repo 'game-template') -Destination $game -Recurse }
$data = [IO.Path]::GetFullPath($GameData).Replace('\', '/').Replace('"', '\"')
$manifest = [IO.File]::ReadAllText((Join-Path $repo 'game-template/fifastreet_manifest.toml'))
$manifest = $manifest.Replace('GAME_ROOT_PLACEHOLDER/FootballCompEngzf.xex.dll', "$data/dlc/dlc_FootballCompEng/dlc/FootballCompEng/FootballCompEngzf.xex.dll").Replace('GAME_ROOT_PLACEHOLDER', $data)
# The generator writes LF manifests. Match that representation before hashing
# inputs so a Windows template does not invalidate every module on each run.
$manifest = $manifest.Replace("`r`n", "`n").TrimEnd("`r", "`n") + "`n"
$manifestPath = Join-Path $game 'fifastreet_manifest.toml'
if (!(Test-Path -LiteralPath $manifestPath) -or [IO.File]::ReadAllText($manifestPath) -ne $manifest) {
    [IO.File]::WriteAllText($manifestPath, $manifest, [Text.UTF8Encoding]::new($false))
}
Push-Location $game
try {
    & $Generator --log-file (Join-Path $repo '.android-work/codegen.log') codegen $manifestPath
    if ($LASTEXITCODE -ne 0) { throw "Code generation failed: exit $LASTEXITCODE" }
} finally { Pop-Location }
& python "$PSScriptRoot/CompactRegistration.py" "$game/generated"
if ($LASTEXITCODE -ne 0) { throw 'Generated registration validation failed.' }
Write-Output "Generated local game source: $game/generated"
