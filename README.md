# CRA Desk

Compliance desk for the **EU Cyber Resilience Act** (Regulation (EU) 2024/2847), built for small manufacturers of hardware, IoT, firmware and installable software.

- Upload a CycloneDX SBOM per product version (web or `cradesk` CLI / GitHub Action)
- Continuous matching against known vulnerabilities (OSV) and CISA KEV
- Human-confirmed reportable events with the Art. 14 clocks: 24 h early warning, 72 h notification, 14 day final report
- AI-drafted, human-approved report drafts and a tamper-evident audit trail

> CRA Desk is a support tool, not legal advice.

## Repository layout

| Path | What |
|---|---|
| `api/` | Spring Boot (Java 21) modular monolith + PostgreSQL |
| `cli/` | `cradesk` CLI in Go |
| `web/` | Next.js 16 web app: landing page, sign-in and the app (Tailwind, shadcn/ui, Motion, TanStack Query) |
| `docs/` | Design, plans, decisions |

## Status

| Stage | What | State |
|---|---|---|
| 0 | Monorepo, PostgreSQL, Spring Boot skeleton | ✅ |
| 1 | Product catalog API (products + versions), validation, RFC 9457 errors | ✅ |
| 2 | Identity & security: session login, invitations, read-only demo, API keys, per-organization isolation, waitlist | ✅ |
| 3 | SBOM ingest: CycloneDX JSON 1.4–1.6 per product version, validation, size limits, components by purl | ✅ |
| 4 | Vulnerability monitoring: OSV matching, CISA KEV catalog, findings to review, scan queue and daily rescan | ✅ |
| 5 | `cradesk` Go CLI (login, upload, wait, fail on KEV) and GitHub Action | ✅ 175 API tests + Go tests |
| 6 | Reporting: finding review, CRA 24 h / 72 h / 14 d clocks, AI or template drafts, approval, sent to ENISA, JSON/Markdown export; hash-chained audit log | ✅ 244 API tests |
| 7 | Web: animated landing page, read-only demo, products and SBOM upload, findings review, live CRA clocks, report editor, audit log, API keys; strict nonce CSP | ✅ 62 web tests + 2 end-to-end flows |
| 8–9 | DevOps, polish | planned |

## Local development

```bash
cp .env.example .env        # set POSTGRES_PASSWORD and CRADESK_ADMIN_TOKEN (e.g. openssl rand -hex 32)
docker compose up -d        # PostgreSQL 17 on 127.0.0.1:5432
cd api && ./mvnw verify     # all tests (needs Docker: Testcontainers)

# Start the API on http://localhost:8080 (it reads ../.env by itself)
./mvnw spring-boot:run

# In another terminal: the web app on http://localhost:3000 (proxies /api to the API)
cd web && npm install && npm run dev
```

| Variable | Purpose | Default |
|---|---|---|
| `POSTGRES_PASSWORD` / `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` | Database connection | local compose database |
| `CRADESK_ENV_FILE` | Optional properties file the API imports for local development; real environment variables take precedence, and tests skip it | `../.env` |
| `CRADESK_ADMIN_TOKEN` | Platform admin token (`X-Admin-Token`), at least 32 characters; empty disables admin endpoints | empty |
| `CRADESK_DEMO_ENABLED` | Seed the read-only demo account on startup | `true` |
| `SESSION_COOKIE_SECURE` | Send the session cookie only over HTTPS (set `true` in production) | `false` |
| `CRADESK_SBOM_MAX_BYTES` | Largest accepted SBOM upload, in bytes | `5242880` (5 MB) |
| `CRADESK_OSV_BASE_URL` | OSV API | `https://api.osv.dev` |
| `CRADESK_KEV_URL` | CISA Known Exploited Vulnerabilities JSON feed | CISA's official feed |
| `CRADESK_SCAN_SCHEDULER_ENABLED` | Run the scan queue (every 30 s), the daily rescan (03:00 UTC) and the startup KEV load | `true` |
| `CRADESK_AI_PROVIDER` | Draft generator: `template` (no AI), `openai-compatible` (e.g. DeepSeek) or `claude` | `template` |
| `CRADESK_AI_BASE_URL` | Base URL for `openai-compatible` | `https://api.deepseek.com` |
| `CRADESK_AI_MODEL` | Model name; empty uses `deepseek-chat` or `claude-haiku-5-5` | empty |
| `CRADESK_AI_API_KEY` | Key for `openai-compatible` (keep it in `.env`, never in git) | empty |
| `ANTHROPIC_API_KEY` | Key for `claude` | empty |
| `CRADESK_AI_DAILY_LIMIT` | AI drafts per organization per UTC day; then 429 (`?ai=false` still works) | `20` |
| `CRADESK_API_URL` | Where the web app proxies `/api/*` (read when the web app is built and started) | `http://localhost:8080` |

