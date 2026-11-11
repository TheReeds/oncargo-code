import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

async function expectNoSeriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
}

test.describe('Accesibilidad de la negociación (axe)', () => {
  test('detalle PYME con panel de conversación y contra-oferta', async ({ page }) => {
    const sc = await api.openBidScenario();
    await api.call('POST', `/shipment-requests/${sc.request.id}/threads/${sc.bid.carrierOrgId}/messages`, sc.pyme.token, { body: 'Hola, ¿puedes cargar el mismo día?' });
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);
    await expect(page.getByRole('log')).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByRole('button', { name: /Contraofertar/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('sheet de negociación del transportista', async ({ page }) => {
    const sc = await api.openBidScenario(6000);
    await api.call('POST', `/bids/${sc.bid.id}/counter-offers`, sc.pyme.token, { amount: 5000, message: 'Cerramos hoy' });
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, `/transportista/ofertas?solicitud=${sc.request.id}`);
    await expect(page.getByRole('dialog').getByText('La PYME te propuso un nuevo monto')).toBeVisible();
    await expectNoSeriousViolations(page);
  });
});
