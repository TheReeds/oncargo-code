import type { APIRoute } from 'astro';
import { API_URL } from '@/lib/api/client';

export const prerender = false;
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', 'cache-control': 'private, no-store' } });
const err = (status: number, code: string, message: string, field?: string) => json(status, { error: { code, message, field: field ?? null } });
const TYPES = ['SERVICE_ORDER', 'REMISSION_GUIDE', 'RECEIPT', 'OTHER'];

/**
 * Subida multipart de documentos de las partes. Reenvía el archivo a la API con el Bearer de la sesión.
 * Respuesta: `{ data }` (201) o `{ error: { code, message, field } }` (la API ya trae `field: "file"` en LOGYX-DOC-00x).
 */
export const POST: APIRoute = async ({ request, locals }) => {
  const session = locals.session;
  if (!session) return err(401, 'LOGYX-AUTH-401', 'Tu sesión expiró. Inicia sesión nuevamente.');
  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return err(400, 'LOGYX-VAL-001', 'No pudimos leer el archivo. Vuelve a elegirlo e inténtalo de nuevo.', 'file');
  }
  const shipmentId = String(form.get('shipmentId') ?? '');
  const type = String(form.get('type') ?? '');
  const file = form.get('file');
  if (!/^[0-9a-f-]{36}$/i.test(shipmentId)) return err(400, 'LOGYX-VAL-001', 'No identificamos el envío. Recarga la página e inténtalo de nuevo.');
  if (!TYPES.includes(type)) return err(400, 'LOGYX-DOC-001', 'Elige el tipo de documento.', 'type');
  if (!(file instanceof File) || file.size === 0) return err(400, 'LOGYX-DOC-003', 'Elige un archivo para subir.', 'file');

  const body = new FormData();
  body.set('file', file, file.name);
  body.set('type', type);
  let res: Response;
  try {
    res = await fetch(`${API_URL}/api/v1/shipments/${shipmentId}/documents`, { method: 'POST', headers: { authorization: `Bearer ${session.token}` }, body });
  } catch {
    return err(502, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  const payload = await res.json().catch(() => null);
  if (!res.ok) {
    const e = payload?.error;
    const fallback = res.status === 413 ? 'El archivo supera el límite de 10 MB. Elige uno más liviano.' : 'No pudimos subir el documento. Inténtalo de nuevo.';
    return err(res.status, e?.code ?? `HTTP-${res.status}`, e?.message ?? fallback, e?.field ?? (res.status === 413 ? 'file' : undefined));
  }
  return json(201, payload);
};
