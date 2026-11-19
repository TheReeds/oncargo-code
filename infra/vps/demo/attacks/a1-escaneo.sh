#!/usr/bin/env bash
# A1 — Escaneo de puertos (nmap) y de rutas sensibles contra el VPS, con su detección.
#   Esperado: abiertos solo 22/80/443 (el resto filtered); journal con «UFW BLOCK» > 0;
#   3 rutas de escaneo (/.env, /wp-login.php, /.git/config) → 404 y la IP queda bloqueada en 80/443 por
#   oncargo-caddy-probe (nunca en el 22). Al final se desbloquea. Volumen: 1 barrido -p- a 1000 pps + 3 GET.
source "$(dirname "$0")/common.sh"; require_authorization
title "A1 · Escaneo de puertos y de rutas sensibles"
command -v docker >/dev/null || { echo "Falta docker (nmap corre en un contenedor alpine)"; exit 1; }
docker image inspect oncargo-nmap:local >/dev/null 2>&1 || printf 'FROM alpine:3.20\nRUN apk add --no-cache nmap\n' | docker build -q -t oncargo-nmap:local - >/dev/null
info "Barrido TCP completo (≈ 100 s)…"
out="$(docker run --rm --network host oncargo-nmap:local nmap -Pn -sS -p- --min-rate 1000 -T4 "$DOMAIN" 2>&1 | mask)"
echo "$out" | grep -E '^[0-9]+/|Not shown' | sed 's/^/  /'
open="$(echo "$out" | awk '/^[0-9]+\/tcp +open/ {split($1,a,"/"); printf "%s ", a[1]}')"
[[ "$open" == "22 80 443 " ]] && ok "abiertos solo: $open" || bad "abiertos: $open"
n="$(vps "sudo journalctl -k --since '-5 min' --no-pager | grep -c 'UFW BLOCK'")"
(( n > 0 )) && ok "detección: $n entradas «UFW BLOCK» en el journal de los últimos 5 min" || bad "sin entradas UFW BLOCK"
info "Rutas de escaneo (3 GET):"
for p in /.env /wp-login.php /.git/config; do info "$p → $(curl -s -4 -o /dev/null -w '%{http_code}' "$WEB$p")"; done
sleep 4
if banned_in oncargo-caddy-probe; then ok "oncargo-caddy-probe bloqueó la IP de origen en 80/443"
  ssh_ok="$("${SSH4[@]}" 'echo ssh-ok' 2>/dev/null)"; [[ "$ssh_ok" == ssh-ok ]] && ok "el SSH (22) sigue disponible para el administrador" || bad "SSH no responde"
else bad "la IP de origen no aparece bloqueada"; fi
unban_me
code="$(curl -s -4 -o /dev/null -w '%{http_code}' "$WEB/")"; [[ "$code" == 200 ]] && ok "tras desbloquear, $WEB/ → 200" || bad "$WEB/ → $code"
