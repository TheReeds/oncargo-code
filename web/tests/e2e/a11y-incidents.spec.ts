import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

async function expectNoSeriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(" ") + " " + n.html.slice(0,160) + " " + (n.any[0]?.message ?? "")).join(" | ")}`)).toEqual([]);
}

test.describe('Accesibilidad incidencias y notificaciones (axe)', () => {
  test('PYME: lista, detalle, diálogo de reporte, notificaciones y popover', async ({ page, context }) => {
    const sc = await api.openBidScenario();
    const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
    const inc = await api.reportIncident(sc.pyme.token, accepted.shipmentId);
    await setSession(context, sc.pyme.token, BASE);

    await goto(page, '/incidencias');
    await expect(page.locator('tbody tr').first()).toBeVisible();
    await expectNoSeriousViolations(page);

    await goto(page, `/incidencias/${inc.id}`);
    await expect(page.getByText('Historial y comentarios')).toBeVisible();
    await expectNoSeriousViolations(page);

    await goto(page, '/notificaciones');
    await expect(page.locator('main ul.divide-y > li').first()).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByRole('button', { name: /^Notificaciones/ }).click();
    await expect(page.getByRole('dialog', { name: 'Notificaciones' })).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('PYME: diálogo Reportar incidencia', async ({ page, context }) => {
    const sc = await api.openBidScenario();
    const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
    await setSession(context, sc.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${accepted.shipmentId}`);
    await page.getByRole('tab', { name: 'Incidencias' }).click();
    await page.getByRole('button', { name: 'Reportar incidencia' }).click();
    await expect(page.getByRole('dialog', { name: 'Reportar incidencia' })).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('operador: bandeja y gestión', async ({ page, context }) => {
    const sc = await api.openBidScenario();
    const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
    const inc = await api.reportIncident(sc.carrier.token, accepted.shipmentId);
    await setSession(context, await api.operatorToken(), BASE);
    await goto(page, '/operador/incidencias');
    await expect(page.locator('tbody tr').first()).toBeVisible();
    await expectNoSeriousViolations(page);
    await goto(page, `/incidencias/${inc.id}`);
    await expect(page.getByRole('button', { name: 'Resolver incidencia' })).toBeVisible();
    await expectNoSeriousViolations(page);
  });
});
