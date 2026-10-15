package sk.glorfeed.topicservice.topic.persistence;

import sk.glorfeed.topicservice.topic.domain.Topic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findAllByOwnerIdOrderByCreatedAtDesc(String ownerId);

    java.util.Optional<Topic> findByIdAndOwnerId(UUID id, String ownerId);
}
