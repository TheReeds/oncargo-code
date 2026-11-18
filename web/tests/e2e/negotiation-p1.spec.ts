import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pick, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

test.describe('Negociación en ambos sentidos y retiro de oferta (RF26, F08, F40, F62)', () => {
  test('el transportista contraoferta y la PYME acepta la propuesta; quien propone no ve botones de responder', async ({ page, browser }) => {
    const sc = await api.openBidScenario(6000);
    const counter = Math.ceil(sc.request.priceFloor) + 20;
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, `/transportista/ofertas?solicitud=${sc.request.id}`);
    const sheet = page.getByRole('dialog', { name: 'Negociar oferta' });
    await sheet.getByRole('button', { name: 'Contraofertar' }).click();
    const dlg = page.getByRole('dialog', { name: 'Proponer un nuevo monto' });
    await dlg.getByLabel('Monto que propones').fill('10');
    await dlg.getByRole('button', { name: 'Enviar contra-oferta' }).click();
    await expect(dlg.getByText(/menor al piso de la subasta/)).toBeVisible();
    await dlg.getByLabel('Monto que propones').fill(String(counter));
    await dlg.getByRole('button', { name: 'Enviar contra-oferta' }).click();
    await expect(toastWith(page, 'Contra-oferta enviada')).toBeVisible();
    await expect(sheet.getByText('Tu contra-oferta espera la respuesta de la PYME')).toBeVisible();
    await expect(sheet.getByRole('button', { name: /Aceptar contra-oferta|Rechazar/ })).toHaveCount(0);
    await expect(sheet.getByRole('button', { name: 'Contraofertar' })).toHaveCount(0);
    await page.screenshot({ path: `${SHOTS}/negociacion-contraoferta-transportista.png` });

    // La PYME ve la propuesta del transportista y la acepta (RF28)
    const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE' });
    const ppage = await ctx.newPage();
    await setSession(ctx, sc.pyme.token, BASE);
    await goto(ppage, `/pyme/solicitudes/${sc.request.id}`);
    await expect(ppage.getByText(/te propone un nuevo monto/)).toBeVisible();
    await expect(ppage.getByRole('button', { name: 'Contraofertar' })).toHaveCount(0); // sustituida por "Responder con otro monto"
    await expect(ppage.getByRole('button', { name: /Responder con otro monto/ })).toBeVisible();
    await ppage.screenshot({ path: `${SHOTS}/negociacion-contraoferta-pyme.png` });
    await ppage.getByRole('button', { name: 'Aceptar propuesta' }).click();
    await ppage.getByRole('alertdialog').getByRole('button', { name: 'Aceptar propuesta' }).click();
    await expect(toastWith(ppage, 'Propuesta aceptada')).toBeVisible();
    await ctx.close();
    const bids = await api.bidsOf(sc.pyme.token, sc.request.id);
    expect(Number(bids[0].amount)).toBe(counter);
    expect(bids[0].status).toBe('PENDING'); // aceptar la propuesta no adjudica (RN05)
  });

  test('la PYME rechaza la propuesta del transportista y queda en el historial', async ({ page }) => {
    const sc = await api.openBidScenario(6000);
    const counter = Math.ceil(sc.request.priceFloor) + 40;
    await api.counterOffer(sc.carrier.token, sc.bid.id, counter, 'Puedo bajar el precio si cargamos temprano');
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);
    await page.getByRole('button', { name: 'Rechazar', exact: true }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Rechazar propuesta' }).click();
    await expect(toastWith(page, 'Propuesta rechazada')).toBeVisible();
    await page.getByRole('button', { name: /Ver historial de contra-ofertas/ }).click();
    await expect(page.getByText('Rechazada', { exact: true }).first()).toBeVisible();
    const offers = await api.call<any[]>('GET', `/bids/${sc.bid.id}/counter-offers`, sc.pyme.token);
    expect(offers[0].status).toBe('REJECTED');
    expect(offers[0].proposedByCarrier).toBe(true);
  });

  test('retirar la oferta (BID-005): confirmación, estado Retirada en todas las listas y no se puede volver a ofertar', async ({ page, browser }) => {
    const sc = await api.openBidScenario(6000);
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, `/transportista/ofertas?solicitud=${sc.request.id}`);
    const sheet = page.getByRole('dialog', { name: 'Negociar oferta' });
    await sheet.getByRole('button', { name: 'Retirar oferta' }).click();
    const confirmDlg = page.getByRole('alertdialog');
    await expect(confirmDlg).toContainText('no podrás volver a ofertar');
    await confirmDlg.getByRole('button', { name: 'Volver' }).click();
    await sheet.getByRole('button', { name: 'Retirar oferta' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Retirar oferta' }).click();
    await expect(toastWith(page, 'Oferta retirada')).toBeVisible();
    await page.keyboard.press('Escape'); // el panel se reabre solo por ?solicitud=
    await expect(page.getByRole('group', { name: 'Estado de la oferta' }).getByRole('button', { name: /Retiradas/ })).toBeVisible();
    await expect(page.getByText('Retirada', { exact: true }).first()).toBeVisible();

    const bids = await api.bidsOf(sc.pyme.token, sc.request.id);
    expect(bids[0]?.status).toBe('WITHDRAWN');
    await expect(api.placeBid(sc.carrier.token, sc.request.id, sc.vehicle.id, 7000)).rejects.toThrow(/BID-003/);
    const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE' });
    const ppage = await ctx.newPage();
    await setSession(ctx, sc.pyme.token, BASE);
    await goto(ppage, `/pyme/solicitudes/${sc.request.id}`);
    await expect(ppage.getByText('Retirada', { exact: true }).first()).toBeVisible();
    await expect(ppage.getByRole('button', { name: 'Aceptar oferta' })).toHaveCount(0);
    expect((await api.notificationsOf(sc.pyme.token)).some((n) => n.type === 'BID_WITHDRAWN')).toBe(true);
    await ctx.close();
  });

  test('las contra-ofertas pendientes vencen al adjudicar otra oferta y se ven como Vencida (F40)', async ({ page }) => {
    const sc = await api.openBidScenario(6000);
    await api.counterOffer(sc.pyme.token, sc.bid.id, Math.ceil(sc.request.priceFloor) + 10);
    const other = await api.registerAccount('CARRIER');
    const v2 = await api.addVehicle(other.token);
    const bid2 = await api.placeBid(other.token, sc.request.id, v2.id, Math.ceil(sc.request.priceFloor) + 200);
    await api.acceptBid(sc.pyme.token, bid2.id);
    await expect.poll(async () => (await api.call<any[]>('GET', `/bids/${sc.bid.id}/counter-offers`, sc.carrier.token))[0]?.status, { timeout: 15_000 }).toBe('EXPIRED');

    // La PYME ve la contra-oferta vencida en el historial de la oferta no elegida
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);
    await page.getByRole('button', { name: /Ver historial de contra-ofertas/ }).click();
    await expect(page.getByText('Vencida', { exact: true })).toBeVisible();
  });
});

