#!/usr/bin/env bash
# A7 — Fuerza bruta SSH contra el VPS: 5 intentos sin clave válida por IPv4 → fail2ban sshd bloquea la IP 1 h.
#   CUIDADO: el bloqueo incluye el 22 por IPv4. El script comprueba ANTES que hay SSH por IPv6 (o con VPS_V6=<ipv6>),
#   desbloquea por IPv6 al final y lo verifica. Si no hay IPv6: usa otra red (datos del celular) como origen
#   o la consola web de Hostinger para `fail2ban-client unban <ip>`. 3 bloqueos sshd en un día → recidive (1 semana):
#   no repetir más de una vez al día.
source "$(dirname "$0")/common.sh"; require_authorization
title "A7 · Fuerza bruta SSH"
"${SSH6[@]}" 'echo v6-ok' 2>/dev/null | grep -q v6-ok || { bad "no hay SSH por IPv6: no se ejecuta (quedarías fuera 1 h)"; exit 1; }
ok "SSH por IPv6 disponible para desbloquear"
ip4="$("${SSH4[@]}" 'echo ${SSH_CLIENT%% *}' 2>/dev/null)"
for i in 1 2 3 4 5 6; do
  ssh -4 -o PreferredAuthentications=password,keyboard-interactive -o PubkeyAuthentication=no -o BatchMode=yes \
      -o ConnectTimeout=6 "nadie$i@$HOST_IP" true 2>&1 | mask | sed 's/^/  /'
done
sleep 3
if "${SSH6[@]}" "sudo fail2ban-client status sshd" 2>/dev/null | grep -q -- "$ip4"; then ok "fail2ban sshd bloqueó la IP de origen (IPv4)"
else bad "la IP no aparece en sshd"; fi
"${SSH4[@]}" true 2>/dev/null && bad "el SSH por IPv4 sigue abierto" || ok "SSH por IPv4 rechazado mientras dura el bloqueo"
"${SSH6[@]}" "sudo fail2ban-client set sshd unbanip $ip4" >/dev/null 2>&1
"${SSH4[@]}" 'echo ok' 2>/dev/null | grep -q ok && ok "desbloqueada; SSH por IPv4 de vuelta" || bad "sigue bloqueada: ssh -6 … sudo fail2ban-client unban <ip>"
