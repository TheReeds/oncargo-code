import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pick, setSession, toastWith } from './helpers/ui';

/**
 * Ola 2 · zona F (web): cobros (2.12), reportes (2.11), liquidación imprimible y datos de cobro. Axe en claro y oscuro.
 * Cada test crea sus cuentas por API. Corre con:
 *   E2E_BASE_URL=http://127.0.0.1:4321 E2E_API_URL=http://localhost:8080 npx playwright test ola2-f.spec.ts --workers=1
 */
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

/** Axe en claro y, tras recargar con el tema oscuro, en oscuro. `include` limita el análisis (p. ej. un diálogo abierto). */
async function axeBoth(page: Page, url: string, include?: string) {
  await expectNoSeriousViolations(page, include);
  await page.evaluate(() => { localStorage.setItem('logyx-theme', 'dark'); });
  await goto(page, url);
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expectNoSeriousViolations(page, include);
  await page.evaluate(() => { localStorage.setItem('logyx-theme', 'light'); });
}

const as = async (page: Page, token: string) => { await page.context().clearCookies(); await setSession(page.context(), token, BASE); };
const limaMonth = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit' }).format(new Date()).slice(0, 7);
const CCI = '00219300123456789012';

async function addPayout(token: string, holder = 'Transportes de Prueba SAC') {
  return api.call<any>('POST', '/organizations/me/payout-accounts', token, { kind: 'BANK', bankName: 'BCP', holderName: holder, number: CCI, primary: true });
}

/** Espera a que el cobro del envío entregado exista (se abre al despachar el outbox). */
async function settlementOf(token: string, shipmentId: string) {
  for (let i = 0; i < 30; i++) {
    const r = await api.raw('GET', `/shipments/${shipmentId}/settlement`, token);
    if (r.status === 200) return r.json?.data;
    await new Promise((res) => setTimeout(res, 500));
  }
  throw new Error('El cobro no se abrió');
}

