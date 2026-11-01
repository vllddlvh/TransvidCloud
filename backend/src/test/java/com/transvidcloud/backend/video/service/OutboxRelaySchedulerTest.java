package com.transvidcloud.backend.video.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transvidcloud.backend.common.config.TvcProperties;
import com.transvidcloud.backend.video.entity.OutboxItem;
import com.transvidcloud.backend.video.repository.OutboxRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private OutboxPublisherService outboxPublisherService;

    private TvcProperties tvcProperties;
    private OutboxRelayScheduler relayScheduler;

    @BeforeEach
    void setUp() {
        tvcProperties = new TvcProperties();
        tvcProperties.getOutbox().setEnabled(true);
        tvcProperties.getOutbox().setBatchSize(10);
        relayScheduler = new OutboxRelayScheduler(outboxRepository, outboxPublisherService, tvcProperties);
    }

    @Test
    @DisplayName("relayPendingOutboxEvents: Quét danh sách pending và phát hành từng bản ghi thành công")
    void testRelayPendingOutboxEventsSuccess() {
        OutboxItem item1 = OutboxItem.builder().targetVideoId("vid_01").payload("{}").build();
        OutboxItem item2 = OutboxItem.builder().targetVideoId("vid_02").payload("{}").build();

        when(outboxRepository.findPendingOutboxEvents(10)).thenReturn(List.of(item1, item2));
        when(outboxPublisherService.publishOutboxItem(any())).thenReturn(true);

        relayScheduler.relayPendingOutboxEvents();

        verify(outboxRepository).findPendingOutboxEvents(10);
        verify(outboxPublisherService, times(2)).publishOutboxItem(any(OutboxItem.class));
    }

    @Test
    @DisplayName("relayPendingOutboxEvents: Không làm gì khi danh sách pending rỗng")
    void testRelayPendingOutboxEventsEmpty() {
        when(outboxRepository.findPendingOutboxEvents(10)).thenReturn(List.of());

        relayScheduler.relayPendingOutboxEvents();

        verify(outboxRepository).findPendingOutboxEvents(10);
        verify(outboxPublisherService, never()).publishOutboxItem(any());
    }

    @Test
    @DisplayName("relayPendingOutboxEvents: Không chạy khi tính năng outbox bị disabled")
    void testRelayPendingOutboxEventsDisabled() {
        tvcProperties.getOutbox().setEnabled(false);

        relayScheduler.relayPendingOutboxEvents();

        verify(outboxRepository, never()).findPendingOutboxEvents(any(Integer.class));
    }
}
