#!/usr/bin/env bash
set -euo pipefail

# Idempotent build & unit-test script for Brother PT-D450VP Android app.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [ -f /etc/profile.d/android-sdk.sh ]; then
  # shellcheck disable=SC1091
  source /etc/profile.d/android-sdk.sh
fi

if [ ! -f local.properties ] && [ -n "${ANDROID_HOME:-}" ]; then
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
fi

./gradlew testDebugUnitTest assembleDebug

APK_PATH="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
  echo "ERROR: Expected APK not found at $APK_PATH" >&2
  exit 1
fi

ls -lh "$APK_PATH"
