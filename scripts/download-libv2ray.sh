#!/usr/bin/env bash
# Downloads official libv2ray.aar into app/libs for local builds.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/app/libs"
URL="${LIBV2RAY_URL:-https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.7.28/libv2ray.aar}"
OUT="$ROOT/app/libs/libv2ray.aar"
echo "→ $URL"
curl -fL --retry 3 -o "$OUT" "$URL"
ls -lh "$OUT"
echo "Done. Run: gradle :app:assembleDebug"
