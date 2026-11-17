/**
 * Polyline codificada (algoritmo de Google) → coordenadas GeoJSON `[lng, lat]`.
 * El backend publica las geometrías de ruta con precisión 5 (ADR-0008, `routing/routes.json`).
 */
export type LngLat = [number, number];

export function decodePolyline(encoded: string, precision = 5): LngLat[] {
  const factor = 10 ** precision;
  const out: LngLat[] = [];
  let index = 0;
  let lat = 0;
  let lng = 0;
  while (index < encoded.length) {
    const delta: [number, number] = [0, 0];
    for (let k = 0; k < 2; k++) {
      let result = 0;
      let shift = 0;
      let b: number;
      do {
        if (index >= encoded.length) throw new Error('Polyline truncada');
        b = encoded.charCodeAt(index++) - 63;
        // Multiplicación en vez de << para no desbordar a 32 bits en tramos largos.
        result += (b & 0x1f) * 2 ** shift;
        shift += 5;
      } while (b >= 0x20);
      delta[k] = result % 2 === 1 ? -(result + 1) / 2 : result / 2;
    }
    lat += delta[0];
    lng += delta[1];
    out.push([lng / factor, lat / factor]);
  }
  return out;
}

export function encodePolyline(coords: LngLat[], precision = 5): string {
  const factor = 10 ** precision;
  let out = '';
  let prevLat = 0;
  let prevLng = 0;
  const write = (d: number) => {
    let v = d < 0 ? -2 * d - 1 : 2 * d;
    while (v >= 0x20) {
      out += String.fromCharCode((0x20 | (v % 32)) + 63);
      v = Math.floor(v / 32);
    }
    out += String.fromCharCode(v + 63);
  };
  for (const [lngV, latV] of coords) {
    const la = Math.round(latV * factor);
    const ln = Math.round(lngV * factor);
    write(la - prevLat);
    write(ln - prevLng);
    prevLat = la;
    prevLng = ln;
  }
  return out;
}
