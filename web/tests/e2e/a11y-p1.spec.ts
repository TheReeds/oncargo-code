import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

/** Pantallas nuevas de la tanda "huecos P1" (verificación, negociación, envío, Smart Load) en claro y oscuro. */
for (const theme of ['light', 'dark'] as const) {
  test.describe(`Accesibilidad huecos P1 (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    });

    test('bandeja de verificación del operador: listado, detalle y diálogo con motivo', async ({ page }) => {
      const op = await api.operatorToken();
      await api.registerAccount('PYME'); // garantiza al menos una pendiente
      await setSession(page.context(), op, BASE);
      await goto(page, '/operador/organizaciones');
      await expect(page.getByRole('heading', { name: 'Verificación de empresas' })).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/operador-organizaciones-${theme}.png`, fullPage: true });
      await page.getByTestId('org-row').first().getByRole('button', { name: /Revisar/ }).click();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.getByRole('button', { name: 'Rechazar verificación' }).click();
      await expect(page.getByRole('dialog', { name: /Rechazar la verificación/ })).toBeVisible();
      await expectNoSeriousViolations(page, '[role=dialog]');
    });

    test('banners de verificación (en revisión y suspendida)', async ({ page }) => {
      const op = await api.operatorToken();
      const pyme = await api.registerAccount('PYME');
      await setSession(page.context(), pyme.token, BASE);
      await goto(page, '/pyme');
      await expect(page.getByText('Estamos verificando tu empresa')).toBeVisible();
      await expectNoSeriousViolations(page);
      await api.suspendOrg(op, await api.orgIdOf(pyme.token), 'Documentos vencidos');
      await goto(page, '/pyme');
      await expect(page.getByText('Tu empresa está suspendida')).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/verificacion-banner-${theme}.png` });
    });

    test('negociación: contraoferta del transportista, propuesta recibida y oferta retirada', async ({ page, browser }) => {
      const sc = await api.openBidScenario(6000);
      await api.counterOffer(sc.carrier.token, sc.bid.id, Math.ceil(sc.request.priceFloor) + 20);
      await setSession(page.context(), sc.pyme.token, BASE);
      await goto(page, `/pyme/solicitudes/${sc.request.id}`);
      await expect(page.getByText(/te propone un nuevo monto/)).toBeVisible();
      await expectNoSeriousViolations(page);
      const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE', viewport: { width: 1440, height: 900 } });
      await ctx.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
      const cpage = await ctx.newPage();
      await setSession(ctx, sc.carrier.token, BASE);
      await goto(cpage, `/transportista/ofertas?solicitud=${sc.request.id}`);
      const sheet = cpage.getByRole('dialog', { name: 'Negociar oferta' });
      await expect(sheet.getByText('Tu contra-oferta espera la respuesta de la PYME')).toBeVisible();
      await expectNoSeriousViolations(cpage, '[role=dialog]');
      await ctx.close();
    });

    test('envío: acciones y diálogos (cancelar, cambiar vehículo, relevo)', async ({ page }) => {
      const sc = await api.openBidScenario(5000);
      await api.addVehicle(sc.carrier.token);
      await api.addDriver(sc.carrier.token);
      const shipmentId = (await api.acceptBid(sc.pyme.token, sc.bid.id)).shipmentId as string;
      await setSession(page.context(), sc.carrier.token, BASE);
      await goto(page, `/transportista/envios?id=${shipmentId}`);
      await expect(page.getByRole('region', { name: 'Acciones del envío' })).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.getByRole('button', { name: 'Cambiar vehículo' }).click();
      await expect(page.getByRole('dialog', { name: 'Cambiar el vehículo del envío' }).getByRole('combobox', { name: 'Nuevo vehículo' })).toBeEnabled();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.keyboard.press('Escape');
      await page.getByRole('button', { name: 'Asignar conductor' }).click();
      await expect(page.getByRole('dialog', { name: 'Asignar conductor' }).getByRole('combobox', { name: 'Conductor' })).toBeEnabled();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.keyboard.press('Escape');
      await page.getByRole('button', { name: 'Cancelar envío' }).click();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.screenshot({ path: `${SHOTS}/envio-acciones-${theme}.png` });
    });

    test('Smart Load: diálogo de ofertas por carga', async ({ page }) => {
      const [pyme, carrier] = await Promise.all([api.registerAccount('PYME'), api.registerAccount('CARRIER')]);
      await api.addVehicle(carrier.token, 20000, 'TRUCK_LARGE');
      await api.createRequest(pyme.token, { weightKg: 7500, pickupDate: api.isoDate(5) });
      await api.createRequest(pyme.token, { destinationCity: 'PUNO', weightKg: 6000, pickupDate: api.isoDate(5) });
      await setSession(page.context(), carrier.token, BASE);
      await goto(page, '/transportista/smart-load');
      await page.getByRole('button', { name: 'Generar sugerencias' }).click();
      await page.getByTestId('smart-load').first().getByRole('button', { name: /Ofertar este plan/ }).click();
      const dlg = page.getByRole('dialog', { name: 'Ofertar este plan' });
      await expect(dlg.getByRole('textbox', { name: /Tu oferta/ }).first()).toBeVisible();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.screenshot({ path: `${SHOTS}/smart-load-dialogo-${theme}.png` });
    });
  });
}
