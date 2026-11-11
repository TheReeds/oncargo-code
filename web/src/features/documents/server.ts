import { apiFor } from '@/lib/api/server';
import { unwrap } from '@/lib/api/client';
import type { Schemas } from '@/lib/api/server';

export type DocumentView = Schemas['DocumentView'];
export type DocumentType = Schemas['DocumentType'];

/** Tipos que las partes (PYME/transportista) pueden subir desde la web. Foto y firma las sube el conductor. */
export const UPLOADABLE_TYPES = ['SERVICE_ORDER', 'REMISSION_GUIDE', 'RECEIPT', 'OTHER'] as const;

export const listDocuments = (token: string, id: string) =>
  unwrap(apiFor(token).GET('/api/v1/shipments/{id}/documents', { params: { path: { id } } }));
