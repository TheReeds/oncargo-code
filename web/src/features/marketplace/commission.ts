import { actions } from 'astro:actions';

/** Tasa de comisión vigente de OnCargo (0–1): una consulta por pestaña. Nula si no se pudo leer (la UI cae al monto sin neto). */
let cache: Promise<number | null> | null = null;
export function loadCommissionRate(): Promise<number | null> {
  cache ??= actions.marketplace.commission({}).then(({ data }) => (typeof data?.commissionRate === 'number' ? data.commissionRate : null)).catch(() => null);
  return cache;
}
