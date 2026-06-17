#!/usr/bin/env bash
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/home/malcolm/Android/Sdk}}"
PLATFORM="${ANDROID_PLATFORM:-android-28}"
BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-28.0.3}"

AAPT="$SDK/build-tools/$BUILD_TOOLS/aapt"
DX="$SDK/build-tools/$BUILD_TOOLS/dx"
ZIPALIGN="$SDK/build-tools/$BUILD_TOOLS/zipalign"
ANDROID_JAR="$SDK/platforms/$PLATFORM/android.jar"

OUT="build"
GEN="$OUT/gen"
CLASSES="$OUT/classes"
UNSIGNED="$OUT/bt300-headmouse-unsigned.apk"
SIGNED="$OUT/bt300-headmouse-debug.apk"
KEYSTORE="debug.keystore"

rm -rf "$OUT"
mkdir -p "$GEN" "$CLASSES"

"$AAPT" package -f -m \
  -J "$GEN" \
  -M AndroidManifest.xml \
  -S res \
  -I "$ANDROID_JAR"

javac -source 1.7 -target 1.7 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$CLASSES" \
  $(find src "$GEN" -name '*.java' | sort)

"$DX" --dex --output="$OUT/classes.dex" "$CLASSES"

"$AAPT" package -f \
  -M AndroidManifest.xml \
  -S res \
  -I "$ANDROID_JAR" \
  -F "$UNSIGNED"

cp "$OUT/classes.dex" classes.dex
"$AAPT" add "$UNSIGNED" classes.dex
rm -f classes.dex

if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

jarsigner \
  -keystore "$KEYSTORE" \
  -storepass android \
  -keypass android \
  "$UNSIGNED" \
  androiddebugkey

"$ZIPALIGN" -f 4 "$UNSIGNED" "$SIGNED"
echo "$SIGNED"