test.describe('Ola 2 · zona F: cobros', () => {
  test('transportista factura, la PYME paga con constancia y el transportista confirma', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await addPayout(sc.carrier.token);
    await settlementOf(sc.carrier.token, sc.shipmentId);

    // Transportista: registra el comprobante con archivo
    await as(page, sc.carrier.token);
    const carrierUrl = `/transportista/envios?id=${sc.shipmentId}`;
    await goto(page, carrierUrl);
    const card = page.getByTestId('settlement-card');
    await expect(card).toContainText('Falta comprobante');
    await expect(card).toContainText('Vence el');
    await expect(card.getByText('Cobras neto')).toBeVisible();
    await card.getByRole('button', { name: 'Registrar comprobante' }).click();
    const dialog = page.getByRole('dialog', { name: 'Registrar comprobante' });
    await dialog.getByRole('button', { name: 'Registrar comprobante' }).click();
    await expect(dialog.getByText('Ingresa el número del comprobante.')).toBeVisible();
    await dialog.getByLabel('Número del comprobante').fill('F001-00231');
    await dialog.locator('input[type=file]').setInputFiles({ name: 'factura.png', mimeType: 'image/png', buffer: api.PNG_1X1 });
    await dialog.getByRole('button', { name: 'Registrar comprobante' }).click();
    await expect(toastWith(page, 'Comprobante registrado')).toBeVisible();
    await expect(card).toContainText('Facturado');
    await expect(page.getByTestId('settlement-invoice')).toContainText('F001-00231');
    await expect(card).toContainText('Desde la factura el precio final ya no cambia');

    // PYME: ve dónde pagar (con botón copiar) y registra el pago
    await as(page, sc.pyme.token);
    await goto(page, `/pyme/envios?id=${sc.shipmentId}`);
    const pcard = page.getByTestId('settlement-card');
    await expect(pcard).toContainText('Facturado');
    await expect(pcard.getByTestId('payout-account')).toContainText('BCP');
    await expect(pcard.getByTestId('payout-account')).toContainText(CCI);
    await expect(pcard.getByRole('button', { name: 'Copiar CCI' })).toBeVisible();
    // La PYME nunca ve la comisión (ADR-0010)
    await expect(pcard).not.toContainText('Comisión');
    await expect(pcard).not.toContainText('Cobras neto');
    await pcard.getByRole('button', { name: 'Registrar pago' }).click();
    const pay = page.getByRole('dialog', { name: 'Registrar pago' });
    await pay.getByRole('button', { name: 'Registrar pago' }).click();
    await expect(pay.getByText('Ingresa el número de operación.')).toBeVisible();
    await pay.getByLabel('Número de operación').fill('00458123');
    await pay.locator('input[type=file]').setInputFiles({ name: 'constancia.png', mimeType: 'image/png', buffer: api.PNG_1X1 });
    await pay.getByRole('button', { name: 'Registrar pago' }).click();
    await expect(toastWith(page, 'Pago registrado')).toBeVisible();
    await expect(pcard).toContainText('Pagado');
    await expect(page.getByTestId('settlement-payment')).toContainText('00458123');

    // La PYME ve el pendiente en «Por pagar» (pagado queda por confirmar)
    await goto(page, '/pyme/pagos');
    await expect(page.getByRole('heading', { name: 'Por pagar', level: 1 })).toBeVisible();
    await expect(page.getByTestId('settlement-row')).toHaveCount(1);
    await expect(page.getByTestId('settlement-row')).toContainText('Pagado');

    // Transportista: confirma el cobro
    await as(page, sc.carrier.token);
    await goto(page, carrierUrl);
    await page.getByTestId('settlement-card').getByRole('button', { name: 'Confirmar cobro' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Confirmar cobro' }).click();
    await expect(toastWith(page, 'Cobro confirmado')).toBeVisible();
    await expect(page.getByTestId('settlement-card')).toContainText('Cobrado');
    await expect(page.getByTestId('settlement-card').getByRole('button', { name: 'Confirmar cobro' })).toHaveCount(0);
  });

  test('disputa de la PYME y resolución del operador', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await settlementOf(sc.carrier.token, sc.shipmentId);
    await api.call('POST', `/shipments/${sc.shipmentId}/settlement/invoice`, sc.carrier.token, { invoiceNumber: 'F001-9', issuedOn: new Date().toISOString().slice(0, 10) });

    await as(page, sc.pyme.token);
    await goto(page, `/pyme/envios?id=${sc.shipmentId}`);
    const card = page.getByTestId('settlement-card');
    await card.getByRole('button', { name: 'Disputar cobro' }).click();
    const dlg = page.getByRole('dialog', { name: 'Disputar el cobro' });
    await dlg.getByLabel('Motivo').fill('El monto facturado no coincide con lo acordado');
    await dlg.getByRole('button', { name: 'Abrir disputa' }).click();
    await expect(toastWith(page, 'Disputa abierta')).toBeVisible();
    await expect(card).toContainText('En disputa');
    await expect(card).toContainText('El monto facturado no coincide');
    await expect(card.getByRole('button', { name: 'Retirar disputa' })).toBeVisible();
    await expect(card.getByRole('button', { name: 'Resolver disputa' })).toHaveCount(0);

    // El operador la resuelve
    await as(page, await api.operatorToken());
    await goto(page, '/operador/cobros');
    await expect(page.getByTestId('disputes-card')).toContainText(sc.pyme.name);
    await goto(page, `/operador/envios/${sc.shipmentId}`);
    const ocard = page.getByTestId('settlement-card');
    await expect(ocard).toContainText('En disputa');
    await ocard.getByRole('button', { name: 'Resolver disputa' }).click();
    const rdlg = page.getByRole('dialog', { name: 'Resolver la disputa' });
    await rdlg.getByRole('button', { name: 'Resolver disputa' }).click();
    await expect(rdlg.getByText(/Indica el motivo/)).toBeVisible();
    await rdlg.getByLabel('Nota de la resolución').fill('Se revisó la factura y el monto es el acordado');
    await rdlg.getByRole('button', { name: 'Resolver disputa' }).click();
    await expect(toastWith(page, 'Disputa resuelta')).toBeVisible();
    await expect(ocard.getByText('Cobro en disputa')).toHaveCount(0);
    await expect(ocard.getByRole('button', { name: 'Resolver disputa' })).toHaveCount(0);
    await expect(ocard.getByRole('heading', { level: 2 })).toContainText('Facturado');
  });

  test('retirar la disputa propia', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await settlementOf(sc.carrier.token, sc.shipmentId);
    await api.call('POST', `/shipments/${sc.shipmentId}/settlement/invoice`, sc.carrier.token, { invoiceNumber: 'F001-10', issuedOn: new Date().toISOString().slice(0, 10) });
    await api.call('POST', `/shipments/${sc.shipmentId}/settlement/dispute`, sc.pyme.token, { reason: 'No recibí la factura' });
    await as(page, sc.pyme.token);
    await goto(page, `/pyme/envios?id=${sc.shipmentId}`);
    const card = page.getByTestId('settlement-card');
    await card.getByRole('button', { name: 'Retirar disputa' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Retirar disputa' }).click();
    await expect(toastWith(page, 'Disputa retirada')).toBeVisible();
    await expect(card).toContainText('Facturado');
  });

  test('el Lector ve el cobro sin botones de escritura y un envío sin entregar no muestra la tarjeta', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await settlementOf(sc.carrier.token, sc.shipmentId);
    const viewer = await api.userWithRole(sc.carrier.token, 'VIEWER');
    await as(page, viewer.token);
    await goto(page, `/transportista/envios?id=${sc.shipmentId}`);
    const card = page.getByTestId('settlement-card');
    await expect(card).toContainText('Falta comprobante');
    await expect(card.getByRole('button', { name: 'Registrar comprobante' })).toHaveCount(0);

    const open = await api.openBidScenario();
    const accepted = await api.acceptBid(open.pyme.token, open.bid.id);
    await as(page, open.pyme.token);
    await goto(page, `/pyme/envios?id=${accepted.shipmentId}`);
    await expect(page.getByTestId('settlement-card')).toHaveCount(0);
  });

  test('listados «Por pagar» y «Por cobrar», filtros en la URL y axe en claro y oscuro', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await addPayout(sc.carrier.token);
    await settlementOf(sc.carrier.token, sc.shipmentId);

    await as(page, sc.pyme.token);
    await goto(page, '/pyme/pagos');
    await expect(page.getByText('Por pagar').first()).toBeVisible();
    await expect(page.getByTestId('settlement-row')).toHaveCount(1);
    await expect(page.getByTestId('settlement-row')).toContainText('Falta comprobante');
    await expect(page.getByRole('columnheader', { name: 'Comisión' })).toHaveCount(0);
    await page.getByRole('button', { name: 'Vencidos' }).click();
    await expect(page).toHaveURL(/estado=VENCIDOS/);
    await expect(page.getByText('No hay cobros con estos filtros')).toBeVisible();
    await axeBoth(page, '/pyme/pagos?estado=VENCIDOS');
    await goto(page, '/pyme/pagos');
    await axeBoth(page, '/pyme/pagos');

    await as(page, sc.carrier.token);
    await goto(page, '/transportista/cobros');
    await expect(page.getByTestId('settlement-row')).toHaveCount(1);
    await expect(page.getByRole('columnheader', { name: 'Comisión', exact: true })).toBeVisible();
    await expect(page.getByText('Comisión de OnCargo').first()).toBeVisible();
    await page.getByRole('link', { name: /Ver envío/ }).first().click();
    await expect(page).toHaveURL(new RegExp(`/transportista/envios\\?id=${sc.shipmentId}`));
    await goto(page, '/transportista/cobros');
    await axeBoth(page, '/transportista/cobros');

    // Diálogos abiertos (axe acotado al diálogo)
    await goto(page, `/transportista/envios?id=${sc.shipmentId}`);
    await page.getByTestId('settlement-card').getByRole('button', { name: 'Registrar comprobante' }).click();
    await expectNoSeriousViolations(page, '[role=dialog]');
    await page.keyboard.press('Escape');
    await axeBoth(page, `/transportista/envios?id=${sc.shipmentId}`, '[data-testid=settlement-card]');
  });

  test('el operador registra el cobro de comisión de un transportista', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await settlementOf(sc.carrier.token, sc.shipmentId);
    const op = await api.operatorToken();
    const OP = `OP-${api.uid()}`;
    await as(page, op);
    await goto(page, '/operador/cobros');
    await expect(page.getByTestId('pending-carrier').filter({ hasText: sc.carrier.name })).toBeVisible();
    await page.getByTestId('pending-carrier').filter({ hasText: sc.carrier.name }).getByRole('button', { name: /Cobrar/ }).click();
    const dlg = page.getByRole('dialog', { name: 'Registrar cobro de comisión' });
    await expect(dlg.getByRole('checkbox')).toHaveCount(1);
    await expect(dlg.getByTestId('commission-total')).not.toHaveText('S/ 0.00');
    await expectNoSeriousViolations(page, '[role=dialog]');
    await dlg.getByRole('button', { name: 'Registrar cobro' }).click();
    await expect(dlg.getByText('Ingresa el número de operación.')).toBeVisible();
    await dlg.getByLabel('Número de operación').fill(OP);
    await dlg.getByRole('button', { name: 'Registrar cobro' }).click();
    await expect(toastWith(page, 'Cobro de comisión registrado')).toBeVisible();
    await expect(page.getByTestId('commission-collection').filter({ hasText: OP })).toBeVisible();
    await expect(page.getByTestId('pending-carrier').filter({ hasText: sc.carrier.name })).toHaveCount(0);

    // El transportista ve el cobro y su comisión como cobrada
    await as(page, sc.carrier.token);
    await goto(page, '/transportista/cobros');
    await expect(page.getByTestId('commission-collection')).toContainText(OP);
    await expect(page.getByTestId('settlement-row')).toContainText('Cobrada');

    await as(page, op);
    await goto(page, '/operador/cobros');
    await axeBoth(page, '/operador/cobros');
  });
});

