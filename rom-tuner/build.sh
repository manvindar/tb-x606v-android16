#!/bin/sh
# Build RomTuner.apk without Gradle. Run from this directory.
set -e
SDK=$HOME/Library/Android/sdk; BT=$SDK/build-tools/35.0.0; PL=$SDK/platforms/android-34/android.jar
rm -rf gen out; mkdir -p gen out/classes
$BT/aapt2 compile --dir res -o out/res.zip
$BT/aapt2 link -o out/base.apk -I $PL --manifest AndroidManifest.xml --java gen --min-sdk-version 29 --target-sdk-version 34 out/res.zip
javac -source 8 -target 8 -classpath $PL -d out/classes $(find src gen -name "*.java") > out/javac.log 2>&1 || { grep -v "^Note\|warning" out/javac.log; exit 1; }
$BT/d8 --lib $PL --min-api 29 --output out $(find out/classes -name '*.class')
cp out/base.apk out/unsigned.apk; (cd out && zip -q unsigned.apk classes.dex)
$BT/zipalign -f 4 out/unsigned.apk out/aligned.apk
[ -f tuner.keystore ] || keytool -genkeypair -keystore tuner.keystore -storepass tuner123 -alias tuner -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=ROM Tuner"
$BT/apksigner sign --ks tuner.keystore --ks-pass pass:tuner123 --out RomTuner.apk out/aligned.apk
echo built; ls -la RomTuner.apk
