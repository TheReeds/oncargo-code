#!/usr/bin/env bash
# OnCargo — kit de demostración de la infraestructura (exposición del EPE). Corre en el equipo LOCAL y consulta el
# VPS por SSH. Toda la salida pasa por un filtro que enmascara la IP del servidor (<IP-VPS>), cualquier otra IP
# pública (<IP-PUBLICA>, p. ej. la de los visitantes en el registro de Caddy), las IPv6 globales y el nombre del host.
# Nunca imprime .env, contraseñas, tokens ni el tema de ntfy.
#
#   infra/vps/demo/demo.sh             menú interactivo (opciones 1–10)
#   infra/vps/demo/demo.sh status      resumen de corrido, no interactivo (alias: all)
#   infra/vps/demo/demo.sh 4           una sola opción (útil para grabar capturas)
#
# Variables: VPS (deploy@<IP>), SSH_KEY (~/.ssh/hostinger_kvm2), DOMAIN (oncargo.tech),
#   DEMO_SSH_EXTERNO=1  la opción 5 también intenta entrar con contraseña DESDE este equipo (cuenta 1 fallo para fail2ban
#                       sshd: 5 en 10 min bloquean el 22 una hora; por omisión el intento se hace desde el propio VPS,
#                       que fail2ban ignora por ignoreself).
#   NO_COLOR=1          sin colores (para pegar en un documento).
# Requisitos locales: ssh, curl, openssl, perl; docker para la opción 7 (restauración) y el nmap de la opción 2.
set -uo pipefail
VPS="${VPS:-deploy@179.66.150.58}"
KEY="${SSH_KEY:-$HOME/.ssh/hostinger_kvm2}"
DOMAIN="${DOMAIN:-oncargo.tech}"
HOST_IP="${VPS#*@}"
HERE="$(cd "$(dirname "$0")" && pwd)"
SSH=(ssh -i "$KEY" -o BatchMode=yes -o ConnectTimeout=8 -o ServerAliveInterval=10 "$VPS")
COMPOSE_DIR=/opt/oncargo/src/infra/vps

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  B=$'\e[1m'; D=$'\e[2m'; G=$'\e[32m'; R=$'\e[31m'; Y=$'\e[33m'; C=$'\e[36m'; M=$'\e[35m'; N=$'\e[0m'
else B=; D=; G=; R=; Y=; C=; M=; N=; fi

# ── utilidades ──────────────────────────────────────────────────────────────────────────────────────────────────
mask() {
  HOST_IP="$HOST_IP" perl -pe '
    BEGIN { $h = quotemeta($ENV{HOST_IP}) }
    s/$h/<IP-VPS>/g;
    s/\bsrv\d+(?:\.hstgr\.cloud)?\b/<host-VPS>/g;
    s/\b(?!(?:10|127)\.)(?!172\.(?:1[6-9]|2\d|3[01])\.)(?!192\.168\.)(?!203\.0\.113\.)(?!198\.51\.100\.)(?!192\.0\.2\.)(?!0\.0\.0\.0)((?:25[0-5]|2[0-4]\d|1?\d?\d)\.(?:25[0-5]|2[0-4]\d|1?\d?\d)\.(?:25[0-5]|2[0-4]\d|1?\d?\d)\.(?:25[0-5]|2[0-4]\d|1?\d?\d))\b(?![-.]\d)/<IP-PUBLICA>/g;
    s/\b2[0-9a-f]{3}:[0-9a-f:]{4,}(?:\/\d+)?/<IPv6-PUBLICA>/gi;
    s/(ntfy\.sh\/)[\w-]+/${1}<tema>/g;
    s/((?:PASSWORD|SECRET|TOKEN|KEY)[A-Z_]*=)\S+/${1}<oculto>/g;'
}
title() { printf '\n%s━━ %s ━━%s\n' "$C$B" "$*" "$N"; }
sub() { printf '%s▸ %s%s\n' "$M$B" "$*" "$N"; }
say() { printf '%s%s%s\n' "$D" "$*" "$N"; }   # qué decir / qué demuestra
ok() { printf '  %s✔ %s%s\n' "$G" "$*" "$N"; }
bad() { printf '  %s✘ %s%s\n' "$R" "$*" "$N"; }
remote() { "${SSH[@]}" "bash -s" 2>&1 | mask; }   # script por stdin → salida enmascarada
pause() { [[ -t 0 && "${MODE:-menu}" == menu ]] && { printf '\n%s[Enter] para volver al menú%s' "$D" "$N"; read -r _; }; }
confirm() { [[ -t 0 && "${MODE:-menu}" != status ]] || return 1; printf '%s%s [s/N] %s' "$Y" "$1" "$N"; read -r a; [[ "$a" =~ ^[sSyY]$ ]]; }

