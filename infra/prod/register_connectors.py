#!/usr/bin/env python3
"""Idempotently apply production Debezium connector configs and verify tasks."""

from __future__ import annotations

import json
import os
import time
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


CONNECT_URL = os.environ.get("CONNECT_URL", "http://127.0.0.1:18083").rstrip("/")
RETRY_SECONDS = float(os.environ.get("CONNECT_RETRY_SECONDS", "180"))
RETRY_INTERVAL_SECONDS = float(os.environ.get("CONNECT_RETRY_INTERVAL_SECONDS", "3"))
CONFIGS = {
    "glorfeed-topic-outbox": Path(__file__).parent / "connectors" / "topic-outbox.json",
    "glorfeed-research-outbox": Path(__file__).parent / "connectors" / "research-outbox.json",
}


def request_json(path: str, *, method: str = "GET", body: Any = None) -> Any:
    data = None if body is None else json.dumps(body).encode("utf-8")
    request = Request(
        f"{CONNECT_URL}{path}",
        data=data,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    with urlopen(request, timeout=10) as response:
        raw = response.read()
    return json.loads(raw) if raw else None


def transient_http_error(error: HTTPError) -> bool:
    return error.code in (409, 429) or error.code >= 500


def put_config(name: str, config: dict[str, Any]) -> None:
    deadline = time.monotonic() + RETRY_SECONDS
    while True:
        try:
            request_json(f"/connectors/{name}/config", method="PUT", body=config)
            return
        except HTTPError as error:
            if not transient_http_error(error):
                raise RuntimeError(f"Connector {name} config rejected with HTTP {error.code}") from None
            if time.monotonic() >= deadline:
                raise RuntimeError(f"Connector {name} config update stayed unavailable (HTTP {error.code})") from None
        except (URLError, TimeoutError, OSError):
            if time.monotonic() >= deadline:
                raise RuntimeError(f"Connector {name} config update timed out") from None
        time.sleep(RETRY_INTERVAL_SECONDS)


def wait_until_ready() -> None:
    deadline = time.monotonic() + RETRY_SECONDS
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            request_json("/")
            return
        except (HTTPError, URLError, TimeoutError, OSError) as error:
            last_error = error
            time.sleep(RETRY_INTERVAL_SECONDS)
    raise RuntimeError(f"Kafka Connect did not become ready within {RETRY_SECONDS:g}s") from last_error


def task_states(name: str) -> tuple[str, list[str]]:
    status = request_json(f"/connectors/{name}/status")
    connector_state = status.get("connector", {}).get("state", "UNKNOWN")
    tasks = [
        f"{task.get('id', 'unknown')}:{task.get('state', 'UNKNOWN')}"
        for task in status.get("tasks", [])
    ]
    return connector_state, tasks


def wait_for_running(name: str) -> None:
    deadline = time.monotonic() + RETRY_SECONDS
    last_status: tuple[str, list[str]] | None = None
    restarted_failed_once = False
    while time.monotonic() < deadline:
        try:
            last_status = task_states(name)
            state, tasks = last_status
            if state == "RUNNING" and tasks == ["0:RUNNING"]:
                return
            failed = state == "FAILED" or any(task.endswith(":FAILED") for task in tasks)
            if failed and not restarted_failed_once:
                request_json(
                    f"/connectors/{name}/restart?includeTasks=true&onlyFailed=true",
                    method="POST",
                )
                restarted_failed_once = True
        except HTTPError as error:
            if error.code != 404 and not transient_http_error(error):
                raise RuntimeError(f"Unable to read connector {name} status (HTTP {error.code})") from None
            last_status = (f"HTTP {error.code}", [])
        except (URLError, TimeoutError, OSError):
            pass
        time.sleep(RETRY_INTERVAL_SECONDS)
    raise RuntimeError(f"Connector {name} did not reach RUNNING state: {last_status}")


def main() -> None:
    wait_until_ready()
    configured = []
    for name, path in CONFIGS.items():
        config = json.loads(path.read_text(encoding="utf-8"))
        # PUT is a full, repeatable reconciliation of the connector config.
        put_config(name, config)
        configured.append(name)
    for name in configured:
        wait_for_running(name)
        print(f"{name}: connector and task RUNNING")


if __name__ == "__main__":
    main()
