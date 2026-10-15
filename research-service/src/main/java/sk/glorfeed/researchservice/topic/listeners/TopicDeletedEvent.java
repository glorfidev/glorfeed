package sk.glorfeed.researchservice.topic.listeners;

import java.util.UUID;

public record TopicDeletedEvent(UUID topicId) {
}
