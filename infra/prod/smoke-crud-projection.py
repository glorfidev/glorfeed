#!/usr/bin/env python3
"""Exercise authenticated topic CRUD and await each research projection change."""

from __future__ import annotations

import json
import os
import subprocess
import time
import urllib.error
import urllib.request
import uuid


BASE_URL = os.environ.get("API_BASE_URL", "https://feed.glorfindela.dev").rstrip("/")
TOKEN = os.environ.get("ACCESS_TOKEN", "")
POLL_SECONDS = int(os.environ.get("PROJECTION_TIMEOUT_SECONDS", "90"))
USER_AGENT = "Glorfeed-Pipeline-Smoke/1.0"


def api(method: str, path: str, payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{BASE_URL}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Accept": "application/json",
            "Content-Type": "application/json",
            "User-Agent": USER_AGENT,
        },
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        body = response.read()
        return response.status, json.loads(body) if body else None


def projected_topic(topic_id: str):
    sql = (
        "SELECT name || E'\\t' || description FROM public.research_topic "
        f"WHERE id = '{topic_id}'::uuid"
    )
    result = subprocess.run(
        [
            "docker",
            "compose",
            "exec",
            "-T",
            "postgres",
            "sh",
            "-c",
            'PGPASSWORD="$RESEARCH_DB_PASSWORD" exec psql --no-psqlrc --quiet '
            '-h 127.0.0.1 -U "$RESEARCH_DB_USER" -d glorfeed_research -At -c "$1"',
            "smoke-crud-projection",
            sql,
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    value = result.stdout.rstrip("\n")
    if not value:
        return None
    name, description = value.split("\t", 1)
    return {"name": name, "description": description}


def wait_for_projection(topic_id: str, expected):
    deadline = time.monotonic() + POLL_SECONDS
    while time.monotonic() < deadline:
        actual = projected_topic(topic_id)
        if actual == expected:
            return
        time.sleep(2)
    raise RuntimeError(f"Research projection did not reach expected state for topic {topic_id}")


def main():
    if not TOKEN:
        raise SystemExit("Set ACCESS_TOKEN to a short-lived access token for an authorized test user.")

    marker = uuid.uuid4().hex[:10]
    topic_id = None
    deleted = False
    try:
        status, topic = api(
            "POST",
            "/api/topics",
            {"name": f"pipeline-smoke-{marker}", "description": "temporary pipeline verification"},
        )
        if status != 201:
            raise RuntimeError(f"Create returned HTTP {status}")
        topic_id = str(uuid.UUID(str(topic["id"])))
        created = {"name": topic["name"], "description": topic["description"]}
        wait_for_projection(topic_id, created)

        status, listed = api("GET", "/api/topics")
        if status != 200 or not any(str(row.get("id")) == topic_id for row in listed):
            raise RuntimeError("Created topic was not visible through the authenticated list endpoint")
        status, fetched = api("GET", f"/api/topics/{topic_id}")
        if status != 200 or str(fetched.get("id")) != topic_id:
            raise RuntimeError("Created topic was not visible through the authenticated get endpoint")

        updated = {"name": f"pipeline-smoke-updated-{marker}", "description": "updated pipeline verification"}
        status, result = api("PUT", f"/api/topics/{topic_id}", updated)
        if status != 200 or result.get("name") != updated["name"]:
            raise RuntimeError("Topic update did not return the updated topic")
        wait_for_projection(topic_id, updated)

        status, _ = api("DELETE", f"/api/topics/{topic_id}")
        if status != 204:
            raise RuntimeError(f"Delete returned HTTP {status}")
        deleted = True
        wait_for_projection(topic_id, None)
        print(f"Authenticated create/update/delete and research projection verified for temporary topic {topic_id}.")
    finally:
        if topic_id and not deleted:
            try:
                api("DELETE", f"/api/topics/{topic_id}")
                wait_for_projection(topic_id, None)
            except (urllib.error.URLError, subprocess.CalledProcessError, RuntimeError):
                print(f"Cleanup could not be confirmed for temporary topic {topic_id}.")


if __name__ == "__main__":
    main()
