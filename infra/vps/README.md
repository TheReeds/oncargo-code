# OnCargo — VPS de pruebas (Hostinger KVM 2)

| | |
|---|---|
| Servidor | Hostinger KVM 2 · Ubuntu 24.04 · 2 vCPU · 8 GB RAM · 96 GB · IP `179.66.150.58` |
| Acceso | `ssh -i ~/.ssh/hostinger_kvm2 deploy@179.66.150.58` (root solo con clave; sin contraseñas) |
| Web | `https://oncargo.tech` (mientras no haya DNS: `http://179.66.150.58`) |
| API (Android) | `https://api.oncargo.tech` (por IP: `http://179.66.150.58/api/...`) |
| App de pruebas | `https://oncargo.tech/app/` — página con QR y el APK |

## Arquitectura
```
Internet ─► Caddy :80/:443 (HTTPS automático, HSTS, gzip/zstd)
              ├─ oncargo.tech        → web:4321 (Astro SSR)   · /app/* → /opt/oncargo/downloads
              ├─ api.oncargo.tech    → backend:8080 (Quarkus; SSE sin buffer)
              └─ http://<IP>         → igual, sin TLS (solo hasta que el DNS apunte)
red internal: db (Postgres 16 + RLS) · minio (documentos) · backend
```
Solo Caddy publica puertos en el host, así UFW (22/80/443) no queda puenteado por Docker.
Endurecimiento aplicado: usuario `deploy` con sudo, SSH solo con clave, UFW, fail2ban (sshd),
unattended-upgrades, 2 GB de swap, logs de Docker rotados (10 MB × 3), zona horaria America/Lima.

## Despliegue
```bash
infra/vps/deploy.sh            # despliega HEAD (git archive, nunca el árbol de trabajo)
infra/vps/publish-apk.sh       # sube android/app/build/outputs/apk/debug/app-debug.apk
```
En el servidor:
```
/opt/oncargo/src         código del commit desplegado (src.old = anterior, para volver atrás)
/opt/oncargo/state/.env  contraseñas aleatorias generadas por bootstrap.sh (chmod 600, no salen del VPS)
/opt/oncargo/state/secrets  claves JWT
/opt/oncargo/downloads   index.html (QR), oncargo.apk, version.json
```
Operación: `cd /opt/oncargo/src/infra/vps && docker compose ps | logs -f backend | restart web`.

## Entorno de pruebas vs. producción
`state/.env` trae `DEMO_DATA=true` (cuentas `*@logyx.pe` / `Logyx2026!`) y `COOKIE_SECURE=false` para probar por IP.
Cuando el dominio tenga HTTPS: `COOKIE_SECURE=true`. Para producción real: `DEMO_DATA=false`, base nueva,
documentos en Cloudflare R2 en lugar de MinIO y backups `pg_dump` diarios a R2 (pendiente).

## DNS (panel de Hostinger → Dominios → oncargo.tech → DNS)
| Tipo | Nombre | Valor |
|---|---|---|
| A | `@` | `179.66.150.58` |
| A | `www` | `179.66.150.58` |
| A | `api` | `179.66.150.58` |
Borra los registros A/CNAME del parking. Caddy pide los certificados solo en cuanto el DNS propaga.