test.describe('Ola 2 · zona F: reportes y liquidación', () => {
  test('reportes por rol: la PYME no ve la comisión; transportista y operador sí', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await settlementOf(sc.carrier.token, sc.shipmentId);

    await as(page, sc.pyme.token);
    await goto(page, '/pyme/reportes');
    await expect(page.getByRole('heading', { name: 'Reportes', level: 1 })).toBeVisible();
    await expect(page.getByText('Ahorro frente al sugerido')).toBeVisible();
    await expect(page.getByText('IGV', { exact: true }).first()).toBeVisible();
    await expect(page.getByTestId('monthly-bars')).toBeVisible();
    await expect(page.getByTestId('report-total').first()).toBeVisible();
    await expect(page.locator('main')).not.toContainText(/Comisi[oó]n/);
    await expect(page.locator('main')).not.toContainText('Neto');
    await page.getByRole('tab', { name: 'Transportistas' }).click();
    await expect(page.getByTestId('report-row').filter({ hasText: sc.carrier.name })).toBeVisible();
    await axeBoth(page, '/pyme/reportes');

    await as(page, sc.carrier.token);
    await goto(page, '/transportista/reportes');
    await expect(page.getByText('Comisión de OnCargo').first()).toBeVisible();
    await expect(page.getByText('Neto por km').first()).toBeVisible();
    await expect(page.getByText('Ocupación media')).toBeVisible();
    await page.getByRole('tab', { name: 'Vehículos' }).click();
    await expect(page.getByTestId('report-row').filter({ hasText: sc.vehicle.plate })).toBeVisible();
    await page.getByRole('tab', { name: 'Adjudicación' }).click();
    await expect(page.getByText('Tasa de adjudicación').first()).toBeVisible();
    await axeBoth(page, '/transportista/reportes');

    await as(page, await api.operatorToken());
    await goto(page, '/operador/reportes');
    await expect(page.getByText('GMV').first()).toBeVisible();
    await expect(page.getByText('Tomas').first()).toBeVisible();
    await axeBoth(page, '/operador/reportes');
  });

  test('rango de fechas en la URL, exceso de 400 días y exportar CSV', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await as(page, sc.pyme.token);
    const month = limaMonth();
    await goto(page, `/pyme/reportes?desde=${month}-01&hasta=${month}-28`);
    await expect(page.getByTestId('report-row').first()).toBeVisible();
    const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Exportar CSV' }).click()]);
    expect(download.suggestedFilename()).toMatch(/\.csv$/);

    await goto(page, '/pyme/reportes?desde=2024-01-01&hasta=2026-11-01');
    await expect(page.getByText('No pudimos armar el reporte')).toBeVisible();

    await goto(page, '/pyme/reportes?desde=2020-01-01&hasta=2020-01-31');
    await expect(page.getByText('No hay envíos en este período')).toBeVisible();
  });

  test('liquidación imprimible: transportista (comisión) y PYME (resumen de costos), en pantalla y en impresión', async ({ page }) => {
    const sc = await api.deliveredShipment();
    await settlementOf(sc.carrier.token, sc.shipmentId);
    const month = limaMonth();

    await as(page, sc.carrier.token);
    await goto(page, `/reportes/liquidacion/${month}`);
    await expect(page.getByRole('heading', { name: 'Liquidación de comisión', level: 2 })).toBeVisible();
    await expect(page.getByTestId('statement-number')).not.toBeEmpty();
    await expect(page.getByTestId('statement-total')).toBeVisible();
    await expect(page.getByTestId('statement-due')).toBeVisible();
    await expect(page.getByTestId('statement-disclaimer')).toContainText(/comprobante|SUNAT/i);
    await expect(page.getByRole('button', { name: 'Imprimir o guardar PDF' })).toBeVisible();
    await axeBoth(page, `/reportes/liquidacion/${month}`);

    // Impresión: sin menús ni botón, y siempre en claro aunque la pantalla esté en oscuro
    await page.evaluate(() => { localStorage.setItem('logyx-theme', 'dark'); });
    await goto(page, `/reportes/liquidacion/${month}`);
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await page.emulateMedia({ media: 'print' });
    await expect(page.locator('#sidebar')).toBeHidden();
    await expect(page.locator('#app-topbar')).toBeHidden();
    await expect(page.getByRole('button', { name: 'Imprimir o guardar PDF' })).toBeHidden();
    await expect(page.getByTestId('statement-number')).toBeVisible();
    await expect(page.getByTestId('statement-disclaimer')).toBeVisible();
    const surface = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--color-surface').trim());
    expect(surface).toBe('#ffffff');
    const ink = await page.getByTestId('statement-number').evaluate((el) => getComputedStyle(el).color);
    expect(ink).toBe('rgb(0, 0, 0)');
    await page.emulateMedia({ media: 'screen' });
    await page.evaluate(() => { localStorage.setItem('logyx-theme', 'light'); });

    // PYME: resumen de costos, sin comisión
    await as(page, sc.pyme.token);
    await goto(page, `/reportes/liquidacion/${month}`);
    await expect(page.getByRole('heading', { name: 'Resumen de costos', level: 2 })).toBeVisible();
    await expect(page.locator('article')).not.toContainText(/Comisi[oó]n/);
    await expect(page.locator('article')).toContainText(sc.carrier.name);
    await axeBoth(page, `/reportes/liquidacion/${month}`);

    // Desde el reporte, el enlace del mes lleva al documento
    await goto(page, '/pyme/reportes');
    await page.getByRole('link', { name: /Resumen de/ }).first().click();
    await expect(page).toHaveURL(/\/reportes\/liquidacion\/\d{4}-\d{2}/);

    // Operador: pide la liquidación de un transportista con ?org=
    const orgId = await api.orgIdOf(sc.carrier.token);
    await as(page, await api.operatorToken());
    await goto(page, `/reportes/liquidacion/${month}?org=${orgId}`);
    await expect(page.getByRole('heading', { name: 'Liquidación de comisión', level: 2 })).toBeVisible();
    await expect(page.locator('article')).toContainText(sc.carrier.name);

    // Mes futuro o mal escrito
    await as(page, sc.pyme.token);
    await page.goto('/reportes/liquidacion/2099-01');
    await expect(page.getByText('No pudimos armar el documento')).toBeVisible();
  });
});

