#!/usr/bin/env bash
# ENV=dev TAG=<image-tag> ./scripts/deploy-api.sh
set -euo pipefail
: "${ENV:?cần ENV}" "${TAG:?cần TAG}"
REGION=${AWS_REGION:-ap-southeast-1}
PREFIX="/tvc/${ENV}/api"
INSTANCE_DATA=$(aws ec2 describe-instances --region "$REGION" \
  --filters "Name=tag:Name,Values=tvc-${ENV}-api" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
  --query 'Reservations[].Instances[].[InstanceId,State.Name]' --output text)
if [ "$(printf '%s\n' "$INSTANCE_DATA" | sed '/^$/d' | wc -l)" -ne 1 ]; then
  echo "Cần đúng một API instance của môi trường $ENV" >&2
  exit 1
fi
read -r INSTANCE INSTANCE_STATE <<<"$INSTANCE_DATA"
if [ -z "$INSTANCE" ] || [ "$INSTANCE" = None ]; then
  echo "Không tìm thấy EC2 API" >&2
  exit 1
fi
PREVIOUS=$(aws ssm get-parameter --region "$REGION" --name "$PREFIX/image-tag" --query Parameter.Value --output text)
if [ "$INSTANCE_STATE" = stopped ] || [ "$INSTANCE_STATE" = stopping ]; then
  aws ssm put-parameter --region "$REGION" --name "$PREFIX/image-tag" --type String --value "$TAG" --overwrite >/dev/null
  MESSAGE="API đang hạ tải; đã lưu tag $TAG cho lần bật máy tiếp theo, chưa kiểm tra health phiên bản này."
  echo "$MESSAGE"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then printf '%s\n' "$MESSAGE" >> "$GITHUB_STEP_SUMMARY"; fi
  exit 0
fi
if [ "$INSTANCE_STATE" != running ]; then
  echo "API đang chuyển trạng thái ($INSTANCE_STATE); chạy lại deploy khi máy ổn định." >&2
  exit 1
fi
restart_api() {
  local command_id
  command_id=$(aws ssm send-command --region "$REGION" --document-name AWS-RunShellScript \
    --instance-ids "$INSTANCE" --parameters '{"commands":["systemctl restart tvc.service"],"executionTimeout":["900"]}' \
    --query Command.CommandId --output text)
  for attempt in $(seq 1 180); do
    local status
    status=$(aws ssm get-command-invocation --region "$REGION" --command-id "$command_id" \
      --instance-id "$INSTANCE" --query Status --output text 2>/dev/null || true)
    case "$status" in
      Success) return 0 ;;
      Failed|Cancelled|TimedOut|Cancelling) echo "Deploy command $command_id: $status" >&2; return 1 ;;
    esac
    sleep 5
  done
  echo "Hết thời gian chờ SSM command $command_id" >&2
  return 1
}
aws ssm put-parameter --region "$REGION" --name "$PREFIX/image-tag" --type String --value "$TAG" --overwrite >/dev/null
if restart_api; then
  echo "API đã triển khai và vượt qua health check: $TAG"
else
  echo "Deploy thất bại; khôi phục tag $PREVIOUS" >&2
  aws ssm put-parameter --region "$REGION" --name "$PREFIX/image-tag" --type String --value "$PREVIOUS" --overwrite >/dev/null
  restart_api || echo "Rollback thất bại, cần kiểm tra /tvc/$ENV/api" >&2
  exit 1
fi
