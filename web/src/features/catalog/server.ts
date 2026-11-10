import { apiClient, unwrap } from '@/lib/api/client';
import type { CargoItem, CityItem } from '@/lib/api/server';

let cache: { at: number; cities: CityItem[]; cargo: CargoItem[] } | null = null;

/** Catálogo (público y casi estático): se cachea 10 min en memoria del servidor. */
export async function getCatalog() {
  if (cache && Date.now() - cache.at < 10 * 60_000) return cache;
  const api = apiClient();
  const [cities, cargo] = await Promise.all([unwrap(api.GET('/api/v1/catalog/cities')), unwrap(api.GET('/api/v1/catalog/cargo-types'))]);
  cache = { at: Date.now(), cities, cargo };
  return cache;
}
