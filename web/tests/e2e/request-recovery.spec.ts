import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

test.describe('Solicitud PYME: cancelar con motivo, republicar y reabrir (F07, F16, RF18)', () => {
  test('cancelar con motivo (RN07 inline) avisa a los transportistas con oferta; republicar crea una nueva', async ({ page }) => {
    const sc = await api.openBidScenario(5000);
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);
    await page.getByRole('button', { name: 'Cancelar solicitud' }).click();
    const dlg = page.getByRole('dialog', { name: '¿Cancelar esta solicitud?' });
    await dlg.getByLabel('Motivo de la cancelación').fill('Escríbeme al 987654321');
    await dlg.getByRole('button', { name: 'Cancelar solicitud' }).click();
    await expect(dlg.getByText(/No puedes compartir teléfonos/)).toBeVisible();
    await dlg.getByLabel('Motivo de la cancelación').fill('Cambió la fecha de despacho');
    await dlg.getByRole('button', { name: 'Cancelar solicitud' }).click();
    await expect(toastWith(page, 'Solicitud cancelada')).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: 'Solicitud cancelada' }).first()).toContainText('Motivo: Cambió la fecha de despacho');
    expect((await api.notificationsOf(sc.carrier.token)).some((n) => n.type === 'REQUEST_CANCELLED')).toBe(true);

    // Republicar: nueva solicitud con la referencia a la cancelada
    await page.getByRole('button', { name: 'Republicar solicitud' }).click();
    const rdlg = page.getByRole('dialog', { name: 'Republicar la solicitud' });
    await expectNoSeriousViolations(page, '[role=dialog]');
    await rdlg.getByRole('button', { name: 'Republicar solicitud' }).click();
    await expect(page).toHaveURL(new RegExp(`/pyme/solicitudes/(?!${sc.request.id})[0-9a-f-]{36}`));
    await expect(toastWith(page, 'Solicitud republicada')).toBeVisible();
    await expect(page.getByText(/es una republicación de/)).toBeVisible();
    await expect(page.getByText('Publicada').first()).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/solicitud-republicada.png` });
    const mine = await api.call<any[]>('GET', '/shipment-requests/mine', sc.pyme.token);
    expect(mine.some((r) => r.republishedFrom === sc.request.id && r.status === 'PUBLISHED')).toBe(true);
  });

  test('la lista ofrece Republicar en una cancelada y muestra el motivo', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const req = await api.createRequest(pyme.token, {});
    await api.call('POST', `/shipment-requests/${req.id}/cancel`, pyme.token, { reason: 'Ya no se necesita el flete' });
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes');
    const row = page.getByRole('row').filter({ hasText: 'Ya no se necesita el flete' });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /Más acciones/ }).click();
    await page.getByRole('menuitem', { name: 'Republicar solicitud' }).click();
    await page.getByRole('dialog', { name: 'Republicar la solicitud' }).getByRole('button', { name: 'Republicar solicitud' }).click();
    await expect(page).toHaveURL(/\/pyme\/solicitudes\/[0-9a-f-]{36}/);
  });

  test('subasta cerrada por tiempo: aparece "Reabrir subasta" y el diálogo muestra los errores de la API', async ({ page }) => {
    // En dev la subasta dura 72 h: no se puede cerrar por tiempo de verdad. Se simula el estado vivo que publica
    // RequestLiveSync (evento de ventana) y se comprueba el diálogo; el éxito de `reopen` lo cubren los tests de backend.
    const pyme = await api.registerAccount('PYME');
    const req = await api.createRequest(pyme.token, {});
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${req.id}`);
    await expect(page.getByRole('button', { name: 'Reabrir subasta' })).toHaveCount(0);
    await page.evaluate((r) => window.dispatchEvent(new CustomEvent('logyx:request-state', { detail: { request: { id: r.id, status: 'PUBLISHED', auction: { status: 'CLOSED', closeReason: 'TIMEOUT', bidCount: 0, maxBids: 5, closesAt: r.auction?.closesAt } }, bids: [] } })), req);
    await expect(page.getByText(/Terminó el tiempo sin ofertas/)).toBeVisible();
    await page.getByRole('button', { name: 'Reabrir subasta' }).click();
    const dlg = page.getByRole('dialog', { name: 'Reabrir la subasta' });
    await expect(dlg.getByLabel('Nueva fecha de recojo')).toBeVisible();
    await expectNoSeriousViolations(page, '[role=dialog]');
    await dlg.getByRole('button', { name: 'Reabrir subasta' }).click();
    await expect(dlg.getByText('No pudimos reabrir la subasta')).toBeVisible(); // AUC-003: en realidad sigue abierta
    await expect(dlg.getByText(/Solo se reabre una subasta que cerró por tiempo/)).toBeVisible();
  });
});
