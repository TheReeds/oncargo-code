#!/usr/bin/env bash
# OnCargo — respaldo cifrado del VPS (lo instala harden.sh en /usr/local/sbin/oncargo-backup; corre como root).
#
#   oncargo-backup run                 respaldo completo (lo lanza oncargo-backup.timer a diario, 03:30 Lima)
#   oncargo-backup verify <identidad>  restaura el último volcado en un Postgres temporal y compara conteos
#   oncargo-backup list                lista los respaldos
#
# Qué guarda (cada archivo cifrado con age para las claves públicas de /etc/oncargo/backup-recipients.txt):
#   db-<fecha>.dump.age     pg_dump -Fc de la base logyx (se valida con pg_restore -l ANTES de cifrar)
#   minio-<fecha>.tar.gz.age  documentos y evidencias (volumen oncargo_miniodata)
#   state-<fecha>.tar.gz.age  /opt/oncargo/state (.env y claves JWT: restaurar sin invalidar sesiones)
# La clave PRIVADA de age no vive en el VPS: la guarda el administrador (gestor de contraseñas). Con ella se
# descifra: age -d -i identidad.txt db-<fecha>.dump.age > db.dump
#
# Destino: /var/backups/oncargo (root, 700), retención BACKUP_KEEP_DAYS (14). Copia externa opcional con rclone
# (BACKUP_RCLONE_REMOTE en /etc/oncargo/backup.env, p. ej. r2:oncargo-backups/vps). Al terminar bien escribe
# /var/lib/oncargo/backup-last-ok (lo vigila oncargo-health: alerta si tiene más de 26 h).
set -euo pipefail
umask 077

[[ -f /etc/oncargo/backup.env ]] && . /etc/oncargo/backup.env
DEST="${BACKUP_DIR:-/var/backups/oncargo}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
RECIPIENTS="${BACKUP_RECIPIENTS:-/etc/oncargo/backup-recipients.txt}"
STATE_DIR=/opt/oncargo/state
STATUS_DIR=/var/lib/oncargo
PROJECT=oncargo

log() { echo "[oncargo-backup] $*"; }
die() { echo "[oncargo-backup] ERROR: $*" >&2; exit 1; }
container() {  # id del contenedor de un servicio del proyecto compose
  docker ps -q --filter "label=com.docker.compose.project=$PROJECT" --filter "label=com.docker.compose.service=$1" | head -1
}
volume_path() { docker volume inspect -f '{{.Mountpoint}}' "${PROJECT}_$1"; }
# Conteos de referencia (mismas consultas al respaldar y al verificar)
COUNTS_SQL="SELECT 'migracion=' || max(version::int) FROM flyway_schema_history WHERE success
UNION ALL SELECT 'tablas=' || count(*) FROM pg_tables WHERE schemaname='public'
UNION ALL SELECT 'politicas_rls=' || count(*) FROM pg_policies
UNION ALL SELECT 'organizaciones=' || count(*) FROM organizations
UNION ALL SELECT 'perfiles=' || count(*) FROM profiles
UNION ALL SELECT 'envios=' || count(*) FROM shipments;"

