#!/usr/bin/env bash
# OnCargo — chequeo de salud liviano (lo instala harden.sh en /usr/local/sbin/oncargo-health; lo corre
# oncargo-health.timer cada 5 min como root). Sin agentes ni bases de métricas: curl, docker y /proc.
#
# Revisa: web, login, /app/ y APK; salud de la API; contenedores healthy; disco; memoria; swap; vigencia del
# certificado; antigüedad del último respaldo; reinicio pendiente por parches de kernel.
# Resultado: /var/lib/oncargo/health.txt y el journal (journalctl -t oncargo-health). Alerta solo en cambios de
# estado (OK→FALLA y FALLA→OK), no cada 5 minutos.
# Canales opcionales en /etc/oncargo/alert.env (no se versiona):
#   ALERT_WEBHOOK_URL=https://ntfy.sh/<tema-aleatorio>   POST de texto plano (ntfy, Discord/Slack vía puente…)
#   HEARTBEAT_URL=https://hc-ping.com/<uuid>             latido en cada chequeo correcto (healthchecks.io):
#                                                        avisa también si el VPS entero se cae (sin latido)
# El mensaje lleva solo el estado, la hora y qué falló (servicio/URL pública, código HTTP, porcentaje): nunca
# contenido de .env, tokens ni respuestas de la API. Un tema de ntfy.sh lo lee cualquiera que conozca su nombre.
# Uso manual: sudo oncargo-health [-v]   ·   sudo oncargo-health --test-alert (manda una alerta de prueba y sale)
set -uo pipefail
[[ -f /etc/oncargo/alert.env ]] && . /etc/oncargo/alert.env
if [[ "${1:-}" == "--test-alert" ]]; then
  [[ -n "${ALERT_WEBHOOK_URL:-}" ]] || { echo "Falta ALERT_WEBHOOK_URL en /etc/oncargo/alert.env" >&2; exit 1; }
  curl -fsS -m 10 -H "Title: OnCargo VPS: PRUEBA" -H "Tags: test_tube" \
    --data-raw "Alerta de prueba de oncargo-health ($(date -Is)). No hay nada caído." "$ALERT_WEBHOOK_URL" > /dev/null \
    && echo "Alerta de prueba enviada" || { echo "No se pudo enviar la alerta" >&2; exit 1; }
  exit 0
fi
DOMAIN="${DOMAIN:-oncargo.tech}"
STATUS_DIR=/var/lib/oncargo
DISK_MAX_PCT="${DISK_MAX_PCT:-85}"
MEM_MIN_MB="${MEM_MIN_MB:-500}"
SWAP_MAX_MB="${SWAP_MAX_MB:-1024}"
CERT_MIN_DAYS="${CERT_MIN_DAYS:-14}"
BACKUP_MAX_HOURS="${BACKUP_MAX_HOURS:-26}"
VERBOSE=0; [[ "${1:-}" == "-v" ]] && VERBOSE=1
mkdir -p "$STATUS_DIR"

fails=(); warns=()
fail() { fails+=("$1"); }
warn() { warns+=("$1"); }
http() {  # http <url> <código esperado>
  local code; code="$(curl -s -o /dev/null -m 15 -w '%{http_code}' "$1")"
  [[ "$code" == "$2" ]] || fail "$1 → HTTP $code (se esperaba $2)"
}

http "https://$DOMAIN/" 200
http "https://$DOMAIN/login" 200
http "https://$DOMAIN/app/" 200
http "https://$DOMAIN/app/oncargo.apk" 200
http "https://api.$DOMAIN/q/health/ready" 200

expected=(db minio backend web caddy)
for svc in "${expected[@]}"; do
  st="$(docker ps --filter label=com.docker.compose.project=oncargo --filter "label=com.docker.compose.service=$svc" --format '{{.Status}}' | head -1)"
  if [[ -z "$st" ]]; then fail "contenedor $svc no está en marcha"
  elif [[ "$st" == *unhealthy* || "$st" == *starting* ]]; then fail "contenedor $svc: $st"
  fi
