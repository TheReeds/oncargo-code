import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { goto, pick, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

/** Envío PENDING listo para reportar incidencias (sin conductor: no hace falta). */
async function pendingShipment() {
  const sc = await api.openBidScenario();
  const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
  return { ...sc, shipmentId: accepted.shipmentId as string };
}

test.describe('Incidencias', () => {
  test('flujo completo: reportar -> operador revisa, comenta y resuelve -> la parte ve Resuelta', async ({ browser }) => {
    const sc = await pendingShipment();
    const desc = `Dos bolsas llegaron rotas ${api.uid()}`;

    // 1) PYME reporta desde Envíos
    const pctx = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima', viewport: { width: 1440, height: 900 } });
    await setSession(pctx, sc.pyme.token, BASE);
    const page = await pctx.newPage();
    await goto(page, `/pyme/envios?id=${sc.shipmentId}`);
    await page.getByRole('tab', { name: 'Incidencias' }).click();
    await expect(page.locator('main').getByText('Este envío no tiene incidencias.')).toBeVisible();

    await page.getByRole('button', { name: 'Reportar incidencia' }).click();
    const dlg = page.getByRole('dialog', { name: 'Reportar incidencia' });
    await dlg.getByRole('button', { name: 'Reportar incidencia' }).click();
    await expect(dlg.getByText('Selecciona una categoría.')).toBeVisible();
    await expect(dlg.getByText('Describe qué ocurrió.')).toBeVisible();
    await pick(page, dlg.getByRole('combobox', { name: 'Categoría' }), 'Daño en la carga');
    await dlg.getByLabel('Descripción').fill(desc);
    await dlg.getByRole('button', { name: 'Reportar incidencia' }).click();

    // RN11: banner de envío detenido y botón bloqueado
    await expect(page.locator('main').getByText('Este envío tiene una incidencia activa')).toBeVisible();
    await page.getByRole('tab', { name: 'Incidencias' }).click();
    await expect(page.getByRole('button', { name: 'Reportar incidencia' })).toBeDisabled();
    await expect(page.locator('main p', { hasText: desc })).toBeVisible();

    // 2) aparece en /incidencias
    await goto(page, '/incidencias');
    const row = page.locator('tbody tr').first();
    await expect(row).toContainText('Daño en la carga');
    await expect(row).toContainText('Abierta');
    await page.screenshot({ path: `${SHOTS}/incidencias-lista.png` });
    await row.getByRole('link', { name: /Ver/ }).click();
    await expect(page.getByRole('heading', { name: 'Daño en la carga', level: 1 })).toBeVisible();
    await expect(page.locator('main p', { hasText: desc })).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/incidencias-detalle.png`, fullPage: true });
    const incidentUrl = page.url();

    // 3) operador por UI
    const octx = await browser.newContext({ locale: 'es-PE', timezoneId: 'America/Lima', viewport: { width: 1440, height: 900 } });
    await setSession(octx, await api.operatorToken(), BASE);
    const op = await octx.newPage();
    await goto(op, '/operador/incidencias');
    const orow = op.locator('tbody tr').filter({ hasText: sc.pyme.name });
    // la bandeja pone las más antiguas primero y la base de pruebas acumula incidencias: avanza de página hasta encontrarla
    await expect(op.locator('tbody tr').first()).toBeVisible(); // espera a que cargue la bandeja
    const next = op.getByRole('button', { name: /Siguiente|Página siguiente/ }).first();
    for (let i = 0; i < 20 && !(await orow.count()) && (await next.count()); i++) await next.click();
    await expect(orow).toBeVisible();
    await op.screenshot({ path: `${SHOTS}/incidencias-operador-bandeja.png` });
    await orow.getByRole('button', { name: 'Pasar a revisión' }).click();
    await op.getByRole('alertdialog').getByRole('button', { name: 'Pasar a revisión' }).click();
    await expect(toastWith(op, 'Incidencia en revisión')).toBeVisible();
    await expect(orow).toContainText('En revisión');

    await goto(op, new URL(incidentUrl).pathname);
    await op.getByLabel('Agregar comentario').fill('Estamos revisando el caso con el transportista.');
    await op.getByRole('button', { name: 'Enviar comentario' }).click();
    await expect(op.locator('main').getByText('Estamos revisando el caso con el transportista.')).toBeVisible();

    // resolver sin notas -> error
    await op.getByRole('button', { name: 'Resolver incidencia' }).click();
    await expect(op.locator('main').getByText('Escribe las notas de resolución')).toBeVisible();
    await op.getByLabel('Notas de resolución').fill('Se repuso la mercadería dañada.');
    // RF55: el estado final del envío es obligatorio al resolver
    await op.getByRole('button', { name: 'Resolver incidencia' }).click();
    await expect(op.locator('main').getByText('Elige qué pasa con el envío.')).toBeVisible();
    await pick(op, op.getByRole('combobox', { name: 'Qué pasa con el envío' }), 'El envío continúa');
    await op.screenshot({ path: `${SHOTS}/incidencias-operador-gestion.png`, fullPage: true });
    await op.getByRole('button', { name: 'Resolver incidencia' }).click();
    await op.getByRole('alertdialog').getByRole('button', { name: 'Resolver incidencia' }).click();
    await expect(toastWith(op, 'Incidencia resuelta')).toBeVisible();

    // 4) la parte ve Resuelta y desaparece el banner
    await goto(page, incidentUrl);
    await expect(page.locator('main').getByText('Se repuso la mercadería dañada.').first()).toBeVisible();
    await expect(page.locator('main').getByText('Estamos revisando el caso con el transportista.')).toBeVisible();
    await goto(page, `/pyme/envios?id=${sc.shipmentId}`);
    await expect(page.locator('main').getByText('Este envío tiene una incidencia activa')).toHaveCount(0);
    await page.getByRole('tab', { name: 'Incidencias' }).click();
    await expect(page.locator('main').getByText('Resuelta').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reportar incidencia' })).toBeEnabled();
    await pctx.close();
    await octx.close();
  });

  test('el transportista comenta y el operador cierra; cerrada no admite comentarios', async ({ page, context }) => {
    const sc = await pendingShipment();
    const inc = await api.reportIncident(sc.carrier.token, sc.shipmentId, 'DELAY');
    await setSession(context, sc.carrier.token, BASE);
    await goto(page, `/incidencias/${inc.id}`);
    await page.getByLabel('Agregar comentario').fill('Llegaremos en dos horas.');
    await page.getByRole('button', { name: 'Enviar comentario' }).click();
    await expect(page.locator('main').getByText('Llegaremos en dos horas.')).toBeVisible();
    await api.call('POST', `/incidents/${inc.id}/status`, await api.operatorToken(), { status: 'CLOSED', resolutionNotes: 'Cierre de prueba.', shipmentOutcome: 'RESUME' });
    await goto(page, `/incidencias/${inc.id}`);
    await expect(page.locator('main').getByText('ya no admite comentarios')).toBeVisible();
  });

  test('incidencia ajena: mensaje de acceso', async ({ page, context }) => {
    const sc = await pendingShipment();
    const inc = await api.reportIncident(sc.pyme.token, sc.shipmentId);
    const other = await api.registerAccount('PYME');
    await setSession(context, other.token, BASE);
    await goto(page, `/incidencias/${inc.id}`);
    await expect(page.locator('main').getByText('No encontramos esa incidencia')).toBeVisible();
  });
});
