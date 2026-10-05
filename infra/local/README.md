# Local shared infrastructure

This Compose setup runs the local Kafka environment shared by every Glorfeed service.

## Start

Create the shared Docker network once before starting either Compose project:

```bash
docker network create glorfeed-local-network
```

Both the Kafka stack and the topic service's local PostgreSQL join this network.
Debezium Connect will reach Kafka at `kafka:19092` and the topic database at
`topic-postgres:5432`.

```bash
docker compose up -d
```

Start Debezium Connect when working on CDC:

```bash
docker compose --profile cdc up -d
```

Its REST API is available only from your Mac at `http://localhost:8083`.
The worker starts without any PostgreSQL connector. Once the topic-service
database has the `outbox_event` table, register its connector from this
directory:

```bash
curl -X PUT -H "Content-Type: application/json" \
  --data-binary @connectors/topic-outbox.json \
  http://localhost:8083/connectors/glorfeed-topic-outbox/config
```

## Addresses

| Purpose | Address |
| --- | --- |
| Java services started from IntelliJ | `localhost:9092` |
| Kafka UI | `http://localhost:8088` |
| Debezium Connect REST API (with `cdc` profile) | `http://localhost:8083` |
| Containers on the shared Docker network | `kafka:19092` |

The broker is a single Kafka node in KRaft combined mode: it acts as both broker and controller. This is suitable for local development, but it is not a highly available production cluster.

Kafka broker topic auto-creation is disabled. The Debezium connector creates
`outbox.event.topic` explicitly with one partition and one replica when it
produces the first outbox event.

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
