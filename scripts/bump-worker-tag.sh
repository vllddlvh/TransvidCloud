#!/usr/bin/env bash
# Dùng: ENV=dev TAG=<commit-sha> ./scripts/bump-worker-tag.sh
set -euo pipefail
: "${ENV:?cần ENV}" "${TAG:?cần TAG}"
REGION=${AWS_REGION:-ap-southeast-1}

aws ssm put-parameter --region "$REGION" \
  --name "/tvc/${ENV}/worker/image-tag" --type String --value "$TAG" --overwrite

aws autoscaling start-instance-refresh --region "$REGION" \
  --auto-scaling-group-name "tvc-${ENV}-worker-asg" \
  --preferences '{"MinHealthyPercentage":50,"ScaleInProtectedInstances":"Wait"}'
# ScaleInProtectedInstances=Wait: không thay máy đang transcode (đang bật scale-in protection), chờ job xong.
