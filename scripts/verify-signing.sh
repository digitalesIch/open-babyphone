#!/usr/bin/env bash
# Verify that a release APK is signed with the expected Open Babyphone key.
#
# Usage:
#   scripts/verify-signing.sh <path-to-apk>
#
# Exit codes:
#   0 = signature valid and fingerprint matches
#   1 = fingerprint mismatch
#   2 = apksigner not found or APK not signed

set -euo pipefail

EXPECTED_SHA256="4485986d9734544bb5cfd0d5f230e1f37ee69ddd91b239696c8dae299e0e5536"

if [ $# -ne 1 ]; then
    echo "Usage: $0 <path-to-apk>" >&2
    exit 1
fi

APK="$1"

# Locate apksigner
APKSIGNER=""
for candidate in \
    "$(command -v apksigner 2>/dev/null || true)" \
    "${ANDROID_HOME:-}/build-tools/34.0.0/apksigner" \
    "${ANDROID_SDK_ROOT:-}/build-tools/34.0.0/apksigner"; do
    if [ -x "$candidate" ]; then
        APKSIGNER="$candidate"
        break
    fi
done

if [ -z "$APKSIGNER" ]; then
    echo "Error: apksigner not found. Install Android SDK build-tools or set ANDROID_HOME." >&2
    exit 2
fi

# Verify signature
if ! "$APKSIGNER" verify --verbose "$APK" >/dev/null 2>&1; then
    echo "Error: APK signature verification failed." >&2
    exit 2
fi

# Extract SHA-256 fingerprint
ACTUAL_SHA256=$("$APKSIGNER" verify --print-certs "$APK" 2>&1 \
    | grep 'SHA-256 digest' \
    | head -1 \
    | sed 's/.*: //')

echo "Expected SHA-256: $EXPECTED_SHA256"
echo "Actual SHA-256:   $ACTUAL_SHA256"

if [ "$ACTUAL_SHA256" = "$EXPECTED_SHA256" ]; then
    echo "OK: fingerprint matches."
    exit 0
else
    echo "MISMATCH: fingerprint does not match expected release key." >&2
    exit 1
fi