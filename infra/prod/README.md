# Glorfeed production infrastructure

This Compose stack runs the frontend, topic-service, Caddy, and one shared PostgreSQL instance. Topic-service and research-service each have a separate database and login inside that instance. The frontend container serves the compiled React files on Docker's `web` network; Caddy publishes ports 80 and 443 and handles HTTPS. PostgreSQL uses a separate `backend` network and has no published host port. Topic-service is reachable only inside Docker and connects to PostgreSQL using its own database login.

Before the first deployment:

1. Add a DNS A record for `feed.glorfindela.dev` pointing to the new Glorfeed server. Add an AAAA record only if its IPv6 address is configured and reachable.
2. In the repository's **Settings → Actions → Runners**, add a self-hosted **Linux ARM64** runner on the new Glorfeed server. Follow GitHub's generated installation commands as a dedicated non-root user, give it the custom label `glorfeed-prod`, and install/start its service. The runner needs outbound HTTPS access to GitHub; it does not need inbound SSH from GitHub.
3. Give that runner user write access to `/opt/glorfeed/infra/prod` and permission to run `docker compose`. For example, after creating the runner user, run `sudo install -d -o <runner-user> -g <runner-user> /opt/glorfeed/infra/prod` and `sudo usermod -aG docker <runner-user>`, substituting its actual name. Restart the runner service after changing group membership. Docker access grants extensive control over the host, so reserve this runner for this repository and deployment. Check that the repository is private before installing a runner on the production server; GitHub advises against production self-hosted runners for public repositories.
4. Allow inbound TCP 80 and 443 in the server firewall for Caddy. The Caddyfile uses `feed.glorfindela.dev`.
5. Before deploying, create `/opt/glorfeed/infra/prod/.env` on the server as the runner user. Use all six variables from [.env.example](.env.example) and fill in three strong passwords. The database credentials are required whenever Compose reads this stack, including frontend and topic-service deployments.

From a root SSH session, create the environment file with:

```bash
su - glorfeed-runner
cd /opt/glorfeed/infra/prod
umask 077
nano .env
```

Add the admin, topic, and research credentials from `.env.example`, then save. Use three distinct usernames and keep the admin credentials separate from application credentials. Single-quote passwords containing `$` so Compose treats them literally. Run `chmod 600 .env` to keep the file readable only by the runner user. The real `.env` is ignored by Git, and the deployment workflow does not copy or replace it.

For the first deployment, create the complete server `.env` described above and ensure the frontend image is published. A normal push to `main` that changes topic-service, production configuration, or either deployment workflow builds and publishes the topic-service image first, then calls Infra to install the Compose file, Caddyfile, and database initialization script and apply the stack. This provides the topic image before Compose starts topic-service. No publish-only bootstrap is needed for this normal first deployment. If you need to publish the image without deploying, manually run [Build and Deploy Topic Service](../../.github/workflows/topic-service.yml) on `main` with `deploy` disabled. The reusable [Deploy Infra](../../.github/workflows/infra.yml) workflow can also be started manually on `main`; manual Infra runs require both frontend and topic-service images to be published already. Infra no longer deploys independently on production configuration pushes.

Deploy Infra runs on the Glorfeed server, copies the Compose file, Caddyfile, and PostgreSQL initialization script to `/opt/glorfeed/infra/prod`, logs in to GHCR using the workflow's short-lived `GITHUB_TOKEN`, and applies the Compose stack. Compose's existing `topic-service.depends_on` health condition waits for PostgreSQL before starting topic-service; the frontend can start independently. Infra then recreates only Caddy so its single-file bind mount uses the newly installed Caddyfile. Caddy's named certificate and configuration volumes persist through the recreation. Compose's default pull policy generally reuses locally present images, but always checks for an updated image tagged `latest`; Infra can therefore update existing images as well as apply configuration.

[Build and Deploy Frontend](../../.github/workflows/frontend.yml) runs when `frontend/` or its workflow file changes on `main`, or when started manually from `main`. It builds and publishes AMD64 and ARM64 images on GitHub's hosted runner. After the build succeeds, its deployment job runs on the self-hosted runner, pulls the frontend image, and updates only the frontend container using the installed Compose file.

[Build and Deploy Topic Service](../../.github/workflows/topic-service.yml) runs when `topic-service/`, `infra/prod/`, or either deployment workflow changes on `main`, or when started manually from `main`. It builds and publishes AMD64 and ARM64 images on the GitHub-hosted runner, then calls the reusable Infra workflow to apply deployment. This means production configuration-only changes also build the topic-service image first. Infra serializes production changes with the shared deployment lock, authenticates to GHCR with the short-lived workflow token, and removes its temporary Docker credentials afterward. Frontend-only updates keep their separate service deployment workflow.

The topic build and deploy steps run in order, and production deployments are serialized by the reusable Infra workflow. No VPS SSH secret is required.

Caddy will request a certificate once the domain resolves to this server and traffic can reach ports 80 and 443. The `caddy_data` volume preserves certificates across container restarts. Future backend services can join the Docker network and get their own Caddy routes.

Both databases are reachable by future services on the `backend` network at `postgres:5432`:

| Service | Database | Credentials | JDBC URL |
| --- | --- | --- | --- |
| topic-service | `glorfeed_topic` | `TOPIC_DB_USER` / `TOPIC_DB_PASSWORD` | `jdbc:postgresql://postgres:5432/glorfeed_topic` |
| research-service | `glorfeed_research` | `RESEARCH_DB_USER` / `RESEARCH_DB_PASSWORD` | `jdbc:postgresql://postgres:5432/glorfeed_research` |

`POSTGRES_USER` / `POSTGRES_PASSWORD` belong to the PostgreSQL administrator. The initialization script creates service users without superuser, role-management, database-creation, or replication privileges. It makes each user the owner of its database and removes default public access to both service databases. Each service user can connect to its own service database; the administrator can manage both. Processing-service will get its own database and credentials when added.

PostgreSQL 18 uses the shared `postgres_data` volume mounted at `/var/lib/postgresql`, with data in `/var/lib/postgresql/18/docker`. Logical decoding is enabled for the future Debezium connector; Kafka and Debezium are not part of this deployment yet.

The [PostgreSQL image](https://github.com/docker-library/docs/blob/master/postgres/README.md#initialization-scripts) runs initialization scripts only when its data directory is empty. Editing `.env` afterward does not change stored passwords or create additional databases. Future database additions need an explicit provisioning step for existing installations. Container replacements preserve the named volume and its database contents. This shared setup initializes a new `postgres_data` volume; it does not migrate or delete any earlier `topic_postgres_data` volume.

After the first Infra deployment, confirm Caddy starts successfully and both `https://feed.glorfindela.dev/` and `https://feed.glorfindela.dev/api/topics` are reachable. Then manually check the API: create a temporary topic, list it, retrieve it, update it, and delete it. Confirm data remains after replacing the topic-service container, and verify the frontend still loads at the domain root.
