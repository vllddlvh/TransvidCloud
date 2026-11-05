// Package state thao tác DynamoDB theo docs/contracts/video-state-machine.md và
// docs-worker/5. Contact docs.md mục 5.2. Worker chỉ dùng 3 thao tác.
package state

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/feature/dynamodb/attributevalue"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

const (
	StatusCompleted        = "COMPLETED"
	StatusProcessing       = "PROCESSING"
	StatusFailedValidation = "FAILED_VALIDATION"
	StatusFailedTranscode  = "FAILED_TRANSCODE"
	StatusFailedStorage    = "FAILED_STORAGE"
)

// ErrAlreadyCompleted: ConditionExpression thua vì video đã COMPLETED.
var ErrAlreadyCompleted = errors.New("video đã COMPLETED, không ghi đè")

type ddbAPI interface {
	GetItem(ctx context.Context, params *dynamodb.GetItemInput, optFns ...func(*dynamodb.Options)) (*dynamodb.GetItemOutput, error)
	UpdateItem(ctx context.Context, params *dynamodb.UpdateItemInput, optFns ...func(*dynamodb.Options)) (*dynamodb.UpdateItemOutput, error)
}

type State struct {
	client ddbAPI
	table  string
}

func New(client ddbAPI, table string) *State {
	return &State{client: client, table: table}
}

// GetStatus trả status hiện tại; item chưa tồn tại trả "".
func (s *State) GetStatus(ctx context.Context, videoID string) (string, error) {
	out, err := s.client.GetItem(ctx, &dynamodb.GetItemInput{
		TableName: aws.String(s.table),
		Key: map[string]types.AttributeValue{
			"videoId": &types.AttributeValueMemberS{Value: videoID},
		},
	})
	if err != nil {
		return "", fmt.Errorf("GetItem %s: %w", videoID, err)
	}
	if out.Item == nil {
		return "", nil
	}
	statusAV, ok := out.Item["status"]
	if !ok {
		return "", nil
	}
	status, ok := statusAV.(*types.AttributeValueMemberS)
	if !ok {
		return "", fmt.Errorf("status của %s không phải chuỗi (%T)", videoID, statusAV)
	}
	return status.Value, nil
}

// MarkProcessing nhận job: SET status=PROCESSING, attemptCount+1, startedAt/updatedAt.
func (s *State) MarkProcessing(ctx context.Context, videoID string) error {
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.client.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName: aws.String(s.table),
		Key: map[string]types.AttributeValue{
			"videoId": &types.AttributeValueMemberS{Value: videoID},
		},
		UpdateExpression:    aws.String("SET #s = :processing, attemptCount = if_not_exists(attemptCount, :zero) + :one, startedAt = :now, updatedAt = :now REMOVE errorCode, completedAt"),
		ConditionExpression: aws.String("#s <> :completed OR attribute_not_exists(#s)"),
		ExpressionAttributeNames: map[string]string{
			"#s": "status",
		},
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":processing": &types.AttributeValueMemberS{Value: StatusProcessing},
			":completed":  &types.AttributeValueMemberS{Value: StatusCompleted},
			":zero":       &types.AttributeValueMemberN{Value: "0"},
			":one":        &types.AttributeValueMemberN{Value: "1"},
			":now":        &types.AttributeValueMemberS{Value: now},
		},
	})
	return mapConditional(err)
}

// MarkCompleted ghi status=COMPLETED + assets; không bao giờ ghi đè COMPLETED.
func (s *State) MarkCompleted(ctx context.Context, videoID string, assets map[string]string) error {
	assetsAV, err := attributevalue.Marshal(assets)
	if err != nil {
		return err
	}
	now := time.Now().UTC().Format(time.RFC3339)
	_, err = s.client.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName: aws.String(s.table),
		Key: map[string]types.AttributeValue{
			"videoId": &types.AttributeValueMemberS{Value: videoID},
		},
		UpdateExpression:    aws.String("SET #s = :completed, assets = :assets, completedAt = :now, updatedAt = :now REMOVE errorCode"),
		ConditionExpression: aws.String("#s <> :completed"),
		ExpressionAttributeNames: map[string]string{
			"#s": "status",
		},
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":completed": &types.AttributeValueMemberS{Value: StatusCompleted},
			":assets":    assetsAV,
			":now":       &types.AttributeValueMemberS{Value: now},
		},
	})
	return mapConditional(err)
}

// MarkFailed ghi FAILED_* + errorCode; vẫn cho retry qua SQS (không xóa message).
func (s *State) MarkFailed(ctx context.Context, videoID, status, errorCode string) error {
	now := time.Now().UTC().Format(time.RFC3339)
	_, err := s.client.UpdateItem(ctx, &dynamodb.UpdateItemInput{
		TableName: aws.String(s.table),
		Key: map[string]types.AttributeValue{
			"videoId": &types.AttributeValueMemberS{Value: videoID},
		},
		UpdateExpression:    aws.String("SET #s = :final, errorCode = :code, completedAt = :now, updatedAt = :now"),
		ConditionExpression: aws.String("#s <> :completed"),
		ExpressionAttributeNames: map[string]string{
			"#s": "status",
		},
		ExpressionAttributeValues: map[string]types.AttributeValue{
			":final":     &types.AttributeValueMemberS{Value: status},
			":code":      &types.AttributeValueMemberS{Value: errorCode},
			":completed": &types.AttributeValueMemberS{Value: StatusCompleted},
			":now":       &types.AttributeValueMemberS{Value: now},
		},
	})
	return mapConditional(err)
}

func mapConditional(err error) error {
	if err == nil {
		return nil
	}
	var ccfe *types.ConditionalCheckFailedException
	if errors.As(err, &ccfe) {
		return ErrAlreadyCompleted
	}
	return err
}
