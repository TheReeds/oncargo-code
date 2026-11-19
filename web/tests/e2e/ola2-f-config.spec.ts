import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pickDay, setSession } from './helpers/ui';

// Ola 2 zona F (V24, 2.14): tarifario versionado, parámetros de subasta y piso, y calendario de feriados y paros.
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const THEMES = ['light', 'dark'] as const;

async function withTheme(page: Page, theme: (typeof THEMES)[number]) {
  await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
}

test.describe('Ola 2 zona F · tarifario, parámetros y feriados', () => {
  for (const theme of THEMES) {
    test(`el operador ve el tarifario, los parámetros nuevos y el calendario (${theme})`, async ({ page }) => {
      await withTheme(page, theme);
      const op = await api.operatorToken();
      await setSession(page.context(), op, BASE);
      await goto(page, '/operador/parametros');
      await expect(page.getByRole('heading', { name: 'Tarifario', level: 2 })).toBeVisible();
      await expect(page.getByTestId('tariff-current')).toContainText('Combustible por km');
      await expect(page.getByTestId('param-auction_max_bids').getByTestId('param-value')).toHaveText(/\d+ ofertas?/);
      await expect(page.getByTestId('param-auction_duration_minutes').getByTestId('param-value')).toHaveText(/\d+ (min|h)/);
      await expect(page.getByTestId('param-payment_terms_days').getByTestId('param-value')).toHaveText(/\d+ días?/);
      await expect(page.getByRole('heading', { name: 'Feriados y paros', level: 2 })).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page, 'main');
    });
  }

  test('programar y anular una versión del tarifario sin cambiar la vigente', async ({ page }) => {
    const op = await api.operatorToken();
    const before = await api.call<any>('GET', '/pricing/tariffs/current', op);
    await setSession(page.context(), op, BASE);
    await goto(page, '/operador/parametros');
    await page.getByTestId('tariff-current').getByRole('button', { name: 'Nueva versión' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Tarifa base').fill('35');
    await dialog.getByLabel('Desde un día (00:00, hora de Lima)').click();
    await dialog.getByLabel('Motivo del cambio').fill('Prueba e2e de versión programada');
    await dialog.getByRole('button', { name: 'Programar versión' }).click();
    await expect(dialog.getByText('Elige desde qué día rige.')).toBeVisible();
    await dialog.getByLabel('Rige desde').click();
    await pickDay(page, api.isoDate(20));
    await dialog.getByRole('button', { name: 'Programar versión' }).click();
    const row = page.getByTestId('tariff-row').filter({ hasText: 'Prueba e2e de versión programada' }).first();
    await expect(row).toContainText('Programada', { timeout: 15_000 });
    const after = await api.call<any>('GET', '/pricing/tariffs/current', op);
    expect(after.version).toBe(before.version);

    await row.getByRole('button', { name: /Anular/ }).click();
    const cancel = page.getByRole('dialog');
    await cancel.getByLabel('Motivo').fill('Era una prueba e2e');
    await cancel.getByRole('button', { name: 'Anular versión' }).click();
    await expect(page.getByTestId('tariff-row').filter({ hasText: 'Prueba e2e de versión programada' }).first())
      .toContainText('Anulada', { timeout: 15_000 });
  });

  test('un paro del día se ve como aviso global y el operador lo quita', async ({ page }) => {
    const op = await api.operatorToken();
    const name = `Paro e2e ${api.uid()}`;
    const day = await api.call<any>('POST', '/catalog/calendar', op, { day: api.isoDate(0), kind: 'STRIKE', name, cities: ['PUNO'] });
    try {
      const pyme = await api.registerAccount('PYME');
      await setSession(page.context(), pyme.token, BASE);
      await goto(page, '/pyme');
      const banner = page.getByTestId('calendar-banner');
      await expect(banner).toContainText(name);
      await expect(banner).toContainText('Puno');
      await expectNoSeriousViolations(page, 'main');

      await setSession(page.context(), op, BASE);
      await goto(page, '/operador/parametros#calendario');
      const row = page.getByTestId('calendar-row').filter({ hasText: name });
      await row.getByRole('button', { name: /Quitar/ }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByLabel('Motivo').fill('Se levantó el paro');
      await dialog.getByRole('button', { name: 'Quitar' }).click();
      await expect(page.getByTestId('calendar-row').filter({ hasText: name })).toHaveCount(0, { timeout: 15_000 });
    } catch (e) {
      await api.call('DELETE', `/catalog/calendar/${day.id}?reason=${encodeURIComponent('Limpieza e2e')}`, op).catch(() => {});
      throw e;
    }
  });

  test('agregar un feriado regional desde el calendario', async ({ page }) => {
    const op = await api.operatorToken();
    await setSession(page.context(), op, BASE);
    await goto(page, '/operador/parametros#calendario');
    await page.getByRole('button', { name: 'Agregar día' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: 'Agregar' }).click();
    await expect(dialog.getByText('Elige la fecha.')).toBeVisible();
    const name = `Feriado e2e ${api.uid()}`;
    await dialog.getByLabel('Fecha').click();
    await pickDay(page, api.isoDate(25));
    await dialog.getByLabel('Nombre').fill(name);
    await dialog.getByRole('button', { name: 'Agregar' }).click();
    const row = page.getByTestId('calendar-row').filter({ hasText: name });
    await expect(row).toBeVisible({ timeout: 15_000 });
    await expect(row).toContainText('Todo el país');
    const list = await api.call<any[]>('GET', `/catalog/calendar?from=${api.isoDate(0)}&to=${api.isoDate(400)}`, op);
    const created = list.find((d) => d.name === name);
    if (created) await api.call('DELETE', `/catalog/calendar/${created.id}?reason=${encodeURIComponent('Limpieza e2e')}`, op);
  });
});
