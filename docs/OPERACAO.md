# Operação: dinheiro, vetting, disputas e suporte

Este documento descreve o que o sistema faz de verdade com o dinheiro e com a confiança, e o que fica
fora dele. Nada aqui afirma custódia regulada, escrow jurídico ou homologação com o provedor.

## Modos financeiros

| `WARROOM_BILLING_MODE` | O que acontece | Onde é permitido |
|---|---|---|
| `demo` | Todo pagamento, repasse e devolução é simulado na hora. A API devolve `demo: true` em `/v1/config` e em cada pagamento; o painel mostra o banner "MODO DEMONSTRAÇÃO". Os registros ficam marcados com `provider = demo`. | Só com `WARROOM_ENVIRONMENT=local`. Em `production` a API **recusa iniciar**. |
| `stripe` | Checkout Session (modo `payment`) para receber; Transfer (Stripe Connect) para repassar; Refund para devolver. Exige `STRIPE_API_KEY` e `STRIPE_WEBHOOK_SECRET`; sem um deles a API **recusa iniciar**. | local e production |

A ausência de chave nunca vira demo. `scripts/prove-fail-closed.sh <imagem>` prova as três recusas.

## Estados do pagamento (`payment.status`)

| Estado | Significa | Quem muda |
|---|---|---|
| `checkout_created` | sessão de pagamento criada; nada foi cobrado | checkout da empresa |
| `paid` | o provedor confirmou que os fundos entraram na conta da plataforma | webhook verificado (`checkout.session.completed` ou `async_payment_succeeded` com `payment_status=paid`, valor, moeda e sessão iguais aos registrados) |
| `payout_requested` | a empresa concluiu o job; a transferência ao profissional foi pedida, não confirmada | conclusão / decisão de disputa |
| `released` | o provedor confirmou a transferência (id em `transferId`) | resposta do provedor ou reconciliação |
| `refund_requested` | devolução pedida, não confirmada | cancelamento com pagamento, decisão de disputa, `charge.refunded` |
| `refunded` | o provedor confirmou a devolução (`refundId`) | resposta do provedor ou reconciliação |
| `disputed` | disputa aberta por uma das partes ou chargeback (`charge.dispute.created`); bloqueia repasse | participantes, provedor |
| `expired` / `failed` | sessão expirou ou pagamento assíncrono falhou; um novo checkout pode ser criado | webhook |

`released` e `refunded` só existem com confirmação remota. No modo Stripe, um profissional **sem conta
conectada** (`stripe_account_id` no perfil) deixa o pagamento em `payout_requested` com
`failureReason = pro_not_onboarded`; o onboarding (Connect, KYC, contrato) é feito pelo proprietário
diretamente na Stripe e está fora deste repositório.

Taxa: `platform_fee = floor(amount × bps / 10000)`; o profissional recebe o restante; as partes sempre
somam o valor. Valores em centavos inteiros, limite de 10^13 centavos.

Todo evento (webhook, ação da plataforma, reconciliação) fica em `payment_events`, com o `event id` do
provedor único: um webhook repetido é respondido com `duplicate` e não altera nada. Um evento tardio
que não cabe na máquina de estados é registrado como `*_ignored`.

## Estados do job

`open → matched → funded → in_progress → delivered → completed`, com `cancelled` a partir de
`open/matched/funded` e `disputed` a partir de `funded/in_progress/delivered`. `completed` e
`cancelled` são finais. Duas aceitações simultâneas: lock otimista no job + índice único de
candidatura aceita por job; a segunda recebe `409 concurrent_update`.

Prazo (`responseDeadlineAt`): depois dele, candidaturas e aceites recebem `409 deadline_passed`; a
empresa cancela e republica. Não há liberação automática de dinheiro por ausência de resposta: um
job `funded` ou `in_progress` sem andamento é resolvido por cancelamento (devolução) ou disputa.

## Vetting

