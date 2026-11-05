package state

import (
	"context"
	"errors"
	"strings"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb"
	"github.com/aws/aws-sdk-go-v2/service/dynamodb/types"
)

type fakeDDB struct {
	getIn     *dynamodb.GetItemInput
	getOut    *dynamodb.GetItemOutput
	getErr    error
	updateIn  *dynamodb.UpdateItemInput
	updateErr error
}

func (f *fakeDDB) GetItem(_ context.Context, in *dynamodb.GetItemInput, _ ...func(*dynamodb.Options)) (*dynamodb.GetItemOutput, error) {
	f.getIn = in
	if f.getErr != nil {
		return nil, f.getErr
	}
	if f.getOut == nil {
		return &dynamodb.GetItemOutput{}, nil
	}
	return f.getOut, nil
}

func (f *fakeDDB) UpdateItem(_ context.Context, in *dynamodb.UpdateItemInput, _ ...func(*dynamodb.Options)) (*dynamodb.UpdateItemOutput, error) {
	f.updateIn = in
	if f.updateErr != nil {
		return nil, f.updateErr
	}
	return &dynamodb.UpdateItemOutput{}, nil
}

func TestGetStatus(t *testing.T) {
	tests := []struct {
		name    string
		out     *dynamodb.GetItemOutput
		want    string
		wantErr bool
	}{
		{"không có item", &dynamodb.GetItemOutput{}, "", false},
		{"item thiếu status", &dynamodb.GetItemOutput{Item: map[string]types.AttributeValue{
			"videoId": &types.AttributeValueMemberS{Value: "v"},
		}}, "", false},
		{"status chuỗi", &dynamodb.GetItemOutput{Item: map[string]types.AttributeValue{
			"status": &types.AttributeValueMemberS{Value: StatusCompleted},
		}}, StatusCompleted, false},
		{"status sai kiểu", &dynamodb.GetItemOutput{Item: map[string]types.AttributeValue{
			"status": &types.AttributeValueMemberN{Value: "1"},
		}}, "", true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := New(&fakeDDB{getOut: tt.out}, "tbl").GetStatus(context.Background(), "vid")
			if (err != nil) != tt.wantErr {
				t.Fatalf("err = %v, wantErr = %v", err, tt.wantErr)
			}
			if got != tt.want {
				t.Errorf("status = %q, want %q", got, tt.want)
			}
		})
	}

	wantErr := errors.New("ddb down")
	if _, err := New(&fakeDDB{getErr: wantErr}, "tbl").GetStatus(context.Background(), "vid"); !errors.Is(err, wantErr) {
		t.Errorf("err = %v, want wrap %v", err, wantErr)
	}
}

func TestMarkProcessing(t *testing.T) {
	f := &fakeDDB{}
	if err := New(f, "tbl").MarkProcessing(context.Background(), "vid"); err != nil {
		t.Fatalf("MarkProcessing: %v", err)
	}
	expr := aws.ToString(f.updateIn.UpdateExpression)
	for _, want := range []string{
		"SET #s = :processing",
		"attemptCount = if_not_exists(attemptCount, :zero) + :one",
		"REMOVE errorCode, completedAt",
	} {
		if !strings.Contains(expr, want) {
			t.Errorf("UpdateExpression thiếu %q: %s", want, expr)
		}
	}
	cond := aws.ToString(f.updateIn.ConditionExpression)
	if !strings.Contains(cond, "#s <> :completed") {
		t.Errorf("ConditionExpression = %q", cond)
	}
	if v, ok := f.updateIn.ExpressionAttributeValues[":processing"].(*types.AttributeValueMemberS); !ok || v.Value != StatusProcessing {
		t.Errorf(":processing sai: %v", f.updateIn.ExpressionAttributeValues[":processing"])
	}
}

func TestMarkCompletedRemovesStaleErrorCode(t *testing.T) {
	f := &fakeDDB{}
	assets := map[string]string{"hls": "p/v/hls/master.m3u8"}
	if err := New(f, "tbl").MarkCompleted(context.Background(), "vid", assets); err != nil {
		t.Fatalf("MarkCompleted: %v", err)
	}
	expr := aws.ToString(f.updateIn.UpdateExpression)
	if !strings.Contains(expr, "REMOVE errorCode") {
		t.Errorf("UpdateExpression thiếu REMOVE errorCode: %s", expr)
	}
	if !strings.Contains(expr, "assets = :assets") {
		t.Errorf("UpdateExpression thiếu assets: %s", expr)
	}
	if _, ok := f.updateIn.ExpressionAttributeValues[":assets"].(*types.AttributeValueMemberM); !ok {
		t.Errorf(":assets sai kiểu: %T", f.updateIn.ExpressionAttributeValues[":assets"])
	}
}

func TestMarkFailed(t *testing.T) {
	f := &fakeDDB{}
	if err := New(f, "tbl").MarkFailed(context.Background(), "vid", StatusFailedStorage, "S3_GET_FAILED"); err != nil {
		t.Fatalf("MarkFailed: %v", err)
	}
	if v := f.updateIn.ExpressionAttributeValues[":final"].(*types.AttributeValueMemberS).Value; v != StatusFailedStorage {
		t.Errorf(":final = %q", v)
	}
	if v := f.updateIn.ExpressionAttributeValues[":code"].(*types.AttributeValueMemberS).Value; v != "S3_GET_FAILED" {
		t.Errorf(":code = %q", v)
	}
}

func TestConditionalFailureMapping(t *testing.T) {
	condErr := &types.ConditionalCheckFailedException{Message: aws.String("conditional")}
	f := &fakeDDB{updateErr: condErr}
	if err := New(f, "tbl").MarkCompleted(context.Background(), "vid", nil); !errors.Is(err, ErrAlreadyCompleted) {
		t.Errorf("err = %v, want ErrAlreadyCompleted", err)
	}

	other := errors.New("throttled")
	f.updateErr = other
	if err := New(f, "tbl").MarkFailed(context.Background(), "vid", StatusFailedTranscode, "X"); !errors.Is(err, other) {
		t.Errorf("err = %v, want %v", err, other)
	}
}
