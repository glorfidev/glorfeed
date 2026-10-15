# Glorfeed production infrastructure

This Compose stack runs the frontend, topic-service, research-service, Caddy, PostgreSQL, single-node Kafka, and Debezium Connect. Topic-service and research-service each have a separate database and application login inside PostgreSQL. Each database also has its own restricted CDC login. Kafka and Connect have no public listener; Connect's REST API is published only on server loopback at `127.0.0.1:18083`. Caddy publishes ports 80 and 443. Application services, PostgreSQL, Kafka, and Connect use private Docker networks.

Before the first deployment:

1. Add a DNS A record for `feed.glorfindela.dev` pointing to the new Glorfeed server. Add an AAAA record only if its IPv6 address is configured and reachable.
2. In the repository's **Settings → Actions → Runners**, add a self-hosted **Linux ARM64** runner on the new Glorfeed server. Follow GitHub's generated installation commands as a dedicated non-root user, give it the custom label `glorfeed-prod`, and install/start its service. The runner needs outbound HTTPS access to GitHub; it does not need inbound SSH from GitHub.
3. Give that runner user write access to `/opt/glorfeed/infra/prod` and permission to run `docker compose`. For example, after creating the runner user, run `sudo install -d -o <runner-user> -g <runner-user> /opt/glorfeed/infra/prod` and `sudo usermod -aG docker <runner-user>`, substituting its actual name. Restart the runner service after changing group membership. Docker access grants extensive control over the host, so reserve this runner for this repository and deployment. Check that the repository is private before installing a runner on the production server; GitHub advises against production self-hosted runners for public repositories.
4. Allow inbound TCP 80 and 443 in the server firewall for Caddy. The Caddyfile uses `feed.glorfindela.dev`.
5. Before deploying, create `/opt/glorfeed/infra/prod/.env` on the server as the runner user. Use the variables from [.env.example](.env.example) and set distinct strong passwords for admin, topic, research, topic-CDC, and research-CDC users. Set public Keycloak and API validation values if they differ from their defaults. Compose requires these values whenever it reads the stack. CDC credentials reach Connect only through its environment-variable config provider; connector JSON stores `${env:...}` references, not serialized credentials. PostgreSQL admin credentials are never sent to Connect.
6. Configure the existing Keycloak server at `https://auth.glorfindela.dev` before enabling sign-in. Create a dedicated `glorfeed` realm and a public OpenID Connect client named `glorfeed-frontend`. Enable Standard Flow, require PKCE with `S256`, and leave the client secret disabled. Add `https://feed.glorfindela.dev/` and `https://feed.glorfindela.dev/silent-check-sso.html` as valid redirect URIs, add `https://feed.glorfindela.dev/` as the post-logout redirect URI, and set `https://feed.glorfindela.dev` as a web origin. Add an audience mapper that adds `glorfeed-topic-service` to the access token's `aud` claim. Do not use an ID-token-only mapper: the topic API checks the access token audience.

From a root SSH session, create the environment file with:

```bash
su - glorfeed-runner
cd /opt/glorfeed/infra/prod
umask 077
nano .env
```

Add the database credentials and auth configuration from `.env.example`, then save. Use distinct usernames and keep admin, application, and CDC credentials separate. Single-quote all passwords, especially values containing `$`, `#`, or spaces, so both shell sourcing and Compose parse them literally. The Keycloak client settings are public browser configuration; do not add a client secret. Run `chmod 600 .env` to keep the file readable only by the runner user. The real `.env` is ignored by Git, and deployment does not copy or replace it.

For local frontend development, use the same Keycloak URL, realm, and client ID. Register `http://localhost:5173/` and `http://localhost:5173/silent-check-sso.html` as redirect URIs, `http://localhost:5173/` as the post-logout redirect URI, and `http://localhost:5173` as a web origin. Set the backend `AUTH_ISSUER_URI` to `https://auth.glorfindela.dev/realms/glorfeed` and `AUTH_AUDIENCE` to `glorfeed-topic-service` when running locally.

The topic service validates the access token signature against the configured JWK set, issuer, expiration, not-before time, audience, and nonblank subject. Set `AUTH_ISSUER_URI` and `AUTH_JWK_SET_URI` to matching URLs if the realm is hosted elsewhere. The service uses the verified subject as the immutable owner ID for new topics. Existing topics are retained with a null owner and remain inaccessible through the API. If existing data needs to be claimed, an administrator must first verify each topic's rightful Keycloak subject and then set `owner_id` for those specific topic IDs using that exact `sub` claim; never copy a user-supplied name, email, or request value into `owner_id`.

The frontend and topic-service images must both be deployed for authenticated topic management. The API starts returning 401 for anonymous requests as soon as the backend update is deployed, and the browser requires the Keycloak runtime settings when the frontend update is deployed. Deploy the backend and frontend updates together during rollout.

For the first deployment, create the complete server `.env` described above and ensure the frontend image is published. A normal push to `main` that changes either service, production configuration, or either deployment workflow builds and publishes both topic-service and research-service images before invoking Infra. Infra installs the Compose file, Caddyfile, database initialization script, deployment/provisioning helpers, connector configs, and smoke script, then runs `./deploy.sh`. This guarantees both service images exist before Compose starts them. The workflow preserves the server `.env` and named database/Kafka volumes. Image-only publishing remains available through the topic-service workflow's `deploy` input; Infra is called by the complete-stack orchestrator only.

