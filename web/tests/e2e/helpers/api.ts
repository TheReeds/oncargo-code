/** Cliente mínimo de la API de LOGYX para preparar datos en los tests (cada test crea sus propias cuentas). */
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8180';
export const PASSWORD = 'Logyx2026!';

export const DEMO = {
  pyme: 'pyme@logyx.pe',
  carrier: 'carrier@logyx.pe',
  carrier2: 'carrier2@logyx.pe',
  driver: 'driver@logyx.pe',
  operator: 'operator@logyx.pe',
};

export async function call<T = any>(method: string, path: string, token?: string, body?: unknown): Promise<T> {
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

/** PNG válido de 1x1 px (la API reconoce el formato por firma binaria). */
export const PNG_1X1 = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==', 'base64');

/** Sube un documento (multipart) a un envío. */
export async function uploadDocument(token: string, shipmentId: string, type: string, opts: { stopId?: string; bytes?: Buffer; name?: string; mime?: string } = {}) {
  const form = new FormData();
  form.set('file', new Blob([new Uint8Array(opts.bytes ?? PNG_1X1)], { type: opts.mime ?? 'image/png' }), opts.name ?? 'evidencia.png');
  form.set('type', type);
  if (opts.stopId) form.set('stopId', opts.stopId);
  const res = await fetch(`${API_URL}/api/v1/shipments/${shipmentId}/documents`, { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: form });
  const json: any = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`upload -> ${res.status} ${JSON.stringify(json.error ?? json)}`);
  return json.data;
}

export type Scenario = { pyme: Account; carrier: Account; vehicle: { id: string; plate: string }; request: any; bid: any };

/** PYME + transportista con flota, una solicitud publicada y una oferta pendiente. */
export async function openBidScenario(amount = 2300, requestOpts: NewRequest = {}): Promise<Scenario> {
  const [pyme, carrier] = await Promise.all([registerAccount('PYME'), registerAccount('CARRIER')]);
  const vehicle = await addVehicle(carrier.token);
  const request = await createRequest(pyme.token, requestOpts);
  const bid = await placeBid(carrier.token, request.id, vehicle.id, Math.max(amount, Math.ceil(request.priceFloor)), 'Oferta de prueba');
  return { pyme, carrier, vehicle, request, bid };
}

/**
 * Envío ENTREGADO por API (sin calificar): solicitud → oferta → aceptación → conductor → paradas → foto de entrega → entrega.
 * Devuelve las cuentas para entrar por UI y calificar.
 */
export async function deliveredShipment(opts: { driverCompletes?: boolean } = {}) {
  const sc = await openBidScenario();
  const accepted = await acceptBid(sc.pyme.token, sc.bid.id);
  const shipmentId: string = accepted.shipmentId;
  const driver = await addDriver(sc.carrier.token);
  await assignDriver(sc.carrier.token, shipmentId, driver.id);
  const dt = await login(driver.email);
  const ship = await call<any>('GET', `/shipments/${shipmentId}`, sc.pyme.token);
  const stops: any[] = [...ship.stops].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0));
  for (const stop of stops) {
    await call('POST', `/shipments/${shipmentId}/stops/${stop.id}/arrive`, dt);
    if (stop.type === 'DELIVERY') await uploadDocument(dt, shipmentId, 'DELIVERY_PHOTO', { stopId: stop.id });
    if (opts.driverCompletes === false && stop.type === 'DELIVERY') break;
    await call('POST', `/shipments/${shipmentId}/stops/${stop.id}/complete`, dt);
  }
  return { ...sc, shipmentId, driver, driverToken: dt };
}

export const operatorToken = () => login(DEMO.operator);
export const reportIncident = (token: string, shipmentId: string, category = 'DELAY', description = 'El camión llegará con retraso por un bloqueo en la vía.') =>
  call<any>('POST', `/shipments/${shipmentId}/incidents`, token, { category, description });

/** Candidatos y asignación manual del operador (RN10). */
export const operatorCandidates = (token: string, requestId: string) => call<any[]>('GET', `/operator/requests/${requestId}/candidates`, token);
export const operatorAlerts = (token: string) => call<any>('GET', '/operator/sla-alerts', token);

export const me = (token: string) => call<{ organizationId: string; organizationName: string }>('GET', '/auth/me', token);
