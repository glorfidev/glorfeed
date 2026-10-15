#!/usr/bin/env bash
set -euo pipefail

: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${CDC_TOPIC_DB_USER:?CDC_TOPIC_DB_USER is required}"
: "${CDC_TOPIC_DB_PASSWORD:?CDC_TOPIC_DB_PASSWORD is required}"
: "${CDC_RESEARCH_DB_USER:?CDC_RESEARCH_DB_USER is required}"
: "${CDC_RESEARCH_DB_PASSWORD:?CDC_RESEARCH_DB_PASSWORD is required}"
: "${TOPIC_DB_USER:?TOPIC_DB_USER is required}"
: "${RESEARCH_DB_USER:?RESEARCH_DB_USER is required}"

all_users=("$POSTGRES_USER" "$TOPIC_DB_USER" "$RESEARCH_DB_USER" "$CDC_TOPIC_DB_USER" "$CDC_RESEARCH_DB_USER")
for ((i = 0; i < ${#all_users[@]}; i++)); do
  for ((j = i + 1; j < ${#all_users[@]}; j++)); do
    if [[ "${all_users[i]}" == "${all_users[j]}" ]]; then
      echo "Admin, application, and CDC users must all be distinct." >&2
      exit 1
    fi
  done
done

compose=(docker compose)

wait_for_outbox_table() {
  local database="$1"
  local attempt
  for attempt in {1..180}; do
    if "${compose[@]}" exec -T postgres psql --no-psqlrc --quiet \
      --username "$POSTGRES_USER" --dbname "$database" --tuples-only --no-align \
      --command "SELECT to_regclass('public.outbox_event') IS NOT NULL" 2>/dev/null | grep -qx t; then
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for $database.public.outbox_event (service migrations did not finish)." >&2
  return 1
}

wait_for_outbox_table glorfeed_topic
wait_for_outbox_table glorfeed_research

# Create/reconcile separate minimum-privilege replication logins. Admin credentials
# are used only for provisioning, never passed to Connect.
"${compose[@]}" exec -T postgres psql --no-psqlrc --quiet --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" --dbname postgres \
  --set=topic_cdc_user="$CDC_TOPIC_DB_USER" --set=topic_cdc_password="$CDC_TOPIC_DB_PASSWORD" \
  --set=research_cdc_user="$CDC_RESEARCH_DB_USER" --set=research_cdc_password="$CDC_RESEARCH_DB_PASSWORD" <<'SQL'
SELECT format(
    'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION PASSWORD %L',
    :'topic_cdc_user', :'topic_cdc_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'topic_cdc_user')
\gexec
SELECT format(
    'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION PASSWORD %L',
    :'research_cdc_user', :'research_cdc_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'research_cdc_user')
\gexec
ALTER ROLE :"topic_cdc_user" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT REPLICATION PASSWORD :'topic_cdc_password';
ALTER ROLE :"research_cdc_user" WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT REPLICATION PASSWORD :'research_cdc_password';
SQL

provision_database() {
  local database="$1"
  local publication="$2"
  local cdc_user_variable="$3"
  local cdc_user="${!cdc_user_variable:?${cdc_user_variable} is required}"
  local exists

  # The heartbeat row is updated by Debezium during idle periods. It belongs to
  # the application DB owner, while the CDC login receives only SELECT/UPDATE.
  "${compose[@]}" exec -T postgres sh -c \
    'case "$1" in
       glorfeed_topic) app_user="$TOPIC_DB_USER"; app_password="$TOPIC_DB_PASSWORD" ;;
       glorfeed_research) app_user="$RESEARCH_DB_USER"; app_password="$RESEARCH_DB_PASSWORD" ;;
       *) echo "Unsupported database: $1" >&2; exit 1 ;;
     esac
     PGPASSWORD="$app_password" exec psql --no-psqlrc --set=ON_ERROR_STOP=1 -h 127.0.0.1 -U "$app_user" -d "$1"' \
    sh "$database" <<'SQL'
CREATE TABLE IF NOT EXISTS public.debezium_heartbeat (
  id integer PRIMARY KEY,
  touched_at timestamp with time zone NOT NULL
);
INSERT INTO public.debezium_heartbeat (id, touched_at)
VALUES (1, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
SQL

  "${compose[@]}" exec -T postgres psql --no-psqlrc --quiet --set=ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" --dbname "$database" \
    --set=database_name="$database" --set=cdc_user="$cdc_user" <<'SQL'
GRANT CONNECT ON DATABASE :"database_name" TO :"cdc_user";
GRANT USAGE ON SCHEMA public TO :"cdc_user";
GRANT SELECT ON TABLE public.outbox_event TO :"cdc_user";
GRANT SELECT, UPDATE ON TABLE public.debezium_heartbeat TO :"cdc_user";
SQL

  exists=$("${compose[@]}" exec -T postgres psql --no-psqlrc --quiet \
    --username "$POSTGRES_USER" --dbname "$database" --tuples-only --no-align \
    --set=publication="$publication" <<'SQL'
SELECT EXISTS (SELECT 1 FROM pg_publication WHERE pubname = :'publication');
SQL
  )

  if [[ "$exists" != "t" ]]; then
    "${compose[@]}" exec -T postgres sh -c \
      'case "$1" in
         glorfeed_topic) app_user="$TOPIC_DB_USER"; app_password="$TOPIC_DB_PASSWORD" ;;
         glorfeed_research) app_user="$RESEARCH_DB_USER"; app_password="$RESEARCH_DB_PASSWORD" ;;
       esac
       PGPASSWORD="$app_password" exec psql --no-psqlrc --set=ON_ERROR_STOP=1 -h 127.0.0.1 -U "$app_user" -d "$1" -c "CREATE PUBLICATION $2 FOR TABLE public.outbox_event, public.debezium_heartbeat WITH (publish = '\''insert, update'\'')"' \
      sh "$database" "$publication"
  fi

  # Reconcile a publication created by an earlier version of this script too.
  "${compose[@]}" exec -T postgres sh -c \
    'case "$1" in
       glorfeed_topic) app_user="$TOPIC_DB_USER"; app_password="$TOPIC_DB_PASSWORD" ;;
       glorfeed_research) app_user="$RESEARCH_DB_USER"; app_password="$RESEARCH_DB_PASSWORD" ;;
     esac
     PGPASSWORD="$app_password" exec psql --no-psqlrc --set=ON_ERROR_STOP=1 -h 127.0.0.1 -U "$app_user" -d "$1" -c "ALTER PUBLICATION $2 SET TABLE public.outbox_event, public.debezium_heartbeat" -c "ALTER PUBLICATION $2 SET (publish = '\''insert, update'\'')"' \
    sh "$database" "$publication"
}

provision_database glorfeed_topic glorfeed_topic_outbox CDC_TOPIC_DB_USER
provision_database glorfeed_research glorfeed_research_outbox CDC_RESEARCH_DB_USER

echo "Scoped CDC roles, heartbeat rows, grants, and outbox publications are ready."
