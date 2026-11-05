#!/usr/bin/env bash
# Chạy các verification commands được maintainer cấu hình trong .harness/verify-commands.txt.
# Chỉ sử dụng file command đã được review: mỗi dòng sẽ được thực thi bằng bash -lc.
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMMAND_FILE="$ROOT_DIR/.harness/verify-commands.txt"

if [[ ! -f "$COMMAND_FILE" ]]; then
  echo "ERROR: Không tìm thấy $COMMAND_FILE" >&2
  exit 2
fi

commands=()
while IFS= read -r line || [[ -n "$line" ]]; do
  # Bỏ dòng trống và comment bắt đầu bằng # sau khoảng trắng.
  trimmed="${line#"${line%%[![:space:]]*}"}"
  [[ -z "$trimmed" ]] && continue
  [[ "$trimmed" == \#* ]] && continue
  commands+=("$line")
done < "$COMMAND_FILE"

if [[ ${#commands[@]} -eq 0 ]]; then
  echo "ERROR: Chưa cấu hình verification command." >&2
  echo "Hãy chỉnh .harness/verify-commands.txt bằng các lệnh đã xác minh của project." >&2
  exit 2
fi

printf 'Verification commands: %d\n' "${#commands[@]}"
failures=0
index=0

for command in "${commands[@]}"; do
  index=$((index + 1))
  echo
  echo "[$index/${#commands[@]}] RUN: $command"
  (cd "$ROOT_DIR" && bash -lc "$command")
  status=$?
  if [[ $status -eq 0 ]]; then
    echo "[$index] PASS (exit 0)"
  else
    echo "[$index] FAIL (exit $status)"
    failures=$((failures + 1))
  fi
done

echo
if [[ $failures -eq 0 ]]; then
  echo "All configured commands passed. This only reports command exit status; review coverage and evidence separately."
  exit 0
fi

echo "$failures/${#commands[@]} command(s) failed." >&2
exit 1
