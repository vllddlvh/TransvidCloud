#!/usr/bin/env bash
# Kiểm tra cấu trúc starter và nhắc các cấu hình chưa được tùy chỉnh.
set -uo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
required=(
  "AGENTS.md"
  "PROGRESS.md"
  "backlog/features.md"
  "docs/architecture.md"
  "docs/development-setup.md"
  "docs/engineering-standards.md"
  "docs/testing-strategy.md"
  ".harness/project-config.md"
  ".harness/verify-commands.txt"
  "scripts/verify.sh"
)
missing=0
for path in "${required[@]}"; do
  if [[ ! -e "$ROOT_DIR/$path" ]]; then
    echo "MISSING: $path"
    missing=$((missing + 1))
  else
    echo "OK: $path"
  fi
done

if [[ $missing -gt 0 ]]; then
  echo "Setup check failed: $missing required path(s) missing." >&2
  exit 1
fi

if grep -RIl --exclude='check-setup.sh' --exclude='verify.sh' 'TODO(SETUP)' \
  "$ROOT_DIR/AGENTS.md" "$ROOT_DIR/PROGRESS.md" "$ROOT_DIR/backlog" "$ROOT_DIR/docs" "$ROOT_DIR/.harness" >/dev/null 2>&1; then
  echo
  echo "NOTICE: Một số TODO(SETUP) vẫn còn. Hãy hoàn thiện các thông tin liên quan trước khi dùng cho task thật."
fi

if ! bash -n "$ROOT_DIR/scripts/verify.sh"; then
  echo "ERROR: scripts/verify.sh có lỗi cú pháp Bash." >&2
  exit 1
fi

echo
echo "Structure check passed. Điều này không chứng minh project build/test pass."
