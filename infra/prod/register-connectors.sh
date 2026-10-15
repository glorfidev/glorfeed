#!/usr/bin/env bash
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://127.0.0.1:18083}"
CONNECT_RETRY_SECONDS="${CONNECT_RETRY_SECONDS:-180}"
CONNECT_RETRY_INTERVAL_SECONDS="${CONNECT_RETRY_INTERVAL_SECONDS:-3}"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

for tool in curl jq; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Missing required command: $tool. Install curl and jq on the server before deployment." >&2
    exit 1
  fi
done

if [[ ! "$CONNECT_RETRY_SECONDS" =~ ^[0-9]+$ || ! "$CONNECT_RETRY_INTERVAL_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "CONNECT_RETRY_SECONDS and CONNECT_RETRY_INTERVAL_SECONDS must be whole numbers." >&2
  exit 1
fi

CONNECT_URL="${CONNECT_URL%/}"
response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

http_request() {
  local method="$1"
  local path="$2"
  local body_file="${3:-}"
  local -a args=(--silent --show-error --connect-timeout 5 --max-time 10 --output "$response_file" --write-out '%{http_code}' -X "$method" -H 'Content-Type: application/json')
  if [[ -n "$body_file" ]]; then
    args+=(--data-binary "@$body_file")
  fi
  if HTTP_STATUS="$(curl "${args[@]}" "$CONNECT_URL$path")"; then
    return 0
  fi
  HTTP_STATUS=000
  return 1
}

is_transient_status() {
  case "$HTTP_STATUS" in
    000|409|429|5??) return 0 ;;
    *) return 1 ;;
  esac
}

request_until_success() {
  local method="$1"
  local path="$2"
  local body_file="$3"
  local retry_not_found="$4"
  local description="$5"
  local deadline=$((SECONDS + CONNECT_RETRY_SECONDS))
  local curl_result

  while :; do
    curl_result=0
    http_request "$method" "$path" "$body_file" || curl_result=$?
    if [[ "$curl_result" == 0 && "$HTTP_STATUS" =~ ^2[0-9][0-9]$ ]]; then
      return 0
    fi
    if [[ "$HTTP_STATUS" == 400 ]]; then
      if [[ "$method" == PUT ]]; then
        echo "$description was rejected with HTTP 400; check the connector configuration." >&2
      else
        echo "$description failed with HTTP 400; check the Kafka Connect endpoint and configuration." >&2
      fi
      return 1
    fi
    if ! is_transient_status && ! { [[ "$retry_not_found" == true && "$HTTP_STATUS" == 404 ]]; }; then
      echo "$description failed with HTTP $HTTP_STATUS." >&2
      return 1
    fi
    if (( SECONDS >= deadline )); then
      echo "$description timed out after ${CONNECT_RETRY_SECONDS}s (last HTTP status: $HTTP_STATUS)." >&2
      return 1
    fi
    sleep "$CONNECT_RETRY_INTERVAL_SECONDS"
  done
}

echo "Waiting for Kafka Connect."
request_until_success GET / "" false "Kafka Connect readiness check"

for connector in glorfeed-topic-outbox glorfeed-research-outbox; do
  case "$connector" in
    glorfeed-topic-outbox) config_file="$script_dir/connectors/topic-outbox.json" ;;
    glorfeed-research-outbox) config_file="$script_dir/connectors/research-outbox.json" ;;
  esac
  request_until_success PUT "/connectors/$connector/config" "$config_file" false "Updating $connector"
done

for connector in glorfeed-topic-outbox glorfeed-research-outbox; do
  deadline=$((SECONDS + CONNECT_RETRY_SECONDS))
  restarted_failed_task=false
  connector_ready=false
  task_summary="status unavailable"
  while (( SECONDS < deadline )); do
    request_until_success GET "/connectors/$connector/status" "" true "Reading $connector status"
    if jq -e '(.connector.state == "RUNNING") and (.tasks | type == "array" and length == 1 and .[0].id == 0 and .[0].state == "RUNNING")' "$response_file" >/dev/null 2>&1; then
      echo "$connector: connector and task 0 RUNNING"
      connector_ready=true
      break
    fi

    task_summary="$(jq -r '"connector=" + (.connector.state // "UNKNOWN") + " tasks=" + ((.tasks // []) | map((.id | tostring) + ":" + (.state // "UNKNOWN")) | join(","))' "$response_file" 2>/dev/null || printf 'invalid status response')"
    if [[ "$restarted_failed_task" == false ]] && jq -e '(.connector.state == "FAILED") or any(.tasks[]?; .state == "FAILED")' "$response_file" >/dev/null 2>&1; then
      request_until_success POST "/connectors/$connector/restart?includeTasks=true&onlyFailed=true" "" false "Restarting failed task for $connector"
      restarted_failed_task=true
    fi
    sleep "$CONNECT_RETRY_INTERVAL_SECONDS"
  done

  if [[ "$connector_ready" != true ]]; then
    echo "$connector did not reach RUNNING before timeout ($task_summary). Check Kafka Connect logs and database connectivity." >&2
    exit 1
  fi
done
