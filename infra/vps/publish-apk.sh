#!/usr/bin/env bash
# Publica el APK de pruebas en https://oncargo.tech/app/ (página con QR) junto con version.json.
# Uso: infra/vps/publish-apk.sh [ruta.apk]
#   Por defecto: android/app/build/outputs/apk/staging/app-staging.apk
#   (cd android && ./gradlew :app:assembleStaging; ver android/AGENTS.md › «APK de pruebas»).
# versionName/versionCode salen de output-metadata.json, que Gradle escribe junto al APK.
# Notas de la versión: infra/vps/app-release-notes.txt (una línea = un punto; # = comentario).
# Variables: VPS (deploy@179.66.150.58), SSH_KEY (~/.ssh/hostinger_kvm2), ALLOW_DEBUG=1 para subir un APK debug.
set -euo pipefail
ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
APK="${1:-$ROOT/android/app/build/outputs/apk/staging/app-staging.apk}"
VPS="${VPS:-deploy@179.66.150.58}"
KEY="${SSH_KEY:-$HOME/.ssh/hostinger_kvm2}"
NOTES="$ROOT/infra/vps/app-release-notes.txt"
[[ -f "$APK" ]] || { echo "No existe $APK (compila con: cd android && ./gradlew :app:assembleStaging)"; exit 1; }

META="$(dirname "$APK")/output-metadata.json"
[[ -f "$META" ]] || { echo "Falta $META (el APK no salió de Gradle)"; exit 1; }

# Firma: el APK de pruebas debe ir con el keystore de pruebas, nunca con el de debug
# (si no, los testers tendrían que desinstalar para actualizar).
APKSIGNER="$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
if [[ -n "$APKSIGNER" ]]; then
  CERT="$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | grep -m1 'certificate DN' || true)"
  [[ -n "$CERT" ]] || { echo "El APK no está firmado"; exit 1; }
  if [[ "$CERT" == *"Android Debug"* && "${ALLOW_DEBUG:-0}" != 1 ]]; then
    echo "El APK está firmado con la clave de debug. Publica la variante staging (o ALLOW_DEBUG=1)."; exit 1
  fi
  echo "Firma: ${CERT#*: }"
fi

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
python3 - "$META" "$APK" "$NOTES" "$(git -C "$ROOT" rev-parse --short HEAD)" > "$TMP/version.json" <<'PY'
import datetime, hashlib, json, os, sys
meta_path, apk, notes_path, sha = sys.argv[1:5]
meta = json.load(open(meta_path))
el = (meta.get("elements") or [{}])[0]
h = hashlib.sha256()
with open(apk, "rb") as f:
    for chunk in iter(lambda: f.read(1 << 20), b""):
        h.update(chunk)
notes = []
if os.path.exists(notes_path):
    for line in open(notes_path, encoding="utf-8"):
        line = line.strip()
        if line and not line.startswith("#"):
            notes.append(line.lstrip("-• ").strip())
print(json.dumps({
    "versionName": el.get("versionName", "0.0.0"),
    "versionCode": el.get("versionCode", 1),
    "applicationId": meta.get("applicationId"),
    "variant": meta.get("variantName"),
    "gitSha": sha,
    "sizeBytes": os.path.getsize(apk),
    "sha256": h.hexdigest(),
    "minAndroid": "10",
    "publishedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds"),
    "changelog": notes[:8],
}, ensure_ascii=False, indent=2))
PY
cat "$TMP/version.json"

# Sube a nombre temporal y renombra: nadie descarga un APK a medias.
scp -i "$KEY" -o BatchMode=yes -q "$APK" "$VPS:/opt/oncargo/downloads/.oncargo.apk.part"
scp -i "$KEY" -o BatchMode=yes -q "$TMP/version.json" "$VPS:/opt/oncargo/downloads/.version.json.part"
ssh -i "$KEY" -o BatchMode=yes "$VPS" 'cd /opt/oncargo/downloads && chmod 644 .oncargo.apk.part .version.json.part && mv .oncargo.apk.part oncargo.apk && mv .version.json.part version.json'
echo "✓ APK publicado ($(du -h "$APK" | cut -f1)) → https://oncargo.tech/app/"
