#!/usr/bin/env bash
# Publica el APK de pruebas en https://oncargo.tech/app/ (página con QR).
# Uso: infra/vps/publish-apk.sh [ruta.apk]   (por defecto el debug recién compilado)
set -euo pipefail
ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
APK="${1:-$ROOT/android/app/build/outputs/apk/debug/app-debug.apk}"
VPS="${VPS:-deploy@179.66.150.58}"
KEY="${SSH_KEY:-$HOME/.ssh/hostinger_kvm2}"
[[ -f "$APK" ]] || { echo "No existe $APK (compila con ./gradlew :app:assembleDebug)"; exit 1; }
NAME="$(grep -m1 -oE 'versionName *= *"[^"]+"' "$ROOT/android/app/build.gradle.kts" | cut -d'"' -f2 || true)"
CODE="$(grep -m1 -oE 'versionCode *= *[0-9]+' "$ROOT/android/app/build.gradle.kts" | grep -oE '[0-9]+' || true)"
SHA="$(git -C "$ROOT" rev-parse --short HEAD)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
printf '{"versionName":"%s (%s)","versionCode":%s,"sha256":"%s"}\n' \
  "${NAME:-0.1.0}" "$SHA" "${CODE:-1}" "$(sha256sum "$APK" | cut -d' ' -f1)" > "$TMP/version.json"
# Sube a nombre temporal y renombra: nadie descarga un APK a medias.
scp -i "$KEY" -o BatchMode=yes -q "$APK" "$VPS:/opt/oncargo/downloads/.oncargo.apk.part"
scp -i "$KEY" -o BatchMode=yes -q "$TMP/version.json" "$VPS:/opt/oncargo/downloads/version.json"
ssh -i "$KEY" -o BatchMode=yes "$VPS" 'mv /opt/oncargo/downloads/.oncargo.apk.part /opt/oncargo/downloads/oncargo.apk'
echo "✓ APK publicado ($(du -h "$APK" | cut -f1)) → https://oncargo.tech/app/"
