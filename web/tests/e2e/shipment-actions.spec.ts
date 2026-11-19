import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pick, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

/** PYME + transportista con DOS vehículos; solicitud adjudicada (envío PENDING). */
async function acceptedShipment() {
  const sc = await api.openBidScenario(5000);
  const vehicle2 = await api.addVehicle(sc.carrier.token);
  const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
  return { ...sc, vehicle2, shipmentId: accepted.shipmentId as string };
}

async function pickupInTransit(carrier: { token: string }, driverEmail: string, shipmentId: string) {
  const dt = await api.login(driverEmail);
  const ship = await api.call<any>('GET', `/shipments/${shipmentId}`, carrier.token);
  const stop = [...ship.stops].sort((a: any, b: any) => a.sequence - b.sequence)[0];
  await api.call('POST', `/shipments/${shipmentId}/stops/${stop.id}/arrive`, dt);
  await api.call('POST', `/shipments/${shipmentId}/stops/${stop.id}/complete`, dt);
}

const dialogOf = (page: Page, name: string | RegExp) => page.getByRole('dialog', { name });

test.describe('Envío: cancelar con motivo (F06, F36)', () => {
  test('la PYME cancela antes del recojo: motivo obligatorio, RN07 inline, la contraparte lo ve', async ({ page, browser }) => {
    const sc = await acceptedShipment();
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${sc.shipmentId}`);
    await expect(page.getByText(/Recojo \d+ \w+/).first()).toBeVisible(); // fecha de recojo (F36)
    await page.getByRole('button', { name: 'Cancelar envío' }).click();
    const dlg = dialogOf(page, '¿Cancelar este envío?');
    await dlg.getByRole('button', { name: 'Cancelar envío' }).click();
    await expect(dlg.getByText(/Indica el motivo/)).toBeVisible();
    await dlg.getByLabel('Motivo de la cancelación').fill('Llámame al 987 654 321 y lo coordinamos');
    await dlg.getByRole('button', { name: 'Cancelar envío' }).click();
    await expect(dlg.getByText(/No puedes compartir teléfonos/)).toBeVisible(); // LOGYX-NEG-001 bajo el campo
    await expectNoSeriousViolations(page, '[role=dialog]');
    await dlg.getByLabel('Motivo de la cancelación').fill('El cliente pidió postergar la entrega');
    await dlg.getByRole('button', { name: 'Cancelar envío' }).click();
    await expect(toastWith(page, 'Envío cancelado')).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: 'Este envío fue cancelado' }).first()).toContainText('Motivo: El cliente pidió postergar la entrega');
    await expect(page.getByRole('status').filter({ hasText: 'Este envío fue cancelado' }).first()).toContainText('Lo cancelaste tú');
    await page.screenshot({ path: `${SHOTS}/envio-cancelado.png` });

    // El transportista ve el motivo y recibe el aviso SHIPMENT_CANCELLED
    const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE' });
    const cpage = await ctx.newPage();
    await setSession(ctx, sc.carrier.token, BASE);
    await goto(cpage, `/transportista/envios?id=${sc.shipmentId}`);
    await expect(cpage.getByRole('status').filter({ hasText: 'Este envío fue cancelado' }).first()).toContainText('El cliente pidió postergar la entrega');
    await expect(cpage.getByRole('status').filter({ hasText: 'Este envío fue cancelado' }).first()).toContainText('Lo canceló la PYME o el equipo de OnCargo');
    await expect(cpage.getByRole('button', { name: 'Cancelar envío' })).toHaveCount(0);
    const notes = await api.notificationsOf(sc.carrier.token);
    expect(notes.some((n) => n.type === 'SHIPMENT_CANCELLED' && n.relatedType === 'SHIPMENT')).toBe(true);
    await ctx.close();
    expect((await api.getRequest(sc.pyme.token, sc.request.id)).status).toBe('CANCELLED');
  });

  test('con una incidencia activa las partes no pueden cancelar (TRK-005): botón deshabilitado y motivo visible', async ({ page }) => {
    const sc = await acceptedShipment();
    // Una que detiene el viaje (desde la Ola 2 DELAY es informativa)
    await api.reportIncident(sc.pyme.token, sc.shipmentId, 'CARGO_DAMAGE', 'Cajas mojadas en el almacén de recojo.');
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${sc.shipmentId}`);
    await expect(page.getByRole('button', { name: 'Cancelar envío' })).toBeDisabled();
    await expect(page.getByText(/Con una incidencia activa no puedes cancelar/)).toBeVisible();
  });

  test('el operador cancela un envío en tránsito con motivo', async ({ page }) => {
    const sc = await acceptedShipment();
    const driver = await api.addDriver(sc.carrier.token);
    await api.assignDriver(sc.carrier.token, sc.shipmentId, driver.id);
    await pickupInTransit(sc.carrier, driver.email, sc.shipmentId);
    const op = await api.operatorToken();
    await setSession(page.context(), op, BASE);
    await goto(page, `/operador/envios/${sc.shipmentId}`);
    await page.getByRole('button', { name: 'Cancelar envío' }).click();
    const dlg = dialogOf(page, '¿Cancelar este envío?');
    await dlg.getByLabel('Motivo de la cancelación').fill('Carga extraviada en ruta');
    await dlg.getByRole('button', { name: 'Cancelar envío' }).click();
    await expect(toastWith(page, 'Envío cancelado')).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: 'Este envío fue cancelado' }).first()).toContainText('Lo canceló el equipo de OnCargo');
    await expect(page.getByRole('button', { name: 'Cancelar envío' })).toHaveCount(0);
    // Las partes no cancelan en ruta (SHP-003): la PYME ni siquiera ve la acción
    const shipment = await api.call<any>('GET', `/shipments/${sc.shipmentId}`, sc.pyme.token);
    expect(shipment.cancellationReason).toBe('Carga extraviada en ruta');
  });
});

