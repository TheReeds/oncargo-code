#!/usr/bin/env bash
# OnCargo — endurecimiento idempotente del sistema operativo del VPS (Ubuntu 24.04). Corre como root.
#
#   sudo /opt/oncargo/src/infra/vps/harden.sh [paso…]      (tras deploy.sh; sin pasos = todos)
#   pasos: packages ssh sysctl journald firewall fail2ban auditd docker secrets ops
#
# Es la parte del VPS que no vive en contenedores: con esto y deploy.sh el servidor se reconstruye desde el repo.
# Lo que ya hizo el aprovisionamiento (usuario deploy, 10-hardening.conf de sshd, UFW 22/80/443, fail2ban sshd,
# unattended-upgrades, swap, logs de Docker) se comprueba o se completa, nunca se quita.
# Cada archivo del sistema que cambia se respalda antes como <archivo>.bak-<fecha>. Reversión: README § Seguridad.
# NUNCA toca el puerto 22, ni las claves autorizadas, ni reinicia contenedores o la base de datos.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo "Ejecuta con sudo" >&2; exit 1; }
HERE="$(cd "$(dirname "$0")" && pwd)"
HOST="$HERE/host"
STAMP="$(date +%Y%m%d-%H%M%S)"
STEPS=("$@"); [[ ${#STEPS[@]} -gt 0 ]] || STEPS=(packages ssh sysctl journald firewall fail2ban auditd docker secrets ops)
say() { printf '\n== %s\n' "$*"; }

# install_file <origen> <destino> [modo] → 0 si cambió (respalda el anterior), 1 si ya estaba igual
install_file() {
  local src="$1" dst="$2" mode="${3:-0644}"
  if [[ -f "$dst" ]] && cmp -s "$src" "$dst"; then return 1; fi
  [[ -f "$dst" ]] && cp -a "$dst" "$dst.bak-$STAMP" && echo "  respaldo: $dst.bak-$STAMP"
  install -D -m "$mode" "$src" "$dst"; echo "  instalado: $dst"
}

step_packages() {
  say "Paquetes: age (cifrado de respaldos), auditd, jq"
  local need=()
  for p in age auditd jq; do dpkg -s "$p" >/dev/null 2>&1 || need+=("$p"); done
  if ((${#need[@]})); then DEBIAN_FRONTEND=noninteractive apt-get install -y -q "${need[@]}"; else echo "  ya instalados"; fi
}

step_ssh() {
  say "SSH: 20-oncargo.conf (AllowUsers, sin reenvío X11/agente, algoritmos modernos)"
  local dst=/etc/ssh/sshd_config.d/20-oncargo.conf
  if install_file "$HOST/sshd-20-oncargo.conf" "$dst" 0644; then
    if ! sshd -t; then
      echo "  sshd -t FALLÓ: se restaura la versión anterior" >&2
      if [[ -f "$dst.bak-$STAMP" ]]; then mv "$dst.bak-$STAMP" "$dst"; else rm -f "$dst"; fi
      sshd -t; exit 1
    fi
    # reload: las sesiones abiertas siguen vivas; solo las nuevas usan la configuración nueva.
    systemctl reload ssh
    echo "  sshd recargado. PRUEBA UN LOGIN NUEVO antes de cerrar esta sesión."
  else echo "  sin cambios"; fi
  sshd -T | grep -E '^(port|permitrootlogin|passwordauthentication|allowusers|x11forwarding|maxauthtries) ' | sed 's/^/  /'
}

step_sysctl() {
  say "sysctl de red y kernel"
  if install_file "$HOST/sysctl-60-oncargo.conf" /etc/sysctl.d/60-oncargo.conf; then sysctl -q -p /etc/sysctl.d/60-oncargo.conf; fi
  sysctl net.ipv4.tcp_syncookies net.ipv4.conf.all.rp_filter net.ipv4.conf.all.send_redirects net.ipv4.ip_forward | sed 's/^/  /'
}

step_journald() {
  say "journald: 1 GB máx., 30 días"
  if install_file "$HOST/journald-60-oncargo.conf" /etc/systemd/journald.conf.d/60-oncargo.conf; then
    systemctl restart systemd-journald
  fi
  journalctl --disk-usage | sed 's/^/  /'
}

step_firewall() {
  say "Firewall: UFW (comprobación) y DOCKER-USER (lista blanca de puertos publicados)"
  # UFW se configuró al aprovisionar; aquí solo se asegura que las reglas sigan (idempotente, sin reset).
  ufw allow 22/tcp >/dev/null; ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null; ufw allow 443/udp >/dev/null
  ufw status | grep -q '^Status: active' || { echo "  UFW inactivo: actívalo a mano (ufw enable) tras revisar" >&2; }
  install_file "$HOST/docker-user.sh" /usr/local/sbin/oncargo-docker-user 0755 || true
  install_file "$HOST/systemd/oncargo-docker-user.service" /etc/systemd/system/oncargo-docker-user.service || true
  systemctl daemon-reload
  systemctl enable oncargo-docker-user.service >/dev/null 2>&1
  systemctl restart oncargo-docker-user.service
  iptables -S DOCKER-USER | sed 's/^/  /'
}

step_fail2ban() {
  say "fail2ban: recidive y jails de Caddy (registro de accesos)"
  install -d -m 0750 -o root -g adm /var/log/oncargo/caddy
  [[ -f /var/log/oncargo/caddy/access.log ]] || install -m 0640 -o root -g adm /dev/null /var/log/oncargo/caddy/access.log
  local changed=0
  install_file "$HOST/fail2ban/jail-oncargo.local" /etc/fail2ban/jail.d/oncargo.local && changed=1
  for f in "$HOST"/fail2ban/filter.d/*.conf; do
    install_file "$f" "/etc/fail2ban/filter.d/$(basename "$f")" && changed=1
  done
  if ! fail2ban-client -t >/dev/null; then echo "  configuración de fail2ban inválida" >&2; fail2ban-client -t; exit 1; fi
  if ((changed)); then systemctl restart fail2ban; sleep 3; fi
  fail2ban-client status | sed 's/^/  /'
}

step_auditd() {
  say "auditd: reglas sobre accesos, secretos y Docker"
  if install_file "$HOST/audit-60-oncargo.rules" /etc/audit/rules.d/60-oncargo.rules 0640; then augenrules --load >/dev/null; fi
  systemctl enable --now auditd >/dev/null 2>&1
  echo "  reglas activas: $(auditctl -l | grep -c -- '-k ')"
}

step_docker() {
  say "Docker: live-restore (reiniciar dockerd sin bajar contenedores)"
  local f=/etc/docker/daemon.json tmp
  tmp="$(mktemp)"
  jq '. + {"live-restore": true}' "$f" > "$tmp"
  if cmp -s <(jq -S . "$f") <(jq -S . "$tmp"); then echo "  sin cambios"; rm -f "$tmp"
  else
    dockerd --validate --config-file "$tmp" >/dev/null
    cp -a "$f" "$f.bak-$STAMP"; install -m 0644 "$tmp" "$f"; rm -f "$tmp"
    # live-restore se puede cambiar con reload (SIGHUP): no reinicia contenedores.
    systemctl reload docker
    echo "  live-restore activado (respaldo: $f.bak-$STAMP)"
  fi
  docker info --format '  live-restore={{.LiveRestoreEnabled}} logging={{.LoggingDriver}}'
}

step_secrets() {
  say "Secretos: permisos de /opt/oncargo/state"
  local st=/opt/oncargo/state
  [[ -d $st ]] || { echo "  no existe $st (primero deploy.sh)"; return; }
  chmod 700 "$st"; chmod 600 "$st/.env"
  if [[ -d $st/secrets ]]; then
    chmod 750 "$st/secrets"
    # La pública puede ser legible; la privada solo para deploy y el grupo 10001 (usuario del backend en el contenedor).
    [[ -f $st/secrets/jwt-private.pem ]] && chgrp 10001 "$st/secrets/jwt-private.pem" && chmod 640 "$st/secrets/jwt-private.pem"
    [[ -f $st/secrets/jwt-public.pem ]] && chmod 644 "$st/secrets/jwt-public.pem"
  fi
  stat -c '  %a %U:%g %n' "$st" "$st/.env" "$st"/secrets "$st"/secrets/*.pem 2>/dev/null
}

step_ops() {
  say "Operación: respaldo diario cifrado y chequeo de salud"
  install -d -m 0755 /etc/oncargo
  install -d -m 0700 /var/backups/oncargo
  install -d -m 0755 /var/lib/oncargo
  install_file "$HERE/backup.sh" /usr/local/sbin/oncargo-backup 0755 || true
  install_file "$HERE/healthcheck.sh" /usr/local/sbin/oncargo-health 0755 || true
  install_file "$HERE/backup-recipients.txt" /etc/oncargo/backup-recipients.txt 0644 || true
  for u in oncargo-backup.service oncargo-backup.timer oncargo-health.service oncargo-health.timer; do
    install_file "$HOST/systemd/$u" "/etc/systemd/system/$u" || true
  done
  systemctl daemon-reload
  systemctl enable --now oncargo-backup.timer oncargo-health.timer >/dev/null 2>&1
  systemctl list-timers 'oncargo-*' --no-pager | sed 's/^/  /'
}

for s in "${STEPS[@]}"; do "step_$s"; done
echo; echo "✓ harden.sh: ${STEPS[*]}"
