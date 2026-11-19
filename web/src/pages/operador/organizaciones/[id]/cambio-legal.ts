import type { APIRoute } from 'astro';
import { API_URL } from '@/lib/api/client';

export const prerender = false;
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', 'cache-control': 'private, no-store' } });
const err = (status: number, code: string, message: string, field?: string) => json(status, { error: { code, message, field: field ?? null } });

/**
 * Cambio asistido de razón social o RUC (2.15): reenvía el multipart (legalName, ruc, reason, file) a la API con el Bearer de
 * la sesión del operador. El token no sale del servidor. Respuesta `{ data }` o `{ error }` tal cual la API.
 */
export const POST: APIRoute = async ({ request, locals, params }) => {
  const session = locals.session;
  if (!session || session.orgType !== 'OPERATOR') return err(403, 'LOGYX-AUTH-403', 'Solo el equipo de OnCargo puede hacer este cambio.');
  const id = String(params.id ?? '');
  if (!/^[0-9a-f-]{36}$/i.test(id)) return err(404, 'LOGYX-GEN-404', 'Organización no encontrada');
  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return err(400, 'LOGYX-VAL-001', 'No pudimos leer el formulario. Vuelve a elegir el archivo.', 'file');
  }
  const file = form.get('file');
  if (!(file instanceof File) || file.size === 0) return err(400, 'LOGYX-DOC-003', 'Adjunta el documento de sustento.', 'file');
  const body = new FormData();
  for (const k of ['legalName', 'ruc', 'reason']) {
    const v = String(form.get(k) ?? '').trim();
    if (v) body.set(k, v);
  }
  body.set('file', file, file.name);
  let res: Response;
  try {
    res = await fetch(`${API_URL}/api/v1/operator/organizations/${id}/legal-change`, { method: 'POST', headers: { authorization: `Bearer ${session.token}` }, body });
  } catch {
    return err(502, 'NETWORK', 'No pudimos conectar con el servidor. Intenta nuevamente en unos segundos.');
  }
  const payload = await res.json().catch(() => null);
  if (!res.ok) {
    const e = payload?.error;
    return err(res.status, e?.code ?? `HTTP-${res.status}`, e?.message ?? 'No pudimos registrar el cambio. Inténtalo de nuevo.', e?.field ?? undefined);
  }
  return json(200, payload);
};
