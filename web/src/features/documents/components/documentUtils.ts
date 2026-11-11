import type { DocumentView } from '../server';

export const isImage = (d: DocumentView) => !!d.contentType?.startsWith('image/');
export const isEvidence = (d: DocumentView) => d.type === 'DELIVERY_PHOTO' || d.type === 'SIGNATURE';
/** URL del documento por el proxy autenticado del servidor Astro (nunca la URL directa de la API). */
export const docUrl = (d: DocumentView, download = false) => `/documentos/${d.id}${download ? '?download=1' : ''}`;
