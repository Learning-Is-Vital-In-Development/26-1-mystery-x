#!/bin/bash
# 크기별 랜덤 바이너리 테스트 파일 생성
# Usage: ./perf/generate-fixtures.sh

set -e

FIXTURES_DIR="$(dirname "$0")/fixtures"
mkdir -p "$FIXTURES_DIR"

echo "Generating random binary fixtures in $FIXTURES_DIR ..."

# 1KB × 10, 100KB × 10, 1MB × 10
for i in $(seq 1 10); do
  dd if=/dev/urandom of="$FIXTURES_DIR/1KB_${i}.bin" bs=1024 count=1 2>/dev/null
  dd if=/dev/urandom of="$FIXTURES_DIR/100KB_${i}.bin" bs=1024 count=100 2>/dev/null
  dd if=/dev/urandom of="$FIXTURES_DIR/1MB_${i}.bin" bs=1024 count=1024 2>/dev/null
done

# 5MB × 3, 10MB × 3
for i in $(seq 1 3); do
  dd if=/dev/urandom of="$FIXTURES_DIR/5MB_${i}.bin" bs=1024 count=5120 2>/dev/null
  dd if=/dev/urandom of="$FIXTURES_DIR/10MB_${i}.bin" bs=1024 count=10240 2>/dev/null
done

echo "Done! Generated 36 fixture files."
ls -lh "$FIXTURES_DIR"
