#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir"

if [[ ! -f .env ]]; then
  echo "Create infra/prod/.env on the server before deployment." >&2
  exit 1
fi

# Compose reads .env itself; export the same values for the idempotent DB
# provisioning and connector registration helpers without printing secrets.
set -a
# shellcheck disable=SC1091
. ./.env
set +a

docker compose config --quiet

wait_healthy() {
  local service="$1"
  local timeout_seconds="${2:-300}"
  local deadline=$((SECONDS + timeout_seconds))
  local container_id health
  while (( SECONDS < deadline )); do
    container_id=$(docker compose ps -q "$service")
    if [[ -n "$container_id" ]]; then
      health=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)
      if [[ "$health" == "healthy" || "$health" == "running" ]]; then
        return 0
      fi
      if [[ "$health" == "unhealthy" || "$health" == "exited" ]]; then
        docker compose logs --tail=80 "$service" >&2
        echo "$service entered state $health." >&2
        return 1
      fi
    fi
    sleep 5
  done
  docker compose logs --tail=80 "$service" >&2
  echo "Timed out waiting for $service to become healthy." >&2
  return 1
}

wait_internal_http() {
  local service="$1"
  local url="$2"
  local deadline=$((SECONDS + 300))
  while (( SECONDS < deadline )); do
    if docker compose exec -T debezium-connect curl --silent --show-error --fail \
      --max-time 5 "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  docker compose logs --tail=80 "$service" >&2
  echo "Timed out waiting for $service health endpoint." >&2
  return 1
}

echo "Starting PostgreSQL and Kafka prerequisites."
docker compose up -d postgres kafka
wait_healthy postgres
wait_healthy kafka

# The consumer subscribes before any topic event exists; precreate its fixed
# topic because broker auto-creation is disabled.
docker compose exec -T kafka sh -c \
  'KAFKA_HEAP_OPTS="-Xms32m -Xmx128m" exec /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic outbox.event.topic --partitions 1 --replication-factor 1'

echo "Starting application services and private Kafka Connect."
docker compose up -d
wait_healthy debezium-connect
wait_internal_http topic-service http://topic-service:8080/actuator/health
wait_internal_http research-service http://research-service:8080/actuator/health

echo "Waiting for Flyway outbox tables, then provisioning the scoped CDC roles and publications."
./provision-cdc.sh

echo "Reconciling Debezium connectors and waiting for all tasks to run."
python3 ./register_connectors.py

# Recreate only Caddy so its file bind mount picks up the installed config.
docker compose up -d --force-recreate --no-deps caddy
echo "Production event pipeline is ready. Run ./smoke-crud-projection.py with a local access token to verify CRUD projection."
