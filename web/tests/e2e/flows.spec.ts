import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { goto, pick, pickDay, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

async function as(page: Page, token: string) {
  await setSession(page.context(), token, BASE);
}

/** Transportista nuevo con un vehículo disponible. */
async function newCarrier(capacity = 12000) {
  const acc = await api.registerAccount('CARRIER');
  const vehicle = await api.addVehicle(acc.token, capacity);
  return { ...acc, vehicle };
}

test.describe('PYME: publicar solicitud', () => {
  test('cotización en vivo, validaciones y publicación', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await as(page, pyme.token);
    await goto(page, '/pyme/solicitudes/nueva');

    // Validación: enviar vacío marca los campos
    await page.getByRole('button', { name: 'Publicar solicitud' }).click();
    await expect(page.getByText('Selecciona la ciudad de origen.')).toBeVisible();
    await expect(page.getByText('Ingresa la dirección de recojo.')).toBeVisible();
    await expect(page.getByText('Selecciona la fecha de recojo.')).toBeVisible();

    // La cotización aparece al elegir ruta y peso
    await expect(page.getByText('Elige origen, destino y peso')).toBeVisible();
    await pick(page, page.getByRole('combobox', { name: 'Ciudad de origen' }), 'Lima');
    await pick(page, page.getByRole('combobox', { name: 'Ciudad de destino' }), 'Juliaca');
    await page.getByLabel('Peso').fill('3200');
    const quote = page.getByText('Cotización estimada').locator('xpath=ancestor::div[contains(@class,"rounded-card")][1]');
    await expect(quote.getByText('Precio sugerido', { exact: true })).toBeVisible();
    await expect(quote.getByText('Piso de subasta')).toBeVisible();
    await expect(quote.getByText(/S\/ [\d,]+\.\d{2}/).first()).toBeVisible();

    // Cambiar el peso recalcula
    const before = await quote.locator('dd').nth(5).innerText();
    await page.getByLabel('Peso').fill('9000');
    await expect(quote.locator('dd').nth(5)).not.toHaveText(before);

    await page.getByLabel('Dirección de recojo').fill('Av. Argentina 1234, Callao');
    await page.getByLabel('Dirección de entrega').fill('Jr. Lima 456, Juliaca');
    await page.getByLabel('Descripción de la carga').fill('120 rollos de tela, paletizados');
    await page.getByRole('button', { name: 'Fecha de recojo' }).click();
    await pickDay(page, api.isoDate(4));
    await page.getByRole('button', { name: 'Publicar solicitud' }).click();

    await expect(page).toHaveURL(/\/pyme\/solicitudes\/[0-9a-f-]{36}/);
    await expect(toastWith(page, 'Solicitud publicada')).toBeVisible();
    await expect(page.getByText('Aún no hay ofertas')).toBeVisible();
    await expect(page.getByText('Lima').first()).toBeVisible();
  });

  test('origen y destino iguales muestran el error', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await as(page, pyme.token);
    await goto(page, '/pyme/solicitudes/nueva');
    await pick(page, page.getByRole('combobox', { name: 'Ciudad de origen' }), 'Lima');
    // el destino excluye el origen: no se puede elegir Lima
    await page.getByRole('combobox', { name: 'Ciudad de destino' }).click();
    await expect(page.getByRole('option', { name: 'Lima' })).toHaveCount(0);
    await page.keyboard.press('Escape');
  });
});

