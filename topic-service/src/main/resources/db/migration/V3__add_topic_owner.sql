ALTER TABLE topic ADD COLUMN owner_id VARCHAR(255);

CREATE INDEX idx_topic_owner_created_at ON topic (owner_id, created_at DESC);
