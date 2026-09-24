# Research service

This service will receive research requests, orchestrate external research, and eventually publish `research.completed` events to Kafka. It currently contains only the service foundation; it has no API, database schema, Kafka listener, or AI-provider implementation yet.

## Local database

Create a private local environment file, then start PostgreSQL:

```bash
cd infra/local
cp .env.example .env
docker compose up -d postgres
```

Run `ResearchServiceApplication` from IntelliJ with these environment variables:

```text
RESEARCH_DB_URL=jdbc:postgresql://localhost:54322/glorfeed_research
RESEARCH_DB_USER=glorfeed
RESEARCH_DB_PASSWORD=<value from infra/local/.env>
SERVER_PORT=8081
```

`SERVER_PORT=8081` keeps this service separate from the topic service while both run locally. Inside its eventual Docker container, the service will still listen on port `8080`.

## Docker image

From the repository root:

```bash
docker build -t glorfeed/research-service:local research-service
```

The image is built with Java 25 and runs as a non-root user. The Kafka library is present, but Kafka configuration and consumers will be added when we define the first research event.

## Useful endpoints

- Health: `http://localhost:8081/actuator/health`
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`
