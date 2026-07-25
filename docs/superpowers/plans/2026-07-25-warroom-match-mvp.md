# WarRoom Match MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a two-sided plantão marketplace MVP: roles, jobs, apply/accept, escrow (demo + Stripe), messages, reviews, dual Next.js UI, free tunnel demo.

**Architecture:** Spring Boot monolith + Postgres; session auth with company/pro roles; escrow ledger with optional Stripe Checkout; Next.js dual dashboard; Cloudflare free demo.

**Tech Stack:** Java 21, Spring Boot 3.3.1, Liquibase, PostgreSQL 16, Next.js 15, Stripe Java 26.12.0, Docker, Caddy, cloudflared.

## Global Constraints

- Package: `br.com.ricarte.warroom`
- Gitflow feature→develop, release→master; delete merged branches
- No AI attribution; minimal code comments
- Take rate 18%; min budget 20000 centavos; demo escrow when Stripe empty
- Portuguese UI copy; user-facing agent replies in PT

## File map

```
apps/api/   — auth, profiles, jobs, escrow, messages, reviews
apps/web/   — login, company jobs, pro feed, job room
deploy/     — Caddyfile(+.free)
docker-compose*.yml
scripts/free-demo.sh
docs/USAGE.md, docs/FREE.md
```

---

### Task 1: Foundation

- [ ] Compose Postgres + Mailpit (ports 5435 / 18027 to avoid clashes)
- [ ] Schema: accounts, sessions, login_tokens, profiles, jobs, applications, escrow_payments, messages, reviews
- [ ] Magic link auth + role selection on first login
- [ ] Profile GET/PUT
- [ ] Tests green
- [ ] Commit `feature/mvp-foundation`

### Task 2: Jobs + matching

- [ ] CRUD/list jobs; apply; accept; state machine
- [ ] Open feed for pros
- [ ] Integration test happy path to matched
- [ ] Commit

### Task 3: Escrow + complete

- [ ] Demo fund/release when `WARROOM_DEMO_ESCROW=true` or Stripe empty
- [ ] Stripe Checkout + webhook when configured
- [ ] start / complete / fee split 18%
- [ ] Commit

### Task 4: Messages + reviews + web

- [ ] Message poll API
- [ ] Reviews after completed
- [ ] Next.js dual UI + same-origin
- [ ] Commit

### Task 5: Free demo + release

- [ ] docker-compose.free + script port 9082
- [ ] USAGE/FREE/README
- [ ] PR → develop → release 0.1.0 → tag

## Verification

```bash
cd apps/api && mvn test
cd apps/web && npm run build
./scripts/free-demo.sh
```
