package com.transvidcloud.backend.video.service;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.common.logging.LogContext;
import com.transvidcloud.backend.video.entity.OutboxItem;
import com.transvidcloud.backend.video.repository.OutboxRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tiến trình nền quét định kỳ các bản ghi Transactional Outbox đang ở trạng thái PENDING
 * và phát bù vào hàng đợi SQS tvc-{env}-jobs để đảm bảo không mất job (At-least-once delivery).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxPublisherService outboxPublisherService;
    private final TvcProperties tvcProperties;

    /**
     * Quét định kỳ theo chu kỳ cấu hình (mặc định 5000ms).
     * Sử dụng GSI project-index Query (không dùng DynamoDB Scan).
     */
    @Scheduled(fixedDelayString = "${tvc.outbox.poll-interval-ms:5000}")
    public void relayPendingOutboxEvents() {
        if (!tvcProperties.getOutbox().isEnabled()) {
            return;
        }

        LogContext.setCorrelationId("sched-outbox-" + UUID.randomUUID());
        int batchSize = tvcProperties.getOutbox().getBatchSize();
        try {
            List<OutboxItem> pendingEvents = outboxRepository.findPendingOutboxEvents(batchSize);
            if (pendingEvents.isEmpty()) {
                return;
            }

            log.info("OutboxRelayScheduler phát hiện {} sự kiện Outbox đang PENDING, bắt đầu phát bù...",
                    pendingEvents.size());

            int publishedCount = 0;
            for (OutboxItem event : pendingEvents) {
                boolean success = outboxPublisherService.publishOutboxItem(event);
                if (success) {
                    publishedCount++;
                }
            }

            log.info("OutboxRelayScheduler hoàn tất đợt phát bù: {}/{} thành công",
                    publishedCount, pendingEvents.size());
        } catch (Exception ex) {
            log.error("Lỗi ngoại lệ trong tiến trình OutboxRelayScheduler: {}", ex.getMessage(), ex);
        } finally {
            LogContext.clear();
        }
    }
}