test.describe('Ola 2 · zona F: datos de cobro', () => {
  test('el administrador agrega, marca como principal y archiva; el Lector solo ve los últimos dígitos', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await as(page, carrier.token);
    await goto(page, '/organizacion');
    await page.getByTestId('org-payout-link').click();
    await expect(page).toHaveURL(/\/organizacion\/datos-de-cobro/);
    await expect(page.getByText('Aún no cargas datos de cobro')).toBeVisible();

    await page.getByRole('button', { name: 'Agregar dato de cobro' }).first().click();
    const dlg = page.getByRole('dialog', { name: 'Agregar dato de cobro' });
    await dlg.getByRole('button', { name: 'Agregar dato de cobro' }).click();
    await expect(dlg.getByText('Ingresa el banco.')).toBeVisible();
    await dlg.getByLabel('Banco').fill('BCP');
    await dlg.getByLabel('Titular').fill('Transportes de Prueba SAC');
    await dlg.getByLabel('CCI').fill('123');
    await dlg.getByRole('button', { name: 'Agregar dato de cobro' }).click();
    await expect(dlg.getByText('El CCI tiene 20 dígitos.')).toBeVisible();
    await dlg.getByLabel('CCI').fill(CCI);
    await dlg.getByRole('button', { name: 'Agregar dato de cobro' }).click();
    await expect(toastWith(page, 'Dato de cobro agregado')).toBeVisible();
    await expect(page.getByTestId('payout-row')).toHaveCount(1);
    await expect(page.getByTestId('payout-row')).toContainText('Principal');

    // Yape (celular de 9 dígitos), luego principal
    await page.getByRole('button', { name: 'Agregar dato de cobro' }).first().click();
    const d2 = page.getByRole('dialog', { name: 'Agregar dato de cobro' });
    await pick(page, d2.getByRole('combobox', { name: 'Tipo' }), 'Yape');
    await d2.getByLabel('Titular').fill('Ana Quispe');
    await d2.getByLabel('Celular').fill('987654321');
    await d2.getByRole('button', { name: 'Agregar dato de cobro' }).click();
    await expect(page.getByTestId('payout-row')).toHaveCount(2);
    const yape = page.getByTestId('payout-row').filter({ hasText: 'Yape' });
    await yape.getByRole('button', { name: 'Marcar como principal' }).click();
    await expect(toastWith(page, 'Cuenta principal actualizada')).toBeVisible();
    await expect(yape).toContainText('Principal');
    await axeBoth(page, '/organizacion/datos-de-cobro');

    await page.getByTestId('payout-row').filter({ hasText: 'BCP' }).getByRole('button', { name: /Archivar/ }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Archivar' }).click();
    await expect(toastWith(page, 'Dato de cobro archivado')).toBeVisible();
    await expect(page.getByTestId('payout-row')).toHaveCount(1);

    // Lector: números enmascarados y sin botones de escritura
    const viewer = await api.userWithRole(carrier.token, 'VIEWER');
    await as(page, viewer.token);
    await goto(page, '/organizacion/datos-de-cobro');
    await expect(page.getByTestId('payout-row')).toContainText('•••• 4321');
    await expect(page.getByRole('button', { name: 'Agregar dato de cobro' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Archivar/ })).toHaveCount(0);
  });

  test('la PYME no tiene esta página', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await as(page, pyme.token);
    const res = await page.goto('/organizacion/datos-de-cobro');
    expect(res?.status()).toBe(403);
  });
});
