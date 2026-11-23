#!/usr/bin/env bash
# CI's checks, run before every push so the run on GitHub passes the first time. The live tests
# use the Mealie server in MEALIE_TEST_URL, MEALIE_TEST_USER and MEALIE_TEST_PASSWORD, like CI's.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${MEALIE_TEST_URL:-}" ]; then
  echo "MEALIE_TEST_URL is not set, so the tests against a live Mealie server are skipped." >&2
fi
./gradlew spotlessCheck :app:lintDebug :app:recordRoborazziDebug :app:assembleDebug "$@"
