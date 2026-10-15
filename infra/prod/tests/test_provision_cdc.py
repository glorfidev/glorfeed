import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).parents[1] / "provision-cdc.sh"


class ProvisionCdcTest(unittest.TestCase):
    def test_reconciles_roles_grants_heartbeat_and_owner_publications(self):
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            calls_path = temp / "docker-calls.txt"
            docker = temp / "docker"
            docker.write_text(
                "#!/usr/bin/env python3\n"
                "import os, sys\n"
                "args = sys.argv[1:]\n"
                "source = sys.stdin.read()\n"
                "with open(os.environ['CALLS_PATH'], 'a') as output:\n"
                "    output.write('ARGS ' + ' '.join(args) + '\\\\n')\n"
                "    output.write('STDIN ' + source.replace('\\\\n', ' <NL> ') + '\\\\n')\n"
                "if '--command' in args and 'to_regclass' in args[args.index('--command') + 1]:\n"
                "    print('t')\n"
                "elif \"pg_publication WHERE pubname\" in source:\n"
                "    print('t')\n"
            )
            docker.chmod(0o755)
            env = os.environ | {
                "PATH": f"{temp}:{os.environ['PATH']}",
                "CALLS_PATH": str(calls_path),
                "POSTGRES_USER": "admin",
                "TOPIC_DB_USER": "topic_app",
                "RESEARCH_DB_USER": "research_app",
                "CDC_TOPIC_DB_USER": "topic_cdc",
                "CDC_TOPIC_DB_PASSWORD": "topic-cdc-secret",
                "CDC_RESEARCH_DB_USER": "research_cdc",
                "CDC_RESEARCH_DB_PASSWORD": "research-cdc-secret",
            }
            result = subprocess.run(["bash", str(SCRIPT)], env=env, capture_output=True, text=True)

            self.assertEqual(result.returncode, 0, result.stderr)
            calls = calls_path.read_text(encoding="utf-8")
            self.assertIn("CREATE ROLE", calls)
            self.assertIn("REPLICATION PASSWORD", calls)
            self.assertIn("GRANT SELECT ON TABLE public.outbox_event", calls)
            self.assertIn("GRANT SELECT, UPDATE ON TABLE public.debezium_heartbeat", calls)
            self.assertIn("CREATE TABLE IF NOT EXISTS public.debezium_heartbeat", calls)
            self.assertIn("ALTER PUBLICATION $2 SET TABLE public.outbox_event, public.debezium_heartbeat", calls)
            self.assertIn("sh glorfeed_topic glorfeed_topic_outbox", calls)
            self.assertIn("sh glorfeed_research glorfeed_research_outbox", calls)
            self.assertNotIn("topic-cdc-secret", result.stdout + result.stderr)
            self.assertNotIn("research-cdc-secret", result.stdout + result.stderr)

    def test_rejects_reused_users_before_touching_database(self):
        env = os.environ | {
            "POSTGRES_USER": "same",
            "TOPIC_DB_USER": "topic_app",
            "RESEARCH_DB_USER": "research_app",
            "CDC_TOPIC_DB_USER": "same",
            "CDC_TOPIC_DB_PASSWORD": "topic-cdc-secret",
            "CDC_RESEARCH_DB_USER": "research_cdc",
            "CDC_RESEARCH_DB_PASSWORD": "research-cdc-secret",
        }
        result = subprocess.run(["bash", str(SCRIPT)], env=env, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("must all be distinct", result.stderr)


if __name__ == "__main__":
    unittest.main()
