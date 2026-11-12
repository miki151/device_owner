#!/usr/bin/env bash
set -euo pipefail

app_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [[ -n "${KOTLINC:-}" ]]; then
    kotlin_compiler=$KOTLINC
elif command -v kotlinc >/dev/null 2>&1; then
    kotlin_compiler=$(command -v kotlinc)
elif [[ -n "${ANDROID_BUILD_TOP:-}" && -x "$ANDROID_BUILD_TOP/external/kotlinc/bin/kotlinc" ]]; then
    kotlin_compiler="$ANDROID_BUILD_TOP/external/kotlinc/bin/kotlinc"
else
    echo 'Set KOTLINC to a Kotlin compiler, or source the Android build environment.' >&2
    exit 1
fi
test_output=$(mktemp -d)
trap 'rm -rf -- "$test_output"' EXIT
"$kotlin_compiler" \
    "$app_dir/src/main/java/eu/dumbdroid/deviceowner/policy/InternetPolicyController.kt" \
    "$app_dir/tests/InternetPolicyControllerTest.kt" \
    -include-runtime -d "$test_output/internet-policy-tests.jar"
java -jar "$test_output/internet-policy-tests.jar"