Deploy Infra runs on the Glorfeed server, installs the task files under `/opt/glorfeed/infra/prod` without replacing `.env`, logs in to GHCR with the workflow's short-lived `GITHUB_TOKEN`, and invokes `./deploy.sh`. The script validates Compose configuration, starts PostgreSQL and Kafka, precreates the fixed research consumer topic, then starts the complete stack and waits for app health endpoints and Flyway-created outbox tables. It idempotently provisions scoped CDC roles, heartbeat rows, table grants, and owner-created filtered publications; reconciles both connectors with repeatable REST `PUT`s; and fails unless each connector's task reaches RUNNING. Caddy is recreated last so its bind-mounted configuration is current. All named data and certificate volumes persist.

[Build and Deploy Frontend](../../.github/workflows/frontend.yml) runs when `frontend/` or its workflow file changes on `main`, or when started manually from `main`. It builds and publishes AMD64 and ARM64 images on GitHub's hosted runner. After the build succeeds, its deployment job runs on the self-hosted runner, pulls the frontend image, and updates only the frontend container using the installed Compose file.

[Build and Deploy Topic Service](../../.github/workflows/topic-service.yml) runs when either backend service, `infra/prod/`, or the deployment workflows change on `main`, or when started manually from `main`. It builds and publishes topic-service and research-service images in parallel on the GitHub-hosted runner, then calls the reusable Infra workflow to apply deployment. Infra serializes production changes with the shared deployment lock, authenticates to GHCR with the short-lived workflow token, and removes its temporary Docker credentials afterward. Frontend-only updates keep their separate service deployment workflow.

Both backend image builds complete before the full-stack deploy starts, and production deployments are serialized by the reusable Infra workflow. No VPS SSH secret is required.

Caddy will request a certificate once the domain resolves to this server and traffic can reach ports 80 and 443. The `caddy_data` volume preserves certificates across container restarts. Future backend services can join the Docker network and get their own Caddy routes.

Both databases are reachable by future services on the `backend` network at `postgres:5432`:

| Service | Database | Credentials | JDBC URL |
| --- | --- | --- | --- |
| topic-service | `glorfeed_topic` | `TOPIC_DB_USER` / `TOPIC_DB_PASSWORD` | `jdbc:postgresql://postgres:5432/glorfeed_topic` |
| research-service | `glorfeed_research` | `RESEARCH_DB_USER` / `RESEARCH_DB_PASSWORD` | `jdbc:postgresql://postgres:5432/glorfeed_research` |

`POSTGRES_USER` / `POSTGRES_PASSWORD` belong to the PostgreSQL administrator. The first-volume initialization script creates application users without superuser, role-management, database-creation, or replication privileges. Repeatable deployment provisioning creates one `REPLICATION` login per database, grants it `CONNECT`, schema `USAGE`, outbox `SELECT`, and heartbeat-row `SELECT`/`UPDATE`, and creates each publication as that database's application owner. Publications contain only the outbox and heartbeat tables; application-table content is not published. Existing databases are never reset or reinitialized during deploy.

PostgreSQL 18 uses the shared `postgres_data` volume mounted at `/var/lib/postgresql`, with data in `/var/lib/postgresql/18/docker`. Logical decoding is enabled, and each replication slot is capped at 1 GiB of retained WAL. If lag exceeds that cap, PostgreSQL may invalidate the slot. Deployment never drops/recreates slots or resets offsets automatically; an operator must inspect state and perform a controlled projection rebuild before resuming. Heartbeat updates advance idle slots but do not remove the finite-retention tradeoff.

Kafka uses one persistent KRaft broker/controller on the private backend network, one partition and replication factor for this PoC, and three-day / 512 MiB per-partition retention. Connect internal topics use replication factor one. These settings keep the PoC bounded more safely on an 8 GiB host, but per-partition retention is not a hard filesystem quota; monitor free disk and the Kafka volume. Do not expose Kafka or Connect publicly. No Kafka UI is included.

Both Debezium connectors use `snapshot.mode=no_data` and begin capturing from their first replication-slot creation. Historical outbox rows, writes accepted before a connector first reaches RUNNING, and existing topic rows without outbox events are not replayed or backfilled. The initial deployment is a cutover: run the CRUD smoke check only after deployment reports both connector tasks RUNNING. If research needs an existing topic, create it again through the authenticated topic API after cutover. Replays or projection rebuilds require a controlled procedure; do not inject an arbitrary old outbox row or delete connector offsets as routine repair.

Research manual paid API calls remain disabled (`RESEARCH_MANUAL_API_ENABLED=false`); no OpenAI key is required or passed to the container. The scheduler remains log-only. Kafka processing and CRUD smoke verification do not call the AI provider.

The [PostgreSQL image](https://github.com/docker-library/docs/blob/master/postgres/README.md#initialization-scripts) runs initialization scripts only when its data directory is empty. Deployment never reruns or edits data through that first-volume script. Editing application `.env` passwords afterward does not change stored database passwords; update those roles deliberately in PostgreSQL. The CDC role/publication provisioning is a separate repeatable procedure for existing data. Container replacements preserve named volumes. This shared setup initializes a new `postgres_data` volume; it does not migrate or delete any earlier `topic_postgres_data` volume.

After deployment, run the repeatable end-to-end check on the server. Obtain a short-lived access token for a test user without placing it in shell history, then enter it without echo:

```bash
cd /opt/glorfeed/infra/prod
read -r -s -p 'Short-lived access token: ' ACCESS_TOKEN
echo
export ACCESS_TOKEN
python3 ./smoke-crud-projection.py
unset ACCESS_TOKEN
```

The script creates a temporary topic through the authenticated API, verifies list/get, updates it, polls the research database until create and update are projected, deletes it, and confirms the projection row disappears. It never prints or persists the token and never calls the paid research endpoint. `API_BASE_URL` and `PROJECTION_TIMEOUT_SECONDS` may override defaults. Confirm anonymous topic API requests return 401 and the frontend loads at the domain root as separate checks.
