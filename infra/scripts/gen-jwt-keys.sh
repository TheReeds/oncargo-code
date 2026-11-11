#!/usr/bin/env bash
# Genera el par RSA 2048 del JWT en infra/secrets/ (PKCS#8). No sobreescribe claves existentes.
# Los archivos quedan 0644 dentro de un directorio 0700: los secretos de compose son bind mounts y el contenedor
# (usuario 10001) debe poder leerlos; el directorio impide que otros usuarios del host los lean.
set -euo pipefail
DIR="$(cd "$(dirname "$0")/.." && pwd)/secrets"
mkdir -p "$DIR" && chmod 700 "$DIR"
if [[ -f "$DIR/jwt-private.pem" ]]; then
  echo "Ya existe $DIR/jwt-private.pem; bórrala para regenerar (invalida las sesiones)."; exit 0
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$DIR/jwt-private.pem" 2>/dev/null
openssl pkey -in "$DIR/jwt-private.pem" -pubout -out "$DIR/jwt-public.pem"
chmod 644 "$DIR/jwt-private.pem" "$DIR/jwt-public.pem"
echo "Claves JWT generadas en $DIR"
