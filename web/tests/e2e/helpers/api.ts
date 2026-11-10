/** Cliente mínimo de la API de LOGYX para preparar datos en los tests (cada test crea sus propias cuentas). */
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8180';
export const PASSWORD = 'Logyx2026!';

export const DEMO = {
  pyme: 'pyme@logyx.pe',
  carrier: 'carrier@logyx.pe',
  carrier2: 'carrier2@logyx.pe',
  driver: 'driver@logyx.pe',
};

async function call<T = any>(method: string, path: string, token?: string, body?: unknown): Promise<T> {
  const res = await fetch(`${API_URL}/api/v1${path}`, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const json: any = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${JSON.stringify(json.error ?? json)}`);
  return json.data as T;
}

const WEIGHTS = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
/** RUC con prefijo 20 y dígito verificador válido (módulo 11). Único por llamada. */
export function validRuc(): string {
  const base = '20' + String(Math.floor(Math.random() * 1e8)).padStart(8, '0');
  const sum = [...base].reduce((a, c, i) => a + Number(c) * WEIGHTS[i], 0);
  let check = 11 - (sum % 11);
  if (check === 10) check = 0;
  if (check === 11) check = 1;
  return base + check;
}

export const uid = () => `${Date.now().toString(36)}${Math.floor(Math.random() * 1e4).toString(36)}`;

export type Account = { email: string; password: string; token: string; name: string; ruc: string };

export async function login(email: string, password = PASSWORD): Promise<string> {
  const r = await call<{ accessToken: string }>('POST', '/auth/login', undefined, { email, password });
  return r.accessToken;
}

export async function registerAccount(type: 'PYME' | 'CARRIER', city = type === 'PYME' ? 'LIMA' : 'JULIACA'): Promise<Account> {
  const id = uid();
  const acc = { email: `e2e-${id}@prueba.pe`, password: PASSWORD, name: `E2E ${type === 'PYME' ? 'Empresa' : 'Transportes'} ${id} SAC`, ruc: validRuc() };
  const r = await call<{ accessToken: string }>('POST', '/auth/register', undefined, {
    organizationType: type, legalName: acc.name, ruc: acc.ruc, city, fullName: `Tester ${id}`, email: acc.email, password: acc.password, acceptPrivacyPolicy: true,
  });
  return { ...acc, token: r.accessToken };
}

export function plate() {
  const l = () => String.fromCharCode(65 + Math.floor(Math.random() * 26));
  return `${l()}${l()}${l()}-${String(Math.floor(Math.random() * 900) + 100)}`;
}

export const addVehicle = (token: string, maxWeightKg = 12000, type = 'TRUCK_MEDIUM') =>
  call<{ id: string; plate: string }>('POST', '/vehicles', token, { plate: plate(), type, maxWeightKg, maxVolumeM3: 30 });

export const addDriver = (token: string, fullName = `Conductor ${uid()}`) =>
  call<{ id: string; fullName: string; email: string }>('POST', '/drivers', token, { fullName, email: `drv-${uid()}@prueba.pe`, password: PASSWORD, licenseNumber: `Q${Math.floor(Math.random() * 9e7 + 1e7)}` });

export function isoDate(daysFromNow: number) {
  const d = new Date(Date.now() + daysFromNow * 86400000);
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(d);
}

export type NewRequest = Partial<{ originCity: string; destinationCity: string; weightKg: number; cargoType: string; pickupDate: string; cargoDescription: string }>;
export const createRequest = (token: string, o: NewRequest = {}) =>
  call<any>('POST', '/shipment-requests', token, {
    originCity: 'LIMA', originAddress: 'Av. Argentina 1234, Callao', destinationCity: 'JULIACA', destinationAddress: 'Jr. Lima 456, Juliaca',
    cargoType: 'GENERAL', cargoDescription: `Carga de prueba ${uid()}`, weightKg: 3200, pickupDate: isoDate(5), ...o,
  });

export const placeBid = (token: string, requestId: string, vehicleId: string, amount: number, notes?: string) =>
  call<any>('POST', `/shipment-requests/${requestId}/bids`, token, { vehicleId, amount, notes });

export const acceptBid = (token: string, bidId: string) => call<any>('POST', `/bids/${bidId}/accept`, token);
export const getRequest = (token: string, id: string) => call<any>('GET', `/shipment-requests/${id}`, token);
export const listShipments = (token: string) => call<any[]>('GET', '/shipments', token);
export const assignDriver = (token: string, shipmentId: string, driverId: string) => call<any>('PUT', `/shipments/${shipmentId}/driver`, token, { driverId });
