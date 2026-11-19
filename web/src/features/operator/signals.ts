import type { FraudSignal } from './server';

/** Señales de fraude (2.15): indicios para revisar, no sanciones. */
export const SIGNAL_LABELS: Record<string, string> = {
  SHARED_PHONE: 'Teléfono repetido',
  SHARED_PLATE: 'Placa repetida',
  SHARED_LICENSE: 'Licencia de conducir repetida',
  SHARED_SIGNUP_IP: 'Registros desde la misma red',
  HIGH_WITHDRAWAL_RATE: 'Retira muchas ofertas',
  HIGH_CANCELLATION_RATE: 'Cancela muchos envíos',
  LOW_BIDS_NEW_ACCOUNT: 'Cuenta nueva con ofertas muy bajas',
};

const pct = (r?: number | null) => (r == null ? '' : `${Math.round(r * 100)} %`);

/** Una línea con el dato: «987654321 en 2 empresas», «45 % de 20 ofertas»… */
export function signalDetail(s: FraudSignal): string {
  switch (s.kind) {
    case 'SHARED_PHONE': case 'SHARED_PLATE': case 'SHARED_LICENSE':
      return `${s.value ?? ''} en ${s.organizations?.length ?? 0} empresas`;
    case 'SHARED_SIGNUP_IP':
      return `${s.organizations?.length ?? 0} empresas registradas desde la misma conexión`;
    case 'HIGH_WITHDRAWAL_RATE':
      return `${pct(s.rate)} de ${s.sample} ofertas retiradas`;
    case 'HIGH_CANCELLATION_RATE':
      return `${pct(s.rate)} de ${s.sample} envíos cancelados por la empresa`;
    case 'LOW_BIDS_NEW_ACCOUNT':
      return `${pct(s.rate)} de ${s.sample} ofertas bajo el 85 % del sugerido`;
    default:
      return '';
  }
}
