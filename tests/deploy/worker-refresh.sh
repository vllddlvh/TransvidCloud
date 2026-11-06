#!/usr/bin/env bash
# Chỉ gỡ protection sau khi SSM drain thành công; fail-closed nếu drain lỗi.
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
  "autoscaling describe-auto-scaling-groups")
    if [[ "$*" == *'length(@)'* ]]; then echo 1; else echo i-worker; fi ;;
  "ssm put-parameter") echo '{"Version":2}' ;;
  "ssm send-command") echo command-test ;;
  "ssm get-command-invocation")
    if [ "$TVC_DEPLOY_TEST_MODE" = success ]; then echo Success; else echo Failed; fi ;;
  "autoscaling set-instance-protection") ;;
  "autoscaling start-instance-refresh") echo refresh-test ;;
  "autoscaling describe-instance-refreshes") echo Successful ;;
  *) echo "Unexpected AWS call: $1 $2" >&2; exit 1 ;;
esac
MOCK
chmod +x "$TEST_DIR/aws"
export PATH="$TEST_DIR:$PATH" ENV=dev TAG=test-release TVC_DEPLOY_TEST_MODE=success
bash "$ROOT/scripts/bump-worker-tag.sh"
DRAIN_LINE=$(grep -n '^ssm get-command-invocation ' "$TVC_DEPLOY_TEST_LOG" | cut -d: -f1)
PROTECT_LINE=$(grep -n '^autoscaling set-instance-protection ' "$TVC_DEPLOY_TEST_LOG" | cut -d: -f1)
REFRESH_LINE=$(grep -n '^autoscaling start-instance-refresh ' "$TVC_DEPLOY_TEST_LOG" | cut -d: -f1)
test "$DRAIN_LINE" -lt "$PROTECT_LINE"
test "$PROTECT_LINE" -lt "$REFRESH_LINE"
: >"$TVC_DEPLOY_TEST_LOG"
export TVC_DEPLOY_TEST_MODE=failure
if bash "$ROOT/scripts/bump-worker-tag.sh"; then
  echo 'FAIL: refresh accepted failed drain' >&2
  exit 1
fi
if grep -Eq '^autoscaling (set-instance-protection|start-instance-refresh)' "$TVC_DEPLOY_TEST_LOG"; then
  echo 'FAIL: failed drain changed protection or refreshed instances' >&2
  exit 1
fi
echo 'PASS: active worker refresh drains before unprotecting; failed drain preserves instances'
