# WarRoom Match — Como usar

## Empresa

1. Entre com magic link e escolha papel **Empresa**.
2. Crie um job (título, descrição, urgência, budget ≥ R$ 200, skills, prazo de resposta opcional).
3. Aceite uma candidatura (só profissionais aprovados aparecem como candidatos).
4. Pague: no modo Stripe você é levado ao checkout e o job só fica `funded` quando o webhook confirma;
   no modo demo o pagamento é simulado e marcado como tal.
5. Acompanhe `in_progress` e `delivered`; aceite a entrega para concluir. O repasse (82%) é
   **solicitado** nesse momento e aparece como confirmado só quando o provedor confirma.
6. Em caso de problema, abra disputa; o suporte decide entre repasse e devolução.

## Profissional

1. Entre e escolha papel **Pro**.
2. Envie as evidências de verificação e aguarde a aprovação do suporte.
3. Complete o perfil (skills, disponibilidade).
4. No feed, candidate-se a jobs abertos.
5. Após o match e o pagamento confirmado, inicie o job, use o chat e marque como entregue.
6. Avalie a empresa depois da conclusão.

## Suporte

Conta com papel `admin` (`WARROOM_ADMIN_EMAILS`); opera pela API (`/v1/admin/*`): fila de vetting,
disputas, pagamentos pendentes, reconciliação e suspensão. Detalhes em [`OPERACAO.md`](OPERACAO.md).

## API (resumo)

- `GET /v1/config` — modo financeiro e ambiente (público)
- `POST /v1/jobs` — criar; `GET /v1/jobs?scope=open|mine` — listar; `GET /v1/jobs/{id}`
- `POST /v1/jobs/{id}/apply` — candidatar (exige vetting aprovado)
- `POST /v1/jobs/{id}/accept/{applicationId}` — match
- `POST /v1/jobs/{id}/checkout` — pagamento (`url` no modo Stripe)
- `POST /v1/jobs/{id}/start` / `deliver` / `complete` / `cancel` / `dispute`
- `GET|POST /v1/jobs/{id}/messages` — chat após o match
- `POST /v1/jobs/{id}/reviews` — após `completed`
- `POST /v1/me/vetting` — evidências; `GET|PUT /v1/me/profile`; `POST /v1/me/role`
- `POST /v1/billing/stripe/webhook` — eventos assinados da Stripe

## Demo gratuita

Veja [`FREE.md`](FREE.md).

## Produção

```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```