- `POST /v1/me/vetting {evidence}` — o profissional envia texto (links, referências, resumo). Documentos
  de identidade não são aceitos nem pedidos por este canal.
- `GET /v1/admin/vetting/pending`, `POST /v1/admin/vetting/{accountId} {decision: approved|rejected|suspended, note}`,
  `GET /v1/admin/vetting/{accountId}/history` — suporte decide; cada decisão vai para `vetting_events`.
- Só `approved` candidata-se ou é aceito (`403 pro_not_vetted`). Suspender um profissional com
  candidatura pendente impede o aceite. `POST /v1/admin/accounts/{id}/suspend` desativa a conta e
  derruba as sessões.

## Disputas e cancelamento

- `POST /v1/jobs/{id}/dispute {reason}` por empresa ou profissional em `funded/in_progress/delivered`:
  job e pagamento ficam `disputed`; conclusão e repasse ficam bloqueados.
- `GET /v1/admin/disputes`, `POST /v1/admin/disputes/{jobId}/resolve {outcome: release|refund, note}`:
  decisão humana registrada em `disputes`; `release` conclui o job e pede o repasse, `refund` cancela
  o job e pede a devolução.
- Cancelamento pela empresa: livre em `open/matched`; em `funded` pede a devolução integral.
- Chargeback no provedor abre disputa automaticamente.

Política antes de contratar: não comparecimento ou entrega parcial → disputa; divergência de escopo →
chat e, se necessário, disputa; devolução só após decisão do suporte ou cancelamento antes do início.

## Chat e acesso

- O chat abre depois do match e só entre empresa, profissional aceito e suporte (leitura). Outros
  candidatos nunca o veem. Mensagens: até 4000 caracteres, sem caracteres de controle. Não há anexos.
- Nunca peça credenciais de produção pelo chat; combine um cofre de segredos ou acesso temporário
  nomeado. A responsabilidade pelo acesso concedido é da empresa.
- Jobs, candidaturas e avaliações são visíveis só a quem participa; outra empresa recebe `403`.

## Sessão e magic link

- Magic link de uso único (o consumo é um `DELETE` condicional; verificações concorrentes recebem
  `401`), validade 20 min, sessão de 30 dias com `logout`. Limite de pedidos de link: 5 por e-mail e
  60 por endereço a cada 15 min (`429 too_many_requests`), em memória por processo.
- Papéis `company` e `pro` são escolhidos uma vez; `admin` só por `WARROOM_ADMIN_EMAILS`.

## Reconciliação e suporte

- `GET /v1/admin/payments/pending` lista pagamentos com operação remota pendente.
- `POST /v1/admin/payments/{paymentId}/reconcile` consulta o provedor (PaymentIntent, Transfer, Refund)
  e avança só o que ele confirmou. Rode diariamente em produção e após qualquer timeout.
- `GET /v1/admin/payments/{paymentId}/events` mostra a trilha completa.
- Nunca edite `job_payments` à mão: use reconciliação ou decisão de disputa.

## Backup e restauração

```bash
docker compose exec postgres pg_dump -U warroom -d warroom -Fc > warroom-$(date +%F).dump
docker compose exec -T postgres pg_restore -U warroom -d warroom --no-owner < warroom-YYYY-MM-DD.dump
```

`scripts/demo-journey.sh <imagem>` executa a jornada completa com três atores e valida pela API que o
histórico financeiro sobrevive a um `pg_dump`/`pg_restore`.

## Sandbox Stripe e homologação

O adaptador Stripe segue a API oficial (Checkout Sessions, Transfers, Refunds, webhooks assinados) e é
exercitado na suíte com o verificador de assinatura real e um gateway substituto para as chamadas de
rede. A execução contra o sandbox real exige chaves de teste e um endpoint de webhook do proprietário;
não foi executada neste repositório e não há homologação afirmada. Checkout Session sozinha não é
custódia: o dinheiro fica na conta Stripe da plataforma até a Transfer.
