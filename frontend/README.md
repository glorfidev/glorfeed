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

Run `npm test` for the mocked topic-management UI tests and `npm run build` for TypeScript checking and a production Vite build. The tests stub the topic API responses; they do not require a running backend.

The production image is built by [frontend.yml](../.github/workflows/frontend.yml) for AMD64 and ARM64 and served by Nginx inside the container. Caddy is the public HTTPS reverse proxy; see [production deployment](../infra/prod/README.md).
