package sk.glorfeed.researchservice.topic.listeners;

import java.util.UUID;

public record TopicDetailsEvent(UUID topicId, String name, String description) {
}
