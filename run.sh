#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] || { echo "ไม่พบ .env — cp .env.example .env ก่อน" >&2; exit 1; }
set -a; source .env; set +a
exec ./gradlew bootRun
