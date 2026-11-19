import { SUPPORT_EMAIL } from '@/lib/contact';

export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED' | 'SUSPENDED';
export type VerificationOrg = 'PYME' | 'CARRIER';

export const VERIFICATION_LABELS: Record<VerificationStatus, string> = { PENDING: 'En revisión', VERIFIED: 'Verificada', REJECTED: 'Rechazada', SUSPENDED: 'Suspendida' };

/** Qué puede y qué no puede hacer una organización según su estado de verificación (RN01). */
export const BANNER: Record<Exclude<VerificationStatus, 'VERIFIED'>, { tone: 'warning' | 'error'; title: string; body: (org: VerificationOrg) => string }> = {
  PENDING: {
    tone: 'warning',
    title: 'Estamos verificando tu empresa',
    body: (org) => `Lo revisamos en 1 día hábil. Mientras tanto puedes explorar la plataforma${org === 'CARRIER' ? ' y registrar tu flota y tus conductores' : ''}; ${org === 'CARRIER' ? 'ofertar y publicar viajes' : 'publicar solicitudes y reservar viajes'} se habilita al terminar. Te avisaremos.`,
  },
  REJECTED: {
    tone: 'error',
    title: 'No pudimos verificar tu empresa',
    body: (org) => `Mientras no se corrija, tu empresa no está habilitada para ${org === 'CARRIER' ? 'ofertar ni publicar viajes' : 'publicar solicitudes ni reservar viajes'}. Puedes seguir consultando tu información.`,
  },
  SUSPENDED: {
    tone: 'error',
    title: 'Tu empresa está suspendida',
    body: () => 'No puedes publicar solicitudes, ofertar, reservar viajes ni aceptar ofertas. Puedes seguir consultando tu información.',
  },
};

/** Qué revisa el equipo de OnCargo (roadmap 2.17, V-07). Se muestra mientras está en revisión o si fue rechazada. */
export const VERIFICATION_CHECKLIST = [
  'Que el RUC exista y esté activo',
  'Que la razón social coincida con la del RUC',
  'Que el teléfono y la ciudad de contacto sean de la empresa',
];

/** Plazo comprometido de la revisión (días hábiles, de lunes a viernes). */
export const VERIFICATION_SLA = '1 día hábil';

export const CONTACT_HINT = `Escribe a ${SUPPORT_EMAIL} con tu RUC para revisar tu caso.`;

/** Explicación de un bloqueo `LOGYX-ORG-003` (sin verificar) u `ORG-004` (suspendida) al intentar una acción. */
export function blockCopy(code: string | null | undefined): { title: string; next: string } {
  return code === 'LOGYX-ORG-004'
    ? { title: 'Tu empresa está suspendida', next: `No puedes realizar esta acción mientras dure la suspensión. ${CONTACT_HINT}` }
    : { title: 'Tu empresa aún no está verificada', next: `Podrás hacerlo en cuanto el equipo de OnCargo termine la revisión; te avisaremos en tus notificaciones. Si fue rechazada, corrige los datos o ${CONTACT_HINT.charAt(0).toLowerCase()}${CONTACT_HINT.slice(1)}` };
}
