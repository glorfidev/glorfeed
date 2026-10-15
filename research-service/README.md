# Research service

This service consumes topic events, maintains a local topic projection, and periodically logs the topics it would investigate. A disabled-by-default manual API can research one projected topic using OpenAI web search, save the cited report, and return it. It records a `ResearchCompleted` outbox event for Debezium to publish when the research connector is running.

## Local database

Create a private local environment file, then start PostgreSQL:

```bash
cd research-service/infra/local
cp .env.example .env
docker compose up -d postgres
```

This PostgreSQL container joins the shared `glorfeed-local-network` Docker network. Create that network as described in [`infra/local/README.md`](../infra/local/README.md) before starting it. It uses logical WAL for the research outbox connector.

Run `ResearchServiceApplication` from IntelliJ with these environment variables:

```text
RESEARCH_DB_URL=jdbc:postgresql://localhost:54322/glorfeed_research
RESEARCH_DB_USER=glorfeed
RESEARCH_DB_PASSWORD=<value from infra/local/.env>
SERVER_PORT=8081
SERVER_ADDRESS=127.0.0.1
RESEARCH_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

`SERVER_PORT=8081` keeps this service separate from the topic service while both run locally. Inside its eventual Docker container, the service will still listen on port `8080`.

## Topic events

The listener reads `outbox.event.topic` as part of the `glorfeed-research-service` consumer group. It expects the Debezium outbox event ID in the `id` header and the topic ID as the Kafka key. `TopicCreated` and `TopicUpdated` contain `topicId`, `name`, and `description`; `TopicDeleted` contains only `topicId`. The listener inserts, updates, or removes the `research_topic` row. The database transaction completes before the listener returns and Kafka advances the offset, so normal at-least-once redelivery is safe. This relies on Kafka's per-key ordering; it does not support injecting one old event or arbitrary out-of-order replay. A failing event is retried rather than skipped, so later events on that partition wait for operator intervention. To rebuild the projection, stop the service, clear `research_topic`, reset this consumer group's offsets to the earliest retained events, then restart it. Events older than Kafka retention require a separate source backfill.

For local development, start the Kafka broker and Debezium Connect worker in the repository's `infra/local` setup, register the topic outbox connector, and run topic-service before creating a topic. On its first run, this consumer group starts at the earliest retained event; later runs resume from its committed offset. Topics created before outbox events existed need a separate backfill.

## Research scan

The scheduler reads `research_topic`, turns each projection row into a provider-independent `ResearchSubject`, and logs each candidate. Its first scan starts 10 seconds after startup, then runs one hour after the previous scan finishes. It does not call external providers or publish research results yet. For a faster local check, set `RESEARCH_SCHEDULE_INTERVAL=PT30S`; `RESEARCH_SCHEDULE_INITIAL_DELAY` also accepts an ISO-8601 duration such as `PT5S`.

## Manual AI research

To enable the manual endpoint for local development, add these environment variables to the research-service run configuration:

```text
RESEARCH_MANUAL_API_ENABLED=true
OPENAI_API_KEY=<your OpenAI API key>
```

Then use `POST http://localhost:8081/api/research/topics/{topicId}/run` with an ID from the local `research_topic` table. The service binds to `127.0.0.1` by default, and the controller rejects requests whose socket peer is not loopback; it does not trust forwarded-address headers. Browser requests must have a same-origin `Origin` for a loopback host such as `http://localhost:8081`; foreign origins and the literal `Origin: null` are rejected. Command-line clients without an `Origin` header are allowed only from a loopback socket. Swagger UI also shows this endpoint when enabled. Each POST makes a paid API request, with at most two web search tool calls and a 2,500-token output cap. The default model is `gpt-6-luna`; `RESEARCH_AI_MODEL` overrides it. The response includes report text, citation and source URLs for a future UI to render as clickable links, actual token counts, and the number of web search calls. A successful call saves the same report as JSON in `research_run` and inserts an outbox event in one database transaction; runs remain available even if their topic is later deleted. The endpoint is off unless `RESEARCH_MANUAL_API_ENABLED=true` and remains loopback-only when enabled. The scheduled scan remains log-only.

The `ResearchCompleted` event is routed to Kafka topic `outbox.event.research`, keyed by topic ID. Its payload contains the run ID, topic ID, topic name and description, and the complete `ResearchReport`. The event type is in the Kafka `type` header. Register the local Debezium connector using the command in [`infra/local/README.md`](../infra/local/README.md) after Flyway creates the outbox table. The processing service does not consume this event yet.

## Docker image

From the repository root:

```bash
docker build -t glorfeed/research-service:local research-service
```

The image is built with Java 25 and runs as a non-root user. For Docker network reachability, set `SERVER_ADDRESS=0.0.0.0` and `RESEARCH_KAFKA_BOOTSTRAP_SERVERS` to the broker's address on the Docker network, such as `kafka:19092`. The manual research endpoint still rejects non-loopback socket peers when enabled.

## Useful endpoints

- Health: `http://localhost:8081/actuator/health`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
