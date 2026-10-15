# Deploy topic management

## Goal

Deploy the existing topic-service on the new Glorfeed server using the shared PostgreSQL instance. Serve its existing topic CRUD endpoints at `https://feed.glorfindela.dev/api/topics` while preserving the frontend at the same domain.

## Scope

- Use one PostgreSQL 18.1 container with separate admin, topic, and research credentials. Preserve the existing initialization script, named storage, logical WAL, and absence of public database ports.
- Add `topic-service` to production Compose using `ghcr.io/glorfidev/glorfeed-topic-service:latest`. Set `TOPIC_DB_URL=jdbc:postgresql://postgres:5432/glorfeed_topic` and use the topic credentials from the server `.env`.
- Rely on topic-service's existing Compose `depends_on` health condition to wait for PostgreSQL before starting topic-service. The frontend can start independently. Attach topic-service to both the `backend` and `web` networks, without publishing its application port.
- Add a Caddy route for both `/api/topics` and `/api/topics/*`, preserving the full request path. Other paths continue to reach the frontend.
- Extend the existing topic-service workflow into Build and Deploy Topic Service, following the frontend workflow structure. Publish AMD64/ARM64 images with `latest` and commit-SHA tags on main; deploy only after a successful build, using the `glorfeed-prod` self-hosted runner.
- Keep production deployment serialized with the existing `glorfeed-production-deploy` concurrency group. Authenticate to GHCR with the job's temporary token and clean up its temporary Docker configuration on success or failure.
- Infra remains responsible for copying Compose, Caddy, and database initialization files. Make it reusable with `workflow_call` while retaining manual main-only dispatch. Remove its independent push trigger so it cannot race topic image publication.
- Topic workflow owns automatic production configuration changes as well as topic code/workflow changes. After a successful image build, call the reusable Infra workflow as its deployment job. Preserve the manual `deploy` boolean for optional publish-only runs.
- Infra applies the stack with Compose's existing health dependency, which waits for PostgreSQL before starting topic-service while allowing the frontend to start independently, then recreates Caddy last. Preserve its shared production concurrency lock, and do not add the same lock to the calling job (which would deadlock).
- Explain that production configuration changes now also build the topic image before applying infrastructure. Frontend-only updates keep their separate service deployment. Server `.env` remains a prerequisite; no manual publish-only bootstrap is necessary for normal first deployment.
- Recreate Caddy after installing its Caddyfile so the single-file bind mount reads the new file. Preserve the named certificate and configuration volumes.
- Correct the README's claim that Compose never pulls existing image tags: `latest` has special pull behavior. Keep the correction limited to documentation unless another change is necessary.

## Boundaries

- Work on `feature/ordered-topic-deployment`, based on current `origin/main`; the original implementation was merged in PR #1 from `feature/topic-service-deployment`. Preserve existing staged and unstaged work.
- Do not modify frontend functionality, research-service, local infrastructure, or topic business logic.
- Do not start Kafka, Debezium, Graylog, or research-service in production.
- Keep real credentials only in `/opt/glorfeed/infra/prod/.env`; do not write, print, copy, or commit a real `.env`.
- Follow AGENTS.md: Luna may perform relevant local verification and may commit/push task-scoped changes and update the draft PR. For this correction, complete Sol review before publication. Preserve unrelated staged changes in all commits.
- Do not run Docker or server/deployment commands, or trigger, monitor, or inspect GitHub Actions runs. Runtime verification and deployment remain with the user.

## Acceptance criteria

1. PostgreSQL retains separate service databases and credentials on persistent storage.
2. Topic-service connects as its own non-admin database user; Flyway applies its existing migrations at application startup.
3. Caddy forwards topic CRUD paths unchanged and preserves frontend routing.
4. Topic workflow builds on GitHub-hosted infrastructure and deploys on the server only for main, with no production deployment from this feature branch.
5. Documentation describes required `.env` values, first-deployment ordering, empty-volume-only initialization, and manual API checks after deployment.

## Manual checks after the user deploys

Use the topic API to create a temporary topic, list and retrieve it, update it, and delete it. Confirm the topic persists across application/container replacement, and confirm the frontend still loads. These checks are described here, not executed by the implementing agent.
