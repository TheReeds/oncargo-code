#!/usr/bin/env bash
# A5 — Acceso a la base (5432) y a MinIO (9000/9001) desde fuera. PASIVA: solo intenta abrir la conexión TCP.
#   Esperado: sin conexión (filtered); dentro del VPS ningún proceso del host escucha en esos puertos.
source "$(dirname "$0")/common.sh"
title "A5 · Datos desde fuera (5432, 9000, 9001)"
for p in 5432 9000 9001; do
  if timeout 4 bash -c "exec 3<>/dev/tcp/$DOMAIN/$p" 2>/dev/null; then bad "$p responde desde Internet"; else ok "$p sin conexión desde Internet"; fi
done
l="$(vps "sudo ss -tlnH | awk '{print \$4}' | grep -cE ':(5432|9000|9001)\$'")"
[[ "$l" == 0 ]] && ok "ningún proceso del host escucha en 5432/9000/9001 (solo existen en la red interna de Docker)" || bad "$l sockets en el host"
r="$(vps "cd /opt/oncargo/src/infra/vps && docker compose exec -T caddy wget -q -T 3 -O /dev/null http://minio:9000/ </dev/null && echo alcanzable || echo inalcanzable")"
[[ "$r" == *inalcanzable* ]] && ok "ni siquiera Caddy (red edge) alcanza MinIO (red internal)" || bad "Caddy alcanza MinIO"
