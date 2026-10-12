# Frontend on the Glorfeed server

This Compose stack runs only the frontend and Caddy. The frontend container serves the compiled React files on Docker's internal network; Caddy publishes ports 80 and 443 and handles HTTPS. It does not expose a Vite development server.

Before starting it:

1. Choose a domain such as `feed.your-domain.com`. Add a DNS A record pointing it to the new Glorfeed server. Add an AAAA record only if its IPv6 address is configured and reachable.
2. Push the frontend changes to `main` so [the workflow](../../.github/workflows/frontend.yml) publishes `ghcr.io/<owner>/glorfeed-frontend`. The workflow builds both AMD64 and ARM64 variants for the CAX21 server.
3. Copy this directory to `/opt/glorfeed/infra/prod` on the server. Create `.env` there from `.env.example` and set `GHCR_OWNER`, `GLORFEED_DOMAIN`, and preferably `FRONTEND_TAG` to the published commit SHA. Do not commit `.env`.
4. If the GHCR package is private, authenticate Docker on the server with a GitHub token that has `read:packages`. A public package needs no login.
5. With TCP 80 and 443 allowed by the server firewall, run from that directory:

```bash
docker compose pull
docker compose up -d
```

Caddy will request a certificate once the domain resolves to this server and traffic can reach ports 80 and 443. The `caddy_data` volume preserves certificates across container restarts. Future backend services can join the Docker network and get their own Caddy routes.
