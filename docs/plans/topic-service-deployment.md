# Deploy topic management

## Goal

Deploy the existing topic-service on the new Glorfeed server using the shared PostgreSQL instance. Serve its existing topic CRUD endpoints at `https://feed.glorfindela.dev/api/topics` while preserving the frontend at the same domain.

## Scope

- Use one PostgreSQL 18.1 container with separate admin, topic, and research credentials. Preserve the existing initialization script, named storage, logical WAL, and absence of public database ports.
- Add `topic-service` to production Compose using `ghcr.io/glorfidev/glorfeed-topic-service:latest`. Set `TOPIC_DB_URL=jdbc:postgresql://postgres:5432/glorfeed_topic` and use the topic credentials from the server `.env`.
- Wait for PostgreSQL's health check before starting topic-service. Attach the service to both the `backend` and `web` networks, without publishing its application port.
- Add a Caddy route for both `/api/topics` and `/api/topics/*`, preserving the full request path. Other paths continue to reach the frontend.
- Extend the existing topic-service workflow into Build and Deploy Topic Service, following the frontend workflow structure. Publish AMD64/ARM64 images with `latest` and commit-SHA tags on main; deploy only after a successful build, using the `glorfeed-prod` self-hosted runner.
- Keep production deployment serialized with the existing `glorfeed-production-deploy` concurrency group. Authenticate to GHCR with the job's temporary token and clean up its temporary Docker configuration on success or failure.
- Infra remains responsible for copying Compose, Caddy, and database initialization files. Topic deployment uses those installed files, pulls the topic image, and starts topic-service with its PostgreSQL dependency. Explain first-deployment ordering in the README.
- Allow a manual image-only topic build for initial setup. After the image is published and server `.env` is configured, run Infra before normal topic deployments.
- Recreate Caddy after installing its Caddyfile so the single-file bind mount reads the new file. Preserve the named certificate and configuration volumes.
- Correct the README's claim that Compose never pulls existing image tags: `latest` has special pull behavior. Keep the correction limited to documentation unless another change is necessary.

## Boundaries

- Work on `feature/topic-service-deployment`; preserve existing staged and unstaged work.
- Do not modify frontend functionality, research-service, local infrastructure, or topic business logic.
- Do not start Kafka, Debezium, Graylog, or research-service in production.
- Keep real credentials only in `/opt/glorfeed/infra/prod/.env`; do not write, print, copy, or commit a real `.env`.
- Do not run tests, builds, Maven, Gradle, npm, Docker, server commands, or long-running verification. Do not stage, commit, push, trigger, monitor, or inspect GitHub Actions runs.
- Review is static only for this implementation. Runtime verification and deployment remain with the user.

## Acceptance criteria

1. PostgreSQL retains separate service databases and credentials on persistent storage.
2. Topic-service connects as its own non-admin database user; Flyway applies its existing migrations at application startup.
3. Caddy forwards topic CRUD paths unchanged and preserves frontend routing.
4. Topic workflow builds on GitHub-hosted infrastructure and deploys on the server only for main, with no production deployment from this feature branch.
5. Documentation describes required `.env` values, first-deployment ordering, empty-volume-only initialization, and manual API checks after deployment.

## Manual checks after the user deploys

Use the topic API to create a temporary topic, list and retrieve it, update it, and delete it. Confirm the topic persists across application/container replacement, and confirm the frontend still loads. These checks are described here, not executed by the implementing agent.
