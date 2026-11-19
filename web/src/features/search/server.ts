import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type SearchResult = Schemas['SearchResultView'];
export const search = (token: string, q: string, types?: string) => unwrap(apiFor(token).GET('/api/v1/search', { params: { query: { q, types } } }));
