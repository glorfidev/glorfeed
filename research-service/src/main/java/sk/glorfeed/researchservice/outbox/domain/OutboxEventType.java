package sk.glorfeed.researchservice.outbox.domain;

public enum OutboxEventType {
    RESEARCH_COMPLETED("ResearchCompleted");

    private final String value;

    OutboxEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
