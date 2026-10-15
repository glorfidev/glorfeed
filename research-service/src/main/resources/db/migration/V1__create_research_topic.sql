CREATE TABLE research_topic (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    source_event_id UUID NOT NULL UNIQUE
);
