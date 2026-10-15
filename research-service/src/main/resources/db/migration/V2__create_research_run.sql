CREATE TABLE research_run (
    id UUID PRIMARY KEY,
    topic_id UUID NOT NULL,
    response_id VARCHAR(255) NOT NULL UNIQUE,
    model VARCHAR(255) NOT NULL,
    report_json TEXT NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE INDEX idx_research_run_topic_created_at ON research_run (topic_id, created_at DESC);
