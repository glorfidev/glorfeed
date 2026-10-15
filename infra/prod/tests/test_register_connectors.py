import importlib.util
import json
from urllib.error import HTTPError
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).parents[1] / "register_connectors.py"
spec = importlib.util.spec_from_file_location("register_connectors", SCRIPT)
register_connectors = importlib.util.module_from_spec(spec)
spec.loader.exec_module(register_connectors)


class FakeResponse:
    def __init__(self, body, status=200):
        self.body = json.dumps(body).encode()
        self.status = status

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return self.body


class RegisterConnectorsTest(unittest.TestCase):
    def test_puts_secret_references_and_waits_for_both_tasks(self):
        requests = []
        put_attempts = {}
        successful_puts = []

        def fake_urlopen(request, timeout):
            requests.append(request)
            if request.method == "PUT":
                name = request.full_url.split("/")[-2]
                put_attempts[name] = put_attempts.get(name, 0) + 1
                if put_attempts[name] == 1:
                    raise HTTPError(request.full_url, 409, "rebalance", {}, None)
                successful_puts.append(request)
                return FakeResponse({})
            if request.full_url.endswith("/status"):
                return FakeResponse({"connector": {"state": "RUNNING"}, "tasks": [{"id": 0, "state": "RUNNING"}]})
            return FakeResponse({"version": "test"})

        with (
            patch.object(register_connectors, "urlopen", side_effect=fake_urlopen),
            patch.object(register_connectors, "RETRY_SECONDS", 1),
            patch.object(register_connectors, "RETRY_INTERVAL_SECONDS", 0),
        ):
            register_connectors.main()
            register_connectors.main()

        puts = [request for request in requests if request.method == "PUT"]
        self.assertEqual(len(puts), 6)
        self.assertEqual(len(successful_puts), 4)
        for request, expected_db in zip(successful_puts[:2], ("glorfeed_topic", "glorfeed_research")):
            body = json.loads(request.data)
            self.assertIn(expected_db, body["database.dbname"])
            self.assertEqual(body["snapshot.mode"], "no_data")
            self.assertIn("${env:CDC_", body["database.user"])
            self.assertIn("${env:CDC_", body["database.password"])
        self.assertEqual(
            [json.loads(request.data) for request in successful_puts[:2]],
            [json.loads(request.data) for request in successful_puts[2:]],
        )

    def test_restarting_failed_task_preserves_connector_offsets(self):
        requests = []
        statuses = iter((
            {"connector": {"state": "RUNNING"}, "tasks": [{"id": 0, "state": "FAILED"}]},
            {"connector": {"state": "RUNNING"}, "tasks": [{"id": 0, "state": "RUNNING"}]},
        ))

        def fake_urlopen(request, timeout):
            requests.append(request)
            if request.full_url.endswith("/status"):
                return FakeResponse(next(statuses))
            return FakeResponse({"version": "test"})

        with (
            patch.object(register_connectors, "urlopen", side_effect=fake_urlopen),
            patch.object(register_connectors, "RETRY_SECONDS", 1),
            patch.object(register_connectors, "RETRY_INTERVAL_SECONDS", 0),
        ):
            register_connectors.wait_for_running("connector")
        restart = next(request for request in requests if request.method == "POST")
        self.assertIn("includeTasks=true&onlyFailed=true", restart.full_url)

    def test_empty_task_list_times_out_instead_of_reporting_ready(self):
        def fake_urlopen(request, timeout):
            if request.full_url.endswith("/status"):
                return FakeResponse({"connector": {"state": "RUNNING"}, "tasks": []})
            return FakeResponse({"version": "test"})

        with (
            patch.object(register_connectors, "urlopen", side_effect=fake_urlopen),
            patch.object(register_connectors, "RETRY_SECONDS", 0.01),
            patch.object(register_connectors, "RETRY_INTERVAL_SECONDS", 0),
        ):
            with self.assertRaisesRegex(RuntimeError, "did not reach RUNNING"):
                register_connectors.wait_for_running("connector")

    def test_permanent_config_error_fails_without_retry(self):
        attempts = 0

        def fake_urlopen(request, timeout):
            nonlocal attempts
            attempts += 1
            raise HTTPError(request.full_url, 400, "invalid config", {}, None)

        with patch.object(register_connectors, "urlopen", side_effect=fake_urlopen):
            with self.assertRaisesRegex(RuntimeError, "HTTP 400"):
                register_connectors.put_config("connector", {})
        self.assertEqual(attempts, 1)


if __name__ == "__main__":
    unittest.main()