cmd_run() {
  [[ -s "$RECIPIENTS" ]] || die "falta $RECIPIENTS (clave pública age)"
  command -v age >/dev/null || die "falta age (apt install age)"
  local db ts
  db="$(container db)"; [[ -n "$db" ]] || die "el contenedor db no está en marcha"
  ts="$(date +%Y%m%d-%H%M)"
  mkdir -p "$DEST" "$STATUS_DIR"; chmod 700 "$DEST"
  tmp="$(mktemp -d "$DEST/.tmp.XXXXXX")"; trap 'rm -rf "${tmp:-}"' EXIT  # global: el trap corre al salir

  log "pg_dump → $ts"
  docker exec "$db" pg_dump -U postgres -d logyx -Fc > "$tmp/db.dump"
  docker exec -i "$db" pg_restore -l < "$tmp/db.dump" > "$tmp/db.toc" || die "el volcado no es legible (pg_restore -l)"
  docker exec "$db" psql -U postgres -d logyx -At -c "$COUNTS_SQL" > "$tmp/counts.txt"
  age -R "$RECIPIENTS" -o "$tmp/db-$ts.dump.age" "$tmp/db.dump"

  log "documentos (MinIO) y state/"
  tar -C "$(volume_path miniodata)" -czf - . | age -R "$RECIPIENTS" -o "$tmp/minio-$ts.tar.gz.age"
  tar -C "$(dirname "$STATE_DIR")" -czf - "$(basename "$STATE_DIR")" | age -R "$RECIPIENTS" -o "$tmp/state-$ts.tar.gz.age"

  # Manifiesto sin secretos: tamaños, sha256 y conteos de referencia para la verificación.
  {
    echo "fecha=$ts"
    echo "commit=$(cat "$STATE_DIR/DEPLOYED" 2>/dev/null || echo '?')"
    echo "objetos_toc=$(grep -vc '^;' "$tmp/db.toc")"
    cat "$tmp/counts.txt"
    (cd "$tmp" && sha256sum ./*.age | sed 's#  \./#  #')
  } > "$tmp/manifest-$ts.txt"
  mv "$tmp"/*.age "$tmp/manifest-$ts.txt" "$DEST/"

  find "$DEST" -maxdepth 1 -type f \( -name '*.age' -o -name 'manifest-*.txt' \) -mtime "+$((KEEP_DAYS - 1))" -delete

  if [[ -n "${BACKUP_RCLONE_REMOTE:-}" ]]; then
    command -v rclone >/dev/null || die "BACKUP_RCLONE_REMOTE definido pero falta rclone"
    log "copia externa → $BACKUP_RCLONE_REMOTE"
    rclone copy --config "${RCLONE_CONFIG:-/etc/oncargo/rclone.conf}" "$DEST" "$BACKUP_RCLONE_REMOTE" \
      --include "*-$ts.*" --include "manifest-$ts.txt"
  fi

  date -Is > "$STATUS_DIR/backup-last-ok"
  log "OK: $(du -ch "$DEST"/*-"$ts".* | tail -1 | cut -f1) en $DEST ($(ls "$DEST"/db-*.age | wc -l) volcados conservados)"
}

cmd_verify() {
  local identity="${1:?uso: oncargo-backup verify <archivo-identidad-age | - (stdin)>}"
  local latest ts pass t0 ok=1
  latest="$(ls -1t "$DEST"/db-*.dump.age 2>/dev/null | head -1)"; [[ -n "$latest" ]] || die "no hay respaldos en $DEST"
  ts="$(basename "$latest" | sed -E 's/^db-(.*)\.dump\.age$/\1/')"
  work="$(mktemp -d)"; ct=""; trap 'rm -rf "${work:-}"; [[ -n "${ct:-}" ]] && docker rm -f "$ct" >/dev/null 2>&1; true' EXIT
  if [[ "$identity" == "-" ]]; then cat > "$work/id"; identity="$work/id"; fi
  log "verificando $(basename "$latest")"
  ( cd "$DEST" && grep -E "  (db|minio|state)-$ts\." "manifest-$ts.txt" | sha256sum -c --quiet ) || die "sha256 no coincide"
  age -d -i "$identity" -o "$work/db.dump" "$latest"
  age -d -i "$identity" "$DEST/minio-$ts.tar.gz.age" | tar -tzf - > /dev/null
  age -d -i "$identity" "$DEST/state-$ts.tar.gz.age" | tar -tzf - | grep -q 'state/.env' || die "state sin .env"

  # Postgres temporal, sin red, con los mismos roles que init-roles.sh (contraseñas de prueba desechables).
  ct="oncargo-restore-test-$$"; pass="$(openssl rand -hex 12)"
  docker run -d --name "$ct" --network none --memory 512m \
    -e POSTGRES_PASSWORD="$pass" -e LOGYX_DB=logyx -e LOGYX_OWNER_PASSWORD="$pass" -e LOGYX_APP_PASSWORD="$pass" \
    -v /opt/oncargo/src/infra/db/init-roles.sh:/docker-entrypoint-initdb.d/10-init-roles.sh:ro \
    postgres:16-alpine > /dev/null
  for _ in $(seq 1 60); do docker exec "$ct" pg_isready -q -h 127.0.0.1 -U postgres -d logyx 2>/dev/null && break; sleep 1; done
  t0=$(date +%s)
  docker exec -i "$ct" pg_restore -U postgres -d logyx --no-owner --role=logyx_owner --exit-on-error < "$work/db.dump"
  docker exec "$ct" psql -U postgres -d logyx -At -c "$COUNTS_SQL" > "$work/restored.txt"
  log "restauración en $(( $(date +%s) - t0 )) s"
  grep -E '^(migracion|tablas|politicas_rls|organizaciones|perfiles|envios)=' "$DEST/manifest-$ts.txt" > "$work/expected.txt"
  if diff -u "$work/expected.txt" "$work/restored.txt"; then
    log "OK: conteos idénticos al momento del respaldo:"; sed 's/^/    /' "$work/restored.txt"
  else ok=0; fi
  [[ $ok == 1 ]] || die "los conteos restaurados no coinciden"
  date -Is > "$STATUS_DIR/restore-test-last-ok"
}

case "${1:-}" in
  run) cmd_run ;;
  verify) shift; cmd_verify "$@" ;;
  list) ls -lh "$DEST" ;;
  *) echo "uso: $0 run | verify <identidad-age|-> | list" >&2; exit 2 ;;
esac
