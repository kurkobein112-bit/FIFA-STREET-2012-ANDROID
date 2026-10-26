[CmdletBinding()]
param(
    [string]$ToolsRoot=(Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'),
    [string]$KeyStore,
    [string]$KeyAlias='androiddebugkey',
    [switch]$Diagnostic
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$game=Join-Path $repo '.android-work/build-game-arm64'
$runtime=Join-Path $game 'runtime'
$work=Join-Path $PSScriptRoot '.local/package'
$sdk=Join-Path $ToolsRoot 'android-sdk'
$java=Join-Path $ToolsRoot 'jdk/jdk-17.0.20.1+1'
$bt=Join-Path $sdk 'build-tools/35.0.0'
$ndk=Join-Path $sdk 'ndk/28.2.13676358'
$jar=Join-Path $sdk 'platforms/android-35/android.jar'
function Checked([string]$Tool,[string[]]$Arguments) {
    & $Tool @Arguments
    if($LASTEXITCODE -ne 0){throw "$Tool failed: $LASTEXITCODE"}
}
foreach($path in @($work,"$work/classes","$work/dex","$work/lib/arm64-v8a","$repo/dist")){
    New-Item -ItemType Directory -Force -Path $path | Out-Null
}
$libraries=@("$game/libfifastreet.so","$game/libfifastreet_fifadllzf_xex.so","$game/libfifastreet_FootballCompEngzf_xex.so",
    "$runtime/librexruntime.so","$runtime/librexgpu-xenos.so", "$ndk/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so")
foreach($library in $libraries){if(!(Test-Path -LiteralPath $library)){throw "Run BuildGame.ps1 first: missing $library"}}
$clang=Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/aarch64-linux-android26-clang++.cmd'
Checked $clang @('-std=c++17','-O2','-fPIC','-shared','-Wall','-Wextra','-Werror','-Wl,-z,max-page-size=16384','-Wl,--no-undefined','-Wl,-soname,libtouchcontrols.so',
    "-I$repo/.android-work/rexglue/thirdparty/sdl3/include","$PSScriptRoot/native/touch_controls.cpp","-L$runtime",'-lrexruntime','-o',"$work/lib/arm64-v8a/libtouchcontrols.so")
$strip=Join-Path $ndk 'toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
foreach($library in $libraries){Checked $strip @('--strip-unneeded','-o',(Join-Path "$work/lib/arm64-v8a" (Split-Path $library -Leaf)),$library)}
$sources=@(Get-ChildItem -LiteralPath "$PSScriptRoot/src" -Filter '*.java' -Recurse | ForEach-Object FullName)
$sources+=@(Get-ChildItem -LiteralPath "$repo/.android-work/rexglue/thirdparty/sdl3/android-project/app/src/main/java/org/libsdl/app" -Filter '*.java' | ForEach-Object FullName)
Checked "$java/bin/javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-Xlint:-options','-classpath',$jar,'-d',"$work/classes")+$sources)
$classes=@(Get-ChildItem -LiteralPath "$work/classes" -Filter '*.class' -Recurse | ForEach-Object FullName)
$originalJava=$env:JAVA_HOME
try{
    $env:JAVA_HOME=$java
    Checked "$java/bin/jar.exe" @('cf',"$work/classes.jar",'-C',"$work/classes",'.')
    Checked "$bt/d8.bat" @('--lib',$jar,'--min-api','26','--output',"$work/dex","$work/classes.jar")
    $manifest=[xml][IO.File]::ReadAllText("$PSScriptRoot/AndroidManifest.xml")
    if($Diagnostic){$manifest.manifest.application.SetAttribute('debuggable','http://schemas.android.com/apk/res/android','true') | Out-Null}
    $manifest.Save("$work/AndroidManifest.xml")
    Checked "$bt/aapt.exe" @('package','-f','-M',"$work/AndroidManifest.xml",'-S',"$PSScriptRoot/res",'-I',$jar,'-F',"$work/unsigned.apk")
    $zip=[IO.Compression.ZipFile]::Open("$work/unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
    try{
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$work/dex/classes.dex",'classes.dex') | Out-Null
        foreach($file in Get-ChildItem -LiteralPath "$work/lib/arm64-v8a" -Filter '*.so'){
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,"lib/arm64-v8a/$($file.Name)") | Out-Null
        }
        foreach($file in Get-ChildItem -LiteralPath "$repo/licenses" -File -Recurse){
            $name='assets/licenses/'+[IO.Path]::GetRelativePath("$repo/licenses",$file.FullName).Replace('\','/')
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$name) | Out-Null
        }
    }finally{$zip.Dispose()}
    Checked "$bt/zipalign.exe" @('-f','-P','16','4',"$work/unsigned.apk","$work/aligned.apk")
    if(!$KeyStore){
        $KeyStore=Join-Path $PSScriptRoot '.local/debug.keystore'
        if(!(Test-Path -LiteralPath $KeyStore)){
            Checked "$java/bin/keytool.exe" @('-genkeypair','-keystore',$KeyStore,'-storepass','android','-keypass','android','-alias','androiddebugkey','-dname','CN=FIFA Street Android Development','-keyalg','RSA','-keysize','2048','-validity','10000','-noprompt')
        }
    }
    $name=if($Diagnostic){'FIFA-STREET-0.1-diagnostic.apk'}else{'FIFA-STREET-0.1.apk'}
    $apk=Join-Path "$repo/dist" $name
    if($env:FIFA_STORE_PASSWORD -and $env:FIFA_KEY_PASSWORD){
        Checked "$bt/apksigner.bat" @('sign','--ks',$KeyStore,'--ks-key-alias',$KeyAlias,'--ks-pass','env:FIFA_STORE_PASSWORD','--key-pass','env:FIFA_KEY_PASSWORD','--out',$apk,"$work/aligned.apk")
    }else{
        if($KeyAlias -ne 'androiddebugkey'){throw 'Set FIFA_STORE_PASSWORD and FIFA_KEY_PASSWORD for a release key'}
        Checked "$bt/apksigner.bat" @('sign','--ks',$KeyStore,'--ks-key-alias',$KeyAlias,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,"$work/aligned.apk")
    }
    Checked "$bt/apksigner.bat" @('verify','--verbose',$apk)
    Checked "$bt/zipalign.exe" @('-c','-P','16','4',$apk)
    $hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $name" | Set-Content -LiteralPath "$apk.sha256" -Encoding ascii
    Write-Output "APK: $apk"
    Write-Output "SHA256: $hash"
}finally{$env:JAVA_HOME=$originalJava}
