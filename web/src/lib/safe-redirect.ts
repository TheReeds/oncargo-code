/**
 * Devuelve `next` solo si es una ruta interna del mismo origen; si no, `fallback`.
 * Evita redirecciones abiertas (p. ej. `/\example.com` o `//example.com`, que el
 * navegador interpreta como otro host).
 */
export function safeNext(next: string | undefined | null, fallback: string, origin: string): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.includes('\\')) return fallback;
  try {
    const url = new URL(next, origin);
    if (url.origin !== origin) return fallback;
    return url.pathname + url.search + url.hash;
  } catch {
    return fallback;
  }
}
