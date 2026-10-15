package sk.glorfeed.researchservice.topic.listeners;

public enum TopicEventType {
    TOPIC_CREATED("TopicCreated"),
    TOPIC_UPDATED("TopicUpdated"),
    TOPIC_DELETED("TopicDeleted");

    private final String value;

    TopicEventType(String value) {
        this.value = value;
    }

    public static TopicEventType fromValue(String value) {
        for (TopicEventType eventType : values()) {
            if (eventType.value.equals(value)) {
                return eventType;
            }
        }
        throw new IllegalArgumentException("Unsupported topic event type: " + value);
    }
}
