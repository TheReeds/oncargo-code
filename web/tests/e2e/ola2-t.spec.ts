import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pickDay, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

/**
 * Ola 2, zona T (transportista): búsquedas guardadas y alertas (2.3), mejorar oferta, posición y precio de la ruta (2.8),
 * documentos y configuración de flota (2.4), agenda de flota y asignación sugerida (2.13).
 */

/** Documento de flota por API (multipart): `owner` = `vehicles` | `drivers`. */
async function uploadFleetDoc(token: string, owner: 'vehicles' | 'drivers', id: string, type: string, expiresAt?: string) {
  const form = new FormData();
  form.set('file', new Blob([new Uint8Array(api.EVIDENCE_PHOTO)], { type: 'image/png' }), 'doc.png');
  form.set('type', type);
  if (expiresAt) form.set('expiresAt', expiresAt);
  const res = await fetch(`${api.API_URL}/api/v1/${owner}/${id}/documents`, { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: form });
  const json: any = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`doc -> ${res.status} ${JSON.stringify(json.error ?? json)}`);
  return json.data;
}

/** PYME + transportista con vehículo (y documentos subidos ANTES de ofertar: los sellos se fijan al ofertar) + oferta pendiente. */
async function bidWithDocs(amount: number, docs: { soatDays?: number; inspectionDays?: number } = {}) {
  const [pyme, carrier] = await Promise.all([api.registerAccount('PYME'), api.registerAccount('CARRIER')]);
  const vehicle = await api.addVehicle(carrier.token);
  if (docs.soatDays) await uploadFleetDoc(carrier.token, 'vehicles', vehicle.id, 'SOAT', api.isoDate(docs.soatDays));
  if (docs.inspectionDays) await uploadFleetDoc(carrier.token, 'vehicles', vehicle.id, 'TECHNICAL_INSPECTION', api.isoDate(docs.inspectionDays));
  const request = await api.createRequest(pyme.token);
  const bid = await api.placeBid(carrier.token, request.id, vehicle.id, Math.max(amount, Math.ceil(request.priceFloor)));
  return { pyme, carrier, vehicle, request, bid };
}

/** Transportista con vehículo + una solicitud de PYME ya adjudicada (envío PENDING sin conductor). */
async function shipmentScenario(opts: { pickupInDays?: number; carrier?: api.Account; vehicle?: { id: string; plate: string }; pyme?: api.Account } = {}) {
  const pyme = opts.pyme ?? (await api.registerAccount('PYME'));
  const carrier = opts.carrier ?? (await api.registerAccount('CARRIER'));
  const vehicle = opts.vehicle ?? (await api.addVehicle(carrier.token));
  const request = await api.createRequest(pyme.token, { pickupDate: api.isoDate(opts.pickupInDays ?? 3) });
  const bid = await api.placeBid(carrier.token, request.id, vehicle.id, Math.ceil(request.priceFloor) + 100);
  const accepted = await api.acceptBid(pyme.token, bid.id);
  return { pyme, carrier, vehicle, request, bid, shipmentId: accepted.shipmentId as string };
}

