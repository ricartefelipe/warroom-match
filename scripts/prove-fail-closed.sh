#!/usr/bin/env bash
# Prova que a API recusa iniciar em produção sem configuração financeira (modo demo) e em modo
# stripe sem as chaves. Uso: ./scripts/prove-fail-closed.sh <imagem>
set -euo pipefail
IMAGE="${1:?informe a imagem da API}"

expect_refusal() { # expect_refusal <descrição> <env...>
  local label="$1"; shift
  local name="warroom-failclosed-$$"
  local logs
  set +e
  docker run --name "$name" "$@" "$IMAGE" >/dev/null 2>&1
  local code=$?
  logs="$(docker logs "$name" 2>&1 | tail -40)"
  docker rm -f "$name" >/dev/null 2>&1
  set -e
  if [ "$code" -eq 0 ]; then
    printf 'FALHA: %s subiu (esperava recusa)\n' "$label" >&2
    exit 1
  fi
  if ! printf '%s' "$logs" | grep -qE "warroom.billing.mode"; then
    printf 'FALHA: %s falhou por outro motivo:\n%s\n' "$label" "$logs" >&2
    exit 1
  fi
  printf 'ok %s -> recusou iniciar (%s)\n' "$label" "$(printf '%s' "$logs" | grep -oE 'warroom.billing.mode[^"]*' | head -1)"
}

expect_refusal "produção em modo demo" -e WARROOM_ENVIRONMENT=production -e WARROOM_BILLING_MODE=demo \
  -e WARROOM_DB_URL=jdbc:postgresql://db-inexistente:5432/x
expect_refusal "modo stripe sem chaves" -e WARROOM_ENVIRONMENT=production -e WARROOM_BILLING_MODE=stripe \
  -e WARROOM_DB_URL=jdbc:postgresql://db-inexistente:5432/x
expect_refusal "modo stripe só com a chave da API" -e WARROOM_ENVIRONMENT=local -e WARROOM_BILLING_MODE=stripe \
  -e STRIPE_API_KEY=sk_test_x -e WARROOM_DB_URL=jdbc:postgresql://db-inexistente:5432/x
echo "fail-closed ok"
