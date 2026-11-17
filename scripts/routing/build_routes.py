#!/usr/bin/env python3
"""Precálculo de rutas por carretera entre las ciudades del catálogo (ADR-0008).

Genera `backend/src/main/resources/routing/routes.json` con, para cada par de ciudades del enum
`pe.logyx.catalog.domain.City`:
  * matriz completa (dirigida) de distancia en km y duración en minutos por carretera;
  * geometría de la ruta simplificada (Douglas-Peucker) y codificada como polyline (precisión 5).

Fuente por omisión: OSRM (perfil `driving`) del servidor demo público sobre OpenStreetMap (ODbL). Alternativa:
Valhalla (`--engine valhalla --costing auto|truck`). Ojo: el perfil `truck` de Valhalla sobre los datos OSM de Perú da
desvíos absurdos (Lima → Ica 506 km por la sierra en vez de 302 km por la Panamericana) por restricciones `hgv` mal
etiquetadas; ver ADR-0008.
Solo se envían coordenadas de las ciudades del catálogo (nunca datos de usuarios). Una petición a la vez, con
pausa (`--sleep`, por omisión 1.5 s: la política de los servidores demo pide como máximo 1 petición por segundo).
Las respuestas crudas se guardan en `--cache` para no repetir peticiones al regenerar.

Uso (desde la raíz del repo; solo biblioteca estándar de Python 3.10+):
    python3 -I scripts/routing/build_routes.py                  # OSRM driving (por omisión): 1 tabla + 55 rutas
    python3 -I scripts/routing/build_routes.py --compare        # además imprime la tabla antes (×1.35) vs carretera
    python3 -I scripts/routing/build_routes.py --offline        # regenera solo desde la caché (sin red)
    python3 -I scripts/routing/build_routes.py --engine valhalla --costing auto
    python3 -I scripts/routing/build_routes.py --base-url http://localhost:5000   # OSRM propio (fase futura)
Al agregar una ciudad al enum `City`, vuelve a correr el script y versiona el JSON (un test del backend falla si
las coordenadas del dataset y las del enum no coinciden).
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
CITY_JAVA = REPO / "backend/src/main/java/pe/logyx/catalog/domain/City.java"
DEFAULT_OUT = REPO / "backend/src/main/resources/routing/routes.json"
DEFAULT_CACHE = REPO / "scripts/routing/.cache"

VALHALLA_URL = "https://valhalla1.openstreetmap.de"
OSRM_URL = "https://router.project-osrm.org"
# Cliente genérico: los servidores demo piden un User-Agent identificable, sin datos personales.
USER_AGENT = "routing-matrix-precompute/1.0 (python-urllib; low-volume one-off precompute)"
ATTRIBUTION = "© OpenStreetMap contributors (ODbL 1.0, https://www.openstreetmap.org/copyright)"

ROAD_FACTOR_OLD = 1.35
ASYMMETRY = 0.03  # >3 % de diferencia entre A→B y B→A: el sentido inverso va por otra vía y lleva su propia geometría  # factor del cálculo anterior (HaversineDistanceProvider / CityProximityPolicy)


# --------------------------------------------------------------------------- catálogo

def read_cities(path: Path) -> list[tuple[str, str, float, float]]:
    """Lee (código, nombre, lat, lng) del enum City en el orden declarado (= ordinal en Java)."""
    src = path.read_text(encoding="utf-8")
    body = src[src.index("enum City"):src.index(";", src.index("enum City"))]
    rows = re.findall(r"([A-Z_]+)\(\"([^\"]+)\",\s*(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)\)", body)
    if not rows:
        sys.exit(f"No se encontraron ciudades en {path}")
    return [(code, label, float(lat), float(lng)) for code, label, lat, lng in rows]


# --------------------------------------------------------------------------- HTTP con caché y pausa

class Client:
    def __init__(self, cache: Path, sleep: float, offline: bool):
        self.cache = cache
        self.sleep = sleep
        self.offline = offline
        self.calls = 0
        self._last = 0.0
        cache.mkdir(parents=True, exist_ok=True)

    def _key(self, url: str, body: dict | None) -> Path:
        h = hashlib.sha256((url + "\n" + json.dumps(body, sort_keys=True)).encode()).hexdigest()[:24]
        return self.cache / f"{h}.json"

    def request(self, url: str, body: dict | None = None) -> dict:
        key = self._key(url, body)
        if key.exists():
            return json.loads(key.read_text(encoding="utf-8"))
        if self.offline:
            sys.exit(f"--offline y no hay caché para {url}")
        wait = self.sleep - (time.monotonic() - self._last)
        if wait > 0:
            time.sleep(wait)
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, headers={"User-Agent": USER_AGENT, "Content-Type": "application/json"})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(req, timeout=60) as resp:
                    payload = json.loads(resp.read().decode())
                break
            except urllib.error.HTTPError as e:
                if e.code in (429, 502, 503, 504) and attempt < 3:
                    time.sleep(10 * (attempt + 1))
                    continue
                raise SystemExit(f"HTTP {e.code} en {url}: {e.read()[:300]!r}")
            except (TimeoutError, urllib.error.URLError) as e:
                if attempt < 3:
                    print(f"  reintento tras error de red ({e})", file=sys.stderr)
                    time.sleep(10 * (attempt + 1))
                    continue
                raise SystemExit(f"Error de red en {url}: {e}")
        self._last = time.monotonic()
        self.calls += 1
        key.write_text(json.dumps(payload), encoding="utf-8")
        return payload


# --------------------------------------------------------------------------- motores

def valhalla_matrix(client: Client, base: str, cities, costing: str):
    """El servidor público limita la matriz a 150 km por celda: se arma con /route por par (simétrica)."""
    n = len(cities)
    km = [[0.0] * n for _ in range(n)]
    minutes = [[0.0] * n for _ in range(n)]
    for i in range(n):
        for j in range(i + 1, n):
            _, rkm, rmin = valhalla_route(client, base, cities[i], cities[j], costing)
            km[i][j] = km[j][i] = rkm
            minutes[i][j] = minutes[j][i] = rmin
    return km, minutes


def valhalla_route(client: Client, base: str, a, b, costing: str) -> tuple[list[tuple[float, float]], float, float]:
    res = client.request(f"{base}/route", {
        "locations": [{"lat": a[2], "lon": a[3]}, {"lat": b[2], "lon": b[3]}],
        "costing": costing, "units": "kilometers", "directions_type": "none"})
    trip = res["trip"]
    pts: list[tuple[float, float]] = []
    for leg in trip["legs"]:
        pts.extend(decode_polyline(leg["shape"], 6))
    return pts, trip["summary"]["length"], trip["summary"]["time"] / 60


def osrm_matrix(client: Client, base: str, cities, costing: str = "driving"):
    coords = ";".join(f"{lng},{lat}" for _, _, lat, lng in cities)
    res = client.request(f"{base}/table/v1/driving/{coords}?annotations=distance,duration")
    if res.get("code") != "Ok":
        sys.exit(f"OSRM table: {res.get('code')}")
    km = [[d / 1000 for d in row] for row in res["distances"]]
    minutes = [[s / 60 for s in row] for row in res["durations"]]
    return km, minutes


def osrm_route(client: Client, base: str, a, b, costing: str = "driving"):
    res = client.request(f"{base}/route/v1/driving/{a[3]},{a[2]};{b[3]},{b[2]}?overview=full&geometries=polyline6")
    if res.get("code") != "Ok":
        sys.exit(f"OSRM route {a[0]} → {b[0]}: {res.get('code')}")
    r = res["routes"][0]
    return decode_polyline(r["geometry"], 6), r["distance"] / 1000, r["duration"] / 60


# --------------------------------------------------------------------------- geometría

def decode_polyline(s: str, precision: int) -> list[tuple[float, float]]:
    """Polyline de Google → [(lat, lng)]."""
    factor = 10 ** precision
    pts, idx, lat, lng = [], 0, 0, 0
    while idx < len(s):
        for which in range(2):
            shift = result = 0
            while True:
                b = ord(s[idx]) - 63
                idx += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            delta = ~(result >> 1) if result & 1 else result >> 1
            if which == 0:
                lat += delta
            else:
                lng += delta
        pts.append((lat / factor, lng / factor))
    return pts


def encode_polyline(pts: list[tuple[float, float]], precision: int = 5) -> str:
    factor = 10 ** precision
    out, plat, plng = [], 0, 0
    for lat, lng in pts:
        ilat, ilng = round(lat * factor), round(lng * factor)
        for d in (ilat - plat, ilng - plng):
            v = ~(d << 1) if d < 0 else d << 1
            while v >= 0x20:
                out.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            out.append(chr(v + 63))
        plat, plng = ilat, ilng
    return "".join(out)


def simplify(pts: list[tuple[float, float]], tolerance_m: float) -> list[tuple[float, float]]:
    """Douglas-Peucker iterativo en una proyección equirectangular local (metros)."""
    if len(pts) < 3:
        return pts
    lat0 = math.radians(sum(p[0] for p in pts) / len(pts))
    xy = [(p[1] * 111_320 * math.cos(lat0), p[0] * 110_540) for p in pts]
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        i, j = stack.pop()
        (x1, y1), (x2, y2) = xy[i], xy[j]
        dx, dy = x2 - x1, y2 - y1
        seg2 = dx * dx + dy * dy
        best, idx = -1.0, -1
        for k in range(i + 1, j):
            px, py = xy[k]
            if seg2 == 0:
                d = math.hypot(px - x1, py - y1)
            else:
                t = max(0.0, min(1.0, ((px - x1) * dx + (py - y1) * dy) / seg2))
                d = math.hypot(px - (x1 + t * dx), py - (y1 + t * dy))
            if d > best:
                best, idx = d, k
        if best > tolerance_m:
            keep[idx] = True
            stack.append((i, idx))
            stack.append((idx, j))
    return [p for p, k in zip(pts, keep) if k]


def haversine_km(a, b) -> float:
    dlat, dlng = math.radians(b[2] - a[2]), math.radians(b[3] - a[3])
    h = math.sin(dlat / 2) ** 2 + math.cos(math.radians(a[2])) * math.cos(math.radians(b[2])) * math.sin(dlng / 2) ** 2
    return 6371 * 2 * math.atan2(math.sqrt(h), math.sqrt(1 - h))


# --------------------------------------------------------------------------- main

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--engine", choices=["osrm", "valhalla"], default="osrm")
    ap.add_argument("--costing", help="perfil: OSRM driving (único en el demo); Valhalla auto|truck (por omisión auto)")
    ap.add_argument("--base-url", help="URL del motor (por omisión, el servidor demo público)")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    ap.add_argument("--sleep", type=float, default=1.5, help="segundos entre peticiones (mín. 1.0)")
    ap.add_argument("--tolerance-m", type=float, default=60.0, help="tolerancia de Douglas-Peucker en metros")
    ap.add_argument("--compare", action="store_true", help="imprime la tabla línea recta / antes (×1.35) / carretera")
    ap.add_argument("--offline", action="store_true", help="solo usa la caché (no hace peticiones)")
    args = ap.parse_args()
    if args.sleep < 1.0:
        sys.exit("--sleep debe ser ≥ 1.0 s (política de los servidores demo)")

    cities = read_cities(CITY_JAVA)
    client = Client(args.cache, args.sleep, args.offline)
    if args.engine == "osrm":
        base, costing = args.base_url or OSRM_URL, args.costing or "driving"
        matrix_fn, route_fn, label = osrm_matrix, osrm_route, f"OSRM (profile={costing})"
    else:
        base, costing = args.base_url or VALHALLA_URL, args.costing or "auto"
        matrix_fn, route_fn, label = valhalla_matrix, valhalla_route, f"Valhalla (costing={costing})"

    km, minutes = matrix_fn(client, base, cities, costing)
    n = len(cities)
    geometries: dict[str, dict] = {}
    for i in range(n):
        for j in range(i + 1, n):
            a, b = cities[i], cities[j]
            pts, rkm, rmin = route_fn(client, base, a, b, costing)
            simple = simplify(pts, args.tolerance_m)
            geometries[f"{a[0]}-{b[0]}"] = {"polyline": encode_polyline(simple, 5), "points": len(simple)}
            print(f"  {a[0]:>9} → {b[0]:<9} {rkm:7.1f} km {rmin / 60:5.1f} h  {len(pts):6d} → {len(simple):4d} pts",
                  file=sys.stderr)

    # Sentido inverso: si la ruta B → A es otra (distancia distinta en más de ASYMMETRY), se guarda su propia línea.
    for i in range(n):
        for j in range(i):
            if abs(km[i][j] - km[j][i]) / min(km[i][j], km[j][i]) <= ASYMMETRY:
                continue
            a, b = cities[i], cities[j]
            try:
                pts, rkm, rmin = route_fn(client, base, a, b, costing)
            except SystemExit as e:  # el demo a veces no responde rutas largas: se usa la línea del otro sentido
                print(f"  AVISO {a[0]} → {b[0]}: sin geometría propia ({e}); se usará {b[0]}-{a[0]} invertida",
                      file=sys.stderr)
                continue
            simple = simplify(pts, args.tolerance_m)
            geometries[f"{a[0]}-{b[0]}"] = {"polyline": encode_polyline(simple, 5), "points": len(simple)}
            print(f"  {a[0]:>9} → {b[0]:<9} {rkm:7.1f} km (inversa distinta)", file=sys.stderr)

    dataset = {
        "version": 1,
        "generatedAt": dt.date.today().isoformat(),
        "engine": label,
        "source": base if args.base_url else f"{base} (servidor demo público; solo coordenadas de ciudades)",
        "attribution": ATTRIBUTION,
        "license": "ODbL-1.0 (base de datos derivada de OpenStreetMap)",
        "metric": "ruta más rápida del perfil; distanceKm = longitud de esa ruta; durationMin = tiempo del perfil (auto)",
        "polylinePrecision": 5,
        "simplifyToleranceM": args.tolerance_m,
        "geometryKey": "ORIGEN-DESTINO; si no existe la clave dirigida, se usa la de DESTINO-ORIGEN invertida (misma vía en ambos sentidos)",
        "cities": [{"code": c, "label": l, "lat": la, "lng": ln} for c, l, la, ln in cities],
        "distanceKm": [[round(v, 1) for v in row] for row in km],
        "durationMin": [[round(v, 1) for v in row] for row in minutes],
        "geometries": geometries,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(dataset, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Escrito {args.out} ({args.out.stat().st_size / 1024:.0f} KB, {client.calls} peticiones nuevas)",
          file=sys.stderr)

    if args.compare:
        print("\n| Par | Línea recta km | Antes km (×1.35) | Carretera km | Diferencia del cálculo anterior |")
        print("|---|---:|---:|---:|---:|")
        for i in range(n):
            for j in range(i + 1, n):
                a, b = cities[i], cities[j]
                straight = haversine_km(a, b)
                old = 44.0 if {a[0], b[0]} == {"JULIACA", "PUNO"} else straight * ROAD_FACTOR_OLD
                road = km[i][j]
                print(f"| {a[1]}–{b[1]} | {straight:.0f} | {old:.0f} | {road:.0f} | {(old - road) / road * 100:+.0f} % |")


if __name__ == "__main__":
    main()
