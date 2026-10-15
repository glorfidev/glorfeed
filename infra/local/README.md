# Local shared infrastructure

This Compose setup runs the local Kafka environment shared by every Glorfeed service.

## Start

Create the shared Docker network once before starting either Compose project:

```bash
docker network create glorfeed-local-network
```

The Kafka stack and both services' local PostgreSQL containers join this network.
Debezium Connect reaches Kafka at `kafka:19092`, the topic database at
`topic-postgres:5432`, and the research database at `research-postgres:5432`.

```bash
docker compose up -d
```

Create both `topic-service/infra/local/.env` and `research-service/infra/local/.env`
before starting Debezium Connect. Start the two local databases and their
services so Flyway creates each `outbox_event` table, then start the worker:

```bash
docker compose --profile cdc up -d
```

Its REST API is available only from your Mac at `http://localhost:8083`.
The worker starts without any PostgreSQL connector. Register each connector
after its service has created the `outbox_event` table. From this directory:

```bash
curl -X PUT -H "Content-Type: application/json" \
  --data-binary @connectors/topic-outbox.json \
  http://localhost:8083/connectors/glorfeed-topic-outbox/config

curl -X PUT -H "Content-Type: application/json" \
  --data-binary @connectors/research-outbox.json \
  http://localhost:8083/connectors/glorfeed-research-outbox/config
```

## Addresses

| Purpose | Address |
| --- | --- |
| Java services started from IntelliJ | `localhost:9092` |
| Kafka UI | `http://localhost:8088` |
| Debezium Connect REST API (with `cdc` profile) | `http://localhost:8083` |
| Containers on the shared Docker network | `kafka:19092` |

The broker is a single Kafka node in KRaft combined mode: it acts as both broker and controller. This is suitable for local development, but it is not a highly available production cluster.

Kafka broker topic auto-creation is disabled. The Debezium connectors create
`outbox.event.topic` and `outbox.event.research` with one partition and one
replica when they produce their first outbox events.

Kafka data is stored in the Docker-managed `kafka_data` volume at the image's writable `/var/lib/kafka/data` directory.

## Stop and reset

Stop the containers while retaining Kafka data:

```bash
docker compose stop
```

Delete the containers and all local Kafka data:

```bash
docker compose down -v
```
