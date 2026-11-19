# Guion de la demo de infraestructura (10–12 min)

**Antes de la clase (15 min antes):** `infra/vps/demo/demo.sh status` para confirmar que todo está en verde; app **ntfy**
abierta en el celular del expositor, suscrita al tema del dueño; terminal a pantalla completa, fuente grande, fondo oscuro;
Docker local encendido (opción 7). Tener abierta en otra pestaña la carpeta de capturas del EPE
(`docs/infraestructura/semestre-2/img/`) por si falla la red. **No** abrir `state/.env` ni `alert.env` en pantalla.

Arranque: `infra/vps/demo/demo.sh` (menú). Cada opción tarda menos de 30 s.

| # | Min | Opción | Qué decir | Demuestra | Plan B (captura) |
|---|---|---|---|---|---|
| 0 | 0:00 | `s` (resumen) | «OnCargo corre en **un** VPS de 8 GB. En 20 segundos: el servicio responde, cinco contenedores sanos, firewall activo, fail2ban, respaldos de hoy y el certificado con 87 días de vigencia.» | Visión general, KPIs | `demo-status.png` |
| 1 | 1:00 | 1 | «Solo Caddy publica puertos. La base y MinIO viven en una red Docker `internal` **sin salida a Internet**: aunque alguien tomara la base, no podría sacar datos por ella.» | T6, H-06 | `demo-1-arquitectura.png` |
| 2 | 2:00 | 2 | «Esto es lo que ve un atacante desde Internet: 22, 80 y 443. 5432 (PostgreSQL) y 9000 (MinIO) están *filtered*: no existen para él.» | T5, A1, A5 | `demo-2-puertos.png` |
| 3 | 3:00 | 3 | «Docker se salta UFW en los puertos que publica. Por eso la cadena `DOCKER-USER` es una lista blanca por puerto **original**; el contador DROP muestra lo que se descartó.» | T5, H-12 | `demo-3-firewall.png` |
| 4 | 4:00 | 4 | «Let's Encrypt renovado solo; TLS 1.0 y 1.1 rechazados; HSTS de un año, CSP, sin cabecera `Server`. Si alguien entra por la IP sin HTTPS, lo mandamos al dominio cifrado.» | T8, T9, T11, A8, H-01 | `demo-4-tls.png` |
| 5 | 5:00 | 5 | «El servidor ni siquiera ofrece contraseña: solo clave pública, solo dos usuarios, algoritmos modernos.» | A7, R-11 | `demo-5-ssh.png` |
| 6 | 5:45 | 6 | «Cuatro *jails*. Bloqueo en vivo una IP de documentación —un rango reservado, nadie real—, aparece en nftables y la libero. Las jails web nunca tocan el 22: aprendimos eso cuando nos bloqueamos a nosotros mismos.» | A1, A2, A7, E4 | `demo-6-fail2ban.png` |
| 7 | 6:45 | 7 → «s» | «El respaldo es diario a las 03:30 con un **timer de systemd**, el equivalente moderno a cron. Está cifrado con age y la clave privada **no** está en el servidor. Ahora lo restauro en vivo en una base temporal sin red de mi laptop y comparo los conteos.» Leer el TOTAL: «menos de 20 segundos para los datos; el objetivo de RTO es 1–2 horas». | SIM-1, R-10, KPI | `demo-7-respaldos.png`, `sim1-restauracion.png` |
| 8 | 8:30 | 8 → «s» | «Cada 5 minutos un chequeo revisa web, API, APK, contenedores, disco, memoria, certificado y la edad del respaldo. Mando una alerta de prueba…» (mostrar el celular). | KPI, NC-05 | `demo-8-monitoreo.png` |
| 9 | 9:15 | 9 | «Registro de accesos en JSON por 14 días: la IP es un dato personal (Ley 29733). Cookie y Authorization salen como REDACTED y también los tokens de los enlaces públicos.» | H-09, NC-04 | `demo-9-logs.png` |
| 10 | 10:00 | 10 → «s» (btop, `q` para salir) | «Todo esto usa ~1.1 GB de 8. Por eso no pusimos Grafana ni Netdata: costarían más memoria que la aplicación.» | KPI de eficiencia | `demo-10-recursos.png` |
| 11 | 10:45 | — | Cierre: hallazgo H-10 (el límite de intentos se compartía entre todos los usuarios de la web; se corrigió con `allowedDomains` y se comprobó con T10), pendientes del dueño (MFA, UptimeRobot, copia fuera del VPS) y las simulaciones A1–A9 que esperan la autorización firmada. | H-10, H-11, NC-03 | — |

**Si algo falla en vivo**
- *Sin red o sin SSH:* `demo.sh` lo detecta y lo dice; pasar a las capturas (mismo orden). Decir: «estas capturas son de la
  ejecución del 19-nov, con la IP enmascarada».
- *La restauración tarda o Docker local no arranca:* mostrar `sim1-restauracion.png` (10,6 s en la medición oficial).
- *La alerta no llega al celular:* mostrar la respuesta «Alerta de prueba enviada» y la evidencia E14 del EPE.
- *Un contenedor aparece `unhealthy`:* es una demo honesta: mostrar `demo.sh 8` (lo detecta el chequeo y avisaría por ntfy).

**Nunca en pantalla:** `state/.env`, `/etc/oncargo/alert.env`, la IP real (todo sale enmascarado por el script; no usar `ssh`
a mano durante la demo), contraseñas demo o tokens.