done
restarts="$(docker ps -q --filter label=com.docker.compose.project=oncargo | xargs -r docker inspect -f '{{.Name}} {{.RestartCount}} {{.State.OOMKilled}}' | awk '$2>0 || $3=="true"')"
[[ -z "$restarts" ]] || warn "reinicios/OOM: $(echo "$restarts" | tr '\n' ';')"

disk="$(df --output=pcent / | tail -1 | tr -dc 0-9)"
(( disk < DISK_MAX_PCT )) || fail "disco / al ${disk}% (máx. ${DISK_MAX_PCT}%)"
mem="$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)"
(( mem >= MEM_MIN_MB )) || fail "memoria disponible ${mem} MB (mín. ${MEM_MIN_MB})"
swap="$(awk '/SwapTotal/ {t=$2} /SwapFree/ {f=$2} END {print int((t-f)/1024)}' /proc/meminfo)"
(( swap <= SWAP_MAX_MB )) || warn "swap en uso ${swap} MB"

for host in "$DOMAIN" "api.$DOMAIN"; do
  end="$(echo | timeout 10 openssl s_client -connect "$host:443" -servername "$host" 2>/dev/null | openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)"
  if [[ -z "$end" ]]; then fail "no se pudo leer el certificado de $host"
  else
    days=$(( ($(date -d "$end" +%s) - $(date +%s)) / 86400 ))
    (( days >= CERT_MIN_DAYS )) || fail "certificado de $host vence en $days días"
  fi
done

if [[ -f "$STATUS_DIR/backup-last-ok" ]]; then
  age_h=$(( ($(date +%s) - $(date -d "$(cat "$STATUS_DIR/backup-last-ok")" +%s)) / 3600 ))
  (( age_h <= BACKUP_MAX_HOURS )) || fail "último respaldo correcto hace ${age_h} h"
else
  fail "nunca hubo un respaldo correcto (oncargo-backup run)"
fi
if [[ -f /var/run/reboot-required ]]; then
  rb_days=$(( ($(date +%s) - $(stat -c %Y /var/run/reboot-required)) / 86400 ))
  (( rb_days < 7 )) || warn "reinicio pendiente por parches hace ${rb_days} días"
fi

now="$(date -Is)"
if (( ${#fails[@]} )); then state=FALLA; else state=OK; fi
{
  echo "estado=$state fecha=$now"
  for f in "${fails[@]}"; do echo "FALLA: $f"; done
  for w in "${warns[@]}"; do echo "AVISO: $w"; done
} > "$STATUS_DIR/health.txt.new"
prev="$(head -1 "$STATUS_DIR/health.txt" 2>/dev/null | sed -E 's/^estado=([A-Z]+).*/\1/')"
prev_body="$(tail -n +2 "$STATUS_DIR/health.txt" 2>/dev/null)"
body="$(tail -n +2 "$STATUS_DIR/health.txt.new")"
mv "$STATUS_DIR/health.txt.new" "$STATUS_DIR/health.txt"

if [[ $state == OK ]]; then
  logger -t oncargo-health -p user.info "OK${warns:+ (avisos: ${#warns[@]})}"
  [[ -n "${HEARTBEAT_URL:-}" ]] && curl -fsS -m 10 --retry 2 "$HEARTBEAT_URL" > /dev/null
else
  logger -t oncargo-health -p user.err "FALLA: ${fails[*]}"
  [[ -n "${HEARTBEAT_URL:-}" ]] && curl -fsS -m 10 --retry 2 --data-raw "$body" "$HEARTBEAT_URL/fail" > /dev/null
fi
# Alerta solo si cambió el estado o el detalle de las fallas.
if [[ "$state" != "${prev:-OK}" || ( $state == FALLA && "$body" != "$prev_body" ) ]]; then
  if [[ -n "${ALERT_WEBHOOK_URL:-}" ]]; then
    prio=default; [[ $state == FALLA ]] && prio=high
    curl -fsS -m 10 -H "Title: OnCargo VPS: $state" -H "Priority: $prio" --data-raw "$(cat "$STATUS_DIR/health.txt")" "$ALERT_WEBHOOK_URL" > /dev/null \
      || logger -t oncargo-health -p user.warning "no se pudo enviar la alerta"
  fi
fi
(( VERBOSE )) && cat "$STATUS_DIR/health.txt"
[[ $state == OK ]]
