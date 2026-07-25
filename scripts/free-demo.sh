#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "==> Empacotando API (Maven local)"
(cd apps/api && mvn -q -DskipTests package)

echo "==> Subindo demo gratuita (Docker + túnel Cloudflare)"
docker compose -f docker-compose.free.yml up -d --build

echo "==> Aguardando URL pública do túnel..."
PUBLIC_URL=""
for _ in $(seq 1 60); do
  PUBLIC_URL="$(docker compose -f docker-compose.free.yml logs tunnel 2>/dev/null \
    | grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' \
    | tail -n 1 || true)"
  if [[ -n "${PUBLIC_URL}" ]]; then
    break
  fi
  sleep 2
done

echo
echo "Local (neste PC):"
echo "  Painel/API: http://localhost:9082"
echo "  Mailpit:    http://localhost:18027"
echo
if [[ -n "${PUBLIC_URL}" ]]; then
  echo "Público (grátis, muda a cada restart):"
  echo "  ${PUBLIC_URL}"
else
  echo "Túnel ainda sem URL. Veja: docker compose -f docker-compose.free.yml logs -f tunnel"
fi
echo
echo "Fluxo demo: login empresa → criar job → logout → login pro → candidatar →"
echo "voltar empresa → aceitar → checkout (escrow demo) → pro inicia → empresa conclui."
echo "Parar: docker compose -f docker-compose.free.yml down"
