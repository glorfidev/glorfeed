# Glorfeed frontend

React, TypeScript, and Vite frontend. Topic management uses the topic-service API. Feed stories and their topic labels are sample preview content held in browser memory; they are separate from live topics and do not represent research for those topics.

To run it locally:

```bash
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite. Opening `index.html` as a `file://` page will not load the TypeScript application.

The Vite development server proxies `/api` requests to `http://localhost:8080`. Start topic-service locally as well to load, create, edit, and delete live topics. The sample feed remains preview content and is not linked to the live topic records.

The frontend loads public sign-in settings from `/auth-config.json` at runtime. Development uses `public/auth-config.json`; the production Nginx image writes this file at container startup from `KEYCLOAK_URL`, `KEYCLOAK_REALM`, and `KEYCLOAK_CLIENT_ID`. The defaults point to the Glorfeed Keycloak realm and public frontend client. Tokens are held in memory and sent only as bearer tokens to the same-origin topic API.

Each live topic has an explicit **Run research** action. It sends one authenticated request to the research service, which uses the server-configured `OPENAI_API_KEY`; the browser never receives that key. A request may incur OpenAI charges, is not automatically retried, and its report and validated HTTP(S) citations appear in the dialog. Manual research must be enabled and configured on the server; see [production deployment setup](../infra/prod/README.md). The sample feed remains sample content and is not replaced by live research.

Run `npm test` for mocked sign-in and topic-management tests and `npm run build` for TypeScript checking and a production Vite build. The tests stub Keycloak and topic API responses; they do not require Keycloak or a running backend.

The production image is built by [frontend.yml](../.github/workflows/frontend.yml) for AMD64 and ARM64 and served by Nginx inside the container. Caddy is the public HTTPS reverse proxy; see [production deployment](../infra/prod/README.md).
