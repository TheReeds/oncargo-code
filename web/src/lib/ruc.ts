/**
 * Validación local de RUC peruano (SUNAT). Espejo de `identity/domain/RucValidator` del backend:
 * 11 dígitos, prefijo 10/15/17/20 y dígito verificador por módulo 11. La API sigue siendo la fuente de verdad.
 */
const WEIGHTS = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];

export function isValidRuc(ruc: string): boolean {
  if (!/^\d{11}$/.test(ruc)) return false;
  if (!['10', '15', '17', '20'].includes(ruc.slice(0, 2))) return false;
  const sum = WEIGHTS.reduce((acc, w, i) => acc + Number(ruc[i]) * w, 0);
  let check = 11 - (sum % 11);
  if (check === 10) check = 0;
  if (check === 11) check = 1;
  return check === Number(ruc[10]);
}

/** Tipo de contribuyente según el prefijo del RUC (orientativo, para mostrar al escribirlo). */
export function rucKind(ruc: string): string | null {
  const p = ruc.slice(0, 2);
  if (p === '20') return 'Persona jurídica';
  if (p === '10') return 'Persona natural con negocio';
  if (p === '15' || p === '17') return 'Otro tipo de contribuyente';
  return null;
}
