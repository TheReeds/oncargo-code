import { test, expect, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { stringify } from 'devalue';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

async function expectNoSeriousViolations(page: Page) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
}

test.describe('Accesibilidad operador (axe)', () => {
  test('panel de operación y sheet de asignación', async ({ page, context }) => {
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token, 30000, 'TRUCK_LARGE');
    const op = await api.operatorToken();
    await setSession(context, op, BASE);
    await goto(page, '/operador');
    await expect(page.getByText('Solicitudes abiertas')).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/operador-panel.png`, fullPage: true });

    const btn = page.getByRole('button', { name: /Asignar transportista/ }).first();
    test.skip(!(await btn.count()), 'No hay solicitudes asignables (SLA no vencido).');
    await btn.click();
    const sheet = page.getByRole('dialog', { name: 'Asignar transportista' });
    await expect(sheet.getByRole('radio').first()).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('estado vacío positivo', async ({ page, context }) => {
    // Con SLA real no hay alertas siempre: se simula la respuesta de la lectura de refresco (action) y se pulsa Actualizar.
    const op = await api.operatorToken();
    await setSession(context, op, BASE);
    await goto(page, '/operador');
    const metrics = await api.call('GET', '/operator/metrics', op);
    await page.route('**/_actions/operator.overview/**', (r) =>
      r.fulfill({ contentType: 'application/json+devalue', body: stringify({ metrics, alerts: { slaHours: 24, expiringWindowMinutes: 10, requestsWithoutBids: [], expiringAuctions: [], shipmentsWithIncidents: [] } }) }));
    await page.getByRole('button', { name: 'Actualizar' }).click();
    await expect(page.getByText('Todo en orden: no hay solicitudes con SLA vencido.')).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/operador-vacio.png`, fullPage: true });
  });
});
