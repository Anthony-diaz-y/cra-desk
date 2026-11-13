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

## Local development

```bash
cp .env.example .env        # set POSTGRES_PASSWORD
docker compose up -d        # PostgreSQL 17 on localhost:5432
```

## License

[Business Source License 1.1](LICENSE) — free for non-production and non-commercial use; converts to Apache 2.0 four years after each release.
