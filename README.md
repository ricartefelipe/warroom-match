# WarRoom Match

Marketplace de plantão técnico. Empresas publicam incidentes ou prazos críticos; profissionais aprovados pelo suporte se candidatam; a empresa paga pelo provedor (Stripe) ou em modo demonstração explicitamente sinalizado; a plataforma fica com 18% e o repasse ao profissional só conta como feito quando o provedor confirma a transferência. Não há custódia regulada nem escrow jurídico: o que existe está descrito em [`docs/OPERACAO.md`](docs/OPERACAO.md).

Guia: [`docs/USAGE.md`](docs/USAGE.md)

## Stack

- Java 21, Spring Boot 4, PostgreSQL 16, Liquibase
- Painel Next.js (empresa + profissional)
- Pagamentos: modo `demo` (simulado, só fora de produção) ou `stripe` (Checkout + Transfer + Refund)
- Take rate 18% (`WARROOM_PLATFORM_FEE_BPS`)

## Estrutura

```
apps/api   — matching, jobs, pagamentos, vetting, disputas, chat, reviews
apps/web   — painéis empresa e profissional
deploy/    — Caddy
docs/      — uso, operação (dinheiro, vetting, disputas), demo gratuita
scripts/   — demo gratuita, prova fail-closed, jornada demo
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
- Modo financeiro local: `demo` (banner no painel). Conta de suporte: `WARROOM_ADMIN_EMAILS`.

## Demo gratuita

```bash
./scripts/free-demo.sh
```

Painel: `http://localhost:9082` — detalhes em [`docs/FREE.md`](docs/FREE.md).
O login usa magic link nativo. O TotalRecall não é modo de login do produto.

## Testes e CI

```bash
cd apps/api && mvn verify        # Testcontainers PostgreSQL 16; Docker obrigatório
cd apps/web && npm ci && npx tsc --noEmit && npm run build
```

O workflow `ci` roda testes da API com banco real, type check e build do painel, scan de dependências e
das imagens finais (HIGH/CRITICAL bloqueiam), a prova de que produção sem configuração financeira não
sobe (`scripts/prove-fail-closed.sh`) e a jornada demo com três atores contra a imagem final
(`scripts/demo-journey.sh`).

## Produção

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Exige `WARROOM_DB_PASSWORD`, `WARROOM_DOMAIN`, `WARROOM_ADMIN_EMAILS`, `STRIPE_API_KEY` e
`STRIPE_WEBHOOK_SECRET`; o modo demo é recusado. Onboarding de profissionais na Stripe (Connect),
contrato e homologação do provedor são do proprietário e ficam fora deste repositório.

## Gitflow

- `feature/*` → PR para `develop`
- `release/*` → PR para `master`
- Branches mescladas são removidas

## Pacote base

`br.com.ricarte.warroom`
