package sk.glorfeed.topicservice.topic.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final TopicRepository topicRepository;
    private final OutboxEventRepository outboxEventRepository;

    public TopicService(TopicRepository topicRepository, OutboxEventRepository outboxEventRepository) {
        this.topicRepository = topicRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional(readOnly = true)
    public List<TopicDto> getTopics() {
        return topicRepository.findAllByOrderByCreatedAtDesc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public TopicDto getTopic(UUID topicId) {
        return toDto(findTopic(topicId));
    }

    public TopicDto createTopic(CreateTopicDto createTopicDto) {
        Topic topic = Topic.builder().name(createTopicDto.name()).description(createTopicDto.description()).build();
        Topic savedTopic = topicRepository.save(topic);

        outboxEventRepository.save(OutboxEvent.builder()
            .aggregateId(savedTopic.getId().toString())
            .aggregateType(OutboxAggregateType.TOPIC.value())
            .eventType(OutboxEventType.TOPIC_CREATED.value())
            .payload(Map.of(
                "topicId", savedTopic.getId().toString(),
                "name", savedTopic.getName(),
                "description", savedTopic.getDescription()
            ))
            .build());

        return toDto(savedTopic);
    }

    public TopicDto updateTopic(UUID topicId, UpdateTopicDto updateTopicDto) {
        Topic topic = findTopic(topicId);

        topic.setName(updateTopicDto.name());
        topic.setDescription(updateTopicDto.description());

        topicRepository.flush();
        return toDto(topic);
    }

    public void deleteTopic(UUID topicId) {
        topicRepository.delete(findTopic(topicId));
    }

    private Topic findTopic(UUID topicId) {
        return topicRepository.findById(topicId)
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
