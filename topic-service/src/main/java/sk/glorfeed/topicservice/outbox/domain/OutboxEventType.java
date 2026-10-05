package sk.glorfeed.topicservice.outbox.domain;

public enum OutboxEventType {
    TOPIC_CREATED("TopicCreated");

    private final String value;

    OutboxEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
