# Glorfeed

- [Topic service](topic-service/README.md)
- [Research service](research-service/README.md)
- [Local shared infrastructure](infra/local/README.md)

## Local development

Start PostgreSQL for local development:

```bash
cd topic-service/infra/local
cp .env.example .env
docker compose up -d postgres
```

The local database is available at `localhost:54321` and is isolated from the
production PostgreSQL instance.

Run `topic-service` from IntelliJ with these environment variables:

```text
TOPIC_DB_URL=jdbc:postgresql://localhost:54321/glorfeed_topic
TOPIC_DB_USER=glorfeed
TOPIC_DB_PASSWORD=<the value of LOCAL_DB_PASSWORD in .env>
```

To also send logs to the local Graylog GELF TCP input, add
`GRAYLOG_ENABLED=true` to the IntelliJ run configuration. The default
destination is `127.0.0.1:12201`; override it with `GRAYLOG_HOST` and
`GRAYLOG_PORT` when needed. Console logging remains enabled even when Graylog
is unavailable.

Stop the local database while preserving its data:

```bash
cd topic-service/infra/local
docker compose stop
```

## Container image

Build a local container image from the repository root:

```bash
docker build -t glorfeed/topic-service:local topic-service
```

The image runs the application on port `8080`. Database credentials are passed
to the container at runtime and are never included in the image.

## API documentation

When `topic-service` is running locally, Swagger UI is available at:

```text
http://localhost:8080/swagger-ui.html
```

The generated OpenAPI document is available at:

```text
http://localhost:8080/v3/api-docs
```
