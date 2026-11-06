#!/usr/bin/env bash
# Kiểm tra deploy khi hạ tải: chỉ lưu tag, không bật máy hay refresh.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT
export TVC_DEPLOY_TEST_LOG="$TEST_DIR/calls"
cat >"$TEST_DIR/aws" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$TVC_DEPLOY_TEST_LOG"
case "$1 $2" in
  "ec2 describe-instances") printf 'i-test\tstopped\n' ;;
  "ssm get-parameter") printf 'previous-tag\n' ;;
  "ssm put-parameter") printf '{"Version":2}\n' ;;
  "autoscaling describe-auto-scaling-groups") printf '0\n' ;;
  *) echo "Unexpected AWS call: $1 $2" >&2; exit 1 ;;
esac
MOCK
chmod +x "$TEST_DIR/aws"
export PATH="$TEST_DIR:$PATH"
export ENV=dev TAG=test-release
bash "$ROOT/scripts/deploy-api.sh"
bash "$ROOT/scripts/bump-worker-tag.sh"
test "$(grep -c '^ssm put-parameter ' "$TVC_DEPLOY_TEST_LOG")" -eq 2
if grep -Eq 'start-instances|stop-instances|send-command|start-instance-refresh|update-auto-scaling-group' "$TVC_DEPLOY_TEST_LOG"; then
  echo 'FAIL: dormant deployment changed compute state' >&2
  exit 1
fi
echo 'PASS: dormant API/worker deployment stages both tags without starting compute'
