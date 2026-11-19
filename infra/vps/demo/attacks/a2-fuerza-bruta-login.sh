#!/usr/bin/env bash
# A2 — Fuerza bruta y exceso de peticiones al login del VPS (cuenta INEXISTENTE).
#   2a) 12 intentos al mismo correo por la API → 401 ×10 y luego 429 LOGYX-AUTH-429 con Retry-After.
#   2b) mismo correo por la web (acción de Astro) → «Demasiados intentos».
#   2c) exceso: 22 peticiones más ya limitadas → oncargo-caddy-auth (20 respuestas 429 en 10 min) bloquea la IP
#       en 80/443 durante 1 h; luego se desbloquea. Volumen total: 35 POST. Consume el límite por IP del login
#       (50 / 15 min) de la IP de origen.
source "$(dirname "$0")/common.sh"; require_authorization
title "A2 · Fuerza bruta y exceso de peticiones al login"
email="a2-$(date +%s)@prueba.invalido"
codes=(); for i in $(seq 1 12); do codes+=("$(login_code "$API/api/v1/auth/login" "$email")"); done
info "2a API, mismo correo: ${codes[*]}"
n401=$(printf '%s\n' "${codes[@]}" | grep -c 401); n429=$(printf '%s\n' "${codes[@]}" | grep -c 429)
(( n401 == 10 && n429 == 2 )) && ok "10 × 401 y luego 429" || bad "401=$n401 429=$n429"
h="$(curl -s -4 -D - -o /dev/null -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"password\":\"x\"}" "$API/api/v1/auth/login" | tr -d '\r' | grep -i '^retry-after')"
[[ -n "$h" ]] && ok "cabecera $h" || bad "sin Retry-After"
w="$(web_login_code "$email")"; [[ "$w" == 429 ]] && ok "2b web: «Demasiados intentos»" || bad "2b web: $w"
info "2c exceso: 22 peticiones más…"
for i in $(seq 1 22); do login_code "$API/api/v1/auth/login" "$email" >/dev/null; done
sleep 4
banned_in oncargo-caddy-auth && ok "oncargo-caddy-auth bloqueó la IP de origen en 80/443 (1 h)" || bad "no hubo bloqueo de oncargo-caddy-auth"
unban_me
