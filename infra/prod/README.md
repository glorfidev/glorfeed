# Glorfeed production infrastructure

This Compose stack runs the frontend, topic-service, research-service, Caddy, PostgreSQL, single-node Kafka, and Debezium Connect. Topic-service and research-service each have a separate database and application login inside PostgreSQL. Each database also has its own restricted CDC login. Kafka and Connect have no public listener; Connect's REST API is published only on server loopback at `127.0.0.1:18083`. Caddy publishes ports 80 and 443. Application services, PostgreSQL, Kafka, and Connect use private Docker networks.

Before the first deployment:

1. Add a DNS A record for `feed.glorfindela.dev` pointing to the new Glorfeed server. Add an AAAA record only if its IPv6 address is configured and reachable.
2. In the repository's **Settings → Actions → Runners**, add a self-hosted **Linux ARM64** runner on the new Glorfeed server. Follow GitHub's generated installation commands as a dedicated non-root user, give it the custom label `glorfeed-prod`, and install/start its service. The runner needs outbound HTTPS access to GitHub; it does not need inbound SSH from GitHub. As root on the server, install Python 3 for connector registration and `curl` for optional manual status checks with `apt-get update && apt-get install -y python3 curl`. Connector registration uses only Python's standard library; no `pip` packages or `jq` are required.
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

For a new server, create the complete server `.env` described above, install the required commands, then publish the frontend, topic-service, and research-service images by manually running each service workflow on `main` with **Deploy after building** disabled. After those images are available, manually run **Deploy Infra** on `main` to install the stack and perform the initial setup. Existing published images do not need to be republished just to deploy infrastructure.

After setup, each service workflow builds and deploys only its own service when its source or workflow changes. A push that changes `infra/prod/**` or `.github/workflows/infra.yml` independently runs full Infra setup using the already-published images; it installs the current Compose and connector configuration, provisions CDC, reconciles connectors, and refreshes Caddy while preserving the server `.env` and named data volumes. Full Infra can also be started manually on `main`.

Full Infra deployment runs on the Glorfeed server in named workflow steps. It checks that `python3` is installed, then installs files under `/opt/glorfeed/infra/prod` without replacing `.env`. The startup step logs in to GHCR with a short-lived `GITHUB_TOKEN`, validates Compose configuration, explicitly pulls the already-published topic-service and research-service images, waits for PostgreSQL and Kafka, creates the fixed research consumer topic, and starts the full stack with Compose's health wait. It then checks both application health endpoints from Connect. A separate step sources `.env` only for CDC provisioning and connector reconciliation, using the Python standard-library helper to reconcile connector configuration and verify that both connector tasks reach RUNNING. The final step recreates Caddy so its bind-mounted configuration is current. Temporary Docker credentials are removed after startup, and all named data and certificate volumes persist.

[Build and Deploy Frontend](../../.github/workflows/frontend.yml), [Build and Deploy Topic Service](../../.github/workflows/topic-service.yml), and [Build and Deploy Research Service](../../.github/workflows/research-service.yml) each run for their own source and workflow changes on `main`, or when started manually from `main`. Each workflow builds and publishes its own AMD64 and ARM64 image on a GitHub-hosted runner, then deploys only that service on the self-hosted runner. A manual run can disable **Deploy after building** to publish without deploying. [Deploy Infra](../../.github/workflows/infra.yml) runs separately for infrastructure changes or manual setup and applies the full stack from images already published by the service workflows.

These workflows share a production deployment queue, which serializes deployments but does not order them by dependency. For a change that requires coordinated application and infrastructure rollout, deploy backward-compatible infrastructure first in a separate commit, then deploy the application changes. For a new server, publish images only and run Deploy Infra after all required images are available. No VPS SSH secret is required.

Caddy will request a certificate once the domain resolves to this server and traffic can reach ports 80 and 443. The `caddy_data` volume preserves certificates across container restarts. Future backend services can join the Docker network and get their own Caddy routes.

Both databases are reachable by future services on the `backend` network at `postgres:5432`:

