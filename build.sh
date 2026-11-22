#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

VERSION=$(grep 'versionName = ' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)".*/\1/')
APK="builds/training-v${VERSION}.apk"

./gradlew :app:assembleRelease
mkdir -p builds
cp app/build/outputs/apk/release/app-release.apk "$APK"

printf '\n%s  %s\n' "$(du -h "$APK" | cut -f1)" "$APK"
printf 'install with: adb install -r %s\n' "$APK"
