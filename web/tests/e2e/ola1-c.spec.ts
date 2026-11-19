import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const as = (page: Page, token: string) => setSession(page.context(), token, BASE);
const money = (n: number) => `S/ ${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const farDay = () => 25 + Math.floor(Math.random() * 300);

/** Misma aritmética que `CommissionPolicy` (HALF_UP en céntimos). */
function net(amount: number, rate: number) {
  const c = Math.round(amount * 100);
  const half = (n: number, d: number) => Math.floor((2 * n + d) / (2 * d));
  const netOfVat = half(c * 100, 118);
  const commission = half(netOfVat * Math.round(rate * 10000), 10000);
  const commissionVat = half(commission * 18, 100);
  return { netOfVat: netOfVat / 100, vat: (c - netOfVat) / 100, commission: commission / 100, commissionVat: commissionVat / 100, carrierNet: (c - commission - commissionVat) / 100 };
}

/** Transportista con dos vehículos aptos (el chico y el grande) y N cargas Lima → Juliaca el mismo día lejano. */
async function scenario(loads = 3) {
  const pyme = await api.registerAccount('PYME');
  const carrier = await api.registerAccount('CARRIER');
  const small = await api.addVehicle(carrier.token, 9000);
  const big = await api.addVehicle(carrier.token, 20000, 'TRUCK_LARGE');
  const day = api.isoDate(farDay());
  const requests = [];
  for (let i = 0; i < loads; i++) requests.push(await api.createRequest(pyme.token, { weightKg: 3200, pickupDate: day }));
  const { commissionRate } = await api.call<{ commissionRate: number }>('GET', '/pricing/commission', carrier.token);
  return { pyme, carrier, small, big, day, requests, rate: commissionRate };
}

test.describe('Oferta rápida (1.10) y cobras neto', () => {
  test('encadena ofertas sin recargar: vehículo preseleccionado, atajo recordado, neto desglosado y foco en la siguiente', async ({ page }) => {
    const s = await scenario(3);
    await as(page, s.carrier.token);
    await goto(page, `/transportista/marketplace?desde=${s.day}&hasta=${s.day}`);
    await page.evaluate(() => { (window as any).__noReload = 1; });

    const rows = page.locator('tbody tr').filter({ has: page.getByRole('button', { name: 'Ofertar' }) });
    await expect(rows).toHaveCount(3);
    await rows.first().getByRole('button', { name: 'Ofertar' }).click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await expect(sheet).toBeVisible();
    // Sin último vehículo: el apto de menor capacidad suficiente
    await expect(sheet.getByRole('combobox', { name: 'Vehículo' })).toContainText(s.small.plate);

    // Atajo −5 %: monto, neto y desglose plegable
    const first = s.requests.find((r) => r.id)!;
    await sheet.getByRole('button', { name: /^−5 %/ }).click();
    const amount = Number((await sheet.locator('#b-amount').inputValue()).replace(/,/g, ''));
    expect(amount).toBeGreaterThan(0);
    const n = net(amount, s.rate);
    await expect(sheet.getByTestId('net-amount')).toHaveText(money(n.carrierNet));
    await expect(sheet.getByTestId('net-box')).toContainText('neto');
    await sheet.getByText('Ver cómo se calcula').click();
    const dl = sheet.getByTestId('net-breakdown');
    await expect(dl).toContainText(money(n.netOfVat));
    await expect(dl).toContainText(money(n.vat));
    await expect(dl).toContainText(`− ${money(n.commission)}`);
    await expect(dl).toContainText(`− ${money(n.commissionVat)}`);
    expect(first.priceFloor).toBeLessThanOrEqual(amount);          // RN03: el atajo nunca baja del piso

    // Enviar y pasar a la siguiente: la hoja sigue abierta con la otra carga, el vehículo y el atajo recordados
    await sheet.locator('#b-notes').fill('Salida a primera hora');
    await sheet.getByRole('button', { name: 'Enviar y ofertar la siguiente carga' }).click();
    await expect(toastWith(page, 'Oferta enviada')).toBeVisible();
    await expect(sheet.getByRole('combobox', { name: 'Vehículo' })).toContainText(s.small.plate);
    await expect(sheet.locator('#b-notes')).toHaveValue('Salida a primera hora');
    await expect(sheet.getByRole('button', { name: /^−5 %/ })).toHaveAttribute('aria-pressed', 'true');
    await expect(sheet.locator('#b-amount')).not.toHaveValue('');
    // El mismo vehículo ya tiene una oferta para esas fechas: aviso (CAP-006) antes de ofertar
    await expect(sheet.getByText(/ya tiene 1 oferta para esas fechas/)).toBeVisible();

    // Segunda: enviar y cerrar; la fila pasa a «Ofertada» y el foco va a la tercera carga, sin recargar
    await sheet.getByRole('button', { name: 'Enviar oferta', exact: true }).click();
    await expect(sheet).toBeHidden();
    await expect(page.getByText('Ofertada')).toHaveCount(2);
    await expect(page.locator('[data-bid-row]')).toHaveCount(1);
    await expect(page.locator('[data-bid-row]').first()).toBeFocused();
    expect(await page.evaluate(() => (window as any).__noReload)).toBe(1);

    // La API tiene las dos ofertas, con el atajo aplicado y la nota recordada
    const mine = await api.call<any[]>('GET', '/shipment-requests/my-bids', s.carrier.token);
    expect(mine).toHaveLength(2);
    for (const r of mine) {
      expect(r.myBid.amount).toBeGreaterThanOrEqual(r.priceFloor);
      expect(r.myBid.amount).toBe(Math.max(r.priceFloor, Math.round(r.suggestedPrice * 0.95)));
    }
    expect(mine[0].myBid.notes).toBe('Salida a primera hora');
  });

  test('un vehículo ocupado esas fechas se explica y no se puede elegir; el atajo −10 % se recorta al piso', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token, 9000);
    const d1 = farDay();
    const r1 = await api.createRequest(pyme.token, { weightKg: 3000, pickupDate: api.isoDate(d1) });
    const r2 = await api.createRequest(pyme.token, { weightKg: 3000, pickupDate: api.isoDate(d1 + 1) });
    const b1 = await api.placeBid(carrier.token, r1.id, v.id, Math.ceil(r1.priceFloor) + 10);
    await api.acceptBid(pyme.token, b1.id);                                 // el vehículo queda comprometido d1..d1+3

    await as(page, carrier.token);
    await goto(page, `/transportista/marketplace?desde=${api.isoDate(d1 + 1)}&hasta=${api.isoDate(d1 + 1)}`);
    await page.locator('tbody tr').first().getByRole('button', { name: 'Ofertar' }).click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await expect(sheet.getByTestId('agenda-blocked')).toContainText(v.plate);
    await expect(sheet.getByTestId('agenda-blocked')).toContainText('libre el');
    await expect(sheet.getByRole('combobox', { name: 'Vehículo' })).not.toContainText(v.plate);
    await sheet.getByRole('button', { name: 'Enviar oferta', exact: true }).click();
    await expect(sheet.getByText('Selecciona el vehículo que hará el envío.')).toBeVisible();
    // el servidor lo confirma por la API de elegibilidad
    const e = await api.call<any>('GET', `/shipment-requests/${r2.id}/vehicle-eligibility`, carrier.token);
    expect(e.vehicles[0]).toMatchObject({ vehicleId: v.id, eligible: false, blockerCode: 'LOGYX-CAP-005' });
  });

  test('el atajo −10 % nunca baja del piso (RN03)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token, 9000);
    const day = api.isoDate(farDay());
    const r = await api.createRequest(pyme.token, { weightKg: 3000, pickupDate: day });
    await as(page, carrier.token);
    await goto(page, `/transportista/marketplace?desde=${day}&hasta=${day}`);
    await page.locator('tbody tr').first().getByRole('button', { name: 'Ofertar' }).click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await sheet.getByRole('button', { name: /^−10 %/ }).click();
    const amount = Number((await sheet.locator('#b-amount').inputValue()).replace(/,/g, ''));
    expect(amount).toBeGreaterThanOrEqual(r.priceFloor);
    expect(amount).toBe(Math.max(r.priceFloor, Math.round(r.suggestedPrice * 0.9)));
    // No existe el atajo «mejor oferta − paso»: el transportista nunca ve montos ajenos (RN04)
    await expect(sheet.getByRole('button', { name: /mejor oferta/i })).toHaveCount(0);
  });

  test('axe en claro y oscuro con la hoja de oferta y el desglose abiertos', async ({ page }) => {
    const s = await scenario(1);
    await as(page, s.carrier.token);
    for (const theme of ['light', 'dark']) {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
      await goto(page, `/transportista/marketplace?desde=${s.day}&hasta=${s.day}`);
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await page.locator('tbody tr').first().getByRole('button', { name: 'Ofertar' }).click();
      const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
      await sheet.getByRole('button', { name: 'Sugerido' }).click();
      await sheet.getByText('Ver cómo se calcula').click();
      await expect(sheet.getByTestId('net-breakdown')).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.keyboard.press('Escape');
    }
  });
});

test.describe('Precio adjudicado con IGV (PYME) y neto (transportista)', () => {
  test('la PYME ve el IGV desglosado y sin comisión; el transportista ve la comisión y su neto', async ({ page }) => {
    const sc = await api.openBidScenario(2300, { pickupDate: api.isoDate(farDay()) });
    const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
    const amount = sc.bid.amount as number;
    const { commissionRate } = await api.call<{ commissionRate: number }>('GET', '/pricing/commission', sc.carrier.token);
    const n = net(amount, commissionRate);

    await as(page, sc.pyme.token);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);
    const card = page.getByTestId('awarded-price');
    await expect(card).toContainText(money(amount));
    await card.getByText('Ver IGV').click();
    await expect(card).toContainText(money(n.netOfVat));
    await expect(card).toContainText(money(n.vat));
    await expect(card).not.toContainText('Comisión');
    await expectNoSeriousViolations(page);

    await page.context().clearCookies();
    await as(page, sc.carrier.token);
    await goto(page, '/transportista/envios');
    const det = page.getByTestId('price-breakdown').first();
    await det.getByText('Ver IGV y comisión').click();
    await expect(det).toContainText(`− ${money(n.commission)}`);
    await expect(det).toContainText(money(n.carrierNet));
    expect(accepted.shipmentId).toBeTruthy();
  });
});

test.describe('Mis ofertas: autoría', () => {
  test('el historial de la oferta muestra quién la envió', async ({ page }) => {
    const sc = await api.openBidScenario(2300, { pickupDate: api.isoDate(farDay()) });
    await as(page, sc.carrier.token);
    await goto(page, '/transportista/ofertas');
    await page.getByRole('button', { name: /Historial/ }).first().click();
    const sheet = page.getByRole('dialog', { name: 'Historial de la oferta' });
    await expect(sheet.getByTestId('author-line')).toContainText('Ofertada por');
    await expect(sheet.getByText('Oferta enviada').first()).toBeVisible();
    await expectNoSeriousViolations(page);
  });
});

test.describe('Exportar CSV de mis ofertas y mis viajes (1.5)', () => {
  test('descarga el CSV de mis ofertas con el neto estimado y el de mis viajes', async ({ page }) => {
    const sc = await api.openBidScenario(2300, { pickupDate: api.isoDate(farDay()) });
    await api.publishTrip(sc.carrier.token, sc.vehicle.id);
    await as(page, sc.carrier.token);
    await goto(page, '/transportista/ofertas');
    const [dl] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Exportar CSV' }).click()]);
    expect(dl.suggestedFilename()).toMatch(/^mis-ofertas-\d{4}-\d{2}-\d{2}\.csv$/);
    await goto(page, '/transportista/viajes');
    const [dl2] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Exportar CSV' }).click()]);
    expect(dl2.suggestedFilename()).toMatch(/^mis-viajes-/);
  });
});

test.describe('Higiene de flota (1.12) y duplicar viaje (1.10)', () => {
  test('editar, archivar y sin «Marcar en ruta»', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token, 8000);
    await as(page, carrier.token);
    await goto(page, '/transportista/flota');
    const row = page.getByRole('row').filter({ hasText: v.plate });
    await row.getByRole('button', { name: `Acciones de ${v.plate}` }).click();
    await expect(page.getByRole('menuitem', { name: 'Marcar en ruta' })).toHaveCount(0);
    await page.getByRole('menuitem', { name: 'Editar vehículo' }).click();
    const dlg = page.getByRole('dialog', { name: `Editar ${v.plate}` });
    await expect(dlg.getByLabel('Placa')).toBeDisabled();
    await dlg.getByLabel('Capacidad de carga').fill('9500');
    await dlg.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByRole('row').filter({ hasText: v.plate })).toContainText('9,500 kg');

    await page.getByRole('row').filter({ hasText: v.plate }).getByRole('button', { name: `Acciones de ${v.plate}` }).click();
    await page.getByRole('menuitem', { name: 'Archivar vehículo' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Archivar vehículo' }).click();
    await expect(page.getByRole('row').filter({ hasText: v.plate })).toHaveCount(0);
    expect((await api.call<any[]>('GET', '/vehicles', carrier.token)).map((x) => x.id)).not.toContain(v.id);
  });

  test('un conductor o vehículo con envío activo no se da de baja (mensaje con el motivo)', async ({ page }) => {
    const sc = await api.openBidScenario(2300, { pickupDate: api.isoDate(farDay()) });
    const { shipmentId } = await api.acceptBid(sc.pyme.token, sc.bid.id);
    const driver = await api.addDriver(sc.carrier.token, 'Chofer Higiene');
    await api.assignDriver(sc.carrier.token, shipmentId, driver.id);
    await as(page, sc.carrier.token);
    await goto(page, '/transportista/flota');
    await page.getByRole('button', { name: 'Acciones de Chofer Higiene' }).click();
    await page.getByRole('menuitem', { name: 'Desactivar conductor' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Desactivar conductor' }).click();
    await expect(toastWith(page, /reasigna primero/)).toBeVisible();
    await page.getByRole('button', { name: `Acciones de ${sc.vehicle.plate}` }).click();
    await page.getByRole('menuitem', { name: 'Enviar a mantenimiento' }).click();
    await expect(toastWith(page, /reasígnalo antes/)).toBeVisible();
  });

  test('duplicar un viaje abre el formulario con los datos y la fecha una semana después', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token, 12000);
    const dep = new Date(Date.now() + 5 * 86400000); dep.setUTCHours(15, 0, 0, 0);
    const trip = await api.publishTrip(carrier.token, v.id, { departureAt: dep.toISOString(), rate: 0.61, capacityKg: 9000 });
    await as(page, carrier.token);
    await goto(page, '/transportista/viajes');
    await page.getByRole('link', { name: /Duplicar/ }).first().click();
    await expect(page).toHaveURL(new RegExp(`/transportista/viajes/nuevo\\?from=${trip.id}`));
    await expect(page.getByText('Copia de un viaje anterior')).toBeVisible();
    await expect(page.getByLabel('Tarifa por kg')).toHaveValue('0.61');
    await expect(page.getByLabel('Capacidad que ofreces')).toHaveValue('9,000');
    await expect(page.getByRole('combobox', { name: 'Vehículo' })).toContainText(v.plate);
    const want = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date(dep.getTime() + 7 * 86400000));
    await expect(page.getByTestId('trip-preview-full')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Fecha de salida' })).toContainText(String(Number(want.slice(8))));
    await page.getByRole('button', { name: 'Publicar viaje' }).click();
    await expect(page).toHaveURL(/\/transportista\/viajes\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('author-line')).toContainText('Publicado por');
  });
});