### Live demo

With the API running, from the repo root:

```bash
set -a && . ./.env && set +a && bash scripts/demo.sh
```

It opens the read-only demo (writes are 403), invites a manufacturer with the admin token, accepts the invitation, registers a product and a firmware version, creates an API key, uploads a CycloneDX SBOM with the `cradesk` CLI as a CI pipeline would and waits for the vulnerability scan against the real OSV and CISA KEV services, shows the CI gate failing with exit code 3 because Log4Shell is in KEV, reads the SBOM, vulnerabilities and findings, shows an SPDX file being rejected (400), revokes the key (401) and sends a "Request access" form (202). It also confirms the Log4Shell finding, which opens a reportable event with its three CRA clocks, drafts the 24 h early warning (AI if configured, otherwise the template), approves it, marks it sent to ENISA with a reference, exports it as Markdown and verifies the audit chain.

The demo account comes with a confirmed Log4Shell event on Smart Door Lock 1.1.0: three sample drafts, the early warning already sent on time, and its audit entries.

## SBOM ingest

- One CycloneDX JSON SBOM (spec 1.4, 1.5 or 1.6) per product version; uploading again replaces it atomically.
- The body is read with a hard 5 MB cap (not trusting `Content-Length`), at most 20 000 components and 16 nesting levels.
- Every component needs a name; a `purl`, when present, must be a valid package URL. All errors come back together with their JSON path.
- Nested components are flattened, `metadata.component` (the product itself) is skipped, exact duplicates are dropped; components without a purl are kept and counted.
- The original document and its SHA-256 are kept for the technical file (CRA Annex VII) and can be downloaded.

```bash
curl -X PUT "$API/api/products/$PRODUCT/versions/$VERSION/sbom" \
  -H "Authorization: Bearer $CRADESK_API_KEY" \
  -H 'Content-Type: application/vnd.cyclonedx+json' --data-binary @bom.json
```

## Vulnerability monitoring

