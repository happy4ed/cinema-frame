param([string]$SdkPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'), [string]$JavaBin = '')
$ErrorActionPreference = 'Stop'
$sdk = $SdkPath
if (-not $JavaBin) {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { $JavaBin = Join-Path $env:JAVA_HOME 'bin' }
    else { $compiler = Get-Command javac.exe -ErrorAction SilentlyContinue; if ($compiler) { $JavaBin = Split-Path $compiler.Source } }
}
if (-not $JavaBin) { throw 'JDK required: provide -JavaBin with the JDK bin directory.' }
$javaDir = $JavaBin
foreach ($requiredTool in @("java.exe","javac.exe","jar.exe","keytool.exe")) {
    if (-not (Test-Path (Join-Path $javaDir $requiredTool))) { throw "JDK tool missing: $requiredTool. Pass -JavaBin with a full JDK bin directory (not a PATH shim)." }
}
$toolsDir = Join-Path $sdk 'build-tools\35.0.0'
$androidJar = Join-Path $sdk 'platforms\android-34\android.jar'
$project = $PSScriptRoot
if (-not (Test-Path (Join-Path $project 'android/assets/movies.json'))) { throw 'Film catalogue is local-only: run python tools/refresh_catalog.py before building.' }
Copy-Item -LiteralPath (Join-Path $project 'config/music-tags.json') -Destination (Join-Path $project 'android/assets/music-tags.json')
$buildRoot = Join-Path $project 'build'
New-Item -ItemType Directory -Force $buildRoot | Out-Null
$keyFile = Join-Path $buildRoot 'cinema-test.p12'
if (-not (Test-Path -LiteralPath $keyFile)) {
    & "$javaDir\keytool.exe" -genkeypair -keystore $keyFile -storepass android -keypass android -alias cinema -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Cinema Frame Personal Test'
    if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed' }
}
foreach ($target in @('android','phone')) {
    $src = Join-Path $project $target
    $build = Join-Path $buildRoot $target
    $classes = Join-Path $build 'classes'
    New-Item -ItemType Directory -Force $build,$classes | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $src 'src') -Filter '*.java' -Recurse | ForEach-Object {$_.FullName})
    & "$javaDir\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d $classes @sources
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed: $target" }
    & "$javaDir\jar.exe" cf (Join-Path $build 'classes.jar') -C $classes .
    & "$javaDir\java.exe" -cp "$toolsDir\lib\d8.jar" com.android.tools.r8.D8 --lib $androidJar --min-api 26 --output $build (Join-Path $build 'classes.jar')
    if ($LASTEXITCODE -ne 0) { throw 'DEX compilation failed' }
    $unsigned = Join-Path $build 'unsigned.apk'
    $aaptArgs = @('package','-f','-M',(Join-Path $src 'AndroidManifest.xml'),'-I',$androidJar,'-F',$unsigned)
    if (Test-Path (Join-Path $src 'assets')) {$aaptArgs += @('-A',(Join-Path $src 'assets'))}
    & "$toolsDir\aapt.exe" @aaptArgs
    if ($LASTEXITCODE -ne 0) { throw 'APK packaging failed' }
    Push-Location $build
    try { & "$toolsDir\aapt.exe" add $unsigned classes.dex } finally { Pop-Location }
    if (Test-Path (Join-Path $src 'lib')) {
        Push-Location $src
        try { & "$toolsDir\aapt.exe" add $unsigned 'lib/arm64-v8a/libgolibrespot.so' } finally { Pop-Location }
    }
    $aligned=Join-Path $build 'aligned.apk'
    & "$toolsDir\zipalign.exe" -f -p 4 $unsigned $aligned
    if ($LASTEXITCODE -ne 0) { throw "APK alignment failed: $target" }
    $name=if($target -eq 'android'){'Cinema-Frame.apk'}else{'Cinema-Remote.apk'}
    & "$javaDir\java.exe" -jar "$toolsDir\lib\apksigner.jar" sign --ks $keyFile --ks-pass pass:android --key-pass pass:android --out (Join-Path $project $name) $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed' }
    & "$javaDir\java.exe" -jar "$toolsDir\lib\apksigner.jar" verify (Join-Path $project $name)
    if ($LASTEXITCODE -ne 0) { throw 'APK verification failed' }
    Get-Item (Join-Path $project $name) | Select-Object Name,Length
}
