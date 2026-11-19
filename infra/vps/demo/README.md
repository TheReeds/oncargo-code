# Kit de demostración de la infraestructura (EPE)

Todo corre **en el equipo local** del expositor y consulta el VPS por SSH (`~/.ssh/hostinger_kvm2`). La salida pasa por un
filtro que enmascara la IP del servidor (`<IP-VPS>`), cualquier otra IP pública (visitantes en el registro de Caddy,
la IP de origen), las IPv6 globales y el nombre del host; nunca se imprime `.env`, contraseñas, tokens ni el tema de ntfy.

| Archivo | Qué hace |
|---|---|
| `demo.sh` | Menú de 10 opciones (cada una < 30 s), `demo.sh status` (resumen de corrido) y `demo.sh <n>` (una sola opción) |
| `sim1-restore.sh` | SIM-1: baja el último respaldo cifrado, lo descifra con la identidad age **local**, lo restaura en un PostgreSQL temporal **sin red** y compara conteos tabla por tabla con producción. Tiempos por fase. `--fresh` hace antes un respaldo nuevo; `--quick` omite el conteo completo (demo) |
| `attacks/` | Simulaciones A1–A9 (ver abajo). Las activas exigen la autorización firmada |
| `GUION.md` | Recorrido de 10–12 min para la exposición, con qué decir y qué control demuestra cada paso |

```bash
infra/vps/demo/demo.sh            # menú
infra/vps/demo/demo.sh status     # resumen: servicio, contenedores, firewall, fail2ban, respaldos, timers, recursos, puertos, TLS
infra/vps/demo/demo.sh 6          # solo la opción 6 (fail2ban)
NO_COLOR=1 infra/vps/demo/demo.sh status > estado.txt   # sin colores, para pegar en un documento
```

| Opción | Muestra | Controles del EPE |
|---|---|---|
| 1 | Arquitectura, `docker compose ps`, `docker stats` | T6, H-06 |
| 2 | nmap desde fuera (solo 22/80/443) frente a `ss` en el host | T5, T6, A1, A5 |
| 3 | UFW y `DOCKER-USER` con contadores | T5, H-12 |
| 4 | Certificado, días de vigencia, TLS 1.2/1.3, cabeceras, acceso por IP → 308, HTTP/3 | T8, T9, T11, A8, H-01 |
| 5 | `sshd -T` e intento con contraseña rechazado | A7, R-11 |
| 6 | Jails de fail2ban; bloqueo y desbloqueo de una IP de documentación (203.0.113.x, RFC 5737) | A1, A2, A7 |
| 7 | Respaldos cifrados, timer diario y restauración en vivo (pregunta antes) | SIM-1, R-10 |
| 8 | `oncargo-health -v` y alerta de prueba al celular por ntfy (pregunta antes) | KPI, NC-05 |
| 9 | Registro de accesos con Cookie/Authorization y tokens de URL redactados | H-09, NC-04 |
| 10 | Carga, memoria, disco, contenedores; `btop` en vivo (pregunta antes) | KPI de eficiencia |

**Cuidados.** La opción 5 hace el intento con contraseña **desde el propio VPS** (fail2ban lo ignora por `ignoreself`); con
`DEMO_SSH_EXTERNO=1` lo repite desde el equipo local, y eso cuenta 1 fallo para la *jail* `sshd` (5 en 10 min bloquean el 22
una hora). La opción 6 usa solo 203.0.113.0/24. La 7 necesita Docker local y la identidad age en
`~/.config/oncargo/backup-age-identity.txt`. La 8 manda una notificación real al tema ntfy del dueño.

**Plan B sin red:** las capturas enmascaradas del EPE (`docs/infraestructura/semestre-2/img/`).

## Simulaciones A1–A9 (`attacks/`)

| Script | Tipo | Volumen | Detección esperada |
|---|---|---|---|
| `a1-escaneo.sh` | Activa | nmap `-p-` a 1000 pps + 3 GET a rutas de escaneo | `UFW BLOCK` en el journal; `oncargo-caddy-probe` bloquea 80/443 (no el 22); se desbloquea |
| `a2-fuerza-bruta-login.sh` | Activa | 35 POST a una cuenta inexistente | 401 ×10 → 429 `LOGYX-AUTH-429` + `Retry-After`; la web también; `oncargo-caddy-auth` bloquea tras 20 respuestas 429; se desbloquea |
| `a3-inyeccion-login.sh` | Activa | 8 POST con cargas SQL/NoSQL/tipos | Nunca 200 ni 500, sin mensajes de la base. sqlmap: solo en el staging local |
| `a4-zap.sh` | Activa | ZAP API contra el staging local; `--prod`: baseline pasivo 1 min con lista blanca temporal | Sin alertas altas |
| `a5-datos-desde-fuera.sh` | **Pasiva** | 3 conexiones TCP | 5432/9000/9001 sin conexión |
| `a6-ssrf.sh` | **Pasiva** (estática) | revisión de código | Solo el SDK S3 abre conexiones salientes |
| `a7-fuerza-bruta-ssh.sh` | Activa | 6 intentos SSH sin clave | `sshd` bloquea la IP 1 h; se desbloquea **por IPv6** (el script lo exige antes de empezar) |
| `a8-http-por-ip.sh` | **Pasiva** | 2 GET | `http://<IP>/login` → 308 al dominio |
| `a9-limite-compartido.sh` | Activa | ~52 POST por la web | El equipo A queda limitado; el B (otra IP; por omisión el VPS) no: H-10 cerrado |

