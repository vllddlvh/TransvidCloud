#!/bin/bash
# Chạy bên trong container LocalStack (có sẵn lệnh awslocal).
set -euo pipefail
REGION=ap-southeast-1
P=tvc-local

echo "[init] S3 buckets"
for b in frontend input output; do
  awslocal s3 mb "s3://${P}-${b}"
done
awslocal s3api put-bucket-cors --bucket "${P}-input" \
  --cors-configuration file:///etc/localstack/init/ready.d/cors.json

echo "[init] SQS + DLQ"
awslocal sqs create-queue --queue-name "${P}-jobs-dlq" >/dev/null
cat > /tmp/queue-attrs.json <<JSON
{"VisibilityTimeout":"300","ReceiveMessageWaitTimeSeconds":"20","RedrivePolicy":"{\"deadLetterTargetArn\":\"arn:aws:sqs:${REGION}:000000000000:${P}-jobs-dlq\",\"maxReceiveCount\":\"3\"}"}
JSON
awslocal sqs create-queue --queue-name "${P}-jobs" --attributes file:///tmp/queue-attrs.json >/dev/null

echo "[init] DynamoDB tables (theo docs/contracts/dynamodb-tables.md)"
awslocal dynamodb create-table --table-name "${P}-users" \
  --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=email,AttributeType=S \
  --key-schema AttributeName=userId,KeyType=HASH \
  --global-secondary-indexes 'IndexName=email-index,KeySchema=[{AttributeName=email,KeyType=HASH}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null

awslocal dynamodb create-table --table-name "${P}-projects" \
  --attribute-definitions AttributeName=projectId,AttributeType=S AttributeName=ownerId,AttributeType=S AttributeName=createdAt,AttributeType=S \
  --key-schema AttributeName=projectId,KeyType=HASH \
  --global-secondary-indexes 'IndexName=owner-index,KeySchema=[{AttributeName=ownerId,KeyType=HASH},{AttributeName=createdAt,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null

awslocal dynamodb create-table --table-name "${P}-apikeys" \
  --attribute-definitions AttributeName=keyHash,AttributeType=S AttributeName=projectId,AttributeType=S \
  --key-schema AttributeName=keyHash,KeyType=HASH \
  --global-secondary-indexes 'IndexName=project-index,KeySchema=[{AttributeName=projectId,KeyType=HASH}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null

awslocal dynamodb create-table --table-name "${P}-videos" \
  --attribute-definitions AttributeName=videoId,AttributeType=S AttributeName=projectId,AttributeType=S AttributeName=createdAt,AttributeType=S \
  --key-schema AttributeName=videoId,KeyType=HASH \
  --global-secondary-indexes 'IndexName=project-index,KeySchema=[{AttributeName=projectId,KeyType=HASH},{AttributeName=createdAt,KeyType=RANGE}],Projection={ProjectionType=ALL}' \
  --billing-mode PAY_PER_REQUEST >/dev/null

echo "[init] Hoàn tất"
