package com.transvidcloud.backend.video.service;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.video.entity.OutboxItem;
import com.transvidcloud.backend.video.repository.OutboxRepository;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * Dịch vụ phát hành các thông điệp Outbox lên hàng đợi Amazon SQS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherService {

    private final SqsAsyncClient sqsAsyncClient;
    private final OutboxRepository outboxRepository;
    private final TvcProperties tvcProperties;

    private final ConcurrentHashMap<String, String> queueUrlCache = new ConcurrentHashMap<>();

    /**
     * Phát hành một bản ghi OutboxItem lên hàng đợi SQS tvc-{env}-jobs:
     * - Nếu thành công: Cập nhật trạng thái OutboxItem sang OUTBOX#PUBLISHED.
     * - Nếu thất bại: Ghi nhận lỗi và tăng attemptCount trong OutboxItem.
     *
     * @param item Bản ghi OutboxItem cần phát hành
     * @return true nếu gửi SQS thành công; false nếu gặp lỗi ngoại lệ
     */
    public boolean publishOutboxItem(OutboxItem item) {
        if (item == null || item.getPayload() == null) {
            log.warn("Bỏ qua phát hành Outbox rỗng");
            return false;
        }

        String queueName = tvcProperties.getAws().getSqs().getJobsQueueName();
        try {
            String queueUrl = resolveQueueUrl(queueName);
            SendMessageRequest sendRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(item.getPayload())
                    .build();

            SendMessageResponse response = sqsAsyncClient.sendMessage(sendRequest).join();
            String now = Instant.now().toString();
            outboxRepository.markAsPublished(item.getTargetVideoId(), now);

            log.info("Phát hành SQS Job thành công: videoId={}, jobId={}, messageId={}",
                    item.getTargetVideoId(), item.getJobId(), response.messageId());
            return true;
        } catch (Exception ex) {
            log.error("Lỗi khi phát hành SQS message cho video {}: {}", item.getTargetVideoId(), ex.getMessage());
            outboxRepository.recordFailedAttempt(item.getTargetVideoId(), ex.getMessage());
            return false;
        }
    }

    /**
     * Tra cứu và cache Queue URL từ Queue Name (hoặc dùng trực tiếp nếu đã là URL) để tối ưu hóa hiệu năng.
     */
    private String resolveQueueUrl(String queueName) {
        if (queueName.startsWith("http://") || queueName.startsWith("https://")) {
            return queueName;
        }
        return queueUrlCache.computeIfAbsent(queueName, qName -> {
            GetQueueUrlRequest request = GetQueueUrlRequest.builder().queueName(qName).build();
            return sqsAsyncClient.getQueueUrl(request).join().queueUrl();
        });
    }
}
