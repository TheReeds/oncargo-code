# OnCargo — utilidades comunes de las simulaciones A1–A9 (se cargan con `source`; no se ejecutan solas).
# Reglas (EPE, Seguridad § 3.2 y autorizacion-pruebas.md): solo contra NUESTRO dominio y VPS, volumen bajo, sin
# borrar ni modificar datos, desde el equipo del equipo cuya IP figura en la autorización firmada.
set -uo pipefail
VPS="${VPS:-deploy@179.66.150.58}"
KEY="${SSH_KEY:-$HOME/.ssh/hostinger_kvm2}"
DOMAIN="${DOMAIN:-oncargo.tech}"
API="https://api.$DOMAIN"
WEB="https://$DOMAIN"
HOST_IP="${VPS#*@}"
SSH4=(ssh -4 -i "$KEY" -o BatchMode=yes -o ConnectTimeout=8 "$VPS")
SSH6=(ssh -6 -i "$KEY" -o BatchMode=yes -o ConnectTimeout=8 "${VPS%@*}@${VPS_V6:-$DOMAIN}")
ATTACKS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REG_DIR="${OC_REGISTRO:-$HOME/oncargo-pruebas}/$(date +%F)"
if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then B=$'\e[1m'; G=$'\e[32m'; R=$'\e[31m'; Y=$'\e[33m'; C=$'\e[36m'; N=$'\e[0m'
else B=; G=; R=; Y=; C=; N=; fi

mask() {
  HOST_IP="$HOST_IP" perl -pe '
    BEGIN { $h = quotemeta($ENV{HOST_IP}) }
    s/$h/<IP-VPS>/g; s/\bsrv\d+(?:\.hstgr\.cloud)?\b/<host-VPS>/g;
    s/\b(?!(?:10|127)\.)(?!172\.(?:1[6-9]|2\d|3[01])\.)(?!192\.168\.)(?!203\.0\.113\.)(?!198\.51\.100\.)(?!192\.0\.2\.)((?:25[0-5]|2[0-4]\d|1?\d?\d)\.(?:25[0-5]|2[0-4]\d|1?\d?\d)\.(?:25[0-5]|2[0-4]\d|1?\d?\d)\.(?:25[0-5]|2[0-4]\d|1?\d?\d))\b(?![-.]\d)/<IP-ORIGEN>/g;
    s/\b2[0-9a-f]{3}:[0-9a-f:]{4,}(?:\/\d+)?/<IPv6>/gi;
    s/("(?:access|refresh)Token":")[^"]+/${1}<oculto>/g;'
}
title() { printf '\n%s━━ %s ━━%s\n' "$C$B" "$*" "$N"; }
ok() { printf '  %sPASA%s  %s\n' "$G" "$N" "$*"; }
bad() { printf '  %sFALLA%s %s\n' "$R" "$N" "$*"; }
info() { printf '  %s\n' "$*"; }
vps() { "${SSH4[@]}" "$@" 2>&1 | mask; }

# Exige la autorización firmada (docs/infraestructura/semestre-2/autorizacion-pruebas.md del EPE):
#   OC_AUTORIZACION=<id de la autorización>  OC_AUT_HASTA=<AAAA-MM-DD, último día de la ventana>
require_authorization() {
  local id="${OC_AUTORIZACION:-}" hasta="${OC_AUT_HASTA:-}"
  if [[ -z "$id" || -z "$hasta" ]]; then
    echo "${R}Esta simulación es activa y necesita la autorización escrita firmada.${N}" >&2
    echo "  Exporta OC_AUTORIZACION=<id> y OC_AUT_HASTA=<AAAA-MM-DD> (ver autorizacion-pruebas.md)." >&2
    exit 3
  fi
  [[ "$(date +%F)" > "$hasta" ]] && { echo "${R}La autorización $id venció el $hasta.${N}" >&2; exit 3; }
  mkdir -p "$REG_DIR"; chmod 700 "$REG_DIR"
  printf '%s %s autorización=%s script=%s\n' "$(date -Is)" "$(whoami)@$(hostname)" "$id" "$(basename "$0")" >> "$REG_DIR/registro.txt"
}

# IP con la que el VPS nos ve por HTTP (la que fail2ban bloquearía). Se averigua con una marca única en el registro
# de Caddy: puede diferir de la de ifconfig.me si el proveedor usa CGNAT con varias salidas.
origin_ip() {
  [[ -n "${OC_ORIGEN:-}" ]] && { echo "$OC_ORIGEN"; return; }
  local mark="oc-origen-$RANDOM$RANDOM" ip=""
  curl -s -4 -o /dev/null "$WEB/?$mark" || true
  sleep 1
  ip="$("${SSH4[@]}" "sudo grep -m1 '$mark' /var/log/oncargo/caddy/access.log | jq -r .request.client_ip" 2>/dev/null)"
  OC_ORIGEN="$ip"; export OC_ORIGEN
  [[ -n "$REG_DIR" && -d "$REG_DIR" ]] && echo "$(date -Is) origen_http=$ip" >> "$REG_DIR/registro.txt"
  echo "$ip"
}
# Desbloquea la IP de origen en todas las jails (por SSH: las jails web nunca bloquean el 22).
unban_me() {
  local ip; ip="$(origin_ip)"; [[ -n "$ip" ]] || return 0
  "${SSH4[@]}" "sudo fail2ban-client unban $ip" >/dev/null 2>&1 || "${SSH6[@]}" "sudo fail2ban-client unban $ip" >/dev/null 2>&1
  info "IP de origen desbloqueada en fail2ban (<IP-ORIGEN>)"
}
banned_in() {  # banned_in <jail> → 0 si la IP de origen está bloqueada en esa jail
  local ip; ip="$(origin_ip)"
  "${SSH4[@]}" "sudo fail2ban-client status $1" 2>/dev/null | grep -q -- "$ip" || \
  "${SSH6[@]}" "sudo fail2ban-client status $1" 2>/dev/null | grep -q -- "$ip"
}
login_code() {  # login_code <url> <email> [cabeceras extra…] → código HTTP
  local url="$1" email="$2"; shift 2
  curl -s -4 -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' "$@" \
    -d "{\"email\":\"$email\",\"password\":\"incorrecta-$RANDOM\"}" "$url"
}
web_login_code() {  # login por la web (acción de Astro); 400 con «Demasiados intentos» = límite
  local email="$1" body
  body="$(curl -s -4 -X POST -H 'Content-Type: application/json' -H "Origin: $WEB" \
    -d "{\"email\":\"$email\",\"password\":\"incorrecta-$RANDOM\"}" "$WEB/_actions/auth.login")"
  case "$body" in *Demasiados*) echo 429 ;; *UNAUTHORIZED*|*incorrect*|*Correo*|*credencial*) echo 401 ;; *) echo "$(jq -r '.status // "?"' <<<"$body" 2>/dev/null)" ;; esac
}
