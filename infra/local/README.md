# Local shared infrastructure

This Compose setup runs the local Kafka environment shared by every Glorfeed service.

## Start

```bash
docker compose up -d
```

## Addresses

| Purpose | Address |
| --- | --- |
| Java services started from IntelliJ | `localhost:9092` |
| Kafka UI | `http://localhost:8088` |
| Containers in this Compose project | `kafka:19092` |

The broker is a single Kafka node in KRaft combined mode: it acts as both broker and controller. This is suitable for local development, but it is not a highly available production cluster.

Kafka topic auto-creation is disabled. We will create each application topic explicitly after deciding its name, partitions, retention, and producer/consumer contract.

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
