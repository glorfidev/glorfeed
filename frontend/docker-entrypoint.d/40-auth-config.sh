#!/bin/sh
set -eu

KEYCLOAK_URL=${KEYCLOAK_URL:-https://auth.glorfindela.dev}
KEYCLOAK_REALM=${KEYCLOAK_REALM:-glorfeed}
KEYCLOAK_CLIENT_ID=${KEYCLOAK_CLIENT_ID:-glorfeed-frontend}

case "$KEYCLOAK_URL" in
  *[!A-Za-z0-9.:/-]*) echo "Invalid KEYCLOAK_URL" >&2; exit 1 ;;
esac
case "$KEYCLOAK_URL" in
  https://[!/]*) ;;
  http://localhost*|http://127.0.0.1*) ;;
  *) echo "Invalid KEYCLOAK_URL" >&2; exit 1 ;;
esac
case "$KEYCLOAK_REALM" in
  ''|*[!A-Za-z0-9._-]*) echo "Invalid KEYCLOAK_REALM" >&2; exit 1 ;;
esac
case "$KEYCLOAK_CLIENT_ID" in
  ''|*[!A-Za-z0-9._-]*) echo "Invalid KEYCLOAK_CLIENT_ID" >&2; exit 1 ;;
esac

export KEYCLOAK_URL KEYCLOAK_REALM KEYCLOAK_CLIENT_ID
envsubst '${KEYCLOAK_URL} ${KEYCLOAK_REALM} ${KEYCLOAK_CLIENT_ID}' \
  < /usr/share/nginx/html/auth-config.json.template \
  > /usr/share/nginx/html/auth-config.json
