import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, hydrated, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

/** Auditoría del operador (V15), ofertas en el detalle de la solicitud y KPI de GMV. */
test.describe('Operador: auditoría y lecturas', () => {
  test('visor de auditoría: filtros en la URL, detalle, seguir la pista y paginación', async ({ page }) => {
    const sc = await api.openBidScenario();
    const op = await api.operatorToken();
    await setSession(page.context(), op, BASE);
    await goto(page, '/operador/auditoria');
    await expect(page.getByRole('heading', { name: 'Auditoría' })).toBeVisible();
    await expect(page.getByTestId('audit-row').first()).toBeVisible();
    await expect(page.getByRole('link', { name: 'Auditoría' })).toHaveAttribute('aria-current', 'page');

    // Acción (combobox con la lista de la API) → URL
    await page.getByRole('combobox', { name: 'Acción' }).click();
    await page.getByRole('option', { name: 'Solicitud publicada' }).click();
    await page.keyboard.press('Escape');
    await expect(page).toHaveURL(/accion=REQUEST_PUBLISHED/);
    await hydrated(page);
    const rows = page.getByTestId('audit-row');
    await expect(rows.first()).toBeVisible();
    for (const r of await rows.all()) await expect(r).toContainText('Solicitud publicada');

    // Rango de fechas (hoy)
    await page.getByRole('button', { name: 'Fechas' }).click();
    await page.getByRole('button', { name: 'Hoy', exact: true }).click();
    await expect(page).toHaveURL(/desde=\d{4}-\d{2}-\d{2}/);
    await hydrated(page);
    await expect(rows.first()).toBeVisible();

    // Detalle de la fila de esta solicitud (por entidad) y enlace a la solicitud
    await page.goto(`/operador/auditoria?accion=REQUEST_PUBLISHED&entidad=shipment_request&entidadId=${sc.request.id}`);
    await hydrated(page);
    await expect(rows).toHaveCount(1);
    await expect(page.getByLabel('Filtros activos')).toContainText('Entidad');
    await rows.first().getByRole('button', { name: /Ver detalle/ }).click();
    const sheet = page.getByRole('dialog');
    await expect(sheet.getByTestId('audit-detail')).toContainText(sc.pyme.email);
    await expect(sheet.getByTestId('audit-detail')).toContainText('Detalle');
    await expect(sheet.getByRole('link', { name: /Abrir solicitud/ })).toHaveAttribute('href', `/operador/solicitudes/${sc.request.id}`);
    await expect(sheet.getByRole('link', { name: 'Ver perfil de la organización' })).toBeVisible();

    // Seguir la pista de un actor: filtro por actor y chip removible
    await sheet.getByRole('button', { name: 'Ver más de este actor' }).click();
    await expect(page).toHaveURL(/actor=[0-9a-f-]{36}/);
    await hydrated(page);
    await expect(page.getByLabel('Filtros activos')).toContainText('Actor');
    for (const r of await rows.all()) await expect(r).toContainText(sc.pyme.email);
    await page.getByLabel('Filtros activos').getByRole('button', { name: /Quitar filtro/ }).first().click();
    await expect(page).not.toHaveURL(/actor=/);

    // Sin resultados y limpiar filtros
    await page.goto('/operador/auditoria?desde=2020-01-01&hasta=2020-01-02');
    await hydrated(page);
    await expect(page.getByText('No hay registros con estos filtros')).toBeVisible();
    await page.getByRole('button', { name: 'Limpiar filtros' }).first().click();
    await expect(page).toHaveURL(/\/operador\/auditoria$/);
    await hydrated(page);

    // Paginación en el servidor
    await page.getByRole('button', { name: 'Página 2' }).click();
    await expect(page).toHaveURL(/pagina=2/);
    await hydrated(page);
    await expect(page.getByRole('button', { name: 'Página 2' })).toHaveAttribute('aria-current', 'page');
    await expect(rows.first()).toBeVisible();
  });

  test('solo el operador entra a la auditoría', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    const res = await page.goto('/operador/auditoria');
    expect(res?.status()).toBe(403);
    await expect(page.getByTestId('forbidden')).toBeVisible();
    expect((await api.raw('GET', '/operator/audit', pyme.token)).status).toBe(403);
  });

  test('detalle de solicitud del operador lista las ofertas; el panel muestra el GMV', async ({ page }) => {
    const sc = await api.openBidScenario(2400);
    const op = await api.operatorToken();
    await setSession(page.context(), op, BASE);
    await goto(page, `/operador/solicitudes/${sc.request.id}`);
    await expect(page.getByRole('heading', { name: /Ofertas recibidas/ })).toBeVisible();
    const bids = page.getByTestId('operator-bid-row');
    await expect(bids).toHaveCount(1);
    await expect(bids.first()).toContainText(sc.carrier.name.replace(/ SAC$/, ''));
    await expect(bids.first()).toContainText('Pendiente');
    await expect(bids.first()).toContainText(sc.vehicle.plate);

    await goto(page, '/operador');
    const metrics = await api.call<any>('GET', '/operator/metrics', op);
    expect(metrics.gmv).toBeGreaterThan(0);
    await expect(page.getByText('GMV contratado')).toBeVisible();
    await expect(page.getByText('GMV entregado')).toBeVisible();
    // Sin KPI duplicado de "adjudicadas": solo aparece como contexto de "Solicitudes abiertas"
    await expect(page.getByText('Adjudicadas', { exact: true })).toHaveCount(0);
  });
});

for (const theme of ['light', 'dark'] as const) {
  test.describe(`Accesibilidad auditoría del operador (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    });
    test('auditoría (lista y detalle), solicitud con ofertas y panel', async ({ page }) => {
      const sc = await api.openBidScenario();
      const op = await api.operatorToken();
      await setSession(page.context(), op, BASE);
      await goto(page, '/operador/auditoria');
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/operador-auditoria-${theme}.png`, fullPage: true });
      await page.getByTestId('audit-row').first().getByRole('button', { name: /Ver detalle/ }).click();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.keyboard.press('Escape');
      await goto(page, `/operador/solicitudes/${sc.request.id}`);
      await expectNoSeriousViolations(page);
      await goto(page, '/operador');
      await expectNoSeriousViolations(page);
    });
  });
}
