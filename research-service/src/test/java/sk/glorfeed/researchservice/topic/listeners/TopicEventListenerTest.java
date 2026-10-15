package sk.glorfeed.researchservice.topic.listeners;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import tools.jackson.databind.json.JsonMapper;

import sk.glorfeed.researchservice.topic.services.ResearchTopicProjection;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopicEventListenerTest {

    @Mock
    private ResearchTopicProjection projection;

    private TopicEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new TopicEventListener(JsonMapper.builder().build(), projection);
    }

    @Test
    void projectsCreateUpdateAndDeleteMessages() {
        UUID topicId = UUID.randomUUID();
        UUID createdEventId = UUID.randomUUID();
        listener.onTopicEvent(record(topicId, "TopicCreated", createdEventId,
                "{\"topicId\":\"" + topicId + "\",\"name\":\"Climate\",\"description\":\"Policy\"}"));
        listener.onTopicEvent(record(topicId, "TopicUpdated", UUID.randomUUID(),
                "{\"topicId\":\"" + topicId + "\",\"name\":\"Climate policy\",\"description\":\"Updated\"}"));
        listener.onTopicEvent(record(topicId, "TopicDeleted", UUID.randomUUID(),
                "{\"topicId\":\"" + topicId + "\"}"));

        verify(projection).recordTopicCreated(createdEventId, topicId, "Climate", "Policy");
        verify(projection).recordTopicUpdated(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.eq(topicId), org.mockito.ArgumentMatchers.eq("Climate policy"), org.mockito.ArgumentMatchers.eq("Updated"));
        verify(projection).recordTopicDeleted(topicId);
    }

    @Test
    void ignoresNullValueTombstoneBeforeReadingHeaders() {
        UUID topicId = UUID.randomUUID();

        listener.onTopicEvent(new ConsumerRecord<>("outbox.event.topic", 0, 10L, topicId.toString(), null));

        verify(projection, never()).recordTopicDeleted(topicId);
        verify(projection, never()).recordTopicCreated(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMissingHeadersMalformedKeysAndMismatchedPayloadIds() {
        UUID topicId = UUID.randomUUID();
        ConsumerRecord<String, String> missingHeader = new ConsumerRecord<>("outbox.event.topic", 0, 11L,
                topicId.toString(), "{}");
        assertThatThrownBy(() -> listener.onTopicEvent(missingHeader)).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> listener.onTopicEvent(record("not-a-uuid", "TopicCreated", UUID.randomUUID(), "{}")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> listener.onTopicEvent(record(topicId, "TopicCreated", UUID.randomUUID(),
                "{\"topicId\":\"" + UUID.randomUUID() + "\",\"name\":\"Climate\",\"description\":\"Policy\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(projection, never()).recordTopicCreated(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void propagatesProjectionFailuresSoTheKafkaRecordIsRetried() {
        UUID topicId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        doThrow(new IllegalStateException("database unavailable"))
                .when(projection).recordTopicCreated(eventId, topicId, "Climate", "Policy");

        assertThatThrownBy(() -> listener.onTopicEvent(record(topicId, "TopicCreated", eventId,
                "{\"topicId\":\"" + topicId + "\",\"name\":\"Climate\",\"description\":\"Policy\"}")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedPayloadAndMissingKafkaKey() {
        UUID topicId = UUID.randomUUID();
        assertThatThrownBy(() -> listener.onTopicEvent(record(topicId, "TopicCreated", UUID.randomUUID(), "{")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> listener.onTopicEvent(record((String) null, "TopicDeleted", UUID.randomUUID(), "{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ConsumerRecord<String, String> record(UUID topicId, String type, UUID eventId, String payload) {
        return record(topicId == null ? null : topicId.toString(), type, eventId, payload);
    }

    private ConsumerRecord<String, String> record(String key, String type, UUID eventId, String payload) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("outbox.event.topic", 0, 12L, key, payload);
        record.headers().add(new RecordHeader(TopicEventListener.EVENT_TYPE_HEADER_KEY, type.getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(TopicEventListener.EVENT_ID_HEADER_KEY, eventId.toString().getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}
