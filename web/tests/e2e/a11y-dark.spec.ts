import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

/** Falla ante violaciones `serious` o `critical` de WCAG A/AA (mismo criterio que a11y.spec.ts). */
async function expectNoSeriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
}

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('logyx-theme', 'dark'));
});

async function expectDark(page: Page) {
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
}

test.describe('Accesibilidad en modo oscuro (axe, WCAG 2.1 AA)', () => {
  test('login', async ({ page }) => {
    await goto(page, '/login');
    await expectDark(page);
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/login-dark.png` });
  });

  test('tema "sistema" sigue prefers-color-scheme', async ({ page }) => {
    await page.addInitScript(() => localStorage.setItem('logyx-theme', 'system'));
    await page.emulateMedia({ colorScheme: 'dark' });
    await goto(page, '/login');
    await expectDark(page);
    await page.emulateMedia({ colorScheme: 'light' });
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
  });

  test('panel PYME', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await api.createRequest(pyme.token, {});
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme');
    await expectDark(page);
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/pyme-panel-dark.png`, fullPage: true });
  });

  test('detalle de solicitud con ofertas (PYME)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token);
    const req = await api.createRequest(pyme.token, {});
    await api.placeBid(carrier.token, req.id, v.id, Math.ceil(req.priceFloor) + 10);
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${req.id}`);
    await expectDark(page);
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/pyme-detalle-solicitud-dark.png`, fullPage: true });
  });

  test('seguimiento de envío con mapa (PYME)', async ({ page }) => {
    const sc = await api.openBidScenario();
    const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
    const driver = await api.addDriver(sc.carrier.token);
    await api.assignDriver(sc.carrier.token, accepted.shipmentId, driver.id);
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${accepted.shipmentId}`);
    await expectDark(page);
    await expect(page.locator('.maplibregl-canvas').first()).toBeVisible({ timeout: 15_000 });
    await page.waitForTimeout(2500); // tiles del estilo oscuro
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/pyme-envios-dark.png`, fullPage: true });
  });

  test('cargas disponibles (transportista)', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token);
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/marketplace');
    await expect(page.locator('tbody tr').first()).toBeVisible();
    await expectDark(page);
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/transportista-marketplace-dark.png` });
  });

  test('notificaciones', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token);
    const r = await api.createRequest(pyme.token, {});
    await api.placeBid(carrier.token, r.id, v.id, Math.ceil(r.priceFloor) + 10);
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/notificaciones');
    await expectDark(page);
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/notificaciones-dark.png` });
  });

  test('toggle de tema persiste', async ({ page }) => {
    await page.addInitScript(() => { if (!sessionStorage.getItem('once')) { sessionStorage.setItem('once', '1'); localStorage.setItem('logyx-theme', 'light'); } });
    await goto(page, '/login');
    await page.getByRole('button', { name: 'Tema oscuro' }).click();
    await expectDark(page);
    await page.reload();
    await expectDark(page);
    expect(await page.evaluate(() => localStorage.getItem('logyx-theme'))).toBe('dark');
  });
});