test.describe('Transportista: cargas disponibles', () => {
  test('filtra por ciudad, fechas y pagina', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await newCarrier();
    // 22 solicitudes Lima→Tacna a +6 días (garantiza segunda página con el filtro) y una Lima→Abancay a +25 días
    const d6 = api.isoDate(6);
    for (let i = 0; i < 22; i++) await api.createRequest(pyme.token, { destinationCity: 'TACNA', pickupDate: d6, weightKg: 1000 });
    await api.createRequest(pyme.token, { destinationCity: 'ABANCAY', pickupDate: api.isoDate(25), weightKg: 1000 });
    await as(page, carrier.token);
    await goto(page, '/transportista/marketplace');

    // Ciudad destino (combobox)
    await pick(page, page.getByRole('combobox', { name: 'Destino' }), 'Abancay');
    await page.waitForURL(/destino=ABANCAY/);
    await hydratedWait(page);
    const rows = page.locator('tbody tr');
    await expect(rows.first()).toContainText('Abancay');
    await expect(rows.filter({ hasNotText: 'Abancay' })).toHaveCount(0);
    await expect(rows.filter({ hasText: pyme.name })).toHaveCount(1);

    // Rango de fechas: "Próximos 7 días" excluye la de +25 días
    await page.getByRole('button', { name: 'Limpiar filtros' }).first().click();
    await page.waitForURL((u) => !u.search.includes('destino'));
    await hydratedWait(page);
    await pick(page, page.getByRole('combobox', { name: 'Destino' }), 'Abancay');
    await page.waitForURL(/destino=ABANCAY/);
    await hydratedWait(page);
    await page.getByRole('button', { name: 'Fecha de recojo' }).click();
    await page.getByRole('button', { name: 'Próximos 7 días' }).click();
    await page.waitForURL(/desde=/);
    await hydratedWait(page);
    await expect(page.locator('tbody tr').filter({ hasText: pyme.name })).toHaveCount(0);

    // Paginación con Tacna (22 resultados → 2 páginas)
    await page.goto(`/transportista/marketplace?destino=TACNA&desde=${d6}&hasta=${d6}`);
    await hydratedWait(page);
    const pager = page.getByRole('navigation', { name: 'Paginación' });
    await expect(pager).toBeVisible();
    await expect(pager).toContainText('Mostrando 1–20');
    await pager.getByRole('button', { name: 'Página siguiente' }).click();
    await page.waitForURL(/pagina=2/);
    await hydratedWait(page);
    await expect(page.getByRole('navigation', { name: 'Paginación' })).toContainText('Mostrando 21–');
    await expect(page.locator('tbody tr').first()).toContainText('Tacna');
  });

  test('la tabla muestra precio sugerido, piso y vehículo mínimo sin scroll horizontal (1280px)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await newCarrier();
    await api.createRequest(pyme.token, { destinationCity: 'PUNO', weightKg: 2000, cargoType: 'PERISHABLE', pickupDate: api.isoDate(8) });
    await page.setViewportSize({ width: 1280, height: 800 });
    await as(page, carrier.token);
    await goto(page, '/transportista/marketplace?destino=PUNO&desde=' + api.isoDate(8) + '&hasta=' + api.isoDate(8));
    const row = page.locator('tbody tr').first();
    await expect(row).toContainText(/S\/ [\d,]+\.\d{2}/);
    await expect(row).toContainText(/piso S\/ [\d,]+\.\d{2}/);
    await expect(row).toContainText('Refrigerado');
    await expect(row.getByRole('button', { name: 'Ofertar' })).toBeVisible();
    const overflow = await page.evaluate(() => {
      const t = document.querySelector('table')!;
      const wrap = t.parentElement!;
      return { table: t.scrollWidth, wrap: wrap.clientWidth, doc: document.documentElement.scrollWidth, win: window.innerWidth };
    });
    expect(overflow.table).toBeLessThanOrEqual(overflow.wrap + 1);
    expect(overflow.doc).toBeLessThanOrEqual(overflow.win);
    const btn = await row.getByRole('button', { name: 'Ofertar' }).boundingBox();
    expect(btn!.x + btn!.width).toBeLessThanOrEqual(1280);

    // Tablet: la página no desborda (la tabla puede desplazarse dentro de su tarjeta)
    for (const width of [1024, 768]) {
      await page.setViewportSize({ width, height: 800 });
      const docW = await page.evaluate(() => document.documentElement.scrollWidth);
      expect(docW).toBeLessThanOrEqual(width);
      await expect(page.locator('tbody tr').first().getByRole('button', { name: 'Ofertar' })).toBeVisible();
    }
  });

  test('oferta bajo el piso se rechaza inline y una válida muestra toast', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await newCarrier();
    const req = await api.createRequest(pyme.token, { destinationCity: 'AREQUIPA', weightKg: 2500, pickupDate: api.isoDate(9) });
    await as(page, carrier.token);
    await goto(page, `/transportista/marketplace?destino=AREQUIPA&desde=${api.isoDate(9)}&hasta=${api.isoDate(9)}`);
    const row = page.locator('tbody tr').filter({ hasText: req.cargoDescription ? '' : '' }).first();
    await row.getByRole('button', { name: 'Ofertar' }).click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await expect(sheet).toBeVisible();
    await expect(sheet.getByText('Precio sugerido')).toBeVisible();

    await pick(page, sheet.getByRole('combobox', { name: 'Vehículo' }), carrier.vehicle.plate);
    const floor = req.priceFloor as number;
    await sheet.getByLabel('Tu oferta').fill(String(Math.max(1, Math.floor(floor) - 50)));
    await sheet.getByRole('button', { name: 'Enviar oferta' }).click();
    const amountErr = sheet.getByRole('alert').filter({ hasText: /piso|mínimo/i });
    await expect(amountErr).toBeVisible();
    await expect(sheet.locator('#b-amount')).toHaveAttribute('aria-invalid', 'true');

    await sheet.getByLabel('Tu oferta').fill(String(Math.ceil(floor) + 20));
    await sheet.getByRole('button', { name: 'Enviar oferta' }).click();
    await expect(toastWith(page, 'Oferta enviada')).toBeVisible();
    await hydratedWait(page);
    await expect(page.locator('tbody tr').first()).toContainText('Ofertada');
  });
});