Las activas se niegan a correr sin `OC_AUTORIZACION` y `OC_AUT_HASTA` (autorización escrita firmada,
`docs/infraestructura/semestre-2/autorizacion-pruebas.md` del EPE). Con ella:

```bash
OC_AUTORIZACION=AUT-INF-2026-01 OC_AUT_HASTA=2026-11-30 infra/vps/demo/attacks/run-all.sh 2>&1 | tee ~/oncargo-pruebas/resultado.txt
```

`run-all.sh` las ordena para que no se pisen (pasivas → A1 → A2 → A3 → A9 → A7) y al salir desbloquea la IP de origen.
El registro con la IP real (lo pide la autorización) queda en `~/oncargo-pruebas/<fecha>/registro.txt`, **fuera del repo**.
La IP con la que el VPS ve al equipo se averigua con una marca única en el registro de Caddy: con CGNAT puede no ser la de
`ifconfig.me`.

## Dashboards visuales para la exposición: recomendación

| Opción | Costo en el VPS | Riesgo / requisitos | Veredicto |
|---|---|---|---|
| **Terminal por SSH**: `demo.sh` + `btop` (instalado, paquete de Ubuntu) + `docker stats` en vivo | ~0 (solo mientras se mira) | Ninguno nuevo: usa el SSH que ya existe. `btop -p 1` (CPU + procesos) no muestra el panel de red | **Recomendado** para la exposición |
| `ctop` / `lazydocker` | ~10–20 MB mientras corren | No están en los repositorios de Ubuntu 24.04: exigen un binario de terceros o un contenedor con el *socket* de Docker (equivale a root). No se instalaron; `docker stats` y la opción 1 cubren lo mismo | No instalado (razón) |
| **Uptime Kuma en el portátil del expositor** (`docker run -p 3001:3001 louislam/uptime-kuma:1.23.16`, monitores HTTP a `https://oncargo.tech/`, `/login`, `/app/` y `https://api.oncargo.tech/q/health/ready` con la palabra `UP`) | 0 | Nada nuevo expuesto en el servidor ni DNS. Además mide desde fuera mientras el portátil esté encendido | **Recomendado** como panel visual |
| Uptime Kuma **en el VPS** (`status.oncargo.tech`) | ~100–150 MB de RAM | Servicio público nuevo: necesita aprobación del dueño, registro DNS `status` y mantener sus parches. Listo como perfil opcional (abajo) | Opcional, solo con aprobación |
| Netdata | ~150–300 MB y CPU constante | Agente residente con muchos colectores; su nube envía métricas a un tercero | No recomendado en 8 GB compartidos con la BD y la JVM |
| Grafana + Prometheus (+ node-exporter, cAdvisor) | ~400–700 MB, 4 servicios | Más superficie y mantenimiento que todo lo demás junto | No recomendado |

### Página de estado en el VPS (perfil `status`, NO desplegada)
Lista para cuando el dueño la apruebe:
1. Panel DNS de Hostinger: registro **A** `status` → la IP del VPS.
2. Descomentar el bloque `status.{$DOMAIN}` del `Caddyfile` (poner un hash de `docker compose exec caddy caddy hash-password`)
   y commitear; `infra/vps/deploy.sh`.
3. En el VPS: `cd /opt/oncargo/src/infra/vps && docker compose --profile status up -d uptime-kuma`.
4. Abrir `https://status.oncargo.tech`, crear el usuario administrador **en el primer acceso** (hasta entonces cualquiera podría
   reclamarlo: hacerlo de inmediato), crear los monitores y una *Status Page* pública.
5. Revertir: `docker compose --profile status rm -sf uptime-kuma`, volver a comentar el bloque y borrar el registro DNS.

`deploy.sh` y `docker compose up -d` **no** la arrancan (los servicios con `profiles` solo suben si se nombra el perfil).
