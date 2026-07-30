#!/usr/bin/env bash
# Build sing-box libbox.aar for Android and copy to app/libs/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT/app/libs"
WORK="${TMPDIR:-/tmp}/ifix-sing-box-build"
TAG="${1:-v1.13.14}"

mkdir -p "$OUT_DIR" "$WORK"
cd "$WORK"

if [[ ! -d sing-box ]]; then
  git clone --depth 1 --branch "$TAG" https://github.com/SagerNet/sing-box.git
else
  cd sing-box && git fetch --depth 1 origin "refs/tags/$TAG:refs/tags/$TAG" && git checkout "$TAG" && cd ..
fi

cd sing-box

echo "==> Building libbox for Android (tag $TAG)"
go run ./cmd/internal/build_libbox -target android || {
  echo "Primary build failed; trying with -with-open"
  go run ./cmd/internal/build_libbox -target android -with-open || true
}

# Locate produced aar
AAR=$(find . -name 'libbox*.aar' 2>/dev/null | head -n 1 || true)
if [[ -z "${AAR}" ]]; then
  echo "ERROR: libbox.aar not found after build. Check Go/NDK setup."
  echo "See: https://sing-box.sagernet.org/installation/build-from-source/"
  exit 1
fi

cp -f "$AAR" "$OUT_DIR/libbox.aar"
echo "OK: $OUT_DIR/libbox.aar ($(wc -c < "$OUT_DIR/libbox.aar") bytes)"
