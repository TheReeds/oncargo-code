#!/usr/bin/env bash
# OnCargo — cadena DOCKER-USER (la instala harden.sh en /usr/local/sbin/oncargo-docker-user y la aplica
# oncargo-docker-user.service después de Docker, también tras cada reinicio).
#
# Docker publica puertos con DNAT y ese tráfico no pasa por las reglas INPUT de UFW. Hoy solo Caddy publica
# 80/443 (que UFW ya permite), pero si alguien agregara `ports:` a otro servicio (p. ej. 5432) quedaría
# expuesto a Internet sin que UFW lo note. Esta cadena hace de lista blanca: desde la interfaz pública solo
# pueden iniciarse conexiones hacia 80/tcp, 443/tcp y 443/udp de los contenedores. La salida de los
# contenedores y las respuestas no cambian.
#   Ver:      iptables -S DOCKER-USER
#   Revertir: iptables -F DOCKER-USER   (Docker deja la cadena vacía = todo permitido)
set -euo pipefail
WAN="${WAN_IF:-$(ip -4 route show default | awk '{print $5; exit}')}"
[[ -n "$WAN" ]] || { echo "sin interfaz por defecto" >&2; exit 1; }
for ipt in iptables ip6tables; do
  command -v "$ipt" >/dev/null || continue
  "$ipt" -N DOCKER-USER 2>/dev/null || true
  "$ipt" -F DOCKER-USER
  "$ipt" -A DOCKER-USER -m conntrack --ctstate RELATED,ESTABLISHED -j RETURN
  # Puerto ORIGINAL (antes del DNAT): un contenedor que escuche en su 80 interno publicado como 18080 no pasa.
  "$ipt" -A DOCKER-USER -i "$WAN" -p tcp -m conntrack --ctorigdstport 80 --ctdir ORIGINAL -j RETURN
  "$ipt" -A DOCKER-USER -i "$WAN" -p tcp -m conntrack --ctorigdstport 443 --ctdir ORIGINAL -j RETURN
  "$ipt" -A DOCKER-USER -i "$WAN" -p udp -m conntrack --ctorigdstport 443 --ctdir ORIGINAL -j RETURN
  "$ipt" -A DOCKER-USER -i "$WAN" -m conntrack --ctstate NEW,INVALID -j DROP
  "$ipt" -A DOCKER-USER -j RETURN
done
echo "DOCKER-USER aplicado (interfaz pública: $WAN)"
