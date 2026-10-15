#!/usr/bin/env bash
set -euo pipefail

: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${TOPIC_DB_USER:?TOPIC_DB_USER is required}"
: "${TOPIC_DB_PASSWORD:?TOPIC_DB_PASSWORD is required}"
: "${RESEARCH_DB_USER:?RESEARCH_DB_USER is required}"
: "${RESEARCH_DB_PASSWORD:?RESEARCH_DB_PASSWORD is required}"

if [[ "$TOPIC_DB_USER" == "$RESEARCH_DB_USER" ||
      "$TOPIC_DB_USER" == "$POSTGRES_USER" ||
      "$RESEARCH_DB_USER" == "$POSTGRES_USER" ]]; then
  echo "The admin, topic, and research database users must be distinct." >&2
  exit 1
fi

# Quoted psql variables safely handle special characters in names/passwords.
psql --no-psqlrc --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=topic_user="$TOPIC_DB_USER" \
  --set=topic_password="$TOPIC_DB_PASSWORD" \
  --set=research_user="$RESEARCH_DB_USER" \
  --set=research_password="$RESEARCH_DB_PASSWORD" <<'SQL'
CREATE ROLE :"topic_user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'topic_password';
CREATE ROLE :"research_user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'research_password';

CREATE DATABASE glorfeed_topic OWNER :"topic_user";
CREATE DATABASE glorfeed_research OWNER :"research_user";

REVOKE ALL ON DATABASE glorfeed_topic FROM PUBLIC;
REVOKE ALL ON DATABASE glorfeed_research FROM PUBLIC;
GRANT CONNECT ON DATABASE glorfeed_topic TO :"topic_user";
GRANT CONNECT ON DATABASE glorfeed_research TO :"research_user";
SQL
