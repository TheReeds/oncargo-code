#!/usr/bin/env bash
# OnCargo — SIM-1: restauración completa y MEDIDA del último respaldo cifrado, en una base temporal aislada
# en el equipo LOCAL (nunca sobre producción). Corre desde la raíz del monorepo o desde cualquier carpeta.
#
#   infra/vps/demo/sim1-restore.sh            usa el último respaldo que ya existe en el VPS
#   infra/vps/demo/sim1-restore.sh --fresh    antes lanza `oncargo-backup run` (mide la duración del respaldo)
#   infra/vps/demo/sim1-restore.sh --quick    modo demo: sin conteo tabla por tabla frente a producción
#
# Fases medidas: [respaldo] → descarga (ssh) → sha256 → descifrado (age) → restauración (pg_restore) → verificación.
# Requisitos locales: docker, ssh con la clave del VPS y la identidad age en ~/.config/oncargo/backup-age-identity.txt.
# age se usa desde una imagen local mínima (oncargo-age:local = alpine + paquete age), creada la primera vez.
# La base temporal corre en un contenedor postgres:16-alpine con --network none (sin red) y se borra al terminar,
# igual que los archivos descifrados. El state/ (secretos) solo se lista; nunca se escribe descifrado en disco.
# Salida sin secretos ni IP del servidor (se muestra <IP-VPS>).
set -euo pipefail
VPS="${VPS:-deploy@179.66.150.58}"
KEY="${SSH_KEY:-$HOME/.ssh/hostinger_kvm2}"
IDENTITY="${AGE_IDENTITY:-$HOME/.config/oncargo/backup-age-identity.txt}"
ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
SSH=(ssh -i "$KEY" -o BatchMode=yes -o ConnectTimeout=10 "$VPS")
FRESH=0; QUICK=0
for a in "$@"; do case "$a" in --fresh) FRESH=1 ;; --quick) QUICK=1 ;; *) echo "uso: $0 [--fresh] [--quick]" >&2; exit 2 ;; esac; done

B=$'\e[1m'; G=$'\e[32m'; R=$'\e[31m'; Y=$'\e[33m'; C=$'\e[36m'; N=$'\e[0m'
mask() { sed -E "s/${VPS#*@}/<IP-VPS>/g"; }
now() { date +%s.%N; }
secs() { awk -v a="$1" -v b="$2" 'BEGIN{printf "%.1f", b-a}'; }
phase() { printf '%s▸ %s%s\n' "$C$B" "$*" "$N"; }
declare -A T

[[ -r "$IDENTITY" ]] || { echo "${R}Falta la identidad age: $IDENTITY$N" >&2; exit 1; }
WORK="$(mktemp -d "${TMPDIR:-/tmp}/oncargo-sim1.XXXXXX")"; chmod 700 "$WORK"
CT="oncargo-sim1-$$"
cleanup() { docker rm -f "$CT" >/dev/null 2>&1 || true; rm -rf "$WORK"; }
trap cleanup EXIT

if ! docker image inspect oncargo-age:local >/dev/null 2>&1; then
  printf 'FROM alpine:3.20\nRUN apk add --no-cache age\n' | docker build -q -t oncargo-age:local - >/dev/null
fi
age_d() { docker run --rm -i --network none -v "$IDENTITY:/id:ro" oncargo-age:local age -d -i /id; }

if (( FRESH )); then
  phase "0. Respaldo nuevo en el VPS (oncargo-backup run)"
  t=$(now); "${SSH[@]}" 'sudo oncargo-backup run' 2>&1 | mask | sed 's/^/   /'; T[respaldo]=$(secs "$t" "$(now)")
fi

T1=$(now)
phase "1. Descarga del último respaldo (ssh → equipo local)"
TS="$("${SSH[@]}" "sudo sh -c 'ls -1t /var/backups/oncargo/db-*.dump.age | head -1'" | sed -E 's#.*/db-(.*)\.dump\.age#\1#')"
[[ -n "$TS" ]] || { echo "${R}No hay respaldos en el VPS$N"; exit 1; }
t=$(now)
"${SSH[@]}" "sudo tar -C /var/backups/oncargo -cf - db-$TS.dump.age minio-$TS.tar.gz.age state-$TS.tar.gz.age manifest-$TS.txt" | tar -x -C "$WORK"
T[descarga]=$(secs "$t" "$(now)")
BK_EPOCH=$(date -d "$(sed -E 's/(....)(..)(..)-(..)(..)/\1-\2-\3 \4:\5/' <<<"$TS")" +%s)
ls -l --time-style=+ "$WORK" | awk 'NR>1 {printf "   %8d B  %s\n", $5, $NF}'
echo "   respaldo de las $(date -d "@$BK_EPOCH" '+%F %H:%M') (antigüedad: $(( ($(date +%s) - BK_EPOCH) / 60 )) min = RPO de esta restauración)"

phase "2. Integridad (sha256 del manifiesto)"
t=$(now); (cd "$WORK" && grep -E "  (db|minio|state)-$TS\." "manifest-$TS.txt" | sha256sum -c) | sed 's/^/   /'; T[sha256]=$(secs "$t" "$(now)")
printf '   ¿cifrado?  %s\n' "$(head -c 40 "$WORK/db-$TS.dump.age" | head -1)"

