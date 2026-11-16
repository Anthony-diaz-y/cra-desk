# CRA Desk — web

Next.js 16 (App Router) app for CRA Desk. See the [main README](../README.md#web) for what it does and how it fits with the API.

```bash
npm install
npm run dev          # http://localhost:3000, with the API on http://localhost:8080
npm test             # Vitest + Testing Library
npm run typecheck
npm run lint
npm run build
```

Set `CRADESK_API_URL` if the API is not on `http://localhost:8080`. End-to-end tests run from the repo root with `bash scripts/e2e.sh`.
