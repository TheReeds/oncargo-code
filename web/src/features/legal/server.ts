import { apiClient, unwrap } from '@/lib/api/client';

/** Respaldo si la API no responde (la versión real viaja con el registro; si no coincide, la API la rechaza con 409). */
export const FALLBACK_TERMS = { termsVersion: '2026-11-17-borrador', termsStatus: 'DRAFT' };

/** Versión vigente de los Términos y condiciones y su estado (`DRAFT` = borrador pendiente de revisión legal). */
export async function getLegal() {
  try {
    return await unwrap(apiClient().GET('/api/v1/public/legal'));
  } catch {
    return FALLBACK_TERMS;
  }
}

/** Comisión vigente de OnCargo (la paga el transportista) y el IGV; para la página de precios. */
export async function getPublicCommission() {
  try {
    return await unwrap(apiClient().GET('/api/v1/public/pricing/commission'));
  } catch {
    return null;
  }
}

type PublicQuoteBody = { originCity: string; destinationCity: string; weightKg: number; cargoType: string };

/**
 * Cotización de referencia sin sesión. `clientIp` (IP del navegador) se reenvía en X-Forwarded-For: el límite del
 * backend es por IP y, sin ella, todos los visitantes compartirían el cupo de este servidor. Lanza `ApiError`
 * (400 por datos inválidos, 429 por exceso de consultas).
 */
export const getPublicQuote = (body: PublicQuoteBody, clientIp?: string) =>
  unwrap(apiClient(undefined, clientIp).POST('/api/v1/public/pricing/quote', { body: body as never }));

let example: { at: number; quote: Awaited<ReturnType<typeof getPublicQuote>> | null } | null = null;

/** Ejemplo fijo de la página de precios (Lima → Juliaca, 3 t, carga general), cacheado 10 min para no gastar el cupo. */
export async function getExampleQuote() {
  if (example && Date.now() - example.at < 10 * 60_000) return example.quote;
  let quote = null;
  try {
    quote = await getPublicQuote({ originCity: 'LIMA', destinationCity: 'JULIACA', weightKg: 3000, cargoType: 'GENERAL' });
  } catch {
    /* sin ejemplo: la página lo omite */
  }
  example = { at: Date.now(), quote };
  return quote;
}
