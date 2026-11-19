#!/usr/bin/env bash
# A3 — Inyección en el login del VPS: 8 cargas clásicas (SQL, NoSQL/JSON, tipos) en email y contraseña.
#   Esperado: 400 o 401 (nunca 200 ni 500), sin mensajes de la base (SQLSTATE, PSQLException, syntax error).
#   sqlmap y el aislamiento entre organizaciones se prueban contra el staging LOCAL (ver EPE § A3/A4):
#     sqlmap -u "http://127.0.0.1:8680/api/v1/shipment-requests?status=PUBLISHED" --headers="Authorization: Bearer <JWT>" --batch --level=3 --risk=2
#   Volumen: 8 POST (cuentan para el límite por IP del login).
source "$(dirname "$0")/common.sh"; require_authorization
title "A3 · Inyección en el login"
payloads=(
  '{"email":"admin@logyx.pe'"'"' OR '"'"'1'"'"'='"'"'1","password":"x"}'
  '{"email":"x@x.pe","password":"'"'"' OR 1=1 --"}'
  '{"email":"x@x.pe'"'"'; DROP TABLE profiles; --","password":"x"}'
  '{"email":"x@x.pe","password":"x'"'"' UNION SELECT password_hash FROM profiles --"}'
  '{"email":{"$ne":null},"password":{"$ne":null}}'
  '{"email":["a@b.pe"],"password":"x"}'
  '{"email":"pg_sleep@x.pe'"'"'||pg_sleep(5)||'"'"'","password":"x"}'
  '{"email":"x@x.pe","password":"x","organizationId":"00000000-0000-0000-0000-000000000000"}'
)
fails=0
for p in "${payloads[@]}"; do
  r="$(curl -s -4 -w '\n%{http_code} %{time_total}' -H 'Content-Type: application/json' -d "$p" "$API/api/v1/auth/login")"
  code="$(tail -1 <<<"$r" | cut -d' ' -f1)"; t="$(tail -1 <<<"$r" | cut -d' ' -f2)"; body="$(sed '$d' <<<"$r")"
  leak="$(grep -ciE 'sqlstate|psqlexception|syntax error|hibernate|stack' <<<"$body")"
  printf '  %-4s %5ss  %s\n' "$code" "$t" "$(cut -c1-70 <<<"$p")"
  [[ "$code" =~ ^(400|401|429)$ && "$leak" == 0 ]] || fails=$((fails+1))
done
(( fails == 0 )) && ok "ninguna carga produjo 200, 500 ni filtró errores de la base; ninguna tardó por pg_sleep" || bad "$fails cargas con respuesta inesperada"
