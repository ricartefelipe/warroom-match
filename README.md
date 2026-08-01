# WarRoom Match

Marketplace de plantão técnico. Empresas publicam incidentes ou prazos críticos; profissionais vetted aceitam o job; pagamento fica em escrow e a plataforma retém 18%.

Guia: [`docs/USAGE.md`](docs/USAGE.md)

## Stack

- Java 21, Spring Boot 3, PostgreSQL 16
- Painel Next.js (empresa + profissional)
- Escrow demo ou Stripe Checkout
- Take rate 18%

## Estrutura

```
apps/api   — matching, jobs, escrow, chat, reviews
apps/web   — painéis empresa e profissional
deploy/    — Caddy
docs/      — especificação, plano e uso
```

## Desenvolvimento local

```bash
docker compose up -d
cp .env.example .env
cd apps/api && mvn spring-boot:run
cd apps/web && npm install && npm run dev
```

- API: `http://localhost:8080`
- Painel: `http://localhost:3000`
- Postgres: `localhost:5435`
- Mailpit: `http://localhost:18027`

## Demo gratuita

```bash
./scripts/free-demo.sh
```

Painel: `http://localhost:9082` — detalhes em [`docs/FREE.md`](docs/FREE.md).
O login por senha usa o sistema TotalRecall `warroom-match`; configure `TOTALRECALL_URL=https://54.94.163.136.sslip.io`.

## Produção

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

## Gitflow

- `feature/*` → PR para `develop`
- `release/*` → PR para `master`
- Branches mescladas são removidas

## Pacote base

`br.com.ricarte.warroom`