test.describe('Zona T · funcionalidad (claro)', () => {
  test('búsquedas guardadas: guardar, ver en «Mis búsquedas», aplicar, pausar y borrar', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token);
    await setSession(page.context(), carrier.token, BASE);

    await goto(page, '/transportista/marketplace?origen=LIMA&destino=JULIACA');
    await page.getByTestId('saved-searches-trigger').click();
    await expect(page.getByTestId('saved-empty')).toBeVisible();
    await expect(page.getByRole('dialog', { name: 'Mis búsquedas' }).getByRole('link', { name: /Elige cómo te avisamos/ })).toHaveAttribute('href', '/cuenta/avisos');
    await page.keyboard.press('Escape');

    await page.getByRole('button', { name: 'Guardar búsqueda' }).click();
    const dlg = page.getByRole('dialog', { name: 'Guardar búsqueda' });
    await expect(dlg.getByTestId('saved-summary')).toContainText('Lima → Juliaca');
    await expect(dlg.getByRole('link', { name: 'Elige cómo te avisamos' })).toHaveAttribute('href', '/cuenta/avisos');
    await dlg.getByRole('button', { name: 'Guardar búsqueda' }).click();           // sin nombre
    await expect(dlg.getByText(/Ponle un nombre/)).toBeVisible();
    await dlg.getByLabel('Nombre de la búsqueda').fill('Lima a Juliaca');
    await dlg.getByRole('button', { name: 'Guardar búsqueda' }).click();
    await expect(toastWith(page, 'Búsqueda guardada')).toBeVisible();

    // Aplicar desde otra vista de cargas
    await goto(page, '/transportista/marketplace');
    await page.getByTestId('saved-searches-trigger').click();
    const item = page.getByTestId('saved-item').filter({ hasText: 'Lima a Juliaca' });
    await expect(item).toContainText('Lima → Juliaca');
    await item.getByRole('link', { name: /Aplicar/ }).click();
    await page.waitForURL(/origen=LIMA&destino=JULIACA/);

    // Pausar y reanudar
    await goto(page, '/transportista/marketplace');
    await page.getByTestId('saved-searches-trigger').click();
    await page.getByRole('switch', { name: /Pausar «Lima a Juliaca»/ }).click();
    await expect(toastWith(page, 'Búsqueda en pausa')).toBeVisible();
    await expect(page.getByTestId('saved-item')).toContainText('En pausa');
    const list: any[] = await api.call('GET', '/saved-searches', carrier.token);
    expect(list.find((s) => s.name === 'Lima a Juliaca').active).toBe(false);
    await page.getByRole('switch', { name: /Reanudar «Lima a Juliaca»/ }).click();
    await expect(toastWith(page, 'Búsqueda reanudada')).toBeVisible();

    // Borrar con confirmación
    await page.getByRole('button', { name: /Borrar «Lima a Juliaca»/ }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Borrar búsqueda' }).click();
    await expect(toastWith(page, 'Búsqueda borrada')).toBeVisible();
    if (!(await page.getByTestId('saved-empty').isVisible())) await page.getByTestId('saved-searches-trigger').click();   // el diálogo de confirmación cierra el menú
    await expect(page.getByTestId('saved-empty')).toBeVisible();
    expect(await api.call<any[]>('GET', '/saved-searches', carrier.token)).toHaveLength(0);
  });

  test('guardar una búsqueda sin filtros no se permite y el servidor explica los errores', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/marketplace');
    await expect(page.getByRole('button', { name: 'Guardar búsqueda' })).toBeDisabled();
    const r = await api.raw('POST', '/saved-searches', carrier.token, { name: 'Sin nada' });
    expect(r.code).toBe('LOGYX-ALT-001');
    const same = await api.raw('POST', '/saved-searches', carrier.token, { name: 'Igual', originCity: 'LIMA', destinationCity: 'LIMA' });
    expect(same.code).toBe('LOGYX-ALT-003');
  });

  test('aviso de carga nueva: abre la carga en «Cargas disponibles» y el aviso de documento lleva a Flota', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const vehicle = await api.addVehicle(carrier.token);
    const req = await api.createRequest(pyme.token, { pickupDate: api.isoDate(6) });
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, `/transportista/marketplace?solicitud=${req.id}`);
    await expect(page.getByRole('dialog', { name: 'Enviar oferta' })).toBeVisible();
    // Una carga que ya no está abierta se explica
    await goto(page, `/transportista/marketplace?solicitud=00000000-0000-4000-8000-000000000000`);
    await expect(page.getByText(/No encontramos esa carga/)).toBeVisible();
    // Enlaces de avisos (función pura)
    const { notificationHref } = await import('../../src/lib/notification-links');
    expect(notificationHref({ type: 'NEW_LOAD_MATCH', relatedType: 'REQUEST', relatedId: req.id }, 'CARRIER')).toBe(`/transportista/marketplace?solicitud=${req.id}`);
    expect(notificationHref({ type: 'FLEET_DOCUMENT_EXPIRING', relatedType: 'VEHICLE', relatedId: vehicle.id }, 'CARRIER')).toBe(`/transportista/flota?vehiculo=${vehicle.id}`);
    expect(notificationHref({ type: 'FLEET_DOCUMENT_EXPIRING', relatedType: 'DRIVER', relatedId: 'd1' }, 'CARRIER')).toBe('/transportista/flota?conductor=d1');
    expect(notificationHref({ type: 'BID_IMPROVED', relatedType: 'REQUEST', relatedId: req.id }, 'PYME')).toBe(`/pyme/solicitudes/${req.id}`);
    expect(notificationHref({ type: 'TIPO_NUEVO', relatedType: 'ALGO_NUEVO' }, 'CARRIER')).toBe('/notificaciones');
  });

  test('/cuenta/avisos pinta «Alertas de carga» y «Documentos de flota»', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/cuenta/avisos');
    await expect(page.getByTestId('pref-ALERTS')).toBeVisible();
    await expect(page.getByTestId('pref-FLEET')).toBeVisible();
  });

  test('mejorar oferta: subir da el error, bajar funciona, queda el historial y la PYME ve «Mejorada» y los sellos', async ({ page, browser }) => {
    const sc = await bidWithDocs(6000, { soatDays: 200 });
    const floor = Math.ceil(sc.request.priceFloor);
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, '/transportista/ofertas');
    await page.getByRole('button', { name: /Mejorar oferta de Lima a Juliaca/ }).click();
    const dlg = page.getByRole('dialog', { name: 'Mejorar oferta' });
    await expect(dlg.getByTestId('revisions-empty')).toBeVisible();
    await expect(dlg.getByRole('combobox', { name: 'Vehículo' })).toBeEnabled();

    // Subir el monto: error en el campo
    await dlg.getByLabel('Monto nuevo').fill('6500');
    await dlg.getByRole('button', { name: 'Mejorar oferta', exact: true }).click();
    await expect(dlg.getByText(/debe ser menor a tu oferta actual/)).toBeVisible();
    // Y el servidor responde LOGYX-BID-006
    const up = await api.raw('POST', `/bids/${sc.bid.id}/improve`, sc.carrier.token, { amount: 6500 });
    expect(up.status).toBe(400);
    expect(up.code).toBe('LOGYX-BID-006');

    // Bajar el monto
    const lower = Math.max(floor, 5800);
    await dlg.getByLabel('Monto nuevo').fill(String(lower));
    await dlg.getByRole('button', { name: 'Mejorar oferta', exact: true }).click();
    await expect(toastWith(page, 'Oferta mejorada')).toBeVisible();
    await expect(page.getByTestId('bid-improved')).toBeVisible();
    await expect(page.getByText(/S\/ 5,800\.00|S\/ [\d,]+\.00/).first()).toBeVisible();

    // Historial
    await page.getByRole('button', { name: /Mejorar oferta de Lima a Juliaca/ }).click();
    await expect(page.getByRole('dialog', { name: 'Mejorar oferta' }).getByTestId('revisions')).toContainText('S/ 6,000.00 →');
    await page.keyboard.press('Escape');

    // La PYME ve «Mejorada» y «SOAT vigente» (sin montos ajenos)
    const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE', viewport: { width: 1440, height: 900 } });
    const ppage = await ctx.newPage();
    await setSession(ctx, sc.pyme.token, BASE);
    await goto(ppage, `/pyme/solicitudes/${sc.request.id}`);
    const seals = ppage.getByTestId('bid-seals');
    await expect(seals).toContainText('Mejorada');
    await expect(seals).toContainText('SOAT vigente');
    await expect(seals).not.toContainText('Revisión técnica vigente');
    await ctx.close();
  });

  test('posición aproximada en «Mis ofertas» (sin montos ajenos) y precio de la ruta oculto sin datos', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const [mine, c2, c3] = await Promise.all([api.registerAccount('CARRIER'), api.registerAccount('CARRIER'), api.registerAccount('CARRIER')]);
    const request = await api.createRequest(pyme.token, { pickupDate: api.isoDate(6) });
    const floor = Math.ceil(request.priceFloor);
    await api.placeBid(c2.token, request.id, (await api.addVehicle(c2.token)).id, floor + 100);
    await api.placeBid(c3.token, request.id, (await api.addVehicle(c3.token)).id, floor + 200);
    await api.placeBid(mine.token, request.id, (await api.addVehicle(mine.token)).id, floor + 500);
    await setSession(page.context(), mine.token, BASE);
    await goto(page, '/transportista/ofertas');
    const standing = page.getByTestId('bid-standing');
    await expect(standing).toContainText('Vas 3.º de 3');
    await expect(page.getByTestId('bid-position')).toHaveText(/Entre las 2 más bajas|Por debajo de la mediana|Por encima de la mediana/);
    await expect(standing).not.toContainText(String(floor + 100));
    await expect(standing).not.toContainText(String(floor + 200));

    // Precio de la ruta: sin 5 envíos en la ruta (aquí Abancay → Ayacucho) el bloque no aparece
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token);
    const quiet = await api.createRequest(pyme.token, { originCity: 'ABANCAY', destinationCity: 'AYACUCHO', pickupDate: api.isoDate(6) });
    expect(await api.call('GET', `/shipment-requests/${quiet.id}/lane-stats`, carrier.token)).toBeNull();
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, `/transportista/marketplace?solicitud=${quiet.id}`);
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await expect(sheet.getByLabel('Tu oferta')).toBeVisible();
    await page.waitForTimeout(800);
    await expect(sheet.getByTestId('lane-stats')).toHaveCount(0);
    await expect(sheet.getByText('Precio de esta ruta')).toHaveCount(0);
  });

  test('precio de la ruta: con datos suficientes muestra la mediana adjudicada y el rango (nunca montos ajenos)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token);
    const req = await api.createRequest(pyme.token, { pickupDate: api.isoDate(6) });   // Lima → Juliaca: la ruta con más envíos de prueba
    const lane = await api.call<any>('GET', `/shipment-requests/${req.id}/lane-stats`, carrier.token);
    test.skip(!lane, 'La ruta Lima → Juliaca aún no tiene 5 envíos adjudicados de 3 transportistas en esta base');
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, `/transportista/marketplace?solicitud=${req.id}`);
    const box = page.getByRole('dialog', { name: 'Enviar oferta' }).getByTestId('lane-stats');
    await expect(box).toContainText('Mediana adjudicada en esta ruta');
    await expect(box).toContainText('últimos 90 días');
    await expect(box).toContainText('Rango habitual');
  });

  test('documentos: subir un SOAT, ver su estado y el resumen de vencimientos; descargar por el proxy', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token);
    const drv = await api.addDriver(carrier.token);
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/flota');
    await expect(page.getByTestId('expiry-list')).toHaveCount(0);

    await page.getByRole('button', { name: `Documentos de ${v.plate}`, exact: true }).click();
    const sheet = page.getByRole('dialog', { name: `Documentos · ${v.plate}` });
    await expect(sheet.getByTestId('doc-SOAT')).toContainText('Sin subir');
    await sheet.getByRole('button', { name: 'Subir documento de SOAT' }).click();
    await sheet.locator('input[type=file]').setInputFiles({ name: 'soat.png', mimeType: 'image/png', buffer: api.EVIDENCE_PHOTO });
    await sheet.getByRole('button', { name: 'Subir documento', exact: true }).click();     // sin vencimiento
    await expect(sheet.getByText('Indica la fecha de vencimiento.')).toBeVisible();
    await sheet.locator('#doc-SOAT-exp').click();
    await pickDay(page, api.isoDate(10));
    await sheet.getByLabel('Número').fill('SOAT-0001');
    await sheet.getByRole('button', { name: 'Subir documento', exact: true }).click();
    await expect(toastWith(page, 'Documento subido')).toBeVisible();
    const soat = sheet.getByTestId('doc-SOAT');
    await expect(soat).toContainText('Por vencer');
    await expect(soat.getByTestId('doc-expiry')).toContainText('Vence en 10 días');
    // Descarga autenticada (sin exponer el JWT)
    const href = await soat.getByRole('link', { name: /Descargar/ }).getAttribute('href');
    expect(href).toMatch(/^\/flota\/documentos\/[0-9a-f-]{36}\?download=1$/);
    const dl = await page.request.get(href!);
    expect(dl.status()).toBe(200);
    expect(dl.headers()['content-type']).toContain('image/png');

    // Reemplazar: el anterior pasa al historial
    await soat.getByRole('button', { name: 'Subir uno nuevo de SOAT' }).click();
    await sheet.locator('input[type=file]').setInputFiles({ name: 'soat2.png', mimeType: 'image/png', buffer: api.EVIDENCE_PHOTO });
    await sheet.locator('#doc-SOAT-exp').click();
    await pickDay(page, api.isoDate(300));
    await sheet.getByRole('button', { name: 'Subir documento', exact: true }).click();
    await expect(soat).toContainText('Vigente');
    await expect(sheet.getByTestId('doc-history')).toContainText('Historial (1)');
    await page.keyboard.press('Escape');

    // Vencimiento por vencer: aviso arriba de Flota con enlace al vehículo
    await uploadFleetDoc(carrier.token, 'vehicles', v.id, 'TECHNICAL_INSPECTION', api.isoDate(5));
    await goto(page, '/transportista/flota');
    await expect(page.getByTestId('expiry-list')).toContainText('Revisión técnica');
    await expect(page.getByTestId('expiry-list')).toContainText('Vence en 5 días');
    await page.getByTestId('expiry-list').getByRole('button', { name: /Ver documentos/ }).click();
    await expect(page.getByRole('dialog', { name: `Documentos · ${v.plate}` })).toBeVisible();
    await page.keyboard.press('Escape');

    // Conductor: licencia y DNI + categoría
    await page.getByRole('button', { name: new RegExp(`Documentos de ${drv.fullName}`) }).click();
    const dsheet = page.getByRole('dialog', { name: new RegExp(`Documentos · ${drv.fullName}`) });
    await expect(dsheet.getByTestId('doc-DRIVER_LICENSE')).toBeVisible();
    await expect(dsheet.getByTestId('doc-DNI')).toBeVisible();
    await dsheet.getByRole('textbox', { name: 'DNI' }).fill('12345678');
    await dsheet.getByRole('button', { name: 'Guardar datos' }).click();
    await expect(toastWith(page, 'Datos del conductor guardados')).toBeVisible();
    const drivers: any[] = await api.call('GET', '/drivers', carrier.token);
    expect(drivers.find((d) => d.id === drv.id).dni).toBe('12345678');
  });

  test('configurar vehículo: equipamiento y tipos de carga que aceptas (solo restringe)', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token);   // camión mediano: general, frágil, peligrosa y a granel
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/flota');
    await page.getByRole('button', { name: `Acciones de ${v.plate}` }).click();
    await page.getByRole('menuitem', { name: 'Configurar vehículo' }).click();
    const dlg = page.getByRole('dialog', { name: `Configurar ${v.plate}` });
    await expect(dlg.getByRole('checkbox', { name: /Perecible/ })).toHaveCount(0);   // la matriz RN02 no lo permite
    await expect(dlg.getByRole('checkbox', { name: /Frágil/ })).toBeChecked();
    await dlg.getByRole('checkbox', { name: 'GPS' }).click();
    await dlg.getByRole('checkbox', { name: /Frágil/ }).click();
    await dlg.getByRole('button', { name: 'Guardar configuración' }).click();
    await expect(toastWith(page, 'Configuración guardada')).toBeVisible();
    await expect(page.getByTestId('vehicle-equipment')).toContainText('GPS');
    const saved: any[] = await api.call('GET', '/vehicles', carrier.token);
    const mine = saved.find((x) => x.id === v.id);
    expect(mine.equipment).toEqual(['GPS']);
    expect(new Set(mine.acceptedCargoTypes)).toEqual(new Set(['GENERAL', 'HAZARDOUS', 'BULK']));
    // El servidor no deja ampliar la matriz
    const wide = await api.raw('PUT', `/vehicles/${v.id}/config`, carrier.token, { equipment: [], acceptedCargoTypes: ['PERISHABLE'] });
    expect(wide.code).toBe('LOGYX-FLT-009');
    // Sin ningún tipo marcado no se guarda
    await page.getByRole('button', { name: `Acciones de ${v.plate}` }).click();
    await page.getByRole('menuitem', { name: 'Configurar vehículo' }).click();
    const again = page.getByRole('dialog', { name: `Configurar ${v.plate}` });
    await expect(again.getByRole('checkbox', { name: 'GPS' })).toBeChecked();
    await expect(again.getByRole('checkbox', { name: /Frágil/ })).not.toBeChecked();
  });

  test('agenda: 7 y 14 días, semana siguiente, envío y filas por vehículo y conductor', async ({ page }) => {
    const sc = await shipmentScenario({ pickupInDays: 2 });
    const driver = await api.addDriver(sc.carrier.token);
    await api.assignDriver(sc.carrier.token, sc.shipmentId, driver.id);
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, '/transportista/agenda');
    await expect(page.getByRole('heading', { name: 'Agenda de flota' })).toBeVisible();
    await expect(page.getByTestId('agenda-legend')).toBeVisible();
    const table = page.getByRole('region', { name: 'Agenda de vehículos' });
    await expect(table.getByRole('columnheader')).toHaveCount(8);                    // vehículo + 7 días
    await expect(table.getByRole('columnheader', { name: /Hoy/ })).toHaveAttribute('aria-current', 'date');
    const row = table.getByRole('row').filter({ hasText: sc.vehicle.plate });
    await expect(row.locator('[data-kind=SHIPMENT]').first()).toContainText('Envío');
    await page.getByRole('tab', { name: /Conductores/ }).click();
    const dtable = page.getByRole('region', { name: 'Agenda de conductores' });
    await expect(dtable.getByRole('row').filter({ hasText: driver.fullName }).locator('[data-kind=SHIPMENT]').first()).toBeVisible();

    await page.getByRole('button', { name: '14 días' }).click();
    await page.waitForURL(/dias=14/);
    await hydrated14(page);
    await expect(page.getByRole('region', { name: 'Agenda de vehículos' }).getByRole('columnheader')).toHaveCount(15);
    await page.getByRole('button', { name: /Semana siguiente/ }).click();
    await page.waitForURL(/desde=/);
    await expect(page).toHaveURL(/dias=14/);
    // Sin scroll horizontal de la página (el scroll queda dentro de la tabla)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test('agenda: documento vencido y vehículo en mantenimiento aparecen marcados', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token);
    const m = await api.addVehicle(carrier.token);
    await uploadFleetDoc(carrier.token, 'vehicles', v.id, 'SOAT', api.isoDate(2));
    await api.call('PATCH', `/vehicles/${m.id}/status`, carrier.token, { status: 'MAINTENANCE' });
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/agenda');
    const table = page.getByRole('region', { name: 'Agenda de vehículos' });
    await expect(table.getByRole('row').filter({ hasText: v.plate }).getByTestId('agenda-expiry').first()).toContainText('SOAT');
    await expect(table.getByRole('row').filter({ hasText: m.plate }).getByTestId('agenda-blocked')).toContainText(/mantenimiento/i);
  });

  test('asignar conductor y vehículo desde AssignSheet (un solo paso)', async ({ page }) => {
    const sc = await shipmentScenario({ pickupInDays: 3 });
    const d1 = await api.addDriver(sc.carrier.token, 'Conductor Libre');
    const d2 = await api.addDriver(sc.carrier.token, 'Conductor Ocupado');
    const other = await shipmentScenario({ carrier: sc.carrier, pickupInDays: 3, vehicle: await api.addVehicle(sc.carrier.token) });
    await api.assignDriver(sc.carrier.token, other.shipmentId, d2.id);   // d2 queda ocupado en esas fechas
    const v2 = await api.addVehicle(sc.carrier.token);
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, '/transportista/agenda');
    const un = page.getByTestId('unassigned');
    await expect(un.getByTestId('unassigned-row')).toHaveCount(1);
    await un.getByRole('button', { name: /Asignar conductor al envío/ }).click();
    const sheet = page.getByRole('dialog', { name: 'Asignar conductor' });
    await expect(sheet.getByText('Conductores libres en esas fechas')).toBeVisible();
    await expect(sheet.getByTestId('assign-window')).toBeVisible();
    // El ocupado queda deshabilitado con su motivo
    await expect(sheet.getByRole('radio', { name: /Conductor Ocupado/ })).toBeDisabled();
    await expect(sheet.getByTestId('driver-blocked')).toContainText('Ocupado');
    await sheet.getByRole('button', { name: 'Asignar conductor' }).click();           // sin elegir
    await expect(sheet.getByText('Elige el conductor')).toBeVisible();
    await sheet.getByRole('radio', { name: /Conductor Libre/ }).click();
    await sheet.getByRole('radio', { name: new RegExp(v2.plate) }).click();           // y cambia de vehículo en el mismo paso
    await sheet.getByRole('button', { name: 'Asignar conductor' }).click();
    await expect(toastWith(page, 'Envío asignado')).toBeVisible();
    await expect(un.getByTestId('unassigned-row')).toHaveCount(0);
    const ship = await api.call<any>('GET', `/shipments/${sc.shipmentId}`, sc.carrier.token);
    expect(ship.driverId).toBe(d1.id);
    expect(ship.vehiclePlate).toBe(v2.plate);
  });

  test('asignar en lote: un resumen por envío, sin abortar al primer error', async ({ page }) => {
    const a = await shipmentScenario({ pickupInDays: 3 });
    const b = await shipmentScenario({ carrier: a.carrier, pyme: a.pyme, pickupInDays: 3, vehicle: await api.addVehicle(a.carrier.token) });
    const c = await shipmentScenario({ carrier: a.carrier, pyme: a.pyme, pickupInDays: 20, vehicle: a.vehicle });
    const driver = await api.addDriver(a.carrier.token, 'Conductor Lote');
    await setSession(page.context(), a.carrier.token, BASE);
    await goto(page, '/transportista/agenda');
    const un = page.getByTestId('unassigned');
    await expect(un.getByTestId('unassigned-row')).toHaveCount(3);
    await expect(un.getByRole('button', { name: /Asignar en lote/ })).toBeDisabled();
    for (const cb of await un.getByRole('checkbox').all()) await cb.click();
    await un.getByRole('button', { name: /Asignar en lote/ }).click();
    const dlg = page.getByRole('dialog', { name: 'Asignar en lote' });
    await dlg.getByRole('combobox', { name: 'Conductor' }).click();
    await page.getByRole('option', { name: /Conductor Lote/ }).click();
    await dlg.getByRole('button', { name: 'Asignar conductor' }).click();
    const res = dlg.getByTestId('batch-results');
    await expect(res.getByText('Asignado', { exact: true })).toHaveCount(2, { timeout: 15_000 });   // a y c (b se solapa con a)
    await expect(res.getByText(/No se pudo/)).toHaveCount(1);
    // a y b ocupan las mismas fechas: el que se procesó primero quedó asignado y el otro explica por qué no. c (otra fecha) siempre.
    const owner = async (sh: { shipmentId: string }) => (await api.call<any>('GET', `/shipments/${sh.shipmentId}`, a.carrier.token)).driverId ?? null;
    expect(await owner(c)).toBe(driver.id);
    expect([await owner(a), await owner(b)].filter((d) => d === driver.id)).toHaveLength(1);
    await expect(res.getByText(/ya tiene otro envío/)).toBeVisible();
  });

  test('Lector: no ve botones de escribir en cargas, ofertas, flota ni agenda', async ({ page }) => {
    const sc = await shipmentScenario({ pickupInDays: 3 });
    const pending = await api.createRequest(sc.pyme.token, { pickupDate: api.isoDate(8) });
    const vehicle2 = await api.addVehicle(sc.carrier.token);
    await api.placeBid(sc.carrier.token, pending.id, vehicle2.id, Math.ceil(pending.priceFloor) + 100);
    await api.call('POST', '/saved-searches', sc.carrier.token, { name: 'Mía', originCity: 'LIMA' });
    const viewer = await api.userWithRole(sc.carrier.token, 'VIEWER');
    await setSession(page.context(), viewer.token, BASE);

    await goto(page, '/transportista/marketplace?origen=LIMA');
    await expect(page.getByRole('button', { name: 'Guardar búsqueda' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Ofertar' })).toHaveCount(0);

    await goto(page, '/transportista/ofertas');
    await expect(page.getByRole('button', { name: /Mejorar oferta/ })).toHaveCount(0);

    await goto(page, '/transportista/flota');
    await expect(page.getByRole('button', { name: `Documentos de ${sc.vehicle.plate}` })).toBeVisible();   // leer sí
    await expect(page.getByRole('button', { name: `Acciones de ${sc.vehicle.plate}` })).toHaveCount(0);
    await page.getByRole('button', { name: `Documentos de ${sc.vehicle.plate}` }).click();
    const sheet = page.getByRole('dialog', { name: /Documentos ·/ });
    await expect(sheet.getByTestId('doc-SOAT')).toBeVisible();
    await expect(sheet.getByRole('button', { name: /Subir/ })).toHaveCount(0);
    await page.keyboard.press('Escape');

    await goto(page, '/transportista/agenda');
    await expect(page.getByRole('region', { name: 'Agenda de vehículos' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Asignar/ })).toHaveCount(0);
    await expect(page.getByRole('checkbox')).toHaveCount(0);
  });

  test('operador: el parámetro «fleet_docs_block» se muestra y se edita como Activado/Desactivado', async ({ page }) => {
    const op = await api.operatorToken();
    await setSession(page.context(), op, BASE);
    await goto(page, '/operador/parametros');
    const card = page.getByTestId('param-fleet_docs_block');
    await expect(card.getByTestId('param-value')).toHaveText(/Activado|Desactivado/);
    await card.getByRole('button', { name: /Cambiar/ }).click();
    const dlg = page.getByRole('dialog', { name: /Cambiar:/ });
    await dlg.getByRole('combobox', { name: 'Nuevo valor' }).click();
    await expect(page.getByRole('option', { name: 'Activado', exact: true })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Desactivado', exact: true })).toBeVisible();
    await page.keyboard.press('Escape');
  });
});

/** Tras navegar con `window.location` la isla vuelve a hidratar. */
async function hydrated14(page: Page) {
  await page.waitForFunction(() => !document.querySelector('astro-island[ssr]'), undefined, { timeout: 20_000 });
}

for (const theme of ['light', 'dark'] as const) {
  test.describe(`Zona T · accesibilidad (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    });

    test('cargas: búsquedas guardadas, diálogo de guardado y hoja de oferta', async ({ page }) => {
      const pyme = await api.registerAccount('PYME');
      const carrier = await api.registerAccount('CARRIER');
      await api.addVehicle(carrier.token);
      const req = await api.createRequest(pyme.token, { pickupDate: api.isoDate(6) });
      await api.call('POST', '/saved-searches', carrier.token, { name: 'Lima a Juliaca', originCity: 'LIMA', destinationCity: 'JULIACA' });
      await setSession(page.context(), carrier.token, BASE);
      await goto(page, '/transportista/marketplace?origen=LIMA&destino=JULIACA');
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
      await page.getByTestId('saved-searches-trigger').click();
      await expect(page.getByTestId('saved-item')).toBeVisible();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.keyboard.press('Escape');
      await page.getByRole('button', { name: 'Guardar búsqueda' }).click();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.screenshot({ path: `${SHOTS}/ola2-t-guardar-busqueda-${theme}.png` });
      await page.keyboard.press('Escape');
      await goto(page, `/transportista/marketplace?solicitud=${req.id}`);
      await expect(page.getByRole('dialog', { name: 'Enviar oferta' }).getByLabel('Tu oferta')).toBeVisible();
      await expectNoSeriousViolations(page, '[role=dialog]');
    });

    test('mis ofertas: mejorar oferta y posición', async ({ page }) => {
      const sc = await api.openBidScenario(6000);
      await setSession(page.context(), sc.carrier.token, BASE);
      await goto(page, '/transportista/ofertas');
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
      await page.getByRole('button', { name: /Mejorar oferta de Lima a Juliaca/ }).click();
      await expect(page.getByRole('dialog', { name: 'Mejorar oferta' }).getByRole('combobox', { name: 'Vehículo' })).toBeEnabled();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.screenshot({ path: `${SHOTS}/ola2-t-mejorar-oferta-${theme}.png` });
    });

    test('comparación de la PYME: sellos y «Mejorada»', async ({ page }) => {
      const sc = await bidWithDocs(6000, { soatDays: 120, inspectionDays: 120 });
      await api.call('POST', `/bids/${sc.bid.id}/improve`, sc.carrier.token, { amount: Math.max(Math.ceil(sc.request.priceFloor), 5800) });
      await setSession(page.context(), sc.pyme.token, BASE);
      await goto(page, `/pyme/solicitudes/${sc.request.id}`);
      await expect(page.getByTestId('bid-seals')).toContainText('Revisión técnica vigente');
      await expectNoSeriousViolations(page);
    });

    test('flota: resumen de vencimientos, documentos y configuración', async ({ page }) => {
      const carrier = await api.registerAccount('CARRIER');
      const v = await api.addVehicle(carrier.token);
      await api.addDriver(carrier.token);
      await uploadFleetDoc(carrier.token, 'vehicles', v.id, 'SOAT', api.isoDate(8));
      await setSession(page.context(), carrier.token, BASE);
      await goto(page, '/transportista/flota');
      await expect(page.getByTestId('expiry-list')).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/ola2-t-flota-${theme}.png`, fullPage: true });
      await page.getByRole('button', { name: `Documentos de ${v.plate}`, exact: true }).click();
      await expect(page.getByTestId('doc-SOAT')).toContainText('Por vencer');
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.getByRole('button', { name: 'Subir documento de Revisión técnica' }).click();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.keyboard.press('Escape');
      await page.getByRole('button', { name: `Acciones de ${v.plate}` }).click();
      await page.getByRole('menuitem', { name: 'Configurar vehículo' }).click();
      await expect(page.getByRole('dialog', { name: `Configurar ${v.plate}` })).toBeVisible();
      await expectNoSeriousViolations(page, '[role=dialog]');
    });

    test('agenda: calendario, «Sin conductor» y hoja de asignación', async ({ page }) => {
      const sc = await shipmentScenario({ pickupInDays: 2 });
      await api.addDriver(sc.carrier.token);
      await setSession(page.context(), sc.carrier.token, BASE);
      await goto(page, '/transportista/agenda');
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expect(page.getByTestId('unassigned-row')).toHaveCount(1);
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/ola2-t-agenda-${theme}.png`, fullPage: true });
      await page.getByRole('tab', { name: /Conductores/ }).click();
      await expectNoSeriousViolations(page);
      await page.getByRole('button', { name: /Asignar conductor al envío/ }).click();
      await expect(page.getByRole('dialog', { name: 'Asignar conductor' }).getByRole('radio').first()).toBeVisible();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.screenshot({ path: `${SHOTS}/ola2-t-asignar-${theme}.png` });
    });

    test('operador: parámetros con el interruptor de documentos', async ({ page }) => {
      const op = await api.operatorToken();
      await setSession(page.context(), op, BASE);
      await goto(page, '/operador/parametros');
      await expect(page.getByTestId('param-fleet_docs_block')).toBeVisible();
      await expectNoSeriousViolations(page);
    });
  });
}

test('agenda en móvil: sin scroll horizontal de la página', async ({ browser }) => {
  const sc = await shipmentScenario({ pickupInDays: 2 });
  const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE', viewport: { width: 390, height: 800 } });
  const page = await ctx.newPage();
  await setSession(ctx, sc.carrier.token, BASE);
  await goto(page, '/transportista/agenda?dias=14');
  await expect(page.getByRole('region', { name: 'Agenda de vehículos' })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
  await ctx.close();
});