test.describe('Envío: cambiar vehículo y relevo de conductor (F06, F36)', () => {
  test('el transportista cambia el vehículo y releva al conductor antes del recojo', async ({ page }) => {
    const sc = await acceptedShipment();
    const d1 = await api.addDriver(sc.carrier.token, `Conductor A ${api.uid()}`);
    const d2 = await api.addDriver(sc.carrier.token, `Conductor B ${api.uid()}`);
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, `/transportista/envios?id=${sc.shipmentId}`);
    await expect(page.getByText('Sin conductor').first()).toBeVisible(); // marca en la lista y en el detalle (F36)

    await page.getByRole('button', { name: 'Asignar conductor' }).click();
    let dlg = dialogOf(page, 'Asignar conductor');
    await pick(page, dlg.getByRole('combobox', { name: 'Conductor' }), d1.fullName);
    await dlg.getByRole('button', { name: 'Asignar conductor' }).click();
    await expect(toastWith(page, 'Conductor asignado')).toBeVisible();

    await page.getByRole('button', { name: 'Cambiar vehículo' }).click();
    dlg = dialogOf(page, 'Cambiar el vehículo del envío');
    await dlg.getByRole('button', { name: 'Cambiar vehículo' }).click();
    await expect(dlg.getByText('Selecciona el vehículo que llevará el envío.')).toBeVisible();
    await pick(page, dlg.getByRole('combobox', { name: 'Nuevo vehículo' }), sc.vehicle2.plate);
    await dlg.getByRole('button', { name: 'Cambiar vehículo' }).click();
    await expect(toastWith(page, 'Vehículo cambiado')).toBeVisible();
    await expect(page.getByText(sc.vehicle2.plate).first()).toBeVisible();

    await page.getByRole('button', { name: 'Relevo de conductor' }).click();
    dlg = dialogOf(page, 'Relevo de conductor');
    await pick(page, dlg.getByRole('combobox', { name: 'Conductor' }), d2.fullName);
    await dlg.getByRole('button', { name: 'Relevar conductor' }).click();
    await expect(toastWith(page, 'Conductor relevado')).toBeVisible();
    const s = await api.call<any>('GET', `/shipments/${sc.shipmentId}`, sc.carrier.token);
    expect(s.vehiclePlate).toBe(sc.vehicle2.plate);
    expect(s.driverId).toBe(d2.id);
    expect((await api.notificationsOf(sc.pyme.token)).some((n) => n.type === 'SHIPMENT_STATUS_CHANGE')).toBe(true);
  });

  test('en ruta el relevo exige motivo (SHP-006) y el transportista no cancela', async ({ page }) => {
    const sc = await acceptedShipment();
    const d1 = await api.addDriver(sc.carrier.token, `Conductor A ${api.uid()}`);
    const d2 = await api.addDriver(sc.carrier.token, `Conductor B ${api.uid()}`);
    await api.assignDriver(sc.carrier.token, sc.shipmentId, d1.id);
    await pickupInTransit(sc.carrier, d1.email, sc.shipmentId);
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, `/transportista/envios?id=${sc.shipmentId}`);
    await expect(page.getByRole('button', { name: 'Cancelar envío' })).toHaveCount(0);
    await page.getByRole('button', { name: 'Relevo de conductor' }).click();
    const dlg = dialogOf(page, 'Relevo de conductor');
    await pick(page, dlg.getByRole('combobox', { name: 'Conductor' }), d2.fullName);
    await dlg.getByRole('button', { name: 'Relevar conductor' }).click();
    await expect(dlg.getByText(/Indica el motivo/)).toBeVisible();
    await dlg.getByLabel('Motivo del relevo').fill('El conductor se enfermó en la ruta');
    await dlg.getByRole('button', { name: 'Relevar conductor' }).click();
    await expect(toastWith(page, 'Conductor relevado')).toBeVisible();
  });

  test('SHP-004: un conductor comprometido en otro envío del mismo día queda inline en el campo', async ({ page }) => {
    const sc = await acceptedShipment(); // vehículo 1 → envío 1
    const req2 = await api.createRequest(sc.pyme.token, { originCity: 'LIMA', destinationCity: 'JULIACA' });
    const bid2 = await api.placeBid(sc.carrier.token, req2.id, sc.vehicle2.id, Math.ceil(req2.priceFloor) + 50);
    const ship2 = (await api.acceptBid(sc.pyme.token, bid2.id)).shipmentId as string;
    const d1 = await api.addDriver(sc.carrier.token, `Conductor A ${api.uid()}`);
    await api.assignDriver(sc.carrier.token, sc.shipmentId, d1.id);
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, `/transportista/envios?id=${ship2}`);
    await page.getByRole('button', { name: 'Asignar conductor' }).click();
    const dlg = dialogOf(page, 'Asignar conductor');
    await pick(page, dlg.getByRole('combobox', { name: 'Conductor' }), d1.fullName);
    await dlg.getByRole('button', { name: 'Asignar conductor' }).click();
    await expect(dlg.getByText(/ya tiene otro envío activo/)).toBeVisible();
    await expect(dlg.getByRole('combobox', { name: 'Conductor' })).toHaveAttribute('aria-invalid', 'true');
  });
});
