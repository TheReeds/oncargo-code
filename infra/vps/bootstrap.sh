#!/usr/bin/env bash
# Primera vez en el VPS: genera infra/vps/.env con contraseñas aleatorias y las claves JWT.
# No sobreescribe nada existente. Los secretos nunca salen del servidor.
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -f .env ]]; then
  rnd() { openssl rand -base64 36 | tr -d '/+=' | cut -c1-32; }
  umask 077
  cat > .env <<ENV
DOMAIN=oncargo.tech
PUBLIC_IP=$(curl -fsS4 https://ifconfig.me || echo 127.0.0.1)
POSTGRES_SUPERUSER_PASSWORD=$(rnd)
DB_ADMIN_PASSWORD=$(rnd)
DB_APP_PASSWORD=$(rnd)
MINIO_ROOT_USER=oncargo-minio
MINIO_ROOT_PASSWORD=$(rnd)
# Clave HMAC del enlace de baja de los correos (V24). Obligatoria: sin ella el backend no arranca.
LOGYX_UNSUBSCRIBE_SECRET=$(rnd)$(rnd)
# Clave AES-256 de los datos de cobro del transportista (V24, ADR-0014). Respáldala: sin ella no se leen.
LOGYX_PAYOUT_KEY=$(openssl rand -base64 32)
# Entorno de pruebas: cuentas demo (*@logyx.pe; contraseña LOGYX_DEMO_PASSWORD, la agrega deploy.sh). En producción real: false.
DEMO_DATA=true
# Cookie de sesión solo por HTTPS (el acceso por IP redirige al dominio).
COOKIE_SECURE=true
DOWNLOADS_DIR=/opt/oncargo/downloads
ENV
  echo ".env creado"
fi
../scripts/gen-jwt-keys.sh
