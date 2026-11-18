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

test.describe('Accesibilidad de viajes, retornos y Smart Load (axe)', () => {
  test('transportista: viajes, formulario con errores y detalle con reservas', async ({ page }) => {
    const sc = await api.tripScenario();
    await api.bookTrip(sc.pyme.token, sc.trip.id);
    await setSession(page.context(), sc.carrier.token, BASE);
    await goto(page, '/transportista/viajes');
    await expect(page.getByRole('row', { name: /Abancay/ })).toBeVisible();
    await page.screenshot({ path: 'docs/screenshots/viajes-lista.png', fullPage: true });
    await expectNoSeriousViolations(page);
    await goto(page, '/transportista/viajes/nuevo');
    await page.getByRole('button', { name: 'Publicar viaje' }).click();
    await expect(page.getByText('Selecciona la ciudad de origen.')).toBeVisible();
    await expectNoSeriousViolations(page);
    await goto(page, `/transportista/viajes/${sc.trip.id}`);
    await expect(page.getByTestId('booking-row')).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByRole('button', { name: /Rechazar reserva/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expectNoSeriousViolations(page);
  });

  test('transportista: panel, retornos y Smart Load', async ({ page }) => {
    const token = await api.login(api.DEMO.carrier);
    await setSession(page.context(), token, BASE);
    await goto(page, '/transportista');
    await expectNoSeriousViolations(page);
    await goto(page, '/transportista/retornos');
    await expect(page.getByTestId('return-load').first()).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByTestId('return-load').first().getByRole('button', { name: /Ofertar/ }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expectNoSeriousViolations(page);
    await goto(page, '/transportista/smart-load');
    await expectNoSeriousViolations(page);
  });

  test('PYME: buscar viajes, reservar y mis reservas', async ({ page }) => {
    const sc = await api.tripScenario();
    await api.bookTrip(sc.pyme.token, sc.trip.id);
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/viajes?origen=ABANCAY&destino=AYACUCHO&desde=${sc.date}`);
    await expect(page.getByTestId('trip-row').first()).toBeVisible();
    await expectNoSeriousViolations(page);
    await page.getByTestId('trip-row').first().getByRole('button', { name: /Reservar espacio/ }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Reservar espacio' }).click();
    await expect(page.getByText('Ingresa el peso que quieres enviar en kg.')).toBeVisible();
    await expectNoSeriousViolations(page);
    await goto(page, '/pyme/reservas');
    await expect(page.getByTestId('booking-card')).toBeVisible();
    await expectNoSeriousViolations(page);
    await goto(page, '/pyme');
    await expectNoSeriousViolations(page);
  });
});
