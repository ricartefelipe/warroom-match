# WarRoom Match — Design Spec

**Date:** 2026-07-25  
**Status:** Ready for implementation planning  
**Mission:** Two-sided marketplace for urgent technical plantão: companies post critical jobs, vetted pros accept, escrow releases payment, platform takes 15–20%.

## 1. Product

### Promise

Incidente ou prazo crítico? Encontre um profissional vetted agora — pagamento seguro, taxa só quando o job acontece.

### Problem

Contratar plantão de arquiteto/backend em emergência é lento (indicação, WhatsApp, risco de no-show). Freelas bons não têm canal confiável de demanda urgente com pagamento garantido.

### ICP

- **Demanda:** times de produto/engenharia em PME/SaaS BR com incidente, go-live ou prazo de auditoria.
- **Oferta:** seniores/arquitetos backend, plataforma, dados — perfil público vetted.

### Success metric (MVP)

Uma empresa publica um job, um profissional aceita, escrow é criado, job é marcado concluído e fundos são liberados (take rate retido) — em ambiente demo, sem operação humana no fluxo feliz.

## 2. Scope

### In MVP

1. Contas com papel `company` ou `pro` (magic link; expose em demo)
2. Perfil pro: skills, senioridade, disponibilidade (`available|busy|offline`), bio curta, taxa horária sugerida
3. Job post (empresa): título, descrição, urgência (`p1|p2|p3`), budget (centavos BRL), skills pedidas, prazo de resposta
4. Matching simples: lista pros `available` com overlap de skills; empresa convida OU pro se candidata (open apply no MVP)
5. Aceite: um pro aceita → job `matched`; demais candidaturas canceladas
6. Escrow Stripe:
   - Empresa paga o budget (Checkout/PaymentIntent)
   - Status: `funded` → trabalho → `completed` (empresa confirma) → release para pro (liquidez Connect ou payout manual simulado se Connect não configurado)
   - Plataforma retém **18%** (`platformFeeBps=1800`)
7. Chat mínimo: mensagens texto no job (polling), sem WebSocket no MVP
8. Reviews: nota 1–5 + comentário curto após conclusão
9. Painel Next.js: dual mode (empresa / pro)
10. Demo gratuita: Docker + túnel Cloudflare

### Out of MVP

- Vetting humano/KYC completo (MVP: self-declare + badge `unverified`; admin flag `vetted` via env/seed)
- SLA contracts, NDAs, time tracking automático
- Assinatura “fila prioritária” para empresas
- Push/SMS/WhatsApp
- Dispute flow completo (MVP: status `disputed` + hold escrow, resolução manual fora do sistema)
- Mobile apps

## 3. Approaches considered

1. **Classificados sem escrow** — rápido, sem confiança de pagamento.  
2. **Escrow + take rate (recomendado)** — monetiza sozinho; alinha incentivos.  
3. **Só assinatura de acesso à rede** — receita previsível, mas fraca sem liquidez de jobs.

**Decisão:** (2) com Stripe Checkout no MVP; Connect Express quando keys presentes; modo demo grava escrow “simulado” se Stripe vazio.

## 4. Architecture

```
Company/Pro ──session──► API (Spring Boot)
                           ├─ accounts, profiles, jobs, applications
                           ├─ messages, reviews
                           ├─ escrow_ledger + Stripe
                           └─ Postgres
Web (Next.js) ── same-origin /v1
```

- Monólito modular; pacote `br.com.ricarte.warroom`
- Estados de job: `draft|open|matched|funded|in_progress|completed|cancelled|disputed`
- Escrow: `pending|held|released|refunded`

## 5. API surface (MVP)

| Method | Path | Role | Notes |
|--------|------|------|-------|
| POST | `/v1/auth/magic-link` | public | |
| POST | `/v1/auth/verify` | public | |
| GET/PUT | `/v1/me/profile` | pro/company | |
| POST | `/v1/jobs` | company | cria open |
| GET | `/v1/jobs` | both | lista própria / open feed |
| GET | `/v1/jobs/{id}` | party | |
| POST | `/v1/jobs/{id}/apply` | pro | candidatura |
| POST | `/v1/jobs/{id}/accept/{applicationId}` | company | match |
| POST | `/v1/jobs/{id}/checkout` | company | Stripe / demo fund |
| POST | `/v1/jobs/{id}/start` | pro | in_progress |
| POST | `/v1/jobs/{id}/complete` | company | release escrow |
| GET/POST | `/v1/jobs/{id}/messages` | party | |
| POST | `/v1/jobs/{id}/reviews` | party | após completed |
| POST | `/v1/billing/stripe/webhook` | Stripe | |

## 6. Pricing

- Take rate padrão: **18%** do budget
- Pro recebe 82% no release
- Sem mensalidade no MVP
- Job mínimo budget: R$ 200,00 (20000 centavos)

## 7. Security & trust

- Session bearer; roles enforced
- Só participantes do job leem mensagens
- Stripe webhook assinatura
- Demo: `WARROOM_DEMO_ESCROW=true` libera fund/release sem Stripe
- LGPD: apagar conta apaga perfil e anonimiza mensagens

## 8. Tech choices

| Layer | Choice |
|-------|--------|
| API | Java 21, Spring Boot 3.3.1 |
| DB | PostgreSQL 16 + Liquibase |
| Web | Next.js 15 |
| Payments | Stripe Checkout + Connect (optional) / demo escrow |
| Auth | Magic link |
| Deploy demo | docker-compose.free + cloudflared |

## 9. Cold-start mitigation (MVP)

- Seed de 3 pros demo + 1 empresa demo (só em profile `demo`)
- Feed público de jobs `open` (sem dados sensíveis da descrição completa até login)
- Pros podem setar `available` e receber matching por skill tags

## 10. Risks

| Risk | Mitigation |
|------|------------|
| Sem liquidez | Seed + demo script; foco em um nicho (backend/platform) |
| No-show | Review + status; disputa hold |
| Stripe Connect complexidade | Demo escrow path sempre disponível |
| Escopo creep de chat | Polling texto only |

## 11. Delivery plan (high level)

1. Foundation: schema, auth, roles, profiles  
2. Jobs + apply/accept + feed  
3. Escrow (demo + Stripe) + complete/release  
4. Messages + reviews  
5. Web dual UI + free demo + release  

## 12. Non-goals for v1

HookGuard/FileNorm features, full staffing ATS, video interviews.
