#!/usr/bin/env bash
# Fails when any C/C++ source of the scan engine is not clang-format clean (contracts/ci.md).
set -euo pipefail
cd "$(dirname "$0")/.."
files=$(find core/scanengine/native -type f \( -name '*.h' -o -name '*.hpp' -o -name '*.cpp' \))
if [ -z "$files" ]; then
  echo "No native sources found"; exit 0
fi
# shellcheck disable=SC2086
clang-format --dry-run -Werror $files
echo "Native formatting OK ($(echo "$files" | wc -l | tr -d ' ') files)"
