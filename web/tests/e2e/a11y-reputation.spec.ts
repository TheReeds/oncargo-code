import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4332';

async function expectNoSeriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
}

test.describe('Accesibilidad reputación y documentos (axe)', () => {
  test('envío entregado: documentos, calificación y visor', async ({ page }) => {
    const s = await api.deliveredShipment();
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${s.shipmentId}`);
    await expect(page.getByText('Evidencia de entrega')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('button', { name: /Ver evidencia\.png/ }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.keyboard.press('Escape');

    await page.getByRole('tab', { name: 'Calificación' }).click();
    await expect(page.getByText('Califica este envío')).toBeVisible();
    await page.getByRole('button', { name: 'Enviar calificación' }).click();
    await expect(page.getByText('Califica la puntualidad.')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('perfil público de organización', async ({ page }) => {
    const s = await api.deliveredShipment();
    const org = await api.me(s.carrier.token);
    await api.call('POST', `/shipments/${s.shipmentId}/reviews`, s.pyme.token, { punctuality: 5, cargoCondition: 4, communication: 5, comment: 'Excelente' });
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, `/organizaciones/${org.organizationId}`);
    await expect(page.getByRole('meter', { name: 'Trust Score' })).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('catálogo de componentes (dev)', async ({ page }) => {
    await goto(page, '/dev/ui');
    await expect(page.getByRole('heading', { name: 'Reputación y archivos' })).toBeVisible();
  });
});
