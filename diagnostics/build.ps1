param([string]$SdkPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk'), [Parameter(Mandatory=$true)][string]$JavaBin)
$ErrorActionPreference='Stop'

$bt=Join-Path $SdkPath 'build-tools/35.0.0'
$platform=Join-Path $SdkPath 'platforms/android-34/android.jar'
$source=$PSScriptRoot
$root=Join-Path $source "build"
New-Item -ItemType Directory -Force $root,"$root/classes","$root/dex" | Out-Null
& "$javaBin\javac.exe" -encoding UTF-8 -source 8 -target 8 -classpath $platform -d "$root\classes" @(Get-ChildItem "$source\src\com\happy\frame\diagnostics" -Filter *.java | ForEach-Object {$_.FullName})
if($LASTEXITCODE){throw 'javac failed'}
& "$javaBin\jar.exe" "-J-Djava.io.tmpdir=$root" cf "$root\classes.jar" -C "$root\classes" .
& "$javaBin\java.exe" -cp "$bt\lib\d8.jar" com.android.tools.r8.D8 --lib $platform --min-api 26 --output "$root\dex" "$root\classes.jar"
if($LASTEXITCODE){throw 'd8 failed'}
& "$bt\aapt2.exe" compile --dir "$source\res" -o "$root\resources.zip"
if($LASTEXITCODE){throw 'resource compile failed'}
& "$bt\aapt2.exe" link -I $platform --manifest "$source\AndroidManifest.xml" -R "$root\resources.zip" -o "$root\unsigned.apk"
if($LASTEXITCODE){throw 'aapt2 failed'}
& "$javaBin\jar.exe" "-J-Djava.io.tmpdir=$root" uf "$root\unsigned.apk" -C "$root\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$root\unsigned.apk" "$root\aligned.apk"
if(!(Test-Path "$root\debug.keystore")){& "$javaBin\keytool.exe" -genkeypair -keystore "$root\debug.keystore" -storepass android -keypass android -alias frame-debug -dname 'CN=Frame Local Diagnostics' -keyalg RSA -validity 10000}
& "$javaBin\java.exe" -jar "$bt\lib\apksigner.jar" sign --ks "$root\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$source/Frame-Diagnostics.apk" "$root\aligned.apk"
& "$javaBin\java.exe" -jar "$bt\lib\apksigner.jar" verify "$source/Frame-Diagnostics.apk"
if($LASTEXITCODE){throw 'signature verification failed'}
