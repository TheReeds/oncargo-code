import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const LIVE = '[data-testid="live-indicator"][data-status="live"]';

/** Marca la página para comprobar después que NO hubo navegación ni recarga. */
const markNoReload = (page: import('@playwright/test').Page) => page.evaluate(() => { (window as any).__noReload = true; });
const stillSamePage = (page: import('@playwright/test').Page) => page.evaluate(() => (window as any).__noReload === true);

test.describe('Tiempo real (SSE)', () => {
  test('el indicador "En vivo" se conecta', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme');
    await expect(page.locator(LIVE)).toBeVisible({ timeout: 10_000 });
    await expect(page.locator(LIVE)).toContainText('En vivo');
  });

  test('una oferta nueva aparece en el detalle de la solicitud sin recargar', async ({ page }) => {
    const sc = await api.openBidScenario(2300);
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);
    await expect(page.locator(LIVE)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('bid-count')).toHaveText(/^1\//);
    await markNoReload(page);

    const carrier2 = await api.registerAccount('CARRIER');
    const v2 = await api.addVehicle(carrier2.token);
    await api.placeBid(carrier2.token, sc.request.id, v2.id, Math.ceil(sc.request.priceFloor) + 100, 'Segunda oferta');

    await expect(page.getByText(carrier2.name).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('bid-count')).toHaveText(/^2\//, { timeout: 10_000 });
    expect(await stillSamePage(page)).toBe(true);
  });

  test('un mensaje del transportista llega en vivo al chat de la PYME', async ({ page }) => {
    const sc = await api.openBidScenario();
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);
    await expect(page.locator(LIVE)).toBeVisible({ timeout: 10_000 });
    await markNoReload(page);

    const carrierOrg = await api.me(sc.carrier.token);
    const text = `Llegamos a las 8:00 en vivo ${Date.now()}`;
    await api.call('POST', `/shipment-requests/${sc.request.id}/threads/${carrierOrg.organizationId}/messages`, sc.carrier.token, { body: text });

    const panel = page.locator('#conversacion').locator('xpath=ancestor::div[contains(@class,"rounded-card")][1]');
    await expect(panel.getByRole('log').getByText(text)).toBeVisible({ timeout: 10_000 });
    expect(await stillSamePage(page)).toBe(true);
  });

  test('la campana sube el contador al llegar una notificación', async ({ page }) => {
    const sc = await api.openBidScenario();
    await setSession(page.context(), sc.pyme.token, BASE);
    // Marca las existentes como leídas para partir de cero.
    await api.call('POST', '/notifications/read-all', sc.pyme.token).catch(() => null);
    await goto(page, '/pyme');
    await expect(page.locator(LIVE)).toBeVisible({ timeout: 10_000 });
    const bell = page.getByRole('button', { name: /^Notificaciones/ });
    await expect(bell).toHaveText(/^Notificaciones$/, { timeout: 10_000 });
    await markNoReload(page);

    const carrier2 = await api.registerAccount('CARRIER');
    const v2 = await api.addVehicle(carrier2.token);
    await api.placeBid(carrier2.token, sc.request.id, v2.id, Math.ceil(sc.request.priceFloor) + 50);

    await expect(bell).toHaveText(/Notificaciones, [1-9]\d* sin leer/, { timeout: 10_000 });
    expect(await stillSamePage(page)).toBe(true);
  });

  test('CSP y tema: sin violaciones en consola y el stream conecta', async ({ page }) => {
    const problems: string[] = [];
    page.on('console', (m) => { if (/Content Security Policy|Refused to/i.test(m.text())) problems.push(m.text()); });
    await page.addInitScript(() => document.addEventListener('securitypolicyviolation', (e) => console.error('CSP-VIOLATION ' + e.violatedDirective + ' ' + e.blockedURI)));
    page.on('console', (m) => { if (m.text().startsWith('CSP-VIOLATION')) problems.push(m.text()); });
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme');
    await expect(page.locator(LIVE)).toBeVisible({ timeout: 10_000 });
    await page.evaluate(() => localStorage.setItem('logyx-theme', 'dark'));
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await expect(page.locator(LIVE)).toBeVisible({ timeout: 10_000 });
    expect(problems).toEqual([]);
  });
});
