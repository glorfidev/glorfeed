package sk.glorfeed.topicservice.outbox.domain;

public enum OutboxAggregateType {
    TOPIC("topic");

    private final String value;

    OutboxAggregateType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