test.describe('Ofertar: compatibilidad de vehículos (RN02, F09/F35/F50)', () => {
  async function hazmatScenario() {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const small = await api.addVehicle(carrier.token, 8000, 'TRUCK_SMALL');
    const medium = await api.addVehicle(carrier.token, 12000, 'TRUCK_MEDIUM');
    const req = await api.createRequest(pyme.token, { originCity: 'MOQUEGUA', destinationCity: 'TACNA', cargoType: 'HAZARDOUS', weightKg: 2000, cargoDescription: 'Insumos químicos en tambores' });
    return { pyme, carrier, small, medium, req };
  }

  test('la hoja de oferta deshabilita los vehículos que no pueden llevar la carga y muestra el motivo', async ({ page }) => {
    const sc = await hazmatScenario();
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, '/transportista/marketplace?origen=MOQUEGUA&destino=TACNA&carga=HAZARDOUS');
    await page.getByRole('button', { name: 'Ofertar' }).first().click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await expect(sheet.getByText('No disponibles para esta carga')).toBeVisible();
    await expect(sheet.getByText(new RegExp(`${sc.small.plate}.*no puede llevar carga peligrosa`))).toBeVisible();
    await expect(sheet.getByText('Es tu único vehículo apto para esta carga.')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/oferta-vehiculos-no-aptos.png` });
    await expectNoSeriousViolations(page, '[role=dialog]');

    await sheet.getByRole('combobox', { name: 'Vehículo' }).click();
    await expect(page.getByRole('option', { name: new RegExp(sc.small.plate) })).toHaveAttribute('aria-disabled', 'true');
    await expect(page.getByRole('option', { name: new RegExp(sc.medium.plate) })).not.toHaveAttribute('aria-disabled', 'true');
    await page.keyboard.press('Escape');
    await sheet.getByLabel('Tu oferta').fill(String(Math.ceil(sc.req.priceFloor) + 50));
    await sheet.getByRole('button', { name: 'Enviar oferta' }).click();
    await expect(toastWith(page, 'Oferta enviada')).toBeVisible();
    const bids = await api.bidsOf(sc.pyme.token, sc.req.id);
    expect(bids.some((b) => b.vehiclePlate === sc.medium.plate)).toBe(true);
  });

  test('el marketplace no marca "Compatible" si la flota no puede llevar el tipo de carga', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token, 8000, 'TRUCK_SMALL');
    await api.createRequest(pyme.token, { originCity: 'MOQUEGUA', destinationCity: 'TACNA', cargoType: 'HAZARDOUS', weightKg: 2000 });
    await api.createRequest(pyme.token, { originCity: 'MOQUEGUA', destinationCity: 'TACNA', cargoType: 'GENERAL', weightKg: 2000 });
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/marketplace?origen=MOQUEGUA&destino=TACNA');
    const hazmat = page.getByRole('row').filter({ hasText: 'Peligrosa' });
    await expect(hazmat.first()).toBeVisible();
    await expect(hazmat.first().getByText('Compatible', { exact: true })).toHaveCount(0);
    await expect(hazmat.first().getByText('Tu flota no lleva carga peligrosa')).toBeVisible();
    const general = page.getByRole('row').filter({ hasText: 'Carga general' });
    await expect(general.first().getByText('Compatible', { exact: true })).toBeVisible();
  });

  test('sin vehículos: aviso de primer ingreso y estado vacío en la hoja con enlace a Flota', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    await api.createRequest(pyme.token, { originCity: 'MOQUEGUA', destinationCity: 'TACNA' });
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/marketplace?origen=MOQUEGUA&destino=TACNA');
    await expect(page.getByText('Registra tu primer vehículo').first()).toBeVisible();
    await page.getByRole('button', { name: 'Ofertar' }).first().click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await expect(sheet.getByText('Aún no tienes vehículos')).toBeVisible();
    await expect(sheet.getByRole('link', { name: 'Registrar un vehículo' })).toHaveAttribute('href', '/transportista/flota');
  });

  test('CAP-005 (vehículo comprometido) queda inline en el campo Vehículo', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const vehicle = await api.addVehicle(carrier.token);
    const day = api.isoDate(6);
    const a = await api.createRequest(pyme.token, { originCity: 'LIMA', destinationCity: 'ICA', pickupDate: day });
    const bidA = await api.placeBid(carrier.token, a.id, vehicle.id, Math.ceil(a.priceFloor) + 10);
    await api.acceptBid(pyme.token, bidA.id);
    const b = await api.createRequest(pyme.token, { originCity: 'ICA', destinationCity: 'NAZCA', pickupDate: day });
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/marketplace?origen=ICA&destino=NAZCA');
    await page.getByRole('button', { name: 'Ofertar' }).first().click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await sheet.getByLabel('Tu oferta').fill(String(Math.ceil(b.priceFloor) + 10));
    await sheet.getByRole('button', { name: 'Enviar oferta' }).click();
    await expect(sheet.getByText(/ya está comprometido en otro envío/)).toBeVisible();
    await expect(sheet.getByRole('combobox', { name: 'Vehículo' })).toHaveAttribute('aria-invalid', 'true');
  });
});
