#!/usr/bin/env bash
# A6 — SSRF. La parte ESTÁTICA es pasiva (revisión del código): el único cliente HTTP saliente debe ser el SDK S3.
#   La parte dinámica (URLs internas en campos libres con tcpdump) se hace en el staging LOCAL: ver EPE § A6.
source "$(dirname "$0")/common.sh"
title "A6 · SSRF (revisión estática)"
ROOT="$(git -C "$ATTACKS_DIR" rev-parse --show-toplevel)"
hits="$(grep -rnE 'HttpClient|new URL\(|URI\.create|RestClient|WebClient|HttpURLConnection|openConnection\(' "$ROOT/backend/src/main/java" | sed "s#$ROOT/##")"
echo "$hits" | sed 's/^/  /' | cut -c1-150
n="$(grep -v 'S3DocumentStorage\|S3Client\|URI\.create(endpoint\|URI.create(config' <<<"$hits" | grep -c .)"
(( n == 0 )) && ok "solo el adaptador S3 abre conexiones salientes (destino de LOGYX_S3_ENDPOINT, no del usuario)" || info "revisar $n usos (ver arriba): confirmar que ninguno toma la URL de la petición"
