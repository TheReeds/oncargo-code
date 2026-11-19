#!/usr/bin/env bash
# A9 — ¿El límite por IP del login web se comparte entre usuarios? (confirma el cierre de H-10).
#   Equipo A (este): 51 intentos por la web con correos distintos e inexistentes → «Demasiados intentos».
#   Equipo B: otra IP (por omisión el propio VPS, que sale por su IP pública y entra por Caddy como cualquier visitante;
#   o B_CMD='ssh otro-equipo' para un segundo equipo) → debe seguir recibiendo 401: el límite ya no es de todos.
#   Volumen: 52 POST. La IP de A queda sin login por 15 min.
source "$(dirname "$0")/common.sh"; require_authorization
title "A9 · Límite por IP compartido en la web (H-10)"
first429=""
for i in $(seq 1 55); do
  c="$(web_login_code "a9-$i-$(date +%s)@prueba.invalido")"
  [[ "$c" == 429 && -z "$first429" ]] && first429=$i
  [[ -n "$first429" ]] && break
done
[[ -n "$first429" ]] && ok "equipo A limitado en el intento $first429" || bad "el equipo A nunca fue limitado"
B="${B_CMD:-${SSH4[*]}}"
cB="$($B "curl -s -X POST -H 'Content-Type: application/json' -H 'Origin: $WEB' -d '{\"email\":\"a9-b@prueba.invalido\",\"password\":\"x\"}' $WEB/_actions/auth.login" 2>/dev/null)"
case "$cB" in *Demasiados*) bad "el equipo B TAMBIÉN está limitado: el límite se comparte (H-10 abierto)";; *) ok "el equipo B no se ve afectado (H-10 cerrado)";; esac
