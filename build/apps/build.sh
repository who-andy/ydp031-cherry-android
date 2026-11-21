#!/bin/sh
set -eu
app=${1:?选择 dictionary、switch 或 modes}
case "$app" in dictionary|switch|modes) ;; *) exit 2;; esac
dest=${2:?指定新 APK 的绝对输出路径}
case "$dest" in /*) ;; *) echo '输出路径需要以 / 开头'; exit 1;; esac
test ! -e "$dest"
key=${CHERRY_KEYSTORE:?请设置自己的签名密钥文件 CHERRY_KEYSTORE}
sdk=${ANDROID_HOME:?请设置 ANDROID_HOME}
tools="$sdk/build-tools/${BUILD_TOOLS_VERSION:-36.0.0}"
api="$sdk/platforms/${ANDROID_PLATFORM:-android-37.0}/android.jar"
src=$(CDPATH= cd -- "$(dirname -- "$0")/$app" && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cd "$tmp"
mkdir classes dex
find "$src/src" -name '*.java' > sources
javac --release 8 -cp "$api" -d classes @sources
find classes -name '*.class' > classlist
"$tools/d8" --min-api 27 --lib "$api" --output dex @classlist
"$tools/aapt" package -f -M "$src/AndroidManifest.xml" -I "$api" -F unsigned.apk
(cd dex && zip -q ../unsigned.apk classes.dex)
"$tools/zipalign" -f 4 unsigned.apk aligned.apk
"$tools/apksigner" sign --ks "$key" --out signed.apk aligned.apk
"$tools/apksigner" verify signed.apk
cp signed.apk "$dest"
