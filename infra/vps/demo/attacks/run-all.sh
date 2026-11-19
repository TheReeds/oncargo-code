#!/usr/bin/env bash
# Ejecuta A1–A9 en un orden que no se pisa (el límite por IP del login es de 50 cada 15 min):
#   pasivas (A5, A6, A8) → A1 → A2 → A3 → A9 → A7 (la última, porque bloquea el SSH por IPv4) → A4 (opcional, --zap).
# Uso, con la autorización firmada:
#   OC_AUTORIZACION=<id> OC_AUT_HASTA=<AAAA-MM-DD> infra/vps/demo/attacks/run-all.sh [--zap] 2>&1 | tee resultado.txt
# Al final desbloquea la IP de origen en fail2ban. Registro (con la IP real, fuera del repo): ~/oncargo-pruebas/<fecha>/
D="$(cd "$(dirname "$0")" && pwd)"; source "$D/common.sh"; require_authorization
trap unban_me EXIT
info "IP de origen vista por el VPS: <IP-ORIGEN> (registrada en $REG_DIR/registro.txt) · inicio $(date '+%F %T')"
for s in a5-datos-desde-fuera a6-ssrf a8-http-por-ip a1-escaneo a2-fuerza-bruta-login a3-inyeccion-login a9-limite-compartido a7-fuerza-bruta-ssh; do
  t=$SECONDS; "$D/$s.sh"; info "(${s%%-*}: $((SECONDS - t)) s)"
done
[[ "${1:-}" == --zap ]] && "$D/a4-zap.sh" --prod
info "fin $(date '+%F %T') · servicio: $(for u in "$WEB/" "$WEB/login" "$WEB/app/" "$API/q/health/ready"; do printf '%s ' "$(curl -s -o /dev/null -w '%{http_code}' "$u")"; done)"
