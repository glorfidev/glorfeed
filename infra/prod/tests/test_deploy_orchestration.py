import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


SOURCE_DIR = Path(__file__).parents[1]


class DeployOrchestrationTest(unittest.TestCase):
    def test_orders_prerequisites_readiness_provisioning_registration_and_caddy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            prod = root / "infra" / "prod"
            bin_dir = root / "bin"
            prod.mkdir(parents=True)
            bin_dir.mkdir()
            shutil.copy(SOURCE_DIR / "deploy.sh", prod / "deploy.sh")
            (prod / ".env").write_text(
                "POSTGRES_USER=admin\nPOSTGRES_PASSWORD=admin-test\n"
                "TOPIC_DB_USER=topic\nTOPIC_DB_PASSWORD=topic-test\n"
                "RESEARCH_DB_USER=research\nRESEARCH_DB_PASSWORD=research-test\n"
                "CDC_TOPIC_DB_USER=topic-cdc\nCDC_TOPIC_DB_PASSWORD=topic-cdc-test\n"
                "CDC_RESEARCH_DB_USER=research-cdc\nCDC_RESEARCH_DB_PASSWORD=research-cdc-test\n",
                encoding="utf-8",
            )
            (prod / "provision-cdc.sh").write_text(
                "#!/usr/bin/env bash\nprintf 'provision\\n' >> \"$CALLS_PATH\"\n", encoding="utf-8"
            )
            (prod / "register_connectors.py").write_text("# mocked registration\n", encoding="utf-8")
            (prod / "provision-cdc.sh").chmod(0o755)
            docker = bin_dir / "docker"
            docker.write_text(
                "#!/usr/bin/env bash\n"
                "printf 'docker %s\\n' \"$*\" >> \"$CALLS_PATH\"\n"
                "if [[ \"$1\" == inspect ]]; then echo healthy; fi\n"
                "if [[ \"$1 $2\" == 'compose ps' ]]; then echo container-id; fi\n",
                encoding="utf-8",
            )
            docker.chmod(0o755)
            python3 = bin_dir / "python3"
            python3.write_text(
                "#!/usr/bin/env bash\nprintf 'register\\n' >> \"$CALLS_PATH\"\n", encoding="utf-8"
            )
            python3.chmod(0o755)

            env = os.environ | {
                "PATH": f"{bin_dir}:{os.environ['PATH']}",
                "CALLS_PATH": str(root / "calls.txt"),
            }
            result = subprocess.run(["bash", str(prod / "deploy.sh")], env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)

            calls = (root / "calls.txt").read_text(encoding="utf-8").splitlines()
            self.assertLess(calls.index("docker compose up -d postgres kafka"), calls.index("docker compose exec -T kafka sh -c KAFKA_HEAP_OPTS=\"-Xms32m -Xmx128m\" exec /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --create --if-not-exists --topic outbox.event.topic --partitions 1 --replication-factor 1"))
            self.assertLess(calls.index("provision"), calls.index("register"))
            self.assertLess(calls.index("register"), calls.index("docker compose up -d --force-recreate --no-deps caddy"))
            self.assertIn("RESEARCH_DB_PASSWORD=research-test", (prod / ".env").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
