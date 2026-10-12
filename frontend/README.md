# Glorfeed frontend

React, TypeScript, and Vite frontend. The feed, topics, saved stories, and dialogs currently use mock data held in browser memory. They do not call the backend yet.

To run it locally:

```bash
cd frontend
npm install
npm run dev
```

Open the URL printed by Vite. Opening `index.html` as a `file://` page will not load the TypeScript application. `npm install` also generates the lockfile for the new React dependencies.

The production image is built by [frontend.yml](../.github/workflows/frontend.yml) for AMD64 and ARM64 and served by Nginx inside the container. Caddy is the public HTTPS reverse proxy; see [production deployment](../infra/prod/README.md).
