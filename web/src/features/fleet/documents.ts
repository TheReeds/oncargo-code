// Documentos de flota (roadmap 2.4): tipos por dueño, etiquetas y estados de vigencia. Espeja `FleetDocumentType` del backend.
import { formatDate } from '@/lib/format';
import { addDays, daysBetween, limaToday } from '@/lib/lima-date';

export type DocOwner = 'VEHICLE' | 'DRIVER';
export type DocType = 'SOAT' | 'TECHNICAL_INSPECTION' | 'PROPERTY_CARD' | 'INSURANCE_POLICY' | 'MTC_PERMIT' | 'DRIVER_LICENSE' | 'DNI';

export const DOC_TYPES: Record<DocOwner, DocType[]> = {
  VEHICLE: ['SOAT', 'TECHNICAL_INSPECTION', 'PROPERTY_CARD', 'INSURANCE_POLICY', 'MTC_PERMIT'],
  DRIVER: ['DRIVER_LICENSE', 'DNI'],
};
export const DOC_LABEL: Record<string, string> = {
  SOAT: 'SOAT', TECHNICAL_INSPECTION: 'Revisión técnica', PROPERTY_CARD: 'Tarjeta de propiedad', INSURANCE_POLICY: 'Póliza de seguro', MTC_PERMIT: 'Permiso MTC',
  DRIVER_LICENSE: 'Licencia de conducir', DNI: 'DNI',
};
/** ¿Se exige fecha de vencimiento al subirlo? (`LOGYX-FLT-010` si falta). */
export const requiresExpiry = (t: string) => t !== 'PROPERTY_CARD' && t !== 'DNI';

export const LICENSE_CATEGORIES = ['A-I', 'A-IIa', 'A-IIb', 'A-IIIa', 'A-IIIb', 'A-IIIc'].map((c) => ({ value: c, label: c }));

export const EQUIPMENT: { value: string; label: string }[] = [
  { value: 'GPS', label: 'GPS' }, { value: 'RAMP', label: 'Rampa' }, { value: 'LIFTGATE', label: 'Plataforma hidráulica' },
  { value: 'TARP', label: 'Toldo' }, { value: 'REEFER_CERTIFIED', label: 'Frío certificado' }, { value: 'STRAPS', label: 'Cintas' },
];
export const EQUIPMENT_LABEL: Record<string, string> = Object.fromEntries(EQUIPMENT.map((e) => [e.value, e.label]));

export type Vigency = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'NO_EXPIRY';
export const VIGENCY_TONE: Record<string, 'emerald' | 'amber' | 'red' | 'neutral'> = { VALID: 'emerald', EXPIRING: 'amber', EXPIRED: 'red', NO_EXPIRY: 'neutral' };
export const VIGENCY_LABEL: Record<string, string> = { VALID: 'Vigente', EXPIRING: 'Por vencer', EXPIRED: 'Vencido', NO_EXPIRY: 'Sin vencimiento' };

/** «Vence en 12 días» · «Vence hoy» · «Venció hace 3 días» · «Vence el 12 mar» (muy lejos) · «Sin vencimiento». */
export function expiryText(status: string | undefined, expiresAt: string | undefined, daysLeft: number | undefined): string {
  if (status === 'NO_EXPIRY' || !expiresAt) return 'Sin vencimiento';
  const d = daysLeft ?? daysBetween(limaToday(), expiresAt);
  if (d < 0) return `Venció hace ${-d} ${-d === 1 ? 'día' : 'días'}`;
  if (d === 0) return 'Vence hoy';
  if (d <= 60) return `Vence en ${d} ${d === 1 ? 'día' : 'días'}`;
  return `Vence el ${formatDate(expiresAt)} ${expiresAt.slice(0, 4)}`;
}

/** Mensaje de la API («SOAT vencido el 2026-10-01: renuévalo…») → «SOAT vencido el 1 oct». Para pistas cortas. */
export function shortIssue(message: string | null | undefined): string {
  const head = (message ?? '').split(':')[0];
  return head.replace(/\d{4}-\d{2}-\d{2}/g, (iso) => formatDate(iso)).trim();
}

/** Fecha mínima razonable de vencimiento para un documento nuevo: hoy. */
export const minExpiry = () => addDays(limaToday(), 0);
