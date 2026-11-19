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
              ├─ api.oncargo.tech    → backend:8080 (Quarkus; SSE sin buffer; /q/* cerrado salvo /q/health)
              └─ http://<IP>         → 308 a https://oncargo.tech (ya no sirve contenido en claro)
red internal (internal: true, sin salida a Internet): db (Postgres 16 + RLS) · minio (documentos) · backend
```
Solo Caddy publica puertos en el host; además la cadena `DOCKER-USER` deja pasar desde Internet solo 80/443 hacia
contenedores, así un `ports:` agregado por error no queda expuesto aunque Docker se salte UFW.
Endurecimiento del sistema operativo: `harden.sh` (sección [Seguridad del host](#seguridad-del-host-hardensh)).

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
Servidor nuevo o cambios en `harden.sh`, `host/`, `backup.sh`, `healthcheck.sh`: tras `deploy.sh`,
`ssh … 'sudo /opt/oncargo/src/infra/vps/harden.sh'` (idempotente; con pasos sueltos: `harden.sh ssh firewall …`).

## Seguridad del host (`harden.sh`)
Estado al 2026-11-19 (evidencia en la documentación del EPE, Infraestructura › Semestre 2). ✅ aplicado · ⏳ pendiente.

| Control | Estado | Dónde / cómo revertir |
|---|---|---|
| SSH solo con clave, `root` solo con clave, `MaxAuthTries 4` | ✅ (aprovisionamiento) | `/etc/ssh/sshd_config.d/10-hardening.conf` |
| SSH: `AllowUsers deploy root`, sin X11/agente/túneles, `LoginGraceTime 30`, KEX/cifrados/MAC modernos (sin SHA-1 ni NIST) | ✅ | `host/sshd-20-oncargo.conf` → `/etc/ssh/sshd_config.d/20-oncargo.conf`. Revertir: borrar el archivo, `sshd -t && systemctl reload ssh` |
| Puerto SSH 22 | se mantiene | Cambiarlo (p. ej. 2222) reduce ruido de bots pero no protege más que la clave; exige `ssh.socket`, UFW y avisar al equipo. **Requiere aprobación** |
| UFW: entrada `deny`, 22/tcp, 80/tcp, 443/tcp+udp | ✅ | `harden.sh firewall` solo comprueba |
| `DOCKER-USER`: desde `eth0` solo nuevas conexiones a 80/tcp, 443/tcp, 443/udp (puerto original, antes del DNAT) | ✅ | `host/docker-user.sh` + `oncargo-docker-user.service`. Revertir: `systemctl disable --now oncargo-docker-user; iptables -F DOCKER-USER` |
| fail2ban: `sshd` (5 fallos → 1 h), `recidive` (3 bloqueos sshd/día → 1 semana, todos los puertos) | ✅ | `host/fail2ban/` → `/etc/fail2ban/jail.d/oncargo.local` |
| fail2ban sobre Caddy: `oncargo-caddy-probe` (3 rutas de escaneo → 1 día) y `oncargo-caddy-auth` (20 respuestas 429 en 10 min → 1 h), **solo 80/443**, en INPUT y FORWARD (nftables) | ✅ | Desbloquear: `fail2ban-client unban <ip>`. Revertir: borrar `jail.d/oncargo.local`, `systemctl restart fail2ban` |
| Registro de accesos de Caddy (JSON, 20 MiB × 10, 14 días; tokens `?t=`, `?token=`, `/s/…` redactados) | ✅ | `/var/log/oncargo/caddy/access.log` (snippet `access_log` del Caddyfile) |
| Cabeceras: HSTS 1 año, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, sin `Server` ni `Via`; CSP y `X-Frame-Options` en `/app/` | ✅ | Caddyfile. La web y la API ya mandan su CSP |
| HSTS `preload` | ⏳ decisión | Irreversible en la práctica (lista de los navegadores): solo cuando todo subdominio futuro sea HTTPS. **Requiere aprobación** |
| `/q/*` de Quarkus cerrado en `api.` salvo `/q/health*` | ✅ | Caddyfile (`@quarkus_internal`) |
| Acceso `http://<IP>` → 308 al dominio (antes servía el login sin TLS) | ✅ | Caddyfile |
| Límite de tasa en Caddy | ⏳ no aplica | `caddy:2.10-alpine` no trae `rate_limit` (módulo de terceros: exige imagen propia con xcaddy). Cubren `AuthRateLimits` (app) + fail2ban `oncargo-caddy-auth`; en el borde, Cloudflare |
| Contenedores: `no-new-privileges`, `cap_drop: ALL` (+ mínimas en db y caddy), `pids_limit`, `read_only` en caddy/web/backend con `tmpfs /tmp` | ✅ | `compose.yml` (`x-hardening`). db y minio sin `read_only` (escriben fuera de sus volúmenes); todos corren sin root salvo el entrypoint de db y caddy (root sin capacidades salvo `NET_BIND_SERVICE`) |
| Red `internal` con `internal: true` (db y minio sin salida a Internet) | ✅ | `compose.yml` |
| MinIO: consola desactivada (`MINIO_BROWSER=off`), bucket privado (anónimo → 403), sin puertos | ✅ | ⏳ usuario MinIO limitado al bucket en vez de las credenciales raíz |
| AppArmor `docker-default` en todos los contenedores | ✅ (Docker) | Perfiles propios: descartado (mantenimiento alto, poco beneficio sobre `cap_drop` + `no-new-privileges`) |
| sysctl: syncookies, sin redirects ni source routing, `rp_filter=2` (laxo, compatible con Docker), `kptr_restrict=2` | ✅ | `host/sysctl-60-oncargo.conf`. Revertir: borrar el archivo y `sysctl --system` |
| journald 1 GB / 30 días | ✅ | `host/journald-60-oncargo.conf` |
| auditd: cambios en sshd, sudoers, cuentas, `authorized_keys`, `state/`, `daemon.json`, UFW, fail2ban | ✅ | `host/audit-60-oncargo.rules`; `ausearch -k oncargo_secrets -i` |
| Docker `live-restore` (actualizar/reiniciar dockerd sin bajar contenedores) | ✅ | `/etc/docker/daemon.json` (respaldo `.bak-<fecha>`) |
| Permisos: `state/` 700, `.env` 600, `secrets/` 750, `jwt-private.pem` 640 grupo 10001 (usuario del backend) | ✅ | `harden.sh secrets` |
| `unattended-upgrades` (seguridad diaria) | ✅ | ⏳ reinicio por kernel: hay `/var/run/reboot-required`; reiniciar en una ventana acordada (`sudo reboot`; todo vuelve solo) o activar `Automatic-Reboot` con aprobación |
| Respaldo diario cifrado (age) de Postgres, MinIO y `state/`, 14 días, prueba de restauración | ✅ local | `backup.sh` (abajo). ⏳ copia **fuera del VPS** (R2 u otro: credenciales del usuario) |
| Chequeo de salud cada 5 min (web, API, APK, contenedores, disco, memoria, certificado, respaldo) | ✅ | `healthcheck.sh`. ⏳ canal de alerta y latido externo (`/etc/oncargo/alert.env`) |
| Cloudflare (proxy, WAF, Bot Fight Mode) | ⏳ plan | Cambia DNS (usuario). Ver docs/security.md § Infraestructura |
| Contraseña demo pública (`Logyx2026!`) | ⏳ propuesta | `DEMO_PASSWORD` en `state/.env` leída por el seeder (toca `backend/`): ver docs/security.md |

### Respaldos (`backup.sh` → `/usr/local/sbin/oncargo-backup`)
`oncargo-backup.timer` corre a diario a las 03:30 (Lima): `pg_dump -Fc` validado con `pg_restore -l`, tar del volumen de
MinIO y de `/opt/oncargo/state` (`.env` con todas las claves, incluidas `LOGYX_UNSUBSCRIBE_SECRET` y `LOGYX_PAYOUT_KEY`, y las
claves JWT). Todo se cifra con **age** para las claves públicas de `backup-recipients.txt`; la **identidad privada no está en el
VPS** (la guarda el administrador; sin ella los respaldos no sirven). Destino `/var/backups/oncargo` (root, 700), 14 días,
manifiesto con sha256 y conteos.
```bash
sudo oncargo-backup run | list
ssh … 'sudo oncargo-backup verify -' < backup-age-identity.txt   # restaura en un Postgres temporal sin red y compara conteos
age -d -i backup-age-identity.txt db-<fecha>.dump.age > db.dump # descifrar en otro equipo
```
Restaurar en el VPS: Datacenter › Runbook 4.4 de la documentación del EPE (`pg_restore --clean` con el backend detenido).
Copia externa: instalar `rclone`, `/etc/oncargo/rclone.conf` (token R2 de solo escritura en el bucket) y
`BACKUP_RCLONE_REMOTE=r2:oncargo-backups/vps` en `/etc/oncargo/backup.env`.

### Salud y alertas (`healthcheck.sh` → `/usr/local/sbin/oncargo-health`)
`oncargo-health.timer` cada 5 min. Estado en `/var/lib/oncargo/health.txt` y `journalctl -t oncargo-health`; alerta solo en
cambios de estado. Canales opcionales en `/etc/oncargo/alert.env` (no se versiona):
`ALERT_WEBHOOK_URL=https://ntfy.sh/<tema-aleatorio>` y `HEARTBEAT_URL=https://hc-ping.com/<uuid>` (healthchecks.io: avisa
también si el VPS entero se cae). Un monitor externo (UptimeRobot, gratis) sobre `https://oncargo.tech/login` y
`https://api.oncargo.tech/q/health/ready` completa la vigilancia desde fuera.

## Entorno de pruebas vs. producción
`state/.env` trae `DEMO_DATA=true` (cuentas `*@logyx.pe` / `Logyx2026!`). `COOKIE_SECURE=true` (el dominio ya tiene
HTTPS y el acceso por IP redirige a él). Para producción real: `DEMO_DATA=false`, base nueva,
documentos en Cloudflare R2 en lugar de MinIO y backups `pg_dump` diarios a R2 (pendiente).

## DNS (panel de Hostinger → Dominios → oncargo.tech → DNS)
| Tipo | Nombre | Valor |
|---|---|---|
| A | `@` | `179.66.150.58` |
| A | `www` | `179.66.150.58` |
| A | `api` | `179.66.150.58` |
Borra los registros A/CNAME del parking. Caddy pide los certificados solo en cuanto el DNS propaga.

## Correo (SMTP): avisos, resumen diario, contraseñas e invitaciones
El backend envía por SMTP con Quarkus Mailer. **El proveedor aún no está elegido**: todo va por variables de entorno en
`/opt/oncargo/state/.env` (y `infra/.env` en el compose local; ver `infra/.env.example`). Sin `SMTP_HOST` el envío falla y
se loguea; la API y los avisos en la app siguen funcionando.

| Variable | Qué es | Ejemplo |
|---|---|---|
| `SMTP_HOST` / `SMTP_PORT` | Servidor y puerto del proveedor | `smtp-relay.brevo.com` / `587` |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | Credencial SMTP (una clave de API del proveedor, no la de tu cuenta) | — |
| `SMTP_START_TLS` / `SMTP_TLS` | 587: `REQUIRED` / `false`. 465 (TLS directo): `DISABLED` / `true` | `REQUIRED` / `false` |
| `MAIL_FROM` | Remitente; el dominio debe estar autenticado en el proveedor | `OnCargo <no-reply@oncargo.tech>` |
| `MAIL_REPLY_TO` | Respuestas (opcional) | `soporte@oncargo.tech` |
| `WEB_URL` | URL pública de la web: enlaces de los correos, `/baja?t=…` y `List-Unsubscribe` (https) | `https://oncargo.tech` |
| `PASSWORD_RESET_URL` | Página que recibe `?token=` | `https://oncargo.tech/restablecer` |
| `LOGYX_UNSUBSCRIBE_SECRET` | Clave HMAC del enlace de baja (≥ 32 caracteres). **Obligatoria**: sin ella el backend no arranca. `bootstrap.sh` la crea y `deploy.sh` la agrega a un `state/.env` anterior. Cambiarla invalida los enlaces de baja ya enviados | `openssl rand -base64 48` |

**Cobros (V24, ADR-0014)**
| Variable | Qué es | Ejemplo |
|---|---|---|
| `LOGYX_PAYOUT_KEY` | Clave AES-256 (base64 de 32 bytes) que cifra el CCI y el Yape/Plin de los transportistas. `bootstrap.sh` la crea y `deploy.sh` la agrega si falta. **Respáldala fuera del VPS**: si se pierde, los datos de cobro guardados no se pueden leer; cambiarla exige re-cifrarlos | `openssl rand -base64 32` |
| `BILLING_ISSUER_NAME` / `BILLING_ISSUER_RUC` | Emisor que figura en las liquidaciones y resúmenes internos (no son comprobantes SUNAT) | `OnCargo S.A.C.` / `20…` |

Después de cambiar `state/.env`: `cd /opt/oncargo/src/infra/vps && docker compose up -d backend`.

**DNS del dominio remitente** (en el panel de Hostinger; los valores exactos los da el proveedor elegido):
| Tipo | Nombre | Valor | Para qué |
|---|---|---|---|
| TXT | `@` | `v=spf1 include:<dominio-spf-del-proveedor> ~all` | SPF: autoriza al proveedor a enviar como `oncargo.tech`. Un solo registro SPF por dominio: si ya existe, agrega el `include:` |
| TXT o CNAME | `<selector>._domainkey` | la clave pública DKIM del proveedor | DKIM: firma cada correo |
| TXT | `_dmarc` | `v=DMARC1; p=none; rua=mailto:dmarc@oncargo.tech; adkim=s; aspf=s` | DMARC: empieza en `p=none` y, tras 2–4 semanas de reportes limpios, sube a `quarantine` |
| (opcional) CNAME/MX | `mail` o el subdominio que pida el proveedor | — | Dominio de rebote (*return-path*) alineado |

Gmail y Yahoo exigen desde 2024 SPF + DKIM + DMARC y la baja en un clic para envíos masivos: los correos de aviso ya
traen `List-Unsubscribe` (https a `/baja/one-click`) y `List-Unsubscribe-Post: List-Unsubscribe=One-Click` (RFC 8058).
Envía desde un subdominio o dirección dedicada (`no-reply@`) y no mezcles marketing con avisos. Prueba la entrega con
`mail-tester.com` o los encabezados `Authentication-Results` de un correo recibido en Gmail.
