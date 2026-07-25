# WarRoom Match — Como usar

## Empresa

1. Entre com magic link e escolha papel **Empresa**.
2. Crie um job (título, descrição, urgência, budget ≥ R$ 200, skills).
3. Aceite uma candidatura.
4. Faça checkout (Stripe ou escrow demo).
5. Quando o trabalho terminar, confirme conclusão — o valor é liberado (82% pro / 18% plataforma).

## Profissional

1. Entre e escolha papel **Pro**.
2. Complete o perfil (skills, disponibilidade).
3. No feed, candidate-se a jobs abertos.
4. Após match e funding, inicie o job e use o chat do job.

## API (resumo)

- `POST /v1/jobs` — criar
- `POST /v1/jobs/{id}/apply` — candidatar
- `POST /v1/jobs/{id}/accept/{applicationId}` — match
- `POST /v1/jobs/{id}/checkout` — escrow
- `POST /v1/jobs/{id}/start` / `complete`

## Demo gratuita

Veja [`FREE.md`](FREE.md).

## Produção

```bash
cp .env.example .env
docker compose -f docker-compose.prod.yml up -d --build
```