- Every SBOM upload queues a scan; a scheduler drains the queue every 30 s and rescans every version daily at 03:00 UTC. Uploads never wait for external services.
- Components with a versioned purl are matched against [OSV](https://osv.dev) (batches of 1000, paginated results, vulnerability details cached and refetched only when modified).
- The [CISA KEV](https://www.cisa.gov/known-exploited-vulnerabilities-catalog) catalog is refreshed daily and replaced atomically; a failed download keeps the previous one.
- A vulnerability in KEV (by its id or any CVE alias) creates a **finding to review** — it does not open a reportable event or start the CRA clocks; a person does that (stage 6). Findings are never deleted: one that disappears after an SBOM fix is marked absent.
- If OSV or KEV fail, the scan is `FAILED` with the reason and nothing is half-written.

## CLI and GitHub Action

`cradesk` is a single static binary (Go, standard library only) for CI pipelines.

```bash
cd cli && go build -o cradesk ./cmd/cradesk        # or: VERSION=0.1.0 bash scripts/build-cli.sh (all platforms)

echo "$CRADESK_API_KEY" | cradesk login --api-url https://cradesk.example.com
cradesk upload --product "Smart Lock" --version 1.4.0 --file bom.json --fail-on-kev
```

- In CI, skip `login` and set `CRADESK_API_URL` and `CRADESK_API_KEY`; they override the saved login.
- The key is read from stdin, never from a flag, and saved with `0600` permissions.
- `upload` creates the version if it does not exist (`--no-create-version` to forbid it); `--wait` waits for the OSV + KEV scan and prints a KEV-first table; `--fail-on-kev` implies `--wait`.

| Exit code | Meaning |
|---|---|
| 0 | OK |
| 1 | Usage or input error (flags, missing file, no login) |
| 2 | API or network error (message from the API's problem details) |
| 3 | Actively exploited (CISA KEV) vulnerability found and `--fail-on-kev` set |
| 4 | Scan failed or did not finish within `--timeout` |

GitHub Action, after generating the SBOM (for example with `anchore/sbom-action`):

```yaml
- uses: Anthony-diaz-y/cra-desk/cli@main
  with:
    api-url: https://cradesk.example.com
    api-key: ${{ secrets.CRADESK_API_KEY }}
    product: Smart Lock
    version: ${{ github.ref_name }}
    file: bom.json
    fail-on-kev: "true"
```

Prebuilt binaries for Linux, macOS and Windows (amd64/arm64) are attached to GitHub releases tagged `cli-v*`.

## Reporting

- **Review:** a member confirms a finding (opens a reportable event) or dismisses it with a reason. Confirming starts three CRA Article 14 clocks: early warning in 24 h, notification in 72 h, final report in 14 days. Each report shows `PENDING`, `DUE_SOON` (last 25 % of its window), `OVERDUE`, `SENT_ON_TIME` or `SENT_LATE`.
- **Drafts:** generated by the configured AI provider, or by a deterministic template. The AI never blocks the work: if it fails, the template draft is returned with `aiError`. The AI call runs outside any database transaction, and AI drafts are capped per organization per day.
- **Human in the loop:** drafts are edited, approved by a member, and marked sent by a member; editing an approved draft takes the approval away. CRA Desk does not submit anything to ENISA; it prepares the text and keeps the record. It is a support tool, not legal advice.
- **Audit:** every review, draft, edit, approval, sent mark, SBOM upload and API key change is written to an append-only log (a database trigger rejects `UPDATE` and `DELETE`). Each organization has its own SHA-256 hash chain; `GET /api/audit/verify` recomputes it and points to the first altered entry.

## Web

The web app lives in `web/` and is served on the same origin as the API: Next.js rewrites `/api/*` to `CRADESK_API_URL`, so the session cookie and CSRF token work without CORS.

- `/` landing page with a live 24-hour countdown, the three Article 14 deadlines, "Try the demo" and "Request access".
- `/login`, `/invite?code=…` (create the company and account from an invitation).
- `/app` deadlines with live clocks · `/app/products` products, versions, drag-and-drop SBOM upload, components and vulnerabilities (KEV first) · `/app/findings` confirm or dismiss · `/app/events/{id}` report editor (AI or template draft, edit, approve, mark sent, export) · `/app/audit` log and chain verification · `/app/settings` API keys.
- The demo session sees everything; write actions are disabled.

```bash
cd web
npm test              # unit and component tests (Vitest + Testing Library)
npm run typecheck && npm run lint && npm run build
bash ../scripts/e2e.sh   # end-to-end with Playwright (needs Docker)
```

`scripts/e2e.sh` recreates a separate `cradesk_e2e` database, starts a fake OSV + CISA KEV server, the API (port 8081) and the production web build (port 3100), and runs two flows: a visitor exploring the demo, and an invited manufacturer going from SBOM upload to an approved, sent and exported early warning with a valid audit chain. It never calls OSV, CISA or an AI provider.

## Security model

- **Browser:** JSON login with an HttpOnly, SameSite=Lax session cookie (8 h); CSRF via the `XSRF-TOKEN` cookie and the `X-XSRF-TOKEN` header (logout clears the token: call `GET /api/auth/csrf` again). Five failed logins lock an account for 15 minutes; unknown email and wrong password get the same answer.
- **CLI:** `Authorization: Bearer crd_<prefix>_<secret>`. Only the SHA-256 of a key is stored; keys are shown once and can be revoked.
- **Tenancy:** every catalog query is scoped to the caller's organization; another organization's ids return 404.
- **Accounts:** created only through single-use, 7-day invitations issued by the platform admin. Passwords are BCrypt-hashed.
- **Demo:** a read-only role; every write is 403.
- **Web:** a fresh nonce per request in a strict Content-Security-Policy (`script-src 'self' 'nonce-…' 'strict-dynamic'`, no inline or eval scripts in production, `frame-ancestors 'none'`), plus `nosniff`, `Referrer-Policy` and `Permissions-Policy`. No tokens in browser storage; a new API key is shown once and kept only in memory. The login `next` parameter only accepts app paths.

## API

All errors are RFC 9457 problem details. "Session" means a signed-in member; "key" means a CLI API key.

| Method | Path | Who | Result |
|---|---|---|---|
| `GET` | `/api/auth/csrf` | anyone | 204, sets `XSRF-TOKEN` |
| `POST` | `/api/auth/login` | anyone | 204 · 401 |
| `POST` | `/api/auth/demo` | anyone | 204 (read-only session) · 404 if disabled |
| `POST` | `/api/auth/accept-invitation` | anyone with a code | 201 · 400 invalid/expired code · 409 email taken |
| `GET` | `/api/auth/me` | signed in | 200 · 401 |
| `POST` | `/api/auth/logout` | signed in | 204 |
| `POST` | `/api/access-requests` | anyone | 202 (also for an email already on the list) · 400 |
| `POST` | `/api/admin/invitations` | `X-Admin-Token` | 201 with one-time code · 401 · 409 |
| `GET` | `/api/admin/access-requests` | `X-Admin-Token` | 200 · 401 |
| `POST` | `/api/api-keys` | session | 201 with the key, shown once |
| `GET` | `/api/api-keys` | session | 200 (no secrets) |
| `DELETE` | `/api/api-keys/{id}` | session | 204 · 404 |
| `POST` | `/api/products` | session, key | 201 + `Location` · 400 · 409 duplicate name in the organization |
| `GET` | `/api/products` | session, demo, key | 200, sorted by name |
| `GET` | `/api/products/{id}` | session, demo, key | 200 · 404 |
| `PUT` | `/api/products/{id}` | session, key | 200 · 400 · 404 · 409 (duplicate name or stale `rowVersion`) |
| `DELETE` | `/api/products/{id}` | session, key | 204 · 404 · 409 if it has versions |
| `POST` | `/api/products/{id}/versions` | session, key | 201 · 400 · 404 · 409 duplicate version |
| `GET` | `/api/products/{id}/versions` | session, demo, key | 200, newest first · 404 |
| `GET` | `/api/products/{id}/versions/{versionId}` | session, demo, key | 200 · 404 |
| `PUT` | `/api/products/{id}/versions/{versionId}/sbom` | session, key | raw CycloneDX JSON body: 201 new · 200 replaced · 400 with `errors` · 404 · 413 over 5 MB · 415 not JSON |
| `GET` | `/api/products/{id}/versions/{versionId}/sbom` | session, demo, key | 200 summary · 404 |
| `GET` | `/api/products/{id}/versions/{versionId}/sbom/components?page=&size=` | session, demo, key | 200 page sorted by name (size 1–200) · 400 · 404 |
| `GET` | `/api/products/{id}/versions/{versionId}/sbom/document` | session, demo, key | 200 original file · 404 |
| `GET` | `/api/products/{id}/versions/{versionId}/vulnerabilities` | session, demo, key | 200 scan status + vulnerabilities, KEV first · 404 without SBOM |
| `POST` | `/api/products/{id}/versions/{versionId}/scan` | session, key | 202 rescan queued · 404 without SBOM |
| `PUT` | `/api/sbom?product=&version=&createVersion=true` | session, key | upload by product and version **name** (CLI): 201 · 200 · 400 · 404 unknown product · 413 · 415 |
| `GET` | `/api/findings?status=PENDING_REVIEW\|CONFIRMED\|DISMISSED` | session, demo, key | 200 the organization's findings with that status |
| `POST` | `/api/findings/{id}/confirm` | session | 201 the new reportable event with its three clocks · 404 · 409 already reviewed |
| `POST` | `/api/findings/{id}/dismiss` | session | `{"reason"}` (10–1000 chars): 200 · 400 · 404 · 409 |
| `GET` | `/api/events` · `/api/events/{id}` | session, demo, key | 200 events with report clocks · 404 |
| `GET` | `/api/events/{id}/reports/{type}` | session, demo, key | 200 report (`EARLY_WARNING`, `NOTIFICATION`, `FINAL_REPORT`) · 404 |
| `POST` | `/api/events/{id}/reports/{type}/draft?ai=true\|false` | session | 200 draft · 409 already sent · 429 daily AI limit |
| `PUT` | `/api/events/{id}/reports/{type}` | session | `{"content":{…}}`: 200 (back to `DRAFTED`) · 400 unknown or missing fields · 409 already sent |
| `POST` | `/api/events/{id}/reports/{type}/approve` | session | 200 · 409 nothing drafted or already sent |
| `POST` | `/api/events/{id}/reports/{type}/sent` | session | `{"sentAt"?, "enisaReference"?}`: 200 · 400 future or before confirmation · 409 not approved or already sent |
| `GET` | `/api/events/{id}/reports/{type}/export?format=json\|markdown` | session, demo, key | 200 file download · 409 not approved |
| `GET` | `/api/audit?page=&size=` | session, demo | 200 audit entries, newest first (size 1–200) |
| `GET` | `/api/audit/verify` | session, demo | 200 `{valid, entries, firstInvalidSeq}` |

## License

[Business Source License 1.1](LICENSE) — free for non-production and non-commercial use; converts to Apache 2.0 four years after each release.
