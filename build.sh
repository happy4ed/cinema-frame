#!/usr/bin/env bash
# Linux 빌드 (build.ps1 과 같은 순서). 서명 키가 없으면 서명 직전(정렬된 unsigned APK)까지만 만든다.
# 사용: ./build.sh [--sdk /path/to/android-sdk] [--build-tools 34.0.0] [--unsigned]
set -euo pipefail
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/mnt/efs/hikari.hong/android-sdk}}"
BT="35.0.0"
UNSIGNED=0
while [ $# -gt 0 ]; do case "$1" in
  --sdk) SDK="$2"; shift 2;;
  --build-tools) BT="$2"; shift 2;;
  --unsigned) UNSIGNED=1; shift;;
  *) echo "unknown option: $1" >&2; exit 2;;
esac; done
PROJECT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$SDK/build-tools/$BT"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
for f in "$TOOLS/aapt" "$TOOLS/zipalign" "$TOOLS/lib/d8.jar" "$ANDROID_JAR"; do [ -e "$f" ] || { echo "missing: $f" >&2; exit 1; }; done
for t in java javac jar; do command -v "$t" >/dev/null || { echo "JDK tool missing: $t" >&2; exit 1; }; done
[ -f "$PROJECT/android/assets/movies.json" ] || { echo 'Film catalogue is local-only: run python3 tools/refresh_catalog.py before building.' >&2; exit 1; }
BUILD="$PROJECT/build"; mkdir -p "$BUILD"
KEY="$BUILD/cinema-test.p12"
if [ "$UNSIGNED" = 0 ] && [ ! -f "$KEY" ]; then
  echo "signing key not found: $KEY — 기존 설치본과 같은 키가 필요하면 그 파일을 넣고 다시 실행. 서명 없이 확인만 하려면 --unsigned" >&2; exit 1
fi
for target in android phone; do
  SRC="$PROJECT/$target"; OUT="$BUILD/$target"; CLASSES="$OUT/classes"
  rm -rf "$OUT"; mkdir -p "$CLASSES"
  find "$SRC/src" -name '*.java' > "$OUT/sources.txt"
  javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$CLASSES" @"$OUT/sources.txt"
  jar cf "$OUT/classes.jar" -C "$CLASSES" .
  java -cp "$TOOLS/lib/d8.jar" com.android.tools.r8.D8 --lib "$ANDROID_JAR" --min-api 26 --output "$OUT" "$OUT/classes.jar"
  UNS="$OUT/unsigned.apk"
  ARGS=(package -f -M "$SRC/AndroidManifest.xml" -I "$ANDROID_JAR" -F "$UNS")
  [ -d "$SRC/assets" ] && ARGS+=(-A "$SRC/assets")
  "$TOOLS/aapt" "${ARGS[@]}"
  (cd "$OUT" && "$TOOLS/aapt" add "$UNS" classes.dex >/dev/null)
  if [ -d "$SRC/lib" ]; then (cd "$SRC" && "$TOOLS/aapt" add "$UNS" lib/arm64-v8a/libgolibrespot.so >/dev/null); fi
  ALIGNED="$OUT/aligned.apk"
  "$TOOLS/zipalign" -f -p 4 "$UNS" "$ALIGNED"
  "$TOOLS/zipalign" -c -p 4 "$ALIGNED"
  NAME=$([ "$target" = android ] && echo Cinema-Frame.apk || echo Cinema-Remote.apk)
  if [ "$UNSIGNED" = 1 ]; then
    cp "$ALIGNED" "$BUILD/${NAME%.apk}-unsigned.apk"; echo "built (unsigned): $BUILD/${NAME%.apk}-unsigned.apk $(stat -c %s "$ALIGNED") bytes"
  else
    java -jar "$TOOLS/lib/apksigner.jar" sign --ks "$KEY" --ks-pass pass:android --key-pass pass:android --out "$PROJECT/$NAME" "$ALIGNED"
    java -jar "$TOOLS/lib/apksigner.jar" verify "$PROJECT/$NAME"; echo "built: $PROJECT/$NAME"
  fi
done
