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
| `web/` | Next.js + TypeScript frontend |
| `docs/` | Design, plans, decisions |

## Status

| Stage | What | State |
|---|---|---|
| 0 | Monorepo, PostgreSQL, Spring Boot skeleton | ✅ |
| 1 | Product catalog API (products + versions), validation, RFC 9457 errors | ✅ 19 integration tests |
| 2 | Identity & security (sessions, invites, API keys, multi-tenant) | next |
| 3–9 | SBOM ingest, vulnerability matching, Go CLI, reporting + audit, web, DevOps | planned |

## Local development

```bash
cp .env.example .env        # set POSTGRES_PASSWORD
docker compose up -d        # PostgreSQL 17 on localhost:5432
cd api && ./mvnw verify     # all tests (needs Docker: Testcontainers)
./mvnw spring-boot:run      # API on http://localhost:8080
```

### Live demo

With the API running, from the repo root:

```bash
bash scripts/demo.sh
```

It registers an IoT product, shows validation (400), duplicate detection (409), firmware versions, the protected delete of compliance records (409) and a 404 — all as RFC 9457 problem details.

### API (catalog)

| Method | Path | Result |
|---|---|---|
| `POST` | `/api/products` | 201 + `Location` · 400 invalid · 409 duplicate name (case-insensitive) |
| `GET` | `/api/products` | 200, sorted by name |
| `GET` | `/api/products/{id}` | 200 · 404 |
| `PUT` | `/api/products/{id}` | 200 · 400 · 404 · 409 |
| `DELETE` | `/api/products/{id}` | 204 · 404 · 409 if it has versions |
| `POST` | `/api/products/{id}/versions` | 201 · 400 · 404 · 409 duplicate version |
| `GET` | `/api/products/{id}/versions` | 200, newest first · 404 |

## License

[Business Source License 1.1](LICENSE) — free for non-production and non-commercial use; converts to Apache 2.0 four years after each release.
