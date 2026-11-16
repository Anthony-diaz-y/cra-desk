#!/usr/bin/env bash
set -euo pipefail
VERSION="${VERSION:-dev}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/cli/dist"
rm -rf "$OUT"
mkdir -p "$OUT"
cd "$ROOT/cli"
for target in linux/amd64 linux/arm64 darwin/amd64 darwin/arm64 windows/amd64 windows/arm64; do
  os="${target%/*}"
  arch="${target#*/}"
  ext=""
  if [ "$os" = "windows" ]; then ext=".exe"; fi
  CGO_ENABLED=0 GOOS="$os" GOARCH="$arch" go build -trimpath \
    -ldflags "-s -w -X main.version=$VERSION" -o "$OUT/cradesk-$os-$arch$ext" ./cmd/cradesk
done
(cd "$OUT" && sha256sum cradesk-* > SHA256SUMS)
ls -1 "$OUT"
