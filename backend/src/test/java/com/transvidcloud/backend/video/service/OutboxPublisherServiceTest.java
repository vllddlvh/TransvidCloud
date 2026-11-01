package com.transvidcloud.backend.video.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.video.entity.OutboxItem;
import com.transvidcloud.backend.video.repository.OutboxRepository;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private OutboxRepository outboxRepository;

    private TvcProperties tvcProperties;
    private OutboxPublisherService publisherService;

    @BeforeEach
    void setUp() {
        tvcProperties = new TvcProperties();
        tvcProperties.getAws().getSqs().setJobsQueueName("tvc-test-jobs");
        publisherService = new OutboxPublisherService(sqsAsyncClient, outboxRepository, tvcProperties);
    }

    @Test
    @DisplayName("publishOutboxItem: gửi SQS thành công -> gọi outboxRepository.markAsPublished")
    void testPublishOutboxItemSuccess() {
        String queueUrl = "http://localhost:4566/000000000000/tvc-test-jobs";
        when(sqsAsyncClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl(queueUrl).build()));

        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("msg_123").build()));

        OutboxItem item = OutboxItem.builder()
                .targetVideoId("vid_001")
                .jobId("job_001")
                .payload("{\"schemaVersion\":1}")
                .build();

        boolean result = publisherService.publishOutboxItem(item);

        assertTrue(result);
        verify(outboxRepository).markAsPublished(eq("vid_001"), anyString());
        verify(outboxRepository, never()).recordFailedAttempt(anyString(), anyString());
    }

    @Test
    @DisplayName("publishOutboxItem: gửi SQS thất bại -> gọi outboxRepository.recordFailedAttempt và trả về false")
    void testPublishOutboxItemFailure() {
        String queueUrl = "http://localhost:4566/000000000000/tvc-test-jobs";
        when(sqsAsyncClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl(queueUrl).build()));

        CompletableFuture<SendMessageResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("SQS timeout"));
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class))).thenReturn(failedFuture);

        OutboxItem item = OutboxItem.builder()
                .targetVideoId("vid_002")
                .jobId("job_002")
                .payload("{\"schemaVersion\":1}")
                .build();

        boolean result = publisherService.publishOutboxItem(item);

        assertFalse(result);
        verify(outboxRepository).recordFailedAttempt(eq("vid_002"), anyString());
        verify(outboxRepository, never()).markAsPublished(anyString(), anyString());
    }

    @Test
    @DisplayName("publishOutboxItem: item rỗng hoặc payload null -> trả về false")
    void testPublishOutboxItemNull() {
        assertFalse(publisherService.publishOutboxItem(null));
        assertFalse(publisherService.publishOutboxItem(new OutboxItem()));
    }
}
