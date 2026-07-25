# WarRoom Match

Marketplace de plantão técnico. Empresas publicam incidentes ou prazos críticos; profissionais vetted aceitam o job; pagamento fica em escrow e a plataforma retém a taxa.

## Stack (previsto)

- Java 21, Spring Boot 3, PostgreSQL 16
- Painel Next.js (empresa + profissional)
- Stripe Connect / Checkout para escrow e take rate

## Estrutura

```
apps/api   — matching, jobs, escrow, billing
apps/web   — painéis empresa e profissional
docs/      — especificação e plano
```

## Gitflow

- `feature/*` → PR para `develop`
- `release/*` → PR para `master`
- Branches mescladas são removidas (local e remoto)

## Pacote base

`br.com.ricarte.warroom`
