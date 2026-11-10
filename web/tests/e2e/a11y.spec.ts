import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

/** Falla ante violaciones `serious` o `critical` de WCAG A/AA. */
async function expectNoSeriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
}

test.describe('Accesibilidad (axe, WCAG 2.1 AA)', () => {
  test('login', async ({ page }) => {
    await goto(page, '/login');
    await expectNoSeriousViolations(page);
  });

  test('registro', async ({ page }) => {
    await goto(page, '/registro');
    await expectNoSeriousViolations(page);
  });

  test('nueva solicitud (PYME)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes/nueva');
    await expectNoSeriousViolations(page);
  });

  test('detalle de solicitud con ofertas (PYME)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token);
    const req = await api.createRequest(pyme.token, {});
    await api.placeBid(carrier.token, req.id, v.id, Math.ceil(req.priceFloor) + 10);
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${req.id}`);
    await expectNoSeriousViolations(page);
  });

  test('cargas disponibles (transportista)', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token);
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/marketplace');
    await expect(page.locator('tbody tr').first()).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('envíos con paradas (transportista)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const v = await api.addVehicle(carrier.token);
    const req = await api.createRequest(pyme.token, {});
    const bid = await api.placeBid(carrier.token, req.id, v.id, Math.ceil(req.priceFloor) + 10);
    await api.acceptBid(pyme.token, bid.id);
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/envios');
    await expectNoSeriousViolations(page);
  });

  test('conductor', async ({ page }) => {
    await setSession(page.context(), await api.login(api.DEMO.driver), BASE);
    await goto(page, '/conductor');
    await expectNoSeriousViolations(page);
  });
});
