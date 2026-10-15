import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPT = Path(__file__).parents[1] / "smoke-crud-projection.py"
spec = importlib.util.spec_from_file_location("smoke_crud_projection", SCRIPT)
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class FakeResponse:
    status = 200

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return b'{"ok":true}'


class SmokeCrudProjectionTest(unittest.TestCase):
    def test_all_api_methods_send_json_headers_and_preserve_bearer_token(self):
        captured = []

        def fake_urlopen(request, timeout):
            captured.append(request)
            return FakeResponse()

        with (
            patch.object(smoke, "BASE_URL", "https://example.test"),
            patch.object(smoke, "TOKEN", "short-lived-test-token"),
            patch.object(smoke.urllib.request, "urlopen", side_effect=fake_urlopen),
        ):
            smoke.api("GET", "/api/topics")
            smoke.api("POST", "/api/topics", {"name": "sample"})
            smoke.api("PUT", "/api/topics/example-id", {"name": "updated"})
            smoke.api("DELETE", "/api/topics/example-id")

        self.assertEqual([request.get_method() for request in captured], ["GET", "POST", "PUT", "DELETE"])
        for request in captured:
            self.assertEqual(request.get_header("Authorization"), "Bearer short-lived-test-token")
            self.assertEqual(request.get_header("Accept"), "application/json")
            self.assertEqual(request.get_header("Content-type"), "application/json")
            self.assertEqual(request.get_header("User-agent"), "Glorfeed-Pipeline-Smoke/1.0")
        self.assertIsNone(captured[0].data)
        self.assertIsNotNone(captured[1].data)
        self.assertIsNotNone(captured[2].data)
        self.assertIsNone(captured[3].data)


if __name__ == "__main__":
    unittest.main()