| Service | Database | Credentials | JDBC URL |
| --- | --- | --- | --- |
| topic-service | `glorfeed_topic` | `TOPIC_DB_USER` / `TOPIC_DB_PASSWORD` | `jdbc:postgresql://postgres:5432/glorfeed_topic` |
| research-service | `glorfeed_research` | `RESEARCH_DB_USER` / `RESEARCH_DB_PASSWORD` | `jdbc:postgresql://postgres:5432/glorfeed_research` |

`POSTGRES_USER` / `POSTGRES_PASSWORD` belong to the PostgreSQL administrator. The first-volume initialization script creates application users without superuser, role-management, database-creation, or replication privileges. Repeatable deployment provisioning creates one `REPLICATION` login per database, grants it `CONNECT`, schema `USAGE`, outbox `SELECT`, and heartbeat-row `SELECT`/`UPDATE`, and creates each publication as that database's application owner. Publications contain only the outbox and heartbeat tables; application-table content is not published. Existing databases are never reset or reinitialized during deploy.

PostgreSQL 18 uses the shared `postgres_data` volume mounted at `/var/lib/postgresql`, with data in `/var/lib/postgresql/18/docker`. Logical decoding is enabled, and each replication slot is capped at 1 GiB of retained WAL. If lag exceeds that cap, PostgreSQL may invalidate the slot. Deployment never drops/recreates slots or resets offsets automatically; an operator must inspect state and perform a controlled projection rebuild before resuming. Heartbeat updates advance idle slots but do not remove the finite-retention tradeoff.

Kafka uses one persistent KRaft broker/controller on the private backend network, one partition and replication factor for this PoC, and three-day / 512 MiB per-partition retention. Connect internal topics use replication factor one. These settings keep the PoC bounded more safely on an 8 GiB host, but per-partition retention is not a hard filesystem quota; monitor free disk and the Kafka volume. Do not expose Kafka or Connect publicly. No Kafka UI is included.

Both Debezium connectors use `snapshot.mode=no_data` and begin capturing from their first replication-slot creation. Historical outbox rows, writes accepted before a connector first reaches RUNNING, and existing topic rows without outbox events are not replayed or backfilled. The initial deployment is a cutover: verify topic changes through the research database only after deployment reports both connector tasks RUNNING. If research needs an existing topic, create it again through the authenticated topic API after cutover. Replays or projection rebuilds require a controlled procedure; do not inject an arbitrary old outbox row or delete connector offsets as routine repair.

Research manual paid API calls remain disabled (`RESEARCH_MANUAL_API_ENABLED=false`); no OpenAI key is required or passed to the container. The scheduler remains log-only. Kafka processing and CRUD projection verification do not call the AI provider.

The [PostgreSQL image](https://github.com/docker-library/docs/blob/master/postgres/README.md#initialization-scripts) runs initialization scripts only when its data directory is empty. Deployment never reruns or edits data through that first-volume script. Editing application `.env` passwords afterward does not change stored database passwords; update those roles deliberately in PostgreSQL. The CDC role/publication provisioning is a separate repeatable procedure for existing data. Container replacements preserve named volumes. This shared setup initializes a new `postgres_data` volume; it does not migrate or delete any earlier `topic_postgres_data` volume.

After deployment reports both connector tasks RUNNING, create, edit, and delete a test topic through the website. Check the connector task states from the server with:

```bash
cd /opt/glorfeed/infra/prod
for connector in glorfeed-topic-outbox glorfeed-research-outbox; do
  curl --fail --silent "http://127.0.0.1:18083/connectors/$connector/status" \
    | python3 -m json.tool
done

docker compose exec -T postgres sh -c \
  'PGPASSWORD="$RESEARCH_DB_PASSWORD" exec psql --no-psqlrc --quiet -h 127.0.0.1 -U "$RESEARCH_DB_USER" -d glorfeed_research -c "SELECT id, name, description, source_event_id FROM public.research_topic ORDER BY name;"'
```

The SQL query should show the newly created or edited topic and no longer show it after deletion. Confirm anonymous topic API requests return 401 and the frontend loads at the domain root as separate checks.
