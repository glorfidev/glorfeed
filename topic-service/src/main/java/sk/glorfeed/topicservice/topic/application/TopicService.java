package sk.glorfeed.topicservice.topic.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import sk.glorfeed.topicservice.outbox.domain.OutboxAggregateType;
import sk.glorfeed.topicservice.outbox.domain.OutboxEvent;
import sk.glorfeed.topicservice.outbox.domain.OutboxEventType;
import sk.glorfeed.topicservice.outbox.persistence.OutboxEventRepository;
import sk.glorfeed.topicservice.topic.api.dto.CreateTopicDto;
import sk.glorfeed.topicservice.topic.api.dto.TopicDto;
import sk.glorfeed.topicservice.topic.api.dto.UpdateTopicDto;
import sk.glorfeed.topicservice.topic.domain.Topic;
import sk.glorfeed.topicservice.topic.persistence.TopicRepository;

@Service
@Transactional
public class TopicService {

    private static final Logger log = LoggerFactory.getLogger(TopicService.class);

    private final TopicRepository topicRepository;
    private final OutboxEventRepository outboxEventRepository;

    public TopicService(TopicRepository topicRepository, OutboxEventRepository outboxEventRepository) {
        this.topicRepository = topicRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public List<TopicDto> getTopics(String ownerId) {
        log.info("Listing topics for authenticated owner scope");
        return topicRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public TopicDto getTopic(UUID topicId, String ownerId) {
        log.info("Loading topic: topicId={}", topicId);
        return toDto(findTopic(topicId, ownerId));
    }

    public TopicDto createTopic(CreateTopicDto createTopicDto, String ownerId) {
        log.info("Creating topic for authenticated owner scope");
        Topic topic = Topic.builder().name(createTopicDto.name()).description(createTopicDto.description()).ownerId(ownerId).build();
        Topic savedTopic = topicRepository.save(topic);
        saveTopicEvent(savedTopic.getId(), OutboxEventType.TOPIC_CREATED, Map.of(
            "topicId", savedTopic.getId().toString(),
            "name", savedTopic.getName(),
            "description", savedTopic.getDescription()
        ));

        return toDto(savedTopic);
    }

    public TopicDto updateTopic(UUID topicId, UpdateTopicDto updateTopicDto, String ownerId) {
        log.info("Updating topic: topicId={}", topicId);
        Topic topic = findTopic(topicId, ownerId);

        topic.setName(updateTopicDto.name());
        topic.setDescription(updateTopicDto.description());

        topicRepository.flush();
        saveTopicEvent(topicId, OutboxEventType.TOPIC_UPDATED, Map.of(
            "topicId", topicId.toString(),
            "name", topic.getName(),
            "description", topic.getDescription()
        ));
        return toDto(topic);
    }

    public void deleteTopic(UUID topicId, String ownerId) {
        log.info("Deleting topic: topicId={}", topicId);
        Topic topic = findTopic(topicId, ownerId);
        topicRepository.delete(topic);
        topicRepository.flush();
        saveTopicEvent(topicId, OutboxEventType.TOPIC_DELETED, Map.of("topicId", topicId.toString()));
    }

    private void saveTopicEvent(UUID topicId, OutboxEventType eventType, Map<String, Object> payload) {
        outboxEventRepository.save(OutboxEvent.builder()
            .aggregateId(topicId.toString())
            .aggregateType(OutboxAggregateType.TOPIC.value())
            .eventType(eventType.value())
            .payload(payload)
            .build());
    }

    private Topic findTopic(UUID topicId, String ownerId) {
        return topicRepository.findByIdAndOwnerId(topicId, ownerId)
            .orElseThrow(() -> new TopicNotFoundException(topicId));
    }

    private TopicDto toDto(Topic topic) {
        return new TopicDto(
            topic.getId(),
            topic.getName(),
            topic.getDescription(),
            topic.getCreatedAt()
        );
    }
}
