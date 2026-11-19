import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, setSession, toastWith } from './helpers/ui';

/**
 * Ola 2 · zona X: evidencia del recojo y registro por la oficina (2.5), ajustes del envío con precio final (2.6),
 * incidencias v2 (2.7) y operador 360, señales y cuentas (2.15). Axe en claro y oscuro.
 */
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

/** Envío adjudicado con conductor; `inTransit` completa el recojo. */
async function awarded(inTransit = false) {
  const sc = await api.openBidScenario();
  const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
  const shipmentId: string = accepted.shipmentId;
  const driver = await api.addDriver(sc.carrier.token);
  await api.assignDriver(sc.carrier.token, shipmentId, driver.id);
  const dt = await api.login(driver.email);
  const ship = await api.call<any>('GET', `/shipments/${shipmentId}`, sc.pyme.token);
  const stops: any[] = [...ship.stops].sort((a, b) => a.sequence - b.sequence);
  if (inTransit) {
    await api.call('POST', `/shipments/${shipmentId}/stops/${stops[0].id}/arrive`, dt);
    await api.call('POST', `/shipments/${shipmentId}/stops/${stops[0].id}/complete`, dt);
  }
  return { ...sc, shipmentId, driverToken: dt, pickupId: stops[0].id, deliveryId: stops[1].id };
}

async function axeBoth(page: Page, url: string, include?: string) {
  await expectNoSeriousViolations(page, include);
  await page.evaluate(() => { localStorage.setItem('logyx-theme', 'dark'); });
  await goto(page, url);
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expectNoSeriousViolations(page, include);
  await page.evaluate(() => { localStorage.setItem('logyx-theme', 'light'); });
}

async function openTab(page: Page, name: RegExp) {
  await page.getByRole('tab', { name }).click();
}

