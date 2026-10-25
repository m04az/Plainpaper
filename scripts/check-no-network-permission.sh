#!/usr/bin/env bash
# Nothing leaves the device (constitution I): the merged release manifest must not declare a
# network permission. Libraries such as Play services' scanner pull in INTERNET and
# ACCESS_NETWORK_STATE; androidApp's manifest removes them with tools:node="remove".
set -euo pipefail

cd "$(dirname "$0")/.."

./gradlew :androidApp:processReleaseMainManifest --quiet

manifest=$(find androidApp/build/intermediates -type f -name AndroidManifest.xml -path '*release*' \
  -path '*merged_manifest*' | head -n 1)
if [[ -z "$manifest" ]]; then
  echo "check-no-network-permission: merged release manifest not found under androidApp/build" >&2
  exit 2
fi

forbidden='android.permission.(INTERNET|ACCESS_NETWORK_STATE)'
if grep -nE "$forbidden" "$manifest"; then
  echo "check-no-network-permission: $manifest declares a network permission (see above)" >&2
  exit 1
fi
echo "check-no-network-permission: OK ($manifest)"
