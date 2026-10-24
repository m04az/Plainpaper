#!/usr/bin/env bash
# Fails when a user-visible string literal appears in shared UI code (constitution VIII, FR-008).
# Every user-visible string must come from composeResources via Res.string.*.
set -euo pipefail

cd "$(dirname "$0")/.."

pattern='(Text\(\s*"|\btext = "|contentDescription = "|\btitle = "|\bbody = "|\blabel = "|placeholder = ")'

matches=$(grep -rnE --include='*.kt' "$pattern" --exclude='*Preview*.kt' \
  composeApp/src/commonMain core/designsystem/src/commonMain core/capture/src/commonMain feature/*/src/commonMain 2>/dev/null \
  | grep -vE '= ""' || true)

if [[ -n "$matches" ]]; then
  echo "Hard-coded user-visible strings found. Move them to composeResources/values*/strings.xml:"
  echo "$matches"
  exit 1
fi

echo "No hard-coded user-visible strings in commonMain."
