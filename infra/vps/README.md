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
infra/vps/publish-apk.sh       # sube android/app/build/outputs/apk/staging/app-staging.apk (ver android/AGENTS.md)
```
En el servidor:
```
/opt/oncargo/src         código del commit desplegado (src.old = anterior, para volver atrás)
/opt/oncargo/state/.env  contraseñas aleatorias generadas por bootstrap.sh / deploy.sh (chmod 600, no salen del VPS),
                         incluida LOGYX_DEMO_PASSWORD (contraseña de las cuentas demo; nunca va al repo)
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
| Puerto SSH 22 | Descartado por ahora | Cambiarlo solo reduce ruido de bots: ya hay clave obligatoria, `AllowUsers`, fail2ban `sshd` + `recidive`, y la consola web de Hostinger sirve de respaldo. Cambiarlo suma riesgo de quedarse fuera (`ssh.socket`, UFW, avisar al equipo) |
| UFW: entrada `deny`, 22/tcp, 80/tcp, 443/tcp+udp | ✅ | `harden.sh firewall` solo comprueba |
| `DOCKER-USER`: desde `eth0` solo nuevas conexiones a 80/tcp, 443/tcp, 443/udp (puerto original, antes del DNAT) | ✅ | `host/docker-user.sh` + `oncargo-docker-user.service`. Revertir: `systemctl disable --now oncargo-docker-user; iptables -F DOCKER-USER` |
| fail2ban: `sshd` (5 fallos → 1 h), `recidive` (3 bloqueos sshd/día → 1 semana, todos los puertos) | ✅ | `host/fail2ban/` → `/etc/fail2ban/jail.d/oncargo.local` |
| fail2ban sobre Caddy: `oncargo-caddy-probe` (3 rutas de escaneo → 1 día) y `oncargo-caddy-auth` (20 respuestas 429 en 10 min → 1 h), **solo 80/443**, en INPUT y FORWARD (nftables) | ✅ | Desbloquear: `fail2ban-client unban <ip>`. Revertir: borrar `jail.d/oncargo.local`, `systemctl restart fail2ban` |
| Registro de accesos de Caddy (JSON, 20 MiB × 10, 14 días; tokens `?t=`, `?token=`, `/s/…` redactados) | ✅ | `/var/log/oncargo/caddy/access.log` (snippet `access_log` del Caddyfile) |
| Cabeceras: HSTS 1 año, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, sin `Server` ni `Via`; CSP y `X-Frame-Options` en `/app/` | ✅ | Caddyfile. La web y la API ya mandan su CSP |
| HSTS `preload` | Descartado por ahora | Prácticamente irreversible (salir de la lista de los navegadores tarda meses) y obliga a HTTPS en todo subdominio futuro: no mientras el dominio está en fase de pruebas. Queda HSTS 1 año sin `preload` |
| `/q/*` de Quarkus cerrado en `api.` salvo `/q/health*` | ✅ | Caddyfile (`@quarkus_internal`) |
| Acceso `http://<IP>` → 308 al dominio (antes servía el login sin TLS) | ✅ | Caddyfile |
| IP real del cliente en la web (H-10): `security.allowedDomains` en `web/astro.config.mjs`; Caddy reemplaza el `X-Forwarded-For` del cliente | ✅ (2026-11-19, `848ba00`) | Comprobado con `tcpdump` (el backend recibe la IP real, no la de `caddy` ni una falsa) y T10 |
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
| `unattended-upgrades` (seguridad diaria) | ✅ | Reinicio por kernel a mano en una ventana (`sudo systemctl reboot`): 2026-11-19, 6.8.0-147 → 6.8.0-149, todo volvió solo (ver [Reinicio](#reinicio-del-servidor)). `Automatic-Reboot` sigue apagado |
| Respaldo diario cifrado (age) de Postgres, MinIO y `state/`, 14 días, prueba de restauración | ✅ local | `backup.sh` (abajo). ⏳ copia **fuera del VPS**: [Pendiente del dueño](#pendiente-del-dueño) |
| Chequeo de salud cada 5 min (web, API, APK, contenedores, disco, memoria, certificado, respaldo) | ✅ | `healthcheck.sh`. Alertas por ntfy.sh ✅ (`/etc/oncargo/alert.env`). ⏳ monitor externo (UptimeRobot): [Pendiente del dueño](#pendiente-del-dueño) |
| Cloudflare (proxy, WAF; sin Bot Fight Mode) | ⏳ plan | Cambia DNS (dueño). Ver docs/security.md § Infraestructura y [Pendiente del dueño](#pendiente-del-dueño) |
| Registro CAA (solo Let's Encrypt emite certificados) | ⏳ dueño | Panel DNS de Hostinger: [Pendiente del dueño](#pendiente-del-dueño) |
| Contraseña demo propia del VPS (la del repo, `Logyx2026!`, es pública) | ✅ | `LOGYX_DEMO_PASSWORD` en `state/.env` (abajo, [Entorno de pruebas](#entorno-de-pruebas-vs-producción)) |

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
cambios de estado (OK→FALLA con prioridad alta, FALLA→OK). Canales en `/etc/oncargo/alert.env` (root, 600, no se versiona):
- `ALERT_WEBHOOK_URL=https://ntfy.sh/oncargo-<32 hex>` — **configurado** (2026-11-19). ntfy.sh sin cuenta: el tema aleatorio
  es la única protección, así que no va en el repo y el mensaje no lleva secretos (estado, hora y qué falló). Para recibirlo:
  app ntfy (Android/iOS) → «+» → servidor `ntfy.sh` → el tema (lo tiene el dueño; `sudo cat /etc/oncargo/alert.env`).
  Probar: `sudo oncargo-health --test-alert` y `curl -s "https://ntfy.sh/<tema>/json?poll=1&since=5m"`.
  Cambiar de tema (si se filtró): editar el archivo con un tema nuevo (`oncargo-$(openssl rand -hex 16)`) y suscribirse de nuevo.
- `HEARTBEAT_URL=https://hc-ping.com/<uuid>` — opcional (healthchecks.io: avisa también si el VPS entero se cae).

## Demo, pruebas y simulaciones (`demo/`)
`infra/vps/demo/demo.sh` (menú o `demo.sh status`, salida con la IP enmascarada), `demo/sim1-restore.sh` (SIM-1: restauración
medida del último respaldo en un PostgreSQL local sin red), `demo/attacks/` (A1–A9; las activas exigen la autorización
firmada) y el guion de la exposición. Página de estado opcional (Uptime Kuma, perfil `status` del compose, **no desplegada**).
Detalle: [`demo/README.md`](demo/README.md). Herramientas de terminal en el VPS: `btop` (apt, 2026-11-19) y `htop`.
El respaldo diario es un **timer de systemd** (equivalente a una línea de cron `30 3 * * *` con `Persistent=true`, que además
recupera la ejecución si el servidor estaba apagado a esa hora): `systemctl list-timers 'oncargo-*'`.

## Entorno de pruebas vs. producción
`state/.env` trae `DEMO_DATA=true` (cuentas `*@logyx.pe`). **La contraseña demo del VPS no es la del repo** (`Logyx2026!`,
que es público y queda solo para dev y tests): es `LOGYX_DEMO_PASSWORD` en `state/.env` (`deploy.sh` genera una si falta:
16 caracteres con mayúsculas, minúsculas, dígito y símbolo). El backend la lee como `logyx.demo.password` y el seeder solo la
usa al **crear** cuentas demo que falten; **nunca la escribas en el repo**. El APK staging la lleva en los chips del login desde
`oncargo.demoPassword` en `~/.gradle/gradle.properties` del equipo que compila (sin ella, sin chips).
Rotarla: (1) para cada cuenta demo (`pyme@`, `pyme.ops@`, `pyme.viewer@`, `carrier@`, `carrier2@`, `driver@`,
`operator@logyx.pe`) `POST https://api.oncargo.tech/api/v1/auth/login` y luego `POST /api/v1/auth/change-password` con
`{currentPassword, newPassword}` (cierra las sesiones de esa cuenta: `sv`); (2) el valor nuevo en `state/.env` y en
`~/.gradle/gradle.properties`; (3) `docker compose up -d backend`, recompilar staging y `publish-apk.sh`.
`COOKIE_SECURE=true` (el dominio ya tiene HTTPS y el acceso por IP redirige a él). Para producción real: `DEMO_DATA=false`, base nueva,
documentos en Cloudflare R2 en lugar de MinIO y backups `pg_dump` diarios a R2 (pendiente).

## Reinicio del servidor
`sudo systemctl reboot` (p. ej. cuando `unattended-upgrades` deja `/var/run/reboot-required` por un kernel nuevo). Todo vuelve
solo, sin intervención: los contenedores (`restart: unless-stopped`; `web` espera a `backend` healthy), UFW, `DOCKER-USER`
(`oncargo-docker-user.service`, después de `docker.service`), fail2ban, auditd y los timers `oncargo-backup` y `oncargo-health`.
Comprobar después:
```bash
uname -r; cat /var/run/reboot-required 2>/dev/null || echo "sin reinicio pendiente"
cd /opt/oncargo/src/infra/vps && docker compose ps            # 5 servicios; db, minio, backend y web «healthy»
sudo ufw status | head -3; sudo iptables -S DOCKER-USER; sudo fail2ban-client status
systemctl list-timers 'oncargo-*'; sudo oncargo-health -v
```
Historial: 2026-11-19 12:05 (Lima), 6.8.0-147 → 6.8.0-149-generic: SSH de vuelta en ~2 min, los 5 contenedores en marcha y
`healthy` ~30 s después, UFW activo, `DOCKER-USER` aplicado tras Docker (primera regla de `FORWARD`), fail2ban con sus 4 jails,
timers activos, `oncargo-health` en OK y sin `/var/run/reboot-required`. No hizo falta ningún arreglo.

## Pendiente del dueño
Necesitan cuentas o paneles del dueño; el resto del servidor ya está listo para cada uno.

### 1. Respaldos fuera del servidor (Cloudflare R2)
Hoy los respaldos cifrados quedan en el mismo disco (`/var/backups/oncargo`): si se pierde el VPS se pierden también.
1. Cloudflare → **R2 Object Storage** → *Create bucket* `oncargo-backups` (ubicación automática; sin acceso público).
2. Opcional, recomendado: en el bucket, *Settings → Bucket lock rules* → regla para el prefijo `vps/` con retención de 14 días
   (nadie puede borrar ni sobreescribir un respaldo antes de ese plazo, ni siquiera con el token).
3. R2 → *Manage API tokens* → *Create API token*: permiso **Object Read & Write** (R2 no tiene «solo escritura»), aplicado
   **solo al bucket `oncargo-backups`**, sin vencimiento o con uno largo. Anota *Access Key ID*, *Secret Access Key* y el
   endpoint `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`. Los archivos van cifrados con age: con el token no se leen datos.
4. En el VPS:
   ```bash
   sudo apt-get install -y rclone
   sudo install -m 600 /dev/null /etc/oncargo/rclone.conf && sudoedit /etc/oncargo/rclone.conf
   #   [r2]
   #   type = s3
   #   provider = Cloudflare
   #   access_key_id = <Access Key ID>
   #   secret_access_key = <Secret Access Key>
   #   endpoint = https://<ACCOUNT_ID>.r2.cloudflarestorage.com
   #   acl = private
   #   no_check_bucket = true
   echo 'BACKUP_RCLONE_REMOTE=r2:oncargo-backups/vps' | sudo tee /etc/oncargo/backup.env && sudo chmod 600 /etc/oncargo/backup.env
   ```
5. Probar: `sudo oncargo-backup run` (debe decir «copia externa → r2:…») y
   `sudo rclone --config /etc/oncargo/rclone.conf ls r2:oncargo-backups/vps` (los `*.age` y el `manifest-*.txt` de hoy).
   Restaurar desde R2: `rclone copy r2:oncargo-backups/vps/<archivo> .` en otro equipo y `age -d -i backup-age-identity.txt`.
   Si falla la copia, `oncargo-backup` sale con error y `oncargo-health` avisa por ntfy («último respaldo correcto hace N h»).

### 2. Registro CAA (panel DNS de Hostinger)
Solo Let's Encrypt (la CA de Caddy) podrá emitir certificados para el dominio. Hostinger → Dominios → `oncargo.tech` → DNS →
*Agregar registro*:
| Tipo | Nombre | Flag | Tag | Valor |
|---|---|---|---|---|
| CAA | `@` | 0 | `issue` | `letsencrypt.org` |
| CAA | `@` | 0 | `issuewild` | `;` (nadie emite comodines; opcional) |
| CAA | `@` | 0 | `iodef` | `mailto:soporte@oncargo.tech` (solo si ese buzón existe y alguien lo lee) |
Comprobar: `dig +short CAA oncargo.tech` (debe listar `0 issue "letsencrypt.org"`). Aplica también a `www` y `api` (heredan
del dominio). Si un día se usa Cloudflare con proxy, agrega también sus CA (Cloudflare lo avisa al activar el certificado).

### 3. Monitor externo (UptimeRobot, gratis)
`oncargo-health` corre dentro del VPS: si el servidor entero se cae, no avisa nadie. UptimeRobot mira desde fuera.
1. Crear cuenta en uptimerobot.com → *Add New Monitor*:
   - **HTTP(s)** `https://oncargo.tech/` · intervalo 5 min · alerta si no responde 2xx.
   - **Keyword** `https://api.oncargo.tech/q/health/ready` · palabra `UP` (la API de salud; no gasta intentos de login).
   - Opcional, el login de la API (el método `POST` puede exigir un plan pago): tipo **HTTP(s)** con método `POST` a `https://api.oncargo.tech/api/v1/auth/login`, cuerpo
     JSON `{"email":"monitor@invalido.pe","password":"x"}` y *alert on* código distinto de `401`. **No** pongas una cuenta demo
     real (la contraseña quedaría en UptimeRobot) y no bajes de 5 min: el límite de intentos por IP de la API lo bloquearía.
2. *Alert contacts*: correo del dueño y, si quiere todo en un lugar, la app de UptimeRobot (o un webhook a su tema de ntfy).
3. Opcional: healthchecks.io (latido) → `HEARTBEAT_URL=https://hc-ping.com/<uuid>` en `/etc/oncargo/alert.env`.

### 4. Cloudflare (resumen)
Proxy naranja delante de `oncargo.tech`, `www` y `api`; WAF gestionado y una regla de rate limit en `/api/v1/auth/*`;
**sin Bot Fight Mode** (desafía a la app Android). Cambia los DNS del dominio, exige restaurar la IP real en Caddy
(`trusted_proxies` con los rangos de Cloudflare) y limitar 80/443 a esos rangos en `DOCKER-USER`. Detalle y riesgos:
`docs/security.md` § Infraestructura del VPS («Pendiente del dueño»).

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
