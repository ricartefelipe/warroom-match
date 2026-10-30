#!/usr/bin/env bash
# Jornada honesta no modo demo contra a imagem final e PostgreSQL real: login real de três atores
# (empresa, profissional, suporte) por magic link, vetting, job, pagamento simulado, entrega,
# conclusão, repasse simulado e avaliação. Uso: ./scripts/demo-journey.sh <imagem-api>
set -euo pipefail
IMAGE="${1:?informe a imagem da API}"
NET="warroom-demo-$$"; DB="warroom-demo-db-$$"; API_C="warroom-demo-api-$$"; MAIL="warroom-demo-mail-$$"
PORT="${DEMO_PORT:-18090}"; API="http://127.0.0.1:${PORT}"
cleanup() { docker rm -f "$API_C" "$DB" "$MAIL" >/dev/null 2>&1 || true; docker network rm "$NET" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker network create "$NET" >/dev/null
docker run -d --name "$DB" --network "$NET" -e POSTGRES_DB=warroom -e POSTGRES_USER=warroom -e POSTGRES_PASSWORD=warroom \
  --health-cmd "pg_isready -U warroom -d warroom" --health-interval 2s --health-retries 30 postgres:16-alpine >/dev/null
for _ in $(seq 1 40); do [[ "$(docker inspect -f '{{.State.Health.Status}}' "$DB")" == healthy ]] && break; sleep 2; done
# O magic link é enviado por e-mail de verdade; um Mailpit descartável recebe as mensagens.
docker run -d --name "$MAIL" --network "$NET" axllent/mailpit:v1.28 >/dev/null
docker run -d --name "$API_C" --network "$NET" -p "127.0.0.1:${PORT}:8080" \
  -e WARROOM_DB_URL="jdbc:postgresql://${DB}:5432/warroom" -e WARROOM_ENVIRONMENT=local -e WARROOM_BILLING_MODE=demo \
  -e WARROOM_EXPOSE_MAGIC_LINK=true -e WARROOM_ADMIN_EMAILS=support@warroom.test -e WARROOM_SMTP_HOST="$MAIL" -e WARROOM_SMTP_PORT=1025 \
  "$IMAGE" >/dev/null
for _ in $(seq 1 60); do curl -fsS "$API/actuator/health" >/dev/null 2>&1 && break; sleep 2; done
curl -fsS "$API/actuator/health" >/dev/null || { docker logs "$API_C" | tail -30; exit 1; }

STATUS_FIELD="d['status']"
j() { # j <expressão python sobre d>
  local expression="$1"
  python3 -c 'import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1]))' "$expression"
}
login() { # login <email>
  local email="$1" link token
  link=$(curl -fsS -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"name\":\"$email\"}" "$API/v1/auth/magic-link" | j "d['magicLink']")
  token="${link#*token=}"
  curl -fsS -H 'Content-Type: application/json' -d "{\"token\":\"$token\"}" "$API/v1/auth/verify" | j "d['sessionToken']"
}
call() { # call <token> <method> <path> [body]
  local token="$1" method="$2" path="$3" body="${4:-}"
  curl -fsS -X "$method" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' ${body:+-d "$body"} "$API$path"
}
code() { # code <token> <method> <path> [body]
  local token="$1" method="$2" path="$3" body="${4:-}"
  curl -sS -o /dev/null -w '%{http_code}' -X "$method" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' ${body:+-d "$body"} "$API$path"
}
expect() { # expect <esperado> <obtido> <descrição>
  local expected="$1" actual="$2" label="$3"
  if [[ "$expected" != "$actual" ]]; then
    printf 'FALHA %s: esperado %s, obtido %s\n' "$label" "$expected" "$actual" >&2
    exit 1
  fi
  printf 'ok %s -> %s\n' "$label" "$actual"
}

echo "## config pública"
curl -fsS "$API/v1/config"; echo
COMPANY=$(login empresa@example.com); PRO=$(login pro@example.com); ADMIN=$(login support@warroom.test)
call "$COMPANY" POST /v1/me/role '{"role":"company"}' >/dev/null
call "$PRO" POST /v1/me/role '{"role":"pro"}' >/dev/null
expect admin "$(call "$ADMIN" GET /v1/auth/me | j "d['role']")" "suporte vem da configuração"
PRO_ID=$(call "$PRO" GET /v1/auth/me | j "d['accountId']")
JOB=$(call "$COMPANY" POST /v1/jobs '{"title":"Incidente em produção","description":"API fora do ar","urgency":"p1","budgetCents":50000,"skillsNeeded":"java,spring"}' | j "d['id']")
expect 403 "$(code "$PRO" POST /v1/jobs/$JOB/apply '{"pitch":"eu"}')" "pro sem vetting não se candidata"
call "$PRO" POST /v1/me/vetting '{"evidence":"github.com/pro, 8 anos"}' >/dev/null
expect 1 "$(call "$ADMIN" GET /v1/admin/vetting/pending | j "len(d)")" "fila de vetting"
call "$ADMIN" POST /v1/admin/vetting/$PRO_ID '{"decision":"approved","note":"ok"}' >/dev/null
APP=$(call "$PRO" POST /v1/jobs/$JOB/apply '{"pitch":"Posso começar agora"}' | j "d['id']")
expect 403 "$(code "$PRO" GET /v1/jobs/$JOB/messages)" "chat fechado antes do match"
expect matched "$(call "$COMPANY" POST /v1/jobs/$JOB/accept/$APP | j "$STATUS_FIELD")" "match"
CHECKOUT=$(call "$COMPANY" POST /v1/jobs/$JOB/checkout)
expect True "$(printf '%s' "$CHECKOUT" | j "d['demo']")" "checkout sinalizado como demo"
expect paid "$(printf '%s' "$CHECKOUT" | j "$STATUS_FIELD")" "pagamento simulado"
expect 409 "$(code "$COMPANY" POST /v1/jobs/$JOB/checkout)" "duplo checkout não cobra"
call "$COMPANY" POST /v1/jobs/$JOB/messages '{"body":"Acesso pelo cofre de segredos; nada de senha aqui."}' >/dev/null
expect 1 "$(call "$PRO" GET /v1/jobs/$JOB/messages | j "len(d)")" "chat entre participantes"
expect in_progress "$(call "$PRO" POST /v1/jobs/$JOB/start | j "$STATUS_FIELD")" "início"
expect delivered "$(call "$PRO" POST /v1/jobs/$JOB/deliver | j "$STATUS_FIELD")" "entrega"
DONE=$(call "$COMPANY" POST /v1/jobs/$JOB/complete)
expect completed "$(printf '%s' "$DONE" | j "$STATUS_FIELD")" "conclusão"
expect released "$(printf '%s' "$DONE" | j "d['payment']['status']")" "repasse simulado confirmado"
expect 41000 "$(printf '%s' "$DONE" | j "d['payment']['proAmountCents']")" "82% ao profissional"
expect 200 "$(code "$COMPANY" POST /v1/jobs/$JOB/reviews '{"rating":5,"comment":"ótimo"}')" "avaliação após conclusão"
OTHER=$(login outra@example.com); call "$OTHER" POST /v1/me/role '{"role":"company"}' >/dev/null
expect 403 "$(code "$OTHER" GET /v1/jobs/$JOB)" "outra empresa não vê o job"
echo "## backup e restore validados pela API"
docker exec "$DB" pg_dump -U warroom -d warroom -Fc > "/tmp/warroom-demo-$$.dump"
docker rm -f "$API_C" >/dev/null; docker exec "$DB" psql -U warroom -d postgres -qc "drop database warroom"; docker exec "$DB" psql -U warroom -d postgres -qc "create database warroom"
docker exec -i "$DB" pg_restore -U warroom -d warroom --no-owner < "/tmp/warroom-demo-$$.dump"; rm -f "/tmp/warroom-demo-$$.dump"
docker run -d --name "$API_C" --network "$NET" -p "127.0.0.1:${PORT}:8080" \
  -e WARROOM_DB_URL="jdbc:postgresql://${DB}:5432/warroom" -e WARROOM_ENVIRONMENT=local -e WARROOM_BILLING_MODE=demo \
  -e WARROOM_EXPOSE_MAGIC_LINK=true -e WARROOM_ADMIN_EMAILS=support@warroom.test -e WARROOM_SMTP_HOST="$MAIL" -e WARROOM_SMTP_PORT=1025 "$IMAGE" >/dev/null
for _ in $(seq 1 60); do curl -fsS "$API/actuator/health" >/dev/null 2>&1 && break; sleep 2; done
COMPANY=$(login empresa@example.com)
expect released "$(call "$COMPANY" GET /v1/jobs/$JOB | j "d['payment']['status']")" "histórico financeiro preservado após restore"
echo "demo journey ok"
