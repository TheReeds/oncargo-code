#!/usr/bin/env bash
# A8 — Credenciales en texto claro por el acceso HTTP por IP. PASIVA (2 GET).
#   Esperado: http://<IP>/login → 308 a https://oncargo.tech/login (no se sirve el formulario sin TLS); https://<IP> sin certificado.
source "$(dirname "$0")/common.sh"
title "A8 · Acceso HTTP por IP"
r="$(curl -s -4 -o /dev/null -m 6 -w '%{http_code} %{redirect_url}' "http://$HOST_IP/login")"
info "http://<IP-VPS>/login → $r"
[[ "$r" == "308 https://$DOMAIN/login" ]] && ok "redirige al dominio con HTTPS" || bad "respuesta inesperada"
c="$(curl -s -4 -k -o /dev/null -m 6 -w '%{http_code}' "https://$HOST_IP/" 2>/dev/null)"; [[ "$c" == 000 ]] && ok "https por IP sin certificado ni contenido" || info "https por IP → $c"
