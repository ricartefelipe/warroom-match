# Changelog

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/). Tags: `vX.Y.Z` (a tag
`v0.1.0` é a primeira publicação).

## [0.2.0] - 2026-10-30

### Added

- Modo financeiro explícito (`WARROOM_BILLING_MODE=demo|stripe`) e ambiente (`WARROOM_ENVIRONMENT`):
  produção recusa demo; stripe exige as duas chaves. `GET /v1/config` e banner no painel.
- Estados reais do pagamento (`checkout_created`, `paid`, `payout_requested`, `released`,
  `refund_requested`, `refunded`, `disputed`, `expired`, `failed`), eventos duráveis e reconciliação.
- Webhook Stripe: assinatura obrigatória, id de evento único, verificação de `payment_status`, valor,
  moeda e sessão; `async_payment_succeeded/failed`, `expired`, `charge.refunded`, `charge.dispute.created`.
- Adaptador Stripe com Transfer (Connect) e Refund idempotentes; gateway demo separado.
- Vetting com evidências, decisão do suporte e histórico; só aprovados candidatam-se ou são aceitos.
- Disputas com decisão humana; cancelamento com devolução; entrega pelo profissional (`delivered`).
- Papel `admin` por configuração; endpoints `/v1/admin/*`; suspensão de contas.
- Lock otimista em jobs e índice único de candidatura aceita; magic link de uso único atômico;
  limite de pedidos de magic link.
- Chat só após o match entre empresa, profissional aceito e suporte (leitura), com limite e sanitização.
- CI real (testes com PostgreSQL via Testcontainers, scans, imagens, prova fail-closed, jornada demo).

### Changed

- Spring Boot 3.3.1 -> 4.0.9; Testcontainers 2; imagens Alpine sem HIGH/CRITICAL; Next standalone sem npm.
- Respostas da API alinhadas ao painel (`id`, `skillsNeeded` como lista, `applications`,
  `myApplicationStatus`, `payment`, `dispute`). Rotas `escrow` deixam de existir.
- Tabela `escrow_payments` renomeada para `job_payments`; `profiles.vetted` vira `vetting_status`.

### Removed

- `WARROOM_DEMO_ESCROW` e o fallback para demo quando a Stripe não estava configurada.
- A promessa de "escrow" e "retenção" sem mecanismo: a documentação descreve o fluxo real do provedor.