check_ssh() {
  "${SSH[@]}" true 2>/dev/null && return 0
  bad "No hay SSH con el VPS ($(echo "$VPS" | mask)). Plan B: capturas en docs/infraestructura/semestre-2/img/ del EPE."
  return 1
}

# ── 1. Arquitectura y contenedores ──────────────────────────────────────────────────────────────────────────────
opt_1() {
  title "1 · Arquitectura y contenedores en vivo"
  say "Un VPS (2 vCPU, 8 GB). Solo Caddy publica puertos; db y minio viven en una red interna sin salida a Internet."
  cat <<EOF
  Internet ─► ${B}Caddy${N} :80/:443 (TLS Let's Encrypt, HTTP/3, cabeceras, registro de accesos)
                ├─ ${DOMAIN}      → ${B}web${N}:4321  (Astro SSR)        · /app/ → APK de pruebas
                └─ api.${DOMAIN}  → ${B}backend${N}:8080 (Quarkus, SSE) · /q/* cerrado salvo /q/health
  red ${B}internal${N} (internal: true, sin salida): ${B}db${N} (PostgreSQL 16 + RLS) · ${B}minio${N} (documentos) · backend
EOF
  remote <<EOF
cd $COMPOSE_DIR
echo; echo "\$ docker compose ps"
docker compose ps --format 'table {{.Service}}\t{{.Status}}\t{{.Networks}}'
echo; echo "\$ docker stats --no-stream"
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.PIDs}}'
echo; echo "commit desplegado: \$(cat /opt/oncargo/state/DEPLOYED)"
EOF
}

# ── 2. Puertos expuestos ────────────────────────────────────────────────────────────────────────────────────────
PORTS=(22 80 443 2019 4321 5432 8080 9000 9001)
scan_devtcp() {  # en paralelo: ~3 s en total aunque los puertos filtrados no respondan
  local p d; d="$(mktemp -d)"
  for p in "${PORTS[@]}"; do
    ( timeout 3 bash -c "exec 3<>/dev/tcp/$DOMAIN/$p" 2>/dev/null && touch "$d/$p" ) &
  done; wait
  for p in "${PORTS[@]}"; do
    if [[ -e "$d/$p" ]]; then printf '  %s%-5s abierto%s\n' "$G" "$p" "$N"
    else printf '  %-5s %scerrado/filtrado%s\n' "$p" "$D" "$N"; fi
  done; rm -rf "$d"
}
opt_2() {
  title "2 · Puertos expuestos: lo que se ve desde Internet frente a lo interno   (T5, T6, A1, A5)"
  say "Desde fuera solo deben responder 22, 80 y 443. La base (5432) y MinIO (9000) no existen para Internet."
  sub "Desde este equipo, contra $DOMAIN"
  if command -v docker >/dev/null && { docker image inspect oncargo-nmap:local >/dev/null 2>&1 || \
       printf 'FROM alpine:3.20\nRUN apk add --no-cache nmap\n' | docker build -q -t oncargo-nmap:local - >/dev/null 2>&1; }; then
    docker run --rm --network host oncargo-nmap:local nmap -Pn -sS --max-retries 1 -p "$(IFS=,; echo "${PORTS[*]}")" "$DOMAIN" 2>&1 \
      | grep -E '^[0-9]+/|Nmap done' | mask
  else scan_devtcp; fi
  sub "Dentro del VPS: quién escucha en el host"
  remote <<'EOF'
sudo ss -tlnp | awk 'NR>1 {split($6,a,"\""); printf "  %-22s %s\n", $4, a[2]}' | grep -v '127.0.0' | sort -u
echo "  (5432, 9000, 4321 y 8080 solo existen dentro de las redes de Docker; ningún proceso del host los publica)"
EOF
}

# ── 3. Firewall ─────────────────────────────────────────────────────────────────────────────────────────────────
opt_3() {
  title "3 · Firewall: UFW y la cadena DOCKER-USER   (T5, H-12)"
  say "Docker se salta UFW en los puertos que publica; DOCKER-USER es una lista blanca por puerto ORIGINAL (80/443)."
  remote <<'EOF'
echo "$ sudo ufw status verbose"; sudo ufw status verbose | sed -n '1,3p;/^To/,$p' | grep -v '^$'
echo; echo "$ sudo iptables -L DOCKER-USER -v -n   (contadores)"
sudo iptables -L DOCKER-USER -v -n | tail -n +2
echo; echo "$ sudo iptables -S FORWARD | head -2"; sudo iptables -S FORWARD | sed -n '2p'
EOF
}

# ── 4. TLS y cabeceras ──────────────────────────────────────────────────────────────────────────────────────────
opt_4() {
  title "4 · TLS y cabeceras de seguridad   (T8, T9, T11, A8)"
  say "Let's Encrypt renovado por Caddy, solo TLS 1.2/1.3, HSTS 1 año, CSP, sin cabecera Server. Por IP: 308 al dominio."
  local end days v
  end="$(echo | timeout 8 openssl s_client -connect "$DOMAIN:443" -servername "$DOMAIN" 2>/dev/null | openssl x509 -noout -enddate | cut -d= -f2)"
  echo | timeout 8 openssl s_client -connect "$DOMAIN:443" -servername "$DOMAIN" 2>/dev/null \
    | openssl x509 -noout -issuer -subject | sed 's/^/  /'
  if [[ -n "$end" ]]; then days=$(( ($(date -d "$end" +%s) - $(date +%s)) / 86400 )); ok "vence $end → $days días"; else bad "no se pudo leer el certificado"; fi
  for v in tls1 tls1_1 tls1_2 tls1_3; do
    if echo | timeout 6 openssl s_client "-$v" -connect "$DOMAIN:443" -servername "$DOMAIN" 2>&1 | grep -qE 'Cipher is [A-Z0-9]'; then
      printf '  %-8s %saceptado%s\n' "$v" "$G" "$N"; else printf '  %-8s %srechazado%s\n' "$v" "$D" "$N"; fi
  done
  sub "Cabeceras de https://$DOMAIN/login"
  curl -sI "https://$DOMAIN/login" | tr -d '\r' | grep -iE '^(strict-transport|content-security|x-frame|x-content|referrer|permissions-policy|alt-svc|server|via)' \
    | cut -c1-150 | sed 's/^/  /'
  curl -sI "https://$DOMAIN/login" | grep -qi '^server:' && bad "aparece la cabecera Server" || ok "sin cabeceras Server ni Via"
  sub "Acceso por IP sin TLS (A8, H-01)"
  printf '  http://<IP-VPS>/login → %s\n' "$(curl -s -o /dev/null -m 5 -w '%{http_code} %{redirect_url}' "http://$HOST_IP/login")"
  printf '  HTTP/3: %s\n' "$(curl -s -o /dev/null -m 8 --http3-only -w 'versión %{http_version}, %{http_code}' "https://$DOMAIN/" 2>/dev/null || echo 'curl sin soporte HTTP/3')"
}

# ── 5. SSH endurecido ───────────────────────────────────────────────────────────────────────────────────────────
opt_5() {
  title "5 · SSH endurecido: solo clave pública   (A7, R-11)"
  say "Sin contraseñas, solo los usuarios deploy y root (con clave), algoritmos modernos; fail2ban sshd + recidive."
  remote <<'EOF'
echo "$ sudo sshd -T (extracto)"
sudo sshd -T | grep -E '^(passwordauthentication|kbdinteractiveauthentication|pubkeyauthentication|permitrootlogin|maxauthtries|allowusers|x11forwarding|logingracetime) ' | sed 's/^/  /'
echo; echo "$ ssh -o PreferredAuthentications=password -o PubkeyAuthentication=no deploy@<servidor>   (intento con contraseña)"
ssh -o PreferredAuthentications=password,keyboard-interactive -o PubkeyAuthentication=no -o BatchMode=yes \
    -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR deploy@127.0.0.1 true 2>&1 | sed 's/127\.0\.0\.1/<servidor>/; s/^/  /'
EOF
  if [[ "${DEMO_SSH_EXTERNO:-0}" == 1 ]]; then
    sub "El mismo intento desde este equipo (cuenta 1 fallo en fail2ban sshd)"
    ssh -o PreferredAuthentications=password,keyboard-interactive -o PubkeyAuthentication=no -o BatchMode=yes \
        -o ConnectTimeout=8 "$VPS" true 2>&1 | mask | sed 's/^/  /'
  fi
  ok "la contraseña ni siquiera se ofrece: el servidor solo acepta «publickey»"
}

# ── 6. fail2ban ─────────────────────────────────────────────────────────────────────────────────────────────────
opt_6() {
  title "6 · fail2ban: jails y prueba de bloqueo con una IP de documentación   (A1, A2, A7)"
  say "203.0.113.0/24 es un rango reservado para documentación (RFC 5737): nadie real queda bloqueado."
  local doit=1
  [[ "${MODE:-menu}" == status ]] && doit=0
  remote <<EOF
for j in sshd recidive oncargo-caddy-probe oncargo-caddy-auth; do
  printf '  %-20s %s en %ss → bloqueo %ss · bloqueadas ahora: %s · total: %s\n' "\$j" "\$(sudo fail2ban-client get \$j maxretry)" \
    "\$(sudo fail2ban-client get \$j findtime)" "\$(sudo fail2ban-client get \$j bantime)" \
    "\$(sudo fail2ban-client status \$j | awk -F'\t' '/Currently banned/ {print \$2}')" \
    "\$(sudo fail2ban-client status \$j | awk -F'\t' '/Total banned/ {print \$2}')"
done
if [[ $doit == 1 ]]; then
  T=203.0.113.\$(( RANDOM % 200 + 20 ))
  echo; echo "\$ sudo fail2ban-client set oncargo-caddy-probe banip \$T"; sudo fail2ban-client set oncargo-caddy-probe banip \$T >/dev/null && echo "  bloqueada"
  echo "  en la lista de la jail: \$(sudo fail2ban-client status oncargo-caddy-probe | grep -o "\$T" | head -1)"
  echo "  en nftables (hooks input y forward): \$(sudo nft list ruleset 2>/dev/null | grep -c "\$T") apariciones"
  echo "\$ sudo fail2ban-client set oncargo-caddy-probe unbanip \$T"; sudo fail2ban-client set oncargo-caddy-probe unbanip \$T >/dev/null && echo "  desbloqueada"
  echo "  en nftables ahora: \$(sudo nft list ruleset 2>/dev/null | grep -c "\$T") apariciones"
fi
EOF
}

# ── 7. Respaldos ────────────────────────────────────────────────────────────────────────────────────────────────
opt_7() {
  title "7 · Respaldos cifrados y restauración en vivo   (SIM-1, R-10)"
  say "Diario 03:30 (timer de systemd = el «cron»), cifrado con age; la clave privada NO está en el servidor."
  remote <<'EOF'
echo "$ sudo oncargo-backup list"; sudo ls -lht --time-style=+%F\ %H:%M /var/backups/oncargo | tail -n +2 | awk '{printf "  %6s  %s %s  %s\n", $5, $6, $7, $8}' | head -8
L=$(sudo sh -c 'ls -1t /var/backups/oncargo/db-*.dump.age | head -1')
echo; echo "  ¿cifrado? primera línea de $(basename "$L"): $(sudo head -c 21 "$L")"
echo "  último respaldo correcto: $(sudo cat /var/lib/oncargo/backup-last-ok 2>/dev/null)"
echo "  manifiesto: $(sudo sh -c 'cat $(ls -1t /var/backups/oncargo/manifest-*.txt | head -1)' | grep -E '^(migracion|tablas|politicas_rls|organizaciones|perfiles|envios)=' | tr '\n' ' ')"
systemctl list-timers oncargo-backup.timer --no-pager | sed -n '1,2p' | cut -c1-110
EOF
  if [[ "${MODE:-menu}" == status ]]; then return; fi
  if confirm "¿Restaurar ahora el último respaldo en una base temporal LOCAL y contar filas? (~15 s)"; then
    "$HERE/sim1-restore.sh" --quick 2>&1 | mask
  fi
}

# ── 8. Monitoreo ────────────────────────────────────────────────────────────────────────────────────────────────
opt_8() {
  title "8 · Monitoreo: oncargo-health cada 5 min y alerta por ntfy   (KPI, NC-05)"
  say "Revisa web, API, APK, contenedores, disco, memoria, certificado y antigüedad del respaldo; avisa al cambiar de estado."
  remote <<'EOF'
echo "$ sudo oncargo-health -v"; sudo oncargo-health -v | sed 's/^/  /'
echo; sudo journalctl -t oncargo-health --since "-30 min" --no-pager -o short | tail -4 | cut -c1-110
EOF
  if [[ "${MODE:-menu}" != status ]] && confirm "¿Enviar una alerta de PRUEBA al celular (ntfy)?"; then
    "${SSH[@]}" 'sudo oncargo-health --test-alert' 2>&1 | mask | sed 's/^/  /'
  fi
}

# ── 9. Registro de accesos ──────────────────────────────────────────────────────────────────────────────────────
opt_9() {
  title "9 · Registro de accesos de Caddy con tokens redactados   (H-09, NC-04)"
  say "JSON por petición, 14 días (la IP es dato personal, Ley 29733). Cookie y Authorization ocultas; tokens de URL → REDACTED."
  remote <<'EOF'
L=/var/log/oncargo/caddy/access.log
echo "$ sudo tail access.log | jq   (IP enmascarada en esta salida)"
sudo tail -n 400 $L | jq -r 'select(.request) | [(.ts|tostring|.[11:19]), .status, .request.method, .request.host, (.request.uri|.[0:60]), .request.client_ip] | @tsv' 2>/dev/null \
  | grep -v '/q/health' | tail -8 | sed 's/^/  /'
echo; echo "  Cookie y Authorization: $(sudo grep -o '"\(Cookie\|Authorization\)":\[[^]]*\]' $L | sort | uniq -c | awk '{printf "%s×%s ", $2, $1}')"
echo "  tokens de URL redactados: $(sudo grep -c 'uri":"[^"]*REDACTED' $L) · ejemplo: $(sudo grep -o '"uri":"[^"]*REDACTED[^"]*"' $L | tail -1)"
echo "  códigos de estado (registro actual): $(sudo jq -r 'select(.status)|.status' $L | sort | uniq -c | sort -rn | head -6 | awk '{printf "%s×%s ", $2, $1}')"
EOF
}

# ── 10. Recursos ────────────────────────────────────────────────────────────────────────────────────────────────
opt_10() {
  title "10 · Recursos del host   (KPI de eficiencia)"
  remote <<'EOF'
echo "  carga:   $(cut -d' ' -f1-3 /proc/loadavg) (2 vCPU) · encendido: $(uptime -p)"
free -m | awk '/Mem:/ {printf "  memoria: %d MB usados de %d (disponibles %d MB)\n", $3, $2, $7} /Swap:/ {printf "  swap:    %d MB de %d\n", $3, $2}'
df -h / | awk 'NR==2 {printf "  disco:   %s de %s (%s)\n", $3, $2, $5}'
echo "  kernel:  $(uname -r) · reinicio pendiente: $([ -f /var/run/reboot-required ] && echo sí || echo no)"
docker stats --no-stream --format '{{.Name}}: {{.CPUPerc}} CPU · {{.MemUsage}}' | sed 's/^/  /'
EOF
  if [[ "${MODE:-menu}" == menu ]] && confirm "¿Abrir btop en vivo (q para salir)?"; then
    # Preset 1 de btop = CPU + procesos, sin el panel de red (no muestra direcciones).
    ssh -t -i "$KEY" -o ConnectTimeout=8 "$VPS" 'btop -p 1 2>/dev/null || htop'
  fi
}

# ── resumen no interactivo ──────────────────────────────────────────────────────────────────────────────────────
opt_status() {
  MODE=status
  title "OnCargo · estado de la infraestructura  ($(date '+%F %T %Z'))"
  sub "Servicio público"
  local u code
  for u in "https://$DOMAIN/" "https://$DOMAIN/login" "https://$DOMAIN/app/" "https://api.$DOMAIN/q/health/ready"; do
    code="$(curl -s -o /dev/null -m 10 -w '%{http_code}' "$u")"
    [[ "$code" == 200 ]] && ok "$u → $code" || bad "$u → $code"
  done
  remote <<EOF
cd $COMPOSE_DIR
echo; echo "▸ Contenedores y salud"; docker compose ps --format '{{.Service}}\t{{.Status}}' | column -t -s \$'\t' | sed 's/^/  /'
echo "  \$(sudo head -1 /var/lib/oncargo/health.txt) · commit \$(cat /opt/oncargo/state/DEPLOYED)"; sudo tail -n +2 /var/lib/oncargo/health.txt | sed 's/^/  /'
echo; echo "▸ Firewall"; echo "  UFW: \$(sudo ufw status | head -1 | cut -d' ' -f2) · permitidos: \$(sudo ufw status | awk '/ALLOW/ && !/v6/ {printf "%s ", \$1}')"
echo "  DOCKER-USER: \$(sudo iptables -S DOCKER-USER | grep -c -- '-j RETURN') reglas RETURN, \$(sudo iptables -S DOCKER-USER | grep -c -- '-j DROP') DROP (descartados: \$(sudo iptables -L DOCKER-USER -v -n | awk '/DROP/ {print \$1}') paquetes)"
echo; echo "▸ fail2ban"
for j in sshd recidive oncargo-caddy-probe oncargo-caddy-auth; do printf '  %-20s bloqueadas: %s · total: %s\n' "\$j" "\$(sudo fail2ban-client status \$j | awk -F'\t' '/Currently banned/ {print \$2}')" "\$(sudo fail2ban-client status \$j | awk -F'\t' '/Total banned/ {print \$2}')"; done
echo; echo "▸ Respaldos y timers"
echo "  \$(sudo sh -c 'ls -1 /var/backups/oncargo/db-*.age | wc -l') volcados · último: \$(sudo sh -c 'ls -1t /var/backups/oncargo/db-*.age | head -1' | xargs -r basename) · ok: \$(sudo cat /var/lib/oncargo/backup-last-ok 2>/dev/null)"
systemctl list-timers 'oncargo-*' --no-pager | awk 'NR>1 && /oncargo/ {printf "  %-22s próximo: %s %s %s\n", \$(NF-1), \$2, \$3, \$4}'
echo; echo "▸ Recursos"
free -m | awk '/Mem:/ {printf "  memoria %d/%d MB · ", \$3, \$2}'; df -h / | awk 'NR==2 {printf "disco %s (%s) · ", \$3, \$5}'; echo "carga \$(cut -d' ' -f1-3 /proc/loadavg) · kernel \$(uname -r)"
EOF
  sub "Puertos vistos desde este equipo"
  scan_devtcp | awk '{printf "%s %s · ", $1, $2} END {print ""}' | sed 's/^/  /; s/ · $//'
  local end
  end="$(echo | timeout 8 openssl s_client -connect "$DOMAIN:443" -servername "$DOMAIN" 2>/dev/null | openssl x509 -noout -enddate | cut -d= -f2)"
  sub "TLS"; [[ -n "$end" ]] && ok "certificado vigente $(( ($(date -d "$end" +%s) - $(date +%s)) / 86400 )) días (vence $end)" || bad "sin certificado"
}

# ── menú ────────────────────────────────────────────────────────────────────────────────────────────────────────
menu() {
  MODE=menu
  while true; do
    printf '\n%sOnCargo · demo de infraestructura%s  %s(VPS <IP-VPS> · %s)%s\n' "$B$C" "$N" "$D" "$DOMAIN" "$N"
    cat <<EOF
   1  Arquitectura y contenedores en vivo       6  fail2ban: jails y bloqueo de prueba
   2  Puertos expuestos (nmap) vs. internos     7  Respaldos y restauración en vivo
   3  Firewall: UFW y DOCKER-USER               8  Monitoreo y alerta al celular (ntfy)
   4  TLS y cabeceras de seguridad              9  Registro de accesos (tokens redactados)
   5  SSH: intento con contraseña rechazado    10  Recursos (resumen y btop)
   s  Resumen de todo (status)                  q  Salir
EOF
    printf '%sOpción: %s' "$B" "$N"; read -r o || exit 0
    case "$o" in
      [1-9]|10) "opt_$o"; pause ;;
      s|S) opt_status; MODE=menu; pause ;;
      q|Q) exit 0 ;;
    esac
  done
}

case "${1:-menu}" in
  menu) check_ssh || exit 1; menu ;;
  status|all) check_ssh || exit 1; opt_status ;;
  [1-9]|10) check_ssh || exit 1; MODE=single; "opt_$1" ;;
  -h|--help) sed -n '2,16p' "$0" ;;
  *) echo "uso: $0 [menu|status|all|1..10]" >&2; exit 2 ;;
esac
