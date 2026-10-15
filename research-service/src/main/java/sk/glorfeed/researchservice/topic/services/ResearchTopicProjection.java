package sk.glorfeed.researchservice.topic.services;

import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sk.glorfeed.researchservice.topic.domain.ResearchTopic;
import sk.glorfeed.researchservice.topic.repositories.ResearchTopicRepository;

@Service
public class ResearchTopicProjection {

    private static final Logger log = LoggerFactory.getLogger(ResearchTopicProjection.class);

    private final ResearchTopicRepository repository;

    public ResearchTopicProjection(ResearchTopicRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void recordTopicCreated(UUID eventId, UUID topicId, String name, String description) {
        log.info("Projecting TopicCreated event: eventId={}, topicId={}", eventId, topicId);
        validateDetails(eventId, topicId, name, description);

        if (!repository.existsById(topicId)) {
            repository.save(new ResearchTopic(topicId, name, description, eventId));
        }
    }

    @Transactional
    public void recordTopicUpdated(UUID eventId, UUID topicId, String name, String description) {
        log.info("Projecting TopicUpdated event: eventId={}, topicId={}", eventId, topicId);
        validateDetails(eventId, topicId, name, description);

        ResearchTopic topic = repository.findById(topicId).orElse(null);
        if (topic == null) {
            repository.save(new ResearchTopic(topicId, name, description, eventId));
        } else {
            topic.updateDetails(name, description);
        }
    }

    @Transactional
    public void recordTopicDeleted(UUID topicId) {
        log.info("Projecting TopicDeleted event: topicId={}", topicId);
        Objects.requireNonNull(topicId, "topicId is required");
        repository.findById(topicId).ifPresent(repository::delete);
    }

    private void validateDetails(UUID eventId, UUID topicId, String name, String description) {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(topicId, "topicId is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Topic name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Topic description is required");
        }
    }
}
