#!/usr/bin/env bash
# ENV=dev TAG=<image-tag> bash scripts/bump-worker-tag.sh
set -euo pipefail
: "${ENV:?cần ENV}" "${TAG:?cần TAG}"
REGION=${AWS_REGION:-ap-southeast-1}
ASG="tvc-${ENV}-worker-asg"
INSTANCE_COUNT=$(aws autoscaling describe-auto-scaling-groups --region "$REGION" \
  --auto-scaling-group-names "$ASG" --query 'AutoScalingGroups[0].Instances | length(@)' --output text)
aws ssm put-parameter --region "$REGION" \
  --name "/tvc/${ENV}/worker/image-tag" --type String --value "$TAG" --overwrite >/dev/null
if [ "$INSTANCE_COUNT" = 0 ]; then
  MESSAGE="Worker đang ở 0 máy; đã lưu tag $TAG cho lần scale-out tiếp theo."
  echo "$MESSAGE"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then printf '%s\n' "$MESSAGE" >> "$GITHUB_STEP_SUMMARY"; fi
  exit 0
fi
REFRESH=$(aws autoscaling start-instance-refresh --region "$REGION" \
  --auto-scaling-group-name "$ASG" \
  --preferences '{"MinHealthyPercentage":50,"ScaleInProtectedInstances":"Wait","InstanceWarmup":60}' \
  --query InstanceRefreshId --output text)
for attempt in $(seq 1 120); do
  STATUS=$(aws autoscaling describe-instance-refreshes --region "$REGION" \
    --auto-scaling-group-name "$ASG" --instance-refresh-ids "$REFRESH" \
    --query 'InstanceRefreshes[0].Status' --output text)
  case "$STATUS" in
    Successful) echo "Worker refresh thành công: $REFRESH"; exit 0 ;;
    Failed|Cancelled|RollbackFailed|RollbackSuccessful) echo "Worker refresh: $STATUS ($REFRESH)" >&2; exit 1 ;;
  esac
  sleep 10
done
echo "Refresh chưa hoàn tất ($REFRESH). Kiểm tra scale-in protection; không tự ngắt worker đang xử lý." >&2
aws autoscaling cancel-instance-refresh --region "$REGION" --auto-scaling-group-name "$ASG" >/dev/null || true
exit 1
