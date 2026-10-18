#!/usr/bin/env bash
# Dùng: ENV=dev TAG=<commit-sha> ECR_REPO=<acct>.dkr.ecr.ap-southeast-1.amazonaws.com/tvc-api ./scripts/deploy-api.sh
set -euo pipefail
: "${ENV:?cần ENV}" "${TAG:?cần TAG}" "${ECR_REPO:?cần ECR_REPO}"
REGION=${AWS_REGION:-ap-southeast-1}

aws ssm send-command \
  --region "$REGION" \
  --document-name "AWS-RunShellScript" \
  --targets "Key=tag:Name,Values=tvc-${ENV}-api" \
  --comment "deploy api ${TAG}" \
  --parameters "commands=[
    \"aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_REPO%%/*}\",
    \"docker pull ${ECR_REPO}:${TAG}\",
    \"docker rm -f tvc-api || true\",
    \"docker run -d --name tvc-api --restart unless-stopped -p 8080:8080 -e SPRING_PROFILES_ACTIVE=${ENV} ${ECR_REPO}:${TAG}\"
  ]"
# TODO (Nguyên):
#   - đọc cấu hình/secret từ /tvc/${ENV}/... trong Parameter Store; thêm health check + rollback
#   - chạy thêm container cloudflared (--network host) với token lấy từ /tvc/${ENV}/cloudflared/token (SecureString)
#   - cho container API nghe ở 127.0.0.1:8080 vì API không mở inbound ra internet
