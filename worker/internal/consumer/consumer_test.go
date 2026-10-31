package consumer

import (
	"context"
	"errors"
	"testing"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
	"github.com/aws/aws-sdk-go-v2/service/sqs/types"
)

type fakeSQS struct {
	receiveIn  *sqs.ReceiveMessageInput
	receiveOut *sqs.ReceiveMessageOutput
	receiveErr error

	deleteIn  *sqs.DeleteMessageInput
	deleteErr error

	changeIn  *sqs.ChangeMessageVisibilityInput
	changeErr error
}

func (f *fakeSQS) ReceiveMessage(_ context.Context, in *sqs.ReceiveMessageInput, _ ...func(*sqs.Options)) (*sqs.ReceiveMessageOutput, error) {
	f.receiveIn = in
	return f.receiveOut, f.receiveErr
}

func (f *fakeSQS) DeleteMessage(_ context.Context, in *sqs.DeleteMessageInput, _ ...func(*sqs.Options)) (*sqs.DeleteMessageOutput, error) {
	f.deleteIn = in
	return &sqs.DeleteMessageOutput{}, f.deleteErr
}

func (f *fakeSQS) ChangeMessageVisibility(_ context.Context, in *sqs.ChangeMessageVisibilityInput, _ ...func(*sqs.Options)) (*sqs.ChangeMessageVisibilityOutput, error) {
	f.changeIn = in
	return &sqs.ChangeMessageVisibilityOutput{}, f.changeErr
}

func TestReceiveMapsMessageAndSetsVisibility(t *testing.T) {
	client := &fakeSQS{receiveOut: &sqs.ReceiveMessageOutput{Messages: []types.Message{{
		Body:          aws.String(`{"schemaVersion":1}`),
		ReceiptHandle: aws.String("rh-1"),
		Attributes:    map[string]string{"ApproximateReceiveCount": "2"},
	}}}}
	r := NewSQSReceiver(client, "q", 300)

	msg, err := r.Receive(context.Background())
	if err != nil {
		t.Fatalf("Receive: %v", err)
	}
	if msg.Body != `{"schemaVersion":1}` || msg.ReceiptHandle != "rh-1" || msg.Attempt != 2 {
		t.Errorf("message sai: %+v", msg)
	}
	if client.receiveIn.VisibilityTimeout != 300 {
		t.Errorf("VisibilityTimeout = %d, want 300", client.receiveIn.VisibilityTimeout)
	}
	if client.receiveIn.WaitTimeSeconds != 20 || client.receiveIn.MaxNumberOfMessages != 1 {
		t.Errorf("tham số nhận sai: %+v", client.receiveIn)
	}
	attrs := client.receiveIn.MessageSystemAttributeNames
	if len(attrs) != 1 || attrs[0] != types.MessageSystemAttributeNameApproximateReceiveCount {
		t.Errorf("thiếu ApproximateReceiveCount: %v", attrs)
	}
}

func TestReceiveEmptyAndError(t *testing.T) {
	r := NewSQSReceiver(&fakeSQS{receiveOut: &sqs.ReceiveMessageOutput{}}, "q", 0)
	msg, err := r.Receive(context.Background())
	if msg != nil || err != nil {
		t.Errorf("queue rỗng mong (nil,nil), nhận (%v,%v)", msg, err)
	}

	wantErr := errors.New("sqs down")
	r = NewSQSReceiver(&fakeSQS{receiveErr: wantErr}, "q", 0)
	if _, err := r.Receive(context.Background()); !errors.Is(err, wantErr) {
		t.Errorf("err = %v, want %v", err, wantErr)
	}
}

func TestDeleteAndChangeVisibility(t *testing.T) {
	client := &fakeSQS{}
	r := NewSQSReceiver(client, "q", 0)
	msg := &Message{ReceiptHandle: "rh-9"}

	if err := r.Delete(context.Background(), msg); err != nil {
		t.Fatalf("Delete: %v", err)
	}
	if aws.ToString(client.deleteIn.ReceiptHandle) != "rh-9" || aws.ToString(client.deleteIn.QueueUrl) != "q" {
		t.Errorf("Delete input sai: %+v", client.deleteIn)
	}

	if err := r.ChangeVisibility(context.Background(), msg, 30); err != nil {
		t.Fatalf("ChangeVisibility: %v", err)
	}
	if aws.ToString(client.changeIn.ReceiptHandle) != "rh-9" || client.changeIn.VisibilityTimeout != 30 {
		t.Errorf("ChangeVisibility input sai: %+v", client.changeIn)
	}

	wantErr := errors.New("change failed")
	client.changeErr = wantErr
	if err := r.ChangeVisibility(context.Background(), msg, 300); !errors.Is(err, wantErr) {
		t.Errorf("err = %v, want %v", err, wantErr)
	}
}
