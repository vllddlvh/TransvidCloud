// Package consumer nhận message SQS theo docs-worker/5. Contact docs.md mục 5.1:
// long polling 20s, MaxNumberOfMessages=1. ChangeMessageVisibility dùng cho
// heartbeat (gia hạn khi job chạy) và trả message sớm khi SIGTERM.
package consumer

import (
	"context"
	"strconv"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
	"github.com/aws/aws-sdk-go-v2/service/sqs/types"
)

type Message struct {
	Body          string
	ReceiptHandle string
	Attempt       int
}

type Receiver interface {
	Receive(ctx context.Context) (*Message, error)
	Delete(ctx context.Context, msg *Message) error
	ChangeVisibility(ctx context.Context, msg *Message, seconds int32) error
}

type sqsAPI interface {
	ReceiveMessage(ctx context.Context, params *sqs.ReceiveMessageInput, optFns ...func(*sqs.Options)) (*sqs.ReceiveMessageOutput, error)
	DeleteMessage(ctx context.Context, params *sqs.DeleteMessageInput, optFns ...func(*sqs.Options)) (*sqs.DeleteMessageOutput, error)
	ChangeMessageVisibility(ctx context.Context, params *sqs.ChangeMessageVisibilityInput, optFns ...func(*sqs.Options)) (*sqs.ChangeMessageVisibilityOutput, error)
}

type SQSReceiver struct {
	client            sqsAPI
	queueURL          string
	visibilitySeconds int32
}

func NewSQSReceiver(client sqsAPI, queueURL string, visibilitySeconds int32) *SQSReceiver {
	return &SQSReceiver{client: client, queueURL: queueURL, visibilitySeconds: visibilitySeconds}
}

// Receive chờ tối đa 20s (long polling); không có message trả (nil, nil).
// VisibilityTimeout đặt tường minh cho lần nhận đầu để không phụ thuộc cấu hình queue.
func (r *SQSReceiver) Receive(ctx context.Context) (*Message, error) {
	in := &sqs.ReceiveMessageInput{
		QueueUrl:            aws.String(r.queueURL),
		MaxNumberOfMessages: 1,
		WaitTimeSeconds:     20,
		MessageSystemAttributeNames: []types.MessageSystemAttributeName{
			types.MessageSystemAttributeNameApproximateReceiveCount,
		},
	}
	if r.visibilitySeconds > 0 {
		in.VisibilityTimeout = r.visibilitySeconds
	}
	out, err := r.client.ReceiveMessage(ctx, in)
	if err != nil {
		return nil, err
	}
	if len(out.Messages) == 0 {
		return nil, nil
	}
	m := out.Messages[0]
	msg := &Message{
		Body:          aws.ToString(m.Body),
		ReceiptHandle: aws.ToString(m.ReceiptHandle),
	}
	if s, ok := m.Attributes["ApproximateReceiveCount"]; ok {
		msg.Attempt, _ = strconv.Atoi(s)
	}
	return msg, nil
}

func (r *SQSReceiver) Delete(ctx context.Context, msg *Message) error {
	_, err := r.client.DeleteMessage(ctx, &sqs.DeleteMessageInput{
		QueueUrl:      aws.String(r.queueURL),
		ReceiptHandle: aws.String(msg.ReceiptHandle),
	})
	return err
}

// ChangeVisibility đặt lại timeout tính từ thời điểm gọi (không cộng thêm vào
// thời điểm hết hạn cũ). Dùng receipt handle của lần nhận hiện tại.
func (r *SQSReceiver) ChangeVisibility(ctx context.Context, msg *Message, seconds int32) error {
	_, err := r.client.ChangeMessageVisibility(ctx, &sqs.ChangeMessageVisibilityInput{
		QueueUrl:          aws.String(r.queueURL),
		ReceiptHandle:     aws.String(msg.ReceiptHandle),
		VisibilityTimeout: seconds,
	})
	return err
}