test.describe('Ola 2 · zona X', () => {
  test('el transportista propone un peaje, la PYME lo acepta y el precio final lo suma', async ({ page, context }) => {
    const sc = await awarded(true);
    await setSession(context, sc.carrier.token, BASE);
    const carrierUrl = `/transportista/envios?id=${sc.shipmentId}`;
    await goto(page, carrierUrl);
    await openTab(page, /Ajustes/);
    await page.getByRole('button', { name: 'Proponer ajuste' }).click();
    const dialog = page.getByRole('dialog', { name: 'Proponer ajuste' });
    await dialog.getByLabel('Peaje').check();
    await dialog.getByLabel('Monto').fill('118');
    await dialog.getByLabel('Nota').fill('Peaje Pativilca');
    await dialog.getByRole('button', { name: 'Proponer ajuste' }).click();
    await expect(toastWith(page, 'Ajuste propuesto')).toBeVisible();
    await expect(page.getByTestId('adjustment').filter({ hasText: 'Peaje' })).toContainText('Por responder');

    await context.clearCookies();
    await setSession(context, sc.pyme.token, BASE);
    const pymeUrl = `/pyme/envios?id=${sc.shipmentId}`;
    await goto(page, pymeUrl);
    await openTab(page, /Ajustes/);
    await page.getByRole('button', { name: 'Aceptar ajuste' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Aceptar ajuste' }).click();
    await expect(toastWith(page, 'Ajuste aceptado')).toBeVisible();
    await expect(page.getByTestId('adjustment')).toContainText('Aceptado');
    const summary = page.getByTestId('adjustments-summary');
    await expect(summary).toContainText('S/ 118.00');
    await openTab(page, /Ajustes/);
    await axeBoth(page, pymeUrl);
  });

  test('la PYME rechaza con motivo y la oficina registra una parada por el conductor', async ({ page, context }) => {
    const sc = await awarded(false);
    await api.call('POST', `/shipments/${sc.shipmentId}/adjustments`, sc.carrier.token, { type: 'HANDLING', amount: 59 });
    await setSession(context, sc.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${sc.shipmentId}`);
    // Qué pide el recojo
    await page.getByRole('switch', { name: 'Foto de la carga' }).click();
    await expect(toastWith(page, 'Recojo actualizado')).toBeVisible();
    await openTab(page, /Ajustes/);
    await page.getByRole('button', { name: 'Rechazar ajuste' }).click();
    const dlg = page.getByRole('dialog', { name: 'Rechazar ajuste' });
    await dlg.getByLabel('Motivo').fill('No hubo maniobra');
    await dlg.getByRole('button', { name: 'Rechazar ajuste' }).click();
    await expect(page.getByTestId('adjustment')).toContainText('No hubo maniobra');

    await context.clearCookies();
    await setSession(context, sc.carrier.token, BASE);
    await goto(page, `/transportista/envios?id=${sc.shipmentId}`);
    await page.getByRole('button', { name: /Registrar llegada al recojo/ }).click();
    const office = page.getByRole('dialog', { name: /Registrar llegada al recojo/ });
    await office.getByLabel('Motivo').fill('Celular sin batería');
    await office.getByRole('button', { name: /Registrar llegada al recojo/ }).click();
    await expect(toastWith(page, 'Parada registrada')).toBeVisible();
    const ship = await api.call<any>('GET', `/shipments/${sc.shipmentId}`, sc.pyme.token);
    expect(ship.stops.find((s: any) => s.type === 'PICKUP').arrivalOfficeReason).toBe('Celular sin batería');
    expect(ship.pickupPhotoRequired).toBe(true);
  });

  test('incidencia informativa: no detiene el viaje, se retira y el operador la gestiona', async ({ page, context }) => {
    const sc = await awarded(true);
    const blocking = await api.call<any>('POST', `/shipments/${sc.shipmentId}/incidents`, sc.pyme.token, { category: 'CARGO_DAMAGE', description: 'Cajas mojadas en la descarga', claimedAmount: 300 });
    const info = await api.reportIncident(sc.carrier.token, sc.shipmentId, 'ROAD_BLOCKED', 'Paro en la vía a Juliaca, vamos lento');
    await setSession(context, sc.carrier.token, BASE);
    await goto(page, `/incidencias/${info.id}`);
    await expect(page.getByText('Incidencia informativa')).toBeVisible();
    await page.getByRole('button', { name: 'Retirar incidencia' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Retirar incidencia' }).click();
    await expect(toastWith(page, 'Incidencia retirada')).toBeVisible();

    const op = await api.operatorToken();
    await context.clearCookies();
    await setSession(context, op, BASE);
    await goto(page, '/operador/incidencias');
    await page.getByRole('button', { name: 'Sin responsable' }).click();
    await expect(page.getByRole('link', { name: 'Daño en la carga' }).first()).toBeVisible();
    await axeBoth(page, '/operador/incidencias', 'main');
    await goto(page, `/incidencias/${blocking.id}`);
    await page.getByRole('button', { name: 'Tomar incidencia' }).click();
    await expect(toastWith(page, 'Incidencia asignada a ti')).toBeVisible();
    await page.getByLabel('Nota interna (solo OnCargo)').check();
    await page.getByRole('textbox', { name: 'Nota interna' }).fill('Pedir póliza al transportista');
    await page.getByRole('button', { name: 'Guardar nota interna' }).click();
    await expect(toastWith(page, 'Nota interna guardada')).toBeVisible();
    await axeBoth(page, `/incidencias/${blocking.id}`, 'main');
    // Las partes no ven la nota interna
    const asPyme = await api.call<any>('GET', `/incidents/${blocking.id}`, sc.pyme.token);
    expect(asPyme.comments.some((c: any) => c.internal)).toBe(false);
  });

  test('operador 360, señales y ajustes escalados', async ({ page, context }) => {
    const sc = await awarded(true);
    const op = await api.operatorToken();
    const orgId = await api.orgIdOf(sc.carrier.token);
    await setSession(context, op, BASE);
    const url = `/operador/organizaciones/${orgId}`;
    await goto(page, url);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(sc.carrier.name);
    await openTab(page, /Usuarios/);
    const row = page.getByTestId('org-user').filter({ hasText: 'Administrador' });
    await row.getByRole('button', { name: /Suspender/ }).click();
    const dlg = page.getByRole('dialog', { name: /Suspender a/ });
    await dlg.getByLabel('Motivo').fill('Revisión de identidad');
    await dlg.getByRole('button', { name: 'Suspender usuario' }).click();
    await expect(toastWith(page, 'Usuario suspendido')).toBeVisible();
    await expect(row).toContainText('Suspendido por OnCargo');
    await row.getByRole('button', { name: /Reactivar/ }).click();
    await page.getByRole('dialog', { name: /Reactivar a/ }).getByRole('button', { name: 'Reactivar usuario' }).click();
    await expect(toastWith(page, 'Usuario reactivado')).toBeVisible();
    await openTab(page, /Resumen/);
    await axeBoth(page, url, 'main');

    await goto(page, '/operador/senales');
    await expect(page.getByRole('heading', { level: 1, name: 'Señales' })).toBeVisible();
    await axeBoth(page, '/operador/senales', 'main');
    await goto(page, '/operador/ajustes');
    await expect(page.getByRole('heading', { level: 1, name: 'Ajustes por decidir' })).toBeVisible();
    await axeBoth(page, '/operador/ajustes', 'main');
  });
});
