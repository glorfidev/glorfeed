# Frontend on the Glorfeed server

This Compose stack runs only the frontend and Caddy. The frontend container serves the compiled React files on Docker's internal network; Caddy publishes ports 80 and 443 and handles HTTPS. It does not expose a Vite development server.

Before the first deployment:

1. Add a DNS A record for `feed.glorfindela.dev` pointing to the new Glorfeed server. Add an AAAA record only if its IPv6 address is configured and reachable.
2. In the repository's **Settings → Actions → Runners**, add a self-hosted **Linux ARM64** runner on the new Glorfeed server. Follow GitHub's generated installation commands as a dedicated non-root user, give it the custom label `glorfeed-prod`, and install/start its service. The runner needs outbound HTTPS access to GitHub; it does not need inbound SSH from GitHub.
3. Give that runner user write access to `/opt/glorfeed/infra/prod` and permission to run `docker compose`. For example, after creating the runner user, run `sudo install -d -o <runner-user> -g <runner-user> /opt/glorfeed/infra/prod` and `sudo usermod -aG docker <runner-user>`, substituting its actual name. Restart the runner service after changing group membership. Docker access grants extensive control over the host, so reserve this runner for this repository and deployment. Check that the repository is private before installing a runner on the production server; GitHub advises against production self-hosted runners for public repositories.
4. Allow inbound TCP 80 and 443 in the server firewall for Caddy. The Caddyfile uses `feed.glorfindela.dev`, and Compose pulls `ghcr.io/glorfidev/glorfeed-frontend:latest`.

After the runner is online, use [Deploy Infra](../../.github/workflows/infra.yml) to install the initial Compose file and Caddyfile. The frontend image must already be published in GHCR for the first startup. This workflow runs when `infra/prod/` or its workflow file changes on `main`, and it can be started manually from `main`.

Deploy Infra runs on the Glorfeed server, copies the Compose file and Caddyfile to `/opt/glorfeed/infra/prod`, logs in to GHCR using the workflow's short-lived `GITHUB_TOKEN`, applies the Compose configuration, and reloads Caddy. It does not build images or pull new versions of images already present on the server.

[Build and Deploy Frontend](../../.github/workflows/frontend.yml) runs when `frontend/` or its workflow file changes on `main`, or when started manually from `main`. It builds and publishes AMD64 and ARM64 images on GitHub's hosted runner. After the build succeeds, its deployment job runs on the self-hosted runner, pulls the frontend image, and updates only the frontend container using the installed Compose file.

The workflows run independently. Production changes are serialized so they cannot apply simultaneously. Configuration-only changes do not rebuild the frontend image. No VPS SSH secret is required.

Caddy will request a certificate once the domain resolves to this server and traffic can reach ports 80 and 443. The `caddy_data` volume preserves certificates across container restarts. Future backend services can join the Docker network and get their own Caddy routes.
