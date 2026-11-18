// Seguro para el cliente (islas) y para el servidor. Complemento de `run()` en lib/actions.ts.
//
// Las Astro Actions solo transportan `code` y `message`. Para que el formulario conozca el `field` y el código
// `LOGYX-XXX-NNN` de la API, `run()` los anexa al final del mensaje, **invisibles**: U+2063 + un JSON codificado en
// caracteres de ancho cero (U+200B = 0, U+200C = 1). Un `toast.error(error.message)` directo se ve limpio; en una isla
// usa `splitActionError(error)` (o `toFormErrors(error, [...])`).

/** Separador (U+2063) entre el mensaje y los metadatos de ancho cero. */
export const FIELD_SEP = '⁣';
const ZERO = '​';
const ONE = '‌';

export type ActionMeta = { field?: string | null; code?: string | null };

/** Sufijo invisible con `field` y `code` (vacío si no hay nada que anexar). */
export function encodeActionMeta(meta: ActionMeta): string {
  if (!meta.field && !meta.code) return '';
  const bytes = new TextEncoder().encode(JSON.stringify({ f: meta.field ?? undefined, c: meta.code ?? undefined }));
  let bits = '';
  for (const b of bytes) for (let i = 7; i >= 0; i--) bits += (b >> i) & 1 ? ONE : ZERO;
  return FIELD_SEP + bits;
}

function decodeActionMeta(tail: string): ActionMeta {
  try {
    const bits = [...tail].filter((c) => c === ZERO || c === ONE);
    const bytes = new Uint8Array(Math.floor(bits.length / 8));
    for (let i = 0; i < bytes.length; i++) for (let j = 0; j < 8; j++) bytes[i] = (bytes[i] << 1) | (bits[i * 8 + j] === ONE ? 1 : 0);
    const o = JSON.parse(new TextDecoder().decode(bytes)) as { f?: string; c?: string };
    return { field: o.f ?? null, code: o.c ?? null };
  } catch {
    return {};
  }
}

export type ActionFailure = { message: string; field: string | null; code: string | null };

/**
 * Separa el mensaje en español de la API, el nombre del campo (`field`) y el código (`LOGYX-XXX-NNN`).
 * `const { message, field } = splitActionError(error); if (field) setErrors({ [field]: message }) else toast.error(message)`
 */
export function splitActionError(error: { message: string }): ActionFailure {
  const i = error.message.indexOf(FIELD_SEP);
  if (i < 0) return { message: error.message, field: null, code: null };
  const meta = decodeActionMeta(error.message.slice(i + 1));
  return { message: error.message.slice(0, i), field: meta.field ?? null, code: meta.code ?? null };
}

/** RN07: texto del campo cuando la API responde `LOGYX-NEG-001` (datos de contacto). */
export const RN07_FIELD_MESSAGE = 'No puedes compartir teléfonos, correos, enlaces ni usuarios de redes. Coordina todo por el chat de OnCargo: así ambas partes quedan protegidas.';
export const isContactFailure = (f: Pick<ActionFailure, 'code'>) => f.code === 'LOGYX-NEG-001';
/** Organización sin verificar (`ORG-003`) o suspendida (`ORG-004`): ver `VerificationBlock`. */
export const isVerificationFailure = (f: Pick<ActionFailure, 'code'>) => f.code === 'LOGYX-ORG-003' || f.code === 'LOGYX-ORG-004';

/**
 * Mecanismo genérico campo → formulario. `fields` lista los `field` de la API que tiene el formulario (o un mapa
 * `campoApi → claveLocal`). El error de un campo conocido queda en `errors[clave]` (con el texto de RN07 si es `NEG-001`);
 * cualquier otro va a `formError` (para un `Alert` junto al botón).
 */
export function toFormErrors(error: { message: string }, fields: string[] | Record<string, string>): { errors: Record<string, string>; formError: string | null; failure: ActionFailure } {
  const failure = splitActionError(error);
  const map = Array.isArray(fields) ? Object.fromEntries(fields.map((f) => [f, f])) : fields;
  // Los errores de capacidad (`LOGYX-CAP-001..005`) siempre son del vehículo, venga o no `field`.
  const key = failure.field ? map[failure.field] : failure.code?.startsWith('LOGYX-CAP-') ? map.vehicleId : undefined;
  if (key) return { errors: { [key]: isContactFailure(failure) ? RN07_FIELD_MESSAGE : failure.message }, formError: null, failure };
  return { errors: {}, formError: failure.message, failure };
}
