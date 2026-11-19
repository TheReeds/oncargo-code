import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pick, setSession, toastWith, pickDay } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

/** Ola 2, zona P: plantillas y cargas recurrentes (2.1), preferidos/bloqueados y segunda oferta (2.9), bandeja de ofertas y acciones masivas (2.10). */
for (const theme of ['light', 'dark'] as const) {
  test.describe(`Zona P · demanda (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    });

    test('plantillas: guardar desde una solicitud, programar y publicar ahora', async ({ page }) => {
      const pyme = await api.registerAccount('PYME');
      const req = await api.createRequest(pyme.token, { pickupDate: api.isoDate(4), cargoDescription: 'Quinua en sacos de 50 kg', weightKg: 3000 });
      await setSession(page.context(), pyme.token, BASE);

      // Vacío
      await goto(page, '/pyme/plantillas');
      await expect(page.getByText('Aún no tienes plantillas')).toBeVisible();
      await expectNoSeriousViolations(page);

      // Guardar como plantilla desde el detalle
      await goto(page, `/pyme/solicitudes/${req.id}`);
      await page.getByRole('button', { name: 'Guardar como plantilla' }).click();
      const dlg = page.getByRole('dialog', { name: 'Guardar como plantilla' });
      await expect(dlg).toBeVisible();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await dlg.getByRole('button', { name: 'Guardar plantilla' }).click();         // el nombre sugerido es la ruta
      await expect(toastWith(page, 'Plantilla guardada')).toBeVisible();

      await goto(page, '/pyme/plantillas');
      const row = page.getByRole('row', { name: /Lima a Juliaca|Lima → Juliaca/ });
      await expect(row).toBeVisible();
      await expect(row.getByText('Sin programar')).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/plantillas-${theme}.png`, fullPage: true });

      // Programar: todos los lunes, 2 días antes
      await row.getByRole('button', { name: /Más acciones de/ }).click();
      await page.getByRole('menuitem', { name: 'Programar' }).click();
      const sched = page.getByRole('dialog', { name: /Programar/ });
      await expect(sched.getByText(/Primer recojo:/)).toBeVisible();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.screenshot({ path: `${SHOTS}/plantillas-programar-${theme}.png` });
      await sched.getByRole('button', { name: 'Programar plantilla' }).click();
      await expect(toastWith(page, 'Plantilla programada')).toBeVisible();
      await expect(page.getByRole('row', { name: /Publica sola/ })).toBeVisible();
      await expect(page.getByText(/Todos los lunes · Se publica 2 días antes/)).toBeVisible();

      // Pausar y reanudar
      await page.getByRole('button', { name: /Más acciones de/ }).click();
      await page.getByRole('menuitem', { name: 'Pausar' }).click();
      await expect(page.getByText('Pausada', { exact: true })).toBeVisible();
      await page.getByRole('button', { name: /Más acciones de/ }).click();
      await page.getByRole('menuitem', { name: 'Reanudar' }).click();
      await expect(page.getByText('Publica sola')).toBeVisible();

      // Publicar ahora
      await page.getByRole('button', { name: /Publicar ahora/ }).click();
      const pub = page.getByRole('dialog', { name: 'Publicar solicitud' });
      await pub.getByRole('button', { name: 'Publicar solicitud' }).click();
      await expect(pub.getByText('Selecciona la fecha de recojo.')).toBeVisible();
      await pub.getByRole('button', { name: 'Fecha de recojo' }).click();
      await pickDay(page, api.isoDate(6));
      await pub.getByRole('button', { name: 'Publicar solicitud' }).click();
      await expect(page).toHaveURL(/\/pyme\/solicitudes\/(?!nueva)[0-9a-f-]{36}/);
      const mine = await api.call<any[]>('GET', '/shipment-requests/mine', pyme.token);
      expect(mine).toHaveLength(2);
      expect(mine.some((r) => r.id !== req.id && r.pickupDate === api.isoDate(6) && r.customerReference == null)).toBe(true);
    });

    test('abrir una plantilla en el formulario y actualizarla', async ({ page }) => {
      const pyme = await api.registerAccount('PYME');
      const tpl = await api.call<any>('POST', '/request-templates', pyme.token, {
        name: 'Tela Lima Juliaca', originCity: 'LIMA', originAddress: 'Av. Argentina 1234, Callao', destinationCity: 'JULIACA', destinationAddress: 'Jr. Lima 456, Juliaca',
        cargoType: 'GENERAL', cargoDescription: 'Rollos de tela', weightKg: 2500,
      });
      await setSession(page.context(), pyme.token, BASE);
      await goto(page, `/pyme/solicitudes/nueva?plantilla=${tpl.id}`);
      await expect(page.getByRole('heading', { name: 'Publicar desde plantilla' })).toBeVisible();
      await expect(page.getByText('Plantilla cargada')).toBeVisible();
      await expect(page.getByLabel('Descripción de la carga')).toHaveValue('Rollos de tela');
      await expect(page.getByText('Precio sugerido', { exact: true })).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.getByLabel('Descripción de la carga').fill('Rollos de tela, 200 unidades');
      // El botón queda bajo la barra de `astro dev`: se dispara el clic sin pasar por el puntero.
      await page.getByRole('button', { name: 'Actualizar plantilla' }).dispatchEvent('click');
      await expect(toastWith(page, 'Plantilla actualizada')).toBeVisible();
      const after = await api.call<any>('GET', `/request-templates/${tpl.id}`, pyme.token);
      expect(after.cargoDescription).toBe('Rollos de tela, 200 unidades');
    });

    test('bandeja de ofertas: la mejor por monto y por Trust, y «Aceptar la mejor» con confirmación', async ({ page }) => {
      const sc = await api.openBidScenario(2300);
      await setSession(page.context(), sc.pyme.token, BASE);
      await goto(page, '/pyme/ofertas');
      await expect(page.getByRole('heading', { name: 'Ofertas por revisar' })).toBeVisible();
      const row = page.getByRole('row', { name: new RegExp(sc.request.code) });
      await expect(row).toBeVisible();
      await expect(row.getByText(sc.carrier.name.split(' ').slice(0, 2).join(' '), { exact: false }).first()).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/pyme-ofertas-${theme}.png`, fullPage: true });
      await row.getByRole('button', { name: /Aceptar la mejor/ }).click();
      const confirm = page.getByRole('alertdialog');
      await expect(confirm).toContainText('¿Aceptar la oferta de');
      await expect(confirm).toContainText(sc.vehicle.plate);
      await expectNoSeriousViolations(page, '[role=alertdialog]');
      await confirm.getByRole('button', { name: 'Aceptar oferta' }).click();
      await expect(page).toHaveURL(/\/pyme\/envios\?id=/);
      const after = await api.getRequest(sc.pyme.token, sc.request.id);
      expect(after.status).toBe('MATCHED');
    });

    test('bandeja vacía', async ({ page }) => {
      const pyme = await api.registerAccount('PYME');
      await setSession(page.context(), pyme.token, BASE);
      await goto(page, '/pyme/ofertas');
      await expect(page.getByText('No tienes ofertas por revisar')).toBeVisible();
      await expectNoSeriousViolations(page);
    });

    test('acciones masivas: duplicar para la semana siguiente, reprogramar con resultado por solicitud y cancelar con motivo común', async ({ page }) => {
      const sc = await api.openBidScenario(2300);                              // una con oferta
      const free1 = await api.createRequest(sc.pyme.token, { pickupDate: api.isoDate(3), cargoDescription: 'Sin ofertas uno' });
      const free2 = await api.createRequest(sc.pyme.token, { pickupDate: api.isoDate(4), cargoDescription: 'Sin ofertas dos' });
      await setSession(page.context(), sc.pyme.token, BASE);
      await goto(page, '/pyme/solicitudes');

      // Seleccionar todas → barra de acciones
      await page.getByRole('checkbox', { name: 'Seleccionar todas las solicitudes de la página' }).click();
      const bar = page.getByRole('region', { name: 'Acciones sobre las solicitudes seleccionadas' });
      await expect(bar).toContainText('3 seleccionadas');
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/solicitudes-masivas-${theme}.png`, fullPage: true });

      // Reprogramar: la que tiene ofertas no se mueve y el resultado lo dice
      await bar.getByRole('button', { name: 'Reprogramar' }).click();
      const dlg = page.getByRole('dialog', { name: /Reprogramar 3 solicitudes/ });
      await dlg.getByRole('button', { name: 'Reprogramar solicitudes' }).click();
      await expect(dlg.getByText('Selecciona la nueva fecha de recojo.')).toBeVisible();
      await dlg.getByRole('button', { name: 'Nueva fecha de recojo' }).click();
      await pickDay(page, api.isoDate(9));
      await expectNoSeriousViolations(page, '[role=dialog]');
      await dlg.getByRole('button', { name: 'Reprogramar solicitudes' }).click();
      const result = page.getByRole('dialog', { name: /Terminamos, con avisos/ });
      await expect(result).toContainText('Reprogramadas: 2 solicitudes de 3');
      await expect(result).toContainText(sc.request.code);
      await expect(result).toContainText('Ya tiene ofertas');
      await expectNoSeriousViolations(page, '[role=dialog]');
      await result.locator('button', { hasText: /^Cerrar$/ }).click();        // recarga
      await expect(page.getByRole('heading', { name: 'Solicitudes' }).first()).toBeVisible();
      expect((await api.getRequest(sc.pyme.token, free1.id)).pickupDate).toBe(api.isoDate(9));
      expect((await api.getRequest(sc.pyme.token, sc.request.id)).pickupDate).not.toBe(api.isoDate(9));

      // Duplicar para la semana siguiente
      await goto(page, '/pyme/solicitudes');
      await page.getByRole('checkbox', { name: /Seleccionar Lima a Juliaca/ }).first().click();
      await page.getByRole('button', { name: 'Duplicar para la semana siguiente' }).click();
      await page.getByRole('button', { name: 'Duplicar solicitudes' }).click();
      await expect(page.getByRole('dialog', { name: 'Listo' })).toContainText('Duplicadas: 1 solicitud de 1');
      await page.locator('[role=dialog] button', { hasText: /^Cerrar$/ }).click();
      expect(await api.call<any[]>('GET', '/shipment-requests/mine', sc.pyme.token)).toHaveLength(4);

      // Cancelar con un motivo común
      await goto(page, '/pyme/solicitudes?estado=PUBLISHED');
      await page.getByRole('checkbox', { name: 'Seleccionar todas las solicitudes de la página' }).click();
      await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
      const cancel = page.getByRole('dialog', { name: /¿Cancelar 4 solicitudes\?/ });
      await expectNoSeriousViolations(page, '[role=dialog]');
      await cancel.getByLabel('Motivo común').fill('Cambió el plan de despacho');
      await cancel.getByRole('button', { name: 'Cancelar solicitudes' }).click();
      await expect(page.getByRole('dialog', { name: 'Listo' })).toContainText('Canceladas: 4 solicitudes de 4');
      await page.locator('[role=dialog] button', { hasText: /^Cerrar$/ }).click();
      const reqs = await api.call<any[]>('GET', '/shipment-requests/mine', sc.pyme.token);
      expect(reqs.every((r) => r.status === 'CANCELLED' && r.cancelReason === 'Cambió el plan de despacho')).toBe(true);
      expect(free2.id).toBeTruthy();
    });

    test('marcar transportistas: preferido, lista de Transportistas y bloqueo que le oculta la solicitud', async ({ page }) => {
      const sc = await api.openBidScenario(2300);
      await setSession(page.context(), sc.pyme.token, BASE);
      await goto(page, `/pyme/solicitudes/${sc.request.id}`);
      await page.getByRole('button', { name: /^Marcar a / }).click();
      await page.getByRole('menuitem', { name: 'Marcar como preferido' }).click();
      await expect(toastWith(page, 'marcado como preferido')).toBeVisible();
      await expect(page.getByText('Preferido').first()).toBeVisible();

      await goto(page, '/pyme/transportistas');
      await expect(page.getByRole('heading', { name: 'Transportistas', level: 1 })).toBeVisible();
      await expect(page.getByRole('link', { name: sc.carrier.name })).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/pyme-transportistas-${theme}.png`, fullPage: true });
      await page.getByRole('button', { name: new RegExp(`^Bloquear a ${sc.carrier.name}`) }).click();
      const confirm = page.getByRole('alertdialog');
      await expect(confirm).toContainText('No verá tus solicitudes nuevas');
      await expectNoSeriousViolations(page, '[role=alertdialog]');
      await confirm.getByRole('button', { name: 'Bloquear transportista' }).click();
      await expect(toastWith(page, 'Transportista bloqueado')).toBeVisible();

      // El bloqueado ya no ve las solicitudes nuevas de esa PYME
      const other = await api.createRequest(sc.pyme.token, { cargoDescription: 'Nueva para el bloqueado' });
      const carrierView = await api.raw('GET', `/shipment-requests/${other.id}`, sc.carrier.token);
      expect(carrierView.status).toBe(404);
      // y su oferta pendiente no se puede aceptar
      await goto(page, `/pyme/solicitudes/${sc.request.id}`);
      await expect(page.getByText('Bloqueado: quita el bloqueo para aceptar su oferta')).toBeVisible();
      await expectNoSeriousViolations(page);
    });

    test('segunda oferta: pedir confirmación, confirmar y adjudicar', async ({ page, browser }) => {
      const sc = await api.openBidScenario(2300);
      const carrier2 = await api.registerAccount('CARRIER');
      const v2 = await api.addVehicle(carrier2.token);
      await api.placeBid(carrier2.token, sc.request.id, v2.id, Math.ceil(sc.request.priceFloor) + 400);
      const award = await api.acceptBid(sc.pyme.token, sc.bid.id);
      await api.cancelShipment(sc.pyme.token, award.shipmentId, 'El transportista no llegó al recojo');

      await setSession(page.context(), sc.pyme.token, BASE);
      await goto(page, `/pyme/solicitudes/${sc.request.id}`);
      const panel = page.getByTestId('backup-bids');
      await expect(panel).toContainText('Segunda oferta');
      await expect(panel).toContainText(carrier2.name);
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/pyme-segunda-oferta-${theme}.png`, fullPage: true });
      await panel.getByRole('button', { name: 'Pedir confirmación' }).click();
      await expect(toastWith(page, 'Le pedimos confirmar')).toBeVisible();
      await expect(panel.getByText('Esperando su respuesta')).toBeVisible();

      // El transportista confirma desde su pantalla
      const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE', viewport: { width: 1440, height: 900 } });
      await ctx.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
      const cpage = await ctx.newPage();
      await setSession(ctx, carrier2.token, BASE);
      await goto(cpage, '/transportista/confirmaciones');
      await expect(cpage.getByText('Por responder')).toBeVisible();
      await expectNoSeriousViolations(cpage);
      await cpage.getByRole('button', { name: 'Confirmar carga' }).click();
      await expect(cpage.getByRole('alertdialog')).toContainText('¿Confirmar que llevas esta carga?');
      await cpage.getByRole('alertdialog').getByRole('button', { name: 'Confirmar carga' }).click();
      await expect(toastWith(cpage, 'Confirmaste la carga')).toBeVisible();
      await ctx.close();

      // La PYME adjudica
      await goto(page, `/pyme/solicitudes/${sc.request.id}`);
      await panel.getByRole('button', { name: 'Adjudicar carga' }).click();
      await page.getByRole('alertdialog').getByRole('button', { name: 'Adjudicar carga' }).click();
      await expect(page).toHaveURL(/\/pyme\/envios\?id=/);
      expect((await api.getRequest(sc.pyme.token, sc.request.id)).status).toBe('MATCHED');
    });

    test('lector: ve las plantillas y la bandeja sin acciones de escritura', async ({ page }) => {
      const pyme = await api.registerAccount('PYME');
      await api.call('POST', '/request-templates', pyme.token, {
        name: 'Solo lectura', originCity: 'LIMA', originAddress: 'Av. Argentina 1234', destinationCity: 'PUNO', destinationAddress: 'Jr. Lima 456',
        cargoType: 'GENERAL', cargoDescription: 'Abarrotes', weightKg: 1800,
      });
      const viewer = await api.userWithRole(pyme.token, 'VIEWER');
      await setSession(page.context(), viewer.token, BASE);
      await goto(page, '/pyme/plantillas');
      await expect(page.getByText('Solo lectura', { exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: /Publicar ahora/ })).toHaveCount(0);
      await expectNoSeriousViolations(page);
      await goto(page, '/pyme/solicitudes');
      await expect(page.getByRole('checkbox', { name: /Seleccionar todas/ })).toHaveCount(0);
    });
  });
}

/** Evita warnings de import sin uso en los helpers cuando se ejecuta un subconjunto. */
export type _Unused = Page | typeof pick;
