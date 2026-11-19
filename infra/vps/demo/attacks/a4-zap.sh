#!/usr/bin/env bash
# A4 — OWASP ZAP. Por omisión contra el staging LOCAL (como define el EPE): escaneo de API con el contrato OpenAPI.
#   --prod: solo el escaneo PASIVO de base (zap-baseline, 1 minuto de araña) contra https://oncargo.tech; durante la
#           prueba la IP de origen queda en la lista blanca de las jails web (addignoreip, solo en memoria) y se quita al final.
#   Esperado: sin alertas de riesgo alto; las medias y bajas se registran con su tratamiento. Informe en $OC_REGISTRO.
source "$(dirname "$0")/common.sh"; require_authorization
title "A4 · OWASP ZAP"
ROOT="$(git -C "$ATTACKS_DIR" rev-parse --show-toplevel)"
if [[ "${1:-}" == --prod ]]; then
  ip="$(origin_ip)"
  for j in oncargo-caddy-probe oncargo-caddy-auth; do "${SSH4[@]}" "sudo fail2ban-client set $j addignoreip $ip" >/dev/null; done
  trap 'for j in oncargo-caddy-probe oncargo-caddy-auth; do "${SSH4[@]}" "sudo fail2ban-client set $j delignoreip '"$ip"'" >/dev/null; done; info "lista blanca temporal retirada"' EXIT
  docker run --rm -v "$REG_DIR:/zap/wrk:rw" ghcr.io/zaproxy/zaproxy:stable zap-baseline.py -t "$WEB/" -m 1 -r a4-zap-baseline.html -I 2>&1 | tail -15 | mask
else
  info "Staging local: docker compose -f infra/docker-compose.yml --env-file infra/.env up -d (API en :8680)"
  docker run --rm --network host -v "$ROOT/docs/api:/zap/wrk:ro" -v "$REG_DIR:/zap/out:rw" ghcr.io/zaproxy/zaproxy:stable \
    zap-api-scan.py -t /zap/wrk/openapi.yaml -f openapi -O 127.0.0.1:8680 -r /zap/out/a4-zap-api.html -I 2>&1 | tail -15
fi
info "Informe: $REG_DIR"
