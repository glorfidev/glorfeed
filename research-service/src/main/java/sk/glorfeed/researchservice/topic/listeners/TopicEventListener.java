package sk.glorfeed.researchservice.topic.listeners;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import sk.glorfeed.researchservice.topic.services.ResearchTopicProjection;
import tools.jackson.databind.json.JsonMapper;

@Component
public class TopicEventListener {

    private static final Logger log = LoggerFactory.getLogger(TopicEventListener.class);

    public static final String EVENT_TYPE_HEADER_KEY = "type";
    public static final String EVENT_ID_HEADER_KEY = "id";

    private final JsonMapper jsonMapper;
    private final ResearchTopicProjection projection;

    public TopicEventListener(JsonMapper jsonMapper, ResearchTopicProjection projection) {
        this.jsonMapper = jsonMapper;
        this.projection = projection;
    }

    @KafkaListener(topics = "outbox.event.topic")
    public void onTopicEvent(ConsumerRecord<String, String> topicRecord) {
        log.info("Received topic event from Kafka: topic={}, partition={}, offset={}, key={}",
                topicRecord.topic(), topicRecord.partition(), topicRecord.offset(), topicRecord.key());

        if (topicRecord.value() == null) {
            log.info("Ignoring Kafka tombstone: topic={}, partition={}, offset={}, key={}",
                    topicRecord.topic(), topicRecord.partition(), topicRecord.offset(), topicRecord.key());
            return;
        }

        TopicEventType eventType = TopicEventType.fromValue(requiredHeader(topicRecord, EVENT_TYPE_HEADER_KEY));
        UUID eventId = UUID.fromString(requiredHeader(topicRecord, EVENT_ID_HEADER_KEY));

        if (topicRecord.key() == null) {
            throw new IllegalArgumentException("Missing topic ID Kafka key");
        }
        UUID topicId = UUID.fromString(topicRecord.key());

        switch (eventType) {
            case TOPIC_CREATED -> {
                TopicDetailsEvent event = topicDetails(topicRecord.value(), topicId);
                projection.recordTopicCreated(eventId, topicId, event.name(), event.description());
            }
            case TOPIC_UPDATED -> {
                TopicDetailsEvent event = topicDetails(topicRecord.value(), topicId);
                projection.recordTopicUpdated(eventId, topicId, event.name(), event.description());
            }
            case TOPIC_DELETED -> {
                TopicDeletedEvent event = jsonMapper.readValue(topicRecord.value(), TopicDeletedEvent.class);
                if (event == null || !topicId.equals(event.topicId())) {
                    throw new IllegalArgumentException("Kafka key does not match topicId");
                }
                projection.recordTopicDeleted(topicId);
            }
        }
    }

    private TopicDetailsEvent topicDetails(String payload, UUID topicId) {
        TopicDetailsEvent event = jsonMapper.readValue(payload, TopicDetailsEvent.class);
        if (event == null || !topicId.equals(event.topicId())) {
            throw new IllegalArgumentException("Kafka key does not match topicId");
        }
        return event;
    }

    private String requiredHeader(ConsumerRecord<String, String> topicRecord, String name) {
        Header header = topicRecord.headers().lastHeader(name);
        if (header == null || header.value() == null) {
            throw new IllegalArgumentException("Missing outbox event " + name);
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
