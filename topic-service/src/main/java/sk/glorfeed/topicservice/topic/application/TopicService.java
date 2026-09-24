package sk.glorfeed.topicservice.topic.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import sk.glorfeed.topicservice.topic.api.dto.CreateTopicDto;
import sk.glorfeed.topicservice.topic.api.dto.TopicDto;
import sk.glorfeed.topicservice.topic.api.dto.UpdateTopicDto;
import sk.glorfeed.topicservice.topic.domain.Topic;
import sk.glorfeed.topicservice.topic.persistence.TopicRepository;

@Service
@Transactional
public class TopicService {

    private final TopicRepository topicRepository;

    public TopicService(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
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

        return toDto(topicRepository.save(topic));
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