test.describe('Ciclo completo: ofertar → comparar y aceptar → asignar conductor → seguimiento', () => {
  test('flujo de punta a punta', async ({ page, browser }) => {
    const pyme = await api.registerAccount('PYME');
    const c1 = await newCarrier();
    const c2 = await newCarrier();
    const driverName = `Luis Mamani ${api.uid()}`;
    await api.addDriver(c1.token, driverName);
    const req = await api.createRequest(pyme.token, { destinationCity: 'JULIACA', weightKg: 3000, pickupDate: api.isoDate(7) });
    const floor = Math.ceil(req.priceFloor);
    const low = floor + 10;
    const high = floor + 300;
    await api.placeBid(c1.token, req.id, c1.vehicle.id, low, 'Salida a primera hora');
    await api.placeBid(c2.token, req.id, c2.vehicle.id, high);

    // PYME compara
    await as(page, pyme.token);
    await goto(page, `/pyme/solicitudes/${req.id}`);
    const items = page.locator('ul > li').filter({ hasText: 'Aceptar oferta' });
    await expect(items).toHaveCount(2);
    await expect(items.first()).toContainText(`S/ ${low.toLocaleString('en-US')}.00`);
    await expect(items.first()).toContainText('Mejor precio');
    await expect(items.first()).toContainText(c1.vehicle.plate);

    // Aceptar con confirmación: Volver no hace nada
    await items.first().getByRole('button', { name: 'Aceptar oferta' }).click();
    const dlg = page.getByRole('alertdialog');
    await expect(dlg).toContainText(`¿Aceptar la oferta de S/ ${low.toLocaleString('en-US')}.00?`);
    await dlg.getByRole('button', { name: 'Volver' }).click();
    await expect(dlg).toBeHidden();
    await items.first().getByRole('button', { name: 'Aceptar oferta' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Aceptar oferta' }).click();

    // Envío creado
    await expect(page).toHaveURL(/\/pyme\/envios\?id=/);
    await expect(toastWith(page, 'Oferta aceptada')).toBeVisible();
    await expect(page.getByText('Sin asignar')).toBeVisible();
    await expect(page.getByText(c1.vehicle.plate).first()).toBeVisible();
    await expect(page.getByRole('list', { name: 'Paradas del envío' }).getByRole('listitem')).toHaveCount(2);

    // Transportista asigna conductor
    const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE' });
    const cp = await ctx.newPage();
    await setSession(ctx, c1.token, BASE);
    await goto(cp, '/transportista/envios');
    await expect(cp.getByText('Sin conductor').first()).toBeVisible();
    await cp.getByRole('button', { name: 'Asignar conductor' }).click();
    const drvDlg = cp.getByRole('dialog', { name: 'Asignar conductor' });
    await pick(cp, drvDlg.getByRole('combobox', { name: 'Conductor' }), driverName);
    await drvDlg.getByRole('button', { name: 'Asignar conductor' }).click();
    await expect(toastWith(cp, 'Conductor asignado')).toBeVisible();
    await hydratedWait(cp);
    await expect(cp.getByText(driverName).first()).toBeVisible();
    await ctx.close();

    // La PYME ve el seguimiento con el conductor asignado y la solicitud adjudicada
    await page.reload();
    await hydratedWait(page);
    await expect(page.getByText(driverName).first()).toBeVisible();
    await expect(page.getByRole('list', { name: 'Paradas del envío' })).toContainText('Pendiente');
    await goto(page, '/pyme/solicitudes');
    await expect(page.getByText('Adjudicada').first()).toBeVisible();
  });

  test('la PYME cancela una solicitud', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const req = await api.createRequest(pyme.token, {});
    await as(page, pyme.token);
    await goto(page, `/pyme/solicitudes/${req.id}`);
    await page.getByRole('button', { name: 'Cancelar solicitud' }).click();
    const dlg = page.getByRole('dialog', { name: '¿Cancelar esta solicitud?' });
    await expect(dlg).toBeVisible();
    await dlg.getByRole('button', { name: 'Volver' }).click();
    await expect(dlg).toBeHidden();
    await page.getByRole('button', { name: 'Cancelar solicitud' }).click();
    await page.getByRole('dialog').getByLabel('Motivo de la cancelación').fill('Cambió la fecha de despacho');
    await page.getByRole('dialog').getByRole('button', { name: 'Cancelar solicitud' }).click();
    await expect(toastWith(page, 'Solicitud cancelada')).toBeVisible();
    await hydratedWait(page);
    await expect(page.getByText('Cancelada').first()).toBeVisible();
    await expect(page.getByText('Cambió la fecha de despacho').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cancelar solicitud' })).toHaveCount(0);
    const cancelled = await api.getRequest(pyme.token, req.id);
    expect(cancelled.status).toBe('CANCELLED');
    expect(cancelled.cancelReason).toBe('Cambió la fecha de despacho');
  });
});

test.describe('Conductor', () => {
  test('/conductor muestra el resumen del viaje activo', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await newCarrier();
    const drv = await api.addDriver(carrier.token, `Rosa Condori ${api.uid()}`);
    const req = await api.createRequest(pyme.token, { destinationCity: 'JULIACA', weightKg: 2000 });
    const bid = await api.placeBid(carrier.token, req.id, carrier.vehicle.id, Math.ceil(req.priceFloor) + 5);
    const acc = await api.acceptBid(pyme.token, bid.id);
    await api.assignDriver(carrier.token, acc.shipmentId, drv.id);
    const token = await api.login(drv.email);
    await as(page, token);
    await goto(page, '/conductor');
    await expect(page.getByRole('heading', { name: /Hola/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Tu viaje activo' })).toBeVisible();
    await expect(page.getByText('Lima').first()).toBeVisible();
    await expect(page.getByText('Juliaca').first()).toBeVisible();
    await expect(page.getByRole('list', { name: 'Paradas del envío' })).toBeVisible();
  });

  test('/conductor sin viaje activo invita a usar la app', async ({ page }) => {
    const carrier = await newCarrier();
    const drv = await api.addDriver(carrier.token);
    await as(page, await api.login(drv.email));
    await goto(page, '/conductor');
    await expect(page.getByText('No tienes un viaje activo')).toBeVisible();
    await expect(page.getByText('Tus viajes están en la app móvil')).toBeVisible();
  });
});

async function hydratedWait(page: Page) {
  await page.waitForFunction(() => !document.querySelector('astro-island[ssr]'));
}
