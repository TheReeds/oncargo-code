#!/usr/bin/env bash
# Despliega en el VPS el último COMMIT (no el árbol de trabajo): git archive → /opt/oncargo/src → compose build/up.
# Uso: infra/vps/deploy.sh [ref]   (por defecto HEAD)
# Variables: VPS (deploy@179.66.150.58), SSH_KEY (~/.ssh/hostinger_kvm2)
set -euo pipefail
ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
REF="${1:-HEAD}"
VPS="${VPS:-deploy@179.66.150.58}"
SSH=(ssh -i "${SSH_KEY:-$HOME/.ssh/hostinger_kvm2}" -o BatchMode=yes "$VPS")
SHA="$(git -C "$ROOT" rev-parse --short "$REF")"
echo "→ Desplegando $SHA en $VPS"

# Código fresco en src.new; .env, secretos y descargas viven fuera del árbol y se conservan.
git -C "$ROOT" archive --format=tar "$REF" backend web infra \
  | "${SSH[@]}" "rm -rf /opt/oncargo/src.new && mkdir -p /opt/oncargo/src.new && tar -x -C /opt/oncargo/src.new"

"${SSH[@]}" bash -s "$SHA" <<'REMOTE'
set -euo pipefail
SHA="$1"
cd /opt/oncargo
mkdir -p state/secrets downloads
# Primer despliegue: genera .env y claves JWT en state/ (nunca salen del servidor).
if [[ ! -f state/.env ]]; then
  (cd src.new/infra/vps && ./bootstrap.sh) && mv src.new/infra/vps/.env state/.env && cp src.new/infra/secrets/*.pem state/secrets/
fi
ln -sfn /opt/oncargo/state/.env src.new/infra/vps/.env
rm -rf src.new/infra/secrets && ln -sfn /opt/oncargo/state/secrets src.new/infra/secrets
rm -rf src.old && { [[ -d src ]] && mv src src.old || true; } && mv src.new src
echo "$SHA" > state/DEPLOYED
# Página de descarga (el APK y version.json no se tocan)
cp src/infra/vps/downloads/index.html src/infra/vps/downloads/qr-oncargo-app.png downloads/
cd src/infra/vps
docker compose build --pull
docker compose up -d --remove-orphans
docker image prune -f >/dev/null
docker compose ps --format 'table {{.Service}}\t{{.Status}}'
REMOTE
echo "✓ Desplegado $SHA"
