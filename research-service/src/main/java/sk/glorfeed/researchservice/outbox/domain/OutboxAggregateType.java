package sk.glorfeed.researchservice.outbox.domain;

public enum OutboxAggregateType {
    RESEARCH("research");

    private final String value;

    OutboxAggregateType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