phase "3. Descifrado con la identidad age local (no existe en el VPS)"
t=$(now)
age_d < "$WORK/db-$TS.dump.age" > "$WORK/db.dump"
age_d < "$WORK/minio-$TS.tar.gz.age" | tar -tzf - > "$WORK/minio.list"
age_d < "$WORK/state-$TS.tar.gz.age" | tar -tzf - > "$WORK/state.list"
T[descifrado]=$(secs "$t" "$(now)")
echo "   db.dump $(stat -c %s "$WORK/db.dump") B · documentos: $(grep -vc '/$' "$WORK/minio.list") archivos · state/: $(grep -c . "$WORK/state.list") entradas ($(grep -q 'state/.env' "$WORK/state.list" && echo 'incluye .env' || echo "${R}SIN .env$N"); no se extrae)"

phase "4. Restauración en un PostgreSQL temporal sin red (postgres:16-alpine, --network none)"
t=$(now)
PASS="$(openssl rand -hex 12)"
docker run -d --name "$CT" --network none --memory 512m \
  -e POSTGRES_PASSWORD="$PASS" -e LOGYX_DB=logyx -e LOGYX_OWNER_PASSWORD="$PASS" -e LOGYX_APP_PASSWORD="$PASS" \
  -v "$ROOT/infra/db/init-roles.sh:/docker-entrypoint-initdb.d/10-init-roles.sh:ro" postgres:16-alpine >/dev/null
until docker exec "$CT" pg_isready -q -h 127.0.0.1 -U postgres -d logyx 2>/dev/null; do sleep 0.5; done
T[arranque_pg]=$(secs "$t" "$(now)")
t=$(now)
docker exec -i "$CT" pg_restore -U postgres -d logyx --no-owner --role=logyx_owner --exit-on-error < "$WORK/db.dump"
T[restauracion]=$(secs "$t" "$(now)")
echo "   red del contenedor: $(docker inspect -f '{{.HostConfig.NetworkMode}}' "$CT") · BD restaurada: $(docker exec "$CT" psql -U postgres -d logyx -Atc "SELECT pg_size_pretty(pg_database_size('logyx'))")"

phase "5. Verificación"
t=$(now)
REF="SELECT 'migracion=' || max(version::int) FROM flyway_schema_history WHERE success
UNION ALL SELECT 'tablas=' || count(*) FROM pg_tables WHERE schemaname='public'
UNION ALL SELECT 'politicas_rls=' || count(*) FROM pg_policies
UNION ALL SELECT 'organizaciones=' || count(*) FROM organizations
UNION ALL SELECT 'perfiles=' || count(*) FROM profiles
UNION ALL SELECT 'envios=' || count(*) FROM shipments;"
docker exec "$CT" psql -U postgres -d logyx -At -c "$REF" > "$WORK/restored.ref"
grep -E '^(migracion|tablas|politicas_rls|organizaciones|perfiles|envios)=' "$WORK/manifest-$TS.txt" > "$WORK/expected.ref"
if diff -q "$WORK/expected.ref" "$WORK/restored.ref" >/dev/null; then
  echo "   ${G}OK${N} conteos de referencia idénticos al manifiesto: $(tr '\n' ' ' < "$WORK/restored.ref")"
else echo "   ${R}FALLA${N} referencia:"; diff "$WORK/expected.ref" "$WORK/restored.ref" | sed 's/^/   /'; fi
RLS="$(docker exec "$CT" psql -U postgres -d logyx -Atc "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind='r' AND c.relrowsecurity")"
echo "   tablas con RLS activada: $RLS · migraciones correctas: $(docker exec "$CT" psql -U postgres -d logyx -Atc "SELECT bool_and(success) FROM flyway_schema_history")"
if (( ! QUICK )); then
  COUNT_ALL="SELECT table_name || '=' || (xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM public.%I', table_name), false, true, '')))[1]::text
             FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY 1;"
  docker exec "$CT" psql -U postgres -d logyx -At -c "$COUNT_ALL" > "$WORK/restored.all"
  "${SSH[@]}" "docker exec -i oncargo-db-1 psql -U postgres -d logyx -At" <<<"$COUNT_ALL" > "$WORK/prod.all"
  same=$(comm -12 <(sort "$WORK/prod.all") <(sort "$WORK/restored.all") | wc -l); total=$(wc -l < "$WORK/prod.all")
  echo "   conteo tabla por tabla frente a producción (ahora): ${B}$same de $total iguales${N}"
  join -t= <(sort "$WORK/prod.all") <(sort "$WORK/restored.all") | awk -F= '$2!=$3 {printf "     ≠ %-32s producción=%s restaurado=%s\n", $1, $2, $3}'
  rows=$(awk -F= '{s+=$2} END {print s}' "$WORK/restored.all"); echo "   filas restauradas: $rows"
  cp "$WORK/restored.all" "${SIM1_COUNTS_OUT:-/dev/null}" 2>/dev/null || true
fi
T[verificacion]=$(secs "$t" "$(now)")

phase "6. Limpieza"
docker rm -f "$CT" >/dev/null && echo "   contenedor temporal eliminado; archivos descifrados borrados (trap)"
TOTAL=$(secs "$T1" "$(now)")
printf '\n%sTiempos (s)%s  ' "$B" "$N"
for k in respaldo descarga sha256 descifrado arranque_pg restauracion verificacion; do [[ -n "${T[$k]:-}" ]] && printf '%s=%s  ' "$k" "${T[$k]}"; done
printf '\n%sTOTAL de la restauración de datos: %s s%s (objetivo RTO 1–2 h)\n' "$G$B" "$TOTAL" "$N"
