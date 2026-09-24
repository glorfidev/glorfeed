package sk.glorfeed.topicservice.topic.api.dto;

import java.time.Instant;
import java.util.UUID;

public record TopicDto(
    UUID id,
    String name,
    String description,
    Instant createdAt
) {
}
