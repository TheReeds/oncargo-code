import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { fillRequestForm, goto, pick, pickDay, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const PHONE = 'Llámame al 987 654 321 para coordinar';
const RN07 = /No puedes compartir teléfonos, correos, enlaces ni usuarios de redes/;

/** RN07 (F10, F37): `LOGYX-NEG-001` llega con el `field` y se muestra bajo el campo, no como un toast genérico. */
test.describe('RN07: datos de contacto rechazados inline en cada texto libre', () => {
  test('solicitud: descripción de la carga', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes/nueva');
    await fillRequestForm(page, { description: PHONE, pickupIso: api.isoDate(4) });
    await page.getByRole('button', { name: 'Publicar solicitud' }).click();
    await expect(page.locator('#cargoDescription-error')).toContainText(RN07);
    await expect(page.getByLabel('Descripción de la carga')).toBeFocused();
    await expect(page).toHaveURL(/nueva/);
  });

  test('oferta: nota para la PYME y contra-oferta: mensaje', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token);
    const req = await api.createRequest(pyme.token, { originCity: 'TACNA', destinationCity: 'MOQUEGUA' });
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/marketplace?origen=TACNA&destino=MOQUEGUA');
    await page.getByRole('button', { name: 'Ofertar' }).first().click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await sheet.getByLabel('Tu oferta').fill(String(Math.ceil(req.priceFloor) + 30));
    await sheet.getByLabel('Nota para la PYME').fill('Escríbeme a transportes@correo.pe');
    await sheet.getByRole('button', { name: 'Enviar oferta' }).click();
    await expect(sheet.locator('#b-notes-error')).toContainText(RN07);
    await expect(sheet.getByRole('combobox', { name: 'Vehículo' })).not.toHaveAttribute('aria-invalid', 'true');
    await sheet.getByLabel('Nota para la PYME').fill('Salimos el mismo día');
    await sheet.getByRole('button', { name: 'Enviar oferta' }).click();
    await expect(toastWith(page, 'Oferta enviada')).toBeVisible();

    // Contra-oferta del transportista con teléfono en el mensaje
    await goto(page, '/transportista/ofertas');
    await page.getByRole('button', { name: /Negociar/ }).first().click();
    await page.getByRole('dialog', { name: 'Negociar oferta' }).getByRole('button', { name: 'Contraofertar' }).click();
    const dlg = page.getByRole('dialog', { name: 'Proponer un nuevo monto' });
    await dlg.getByLabel('Monto que propones').fill(String(Math.ceil(req.priceFloor) + 10));
    await dlg.getByLabel('Mensaje').fill(PHONE);
    await dlg.getByRole('button', { name: 'Enviar contra-oferta' }).click();
    await expect(dlg.locator('#co-message-error')).toContainText(RN07);
  });

  test('viaje (notas) y reserva (descripción y direcciones)', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    const vehicle = await api.addVehicle(carrier.token);
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/viajes/nuevo');
    await pick(page, page.getByRole('combobox', { name: 'Ciudad de origen' }), 'Lima');
    await pick(page, page.getByRole('combobox', { name: 'Ciudad de destino' }), 'Juliaca');
    await page.getByRole('button', { name: 'Fecha de salida' }).click();
    await pickDay(page, api.isoDate(3));
    await pick(page, page.getByRole('combobox', { name: 'Hora de salida' }), '08:00');
    await pick(page, page.getByRole('combobox', { name: 'Vehículo' }), vehicle.plate);
    await page.getByLabel('Tarifa por kg').fill('0.48');
    await page.getByLabel('Notas para las PYMEs').fill('Contacto: https://wa.me/51987654321');
    await page.getByRole('button', { name: 'Publicar viaje' }).click();
    await expect(page.locator('#t-notes-error')).toContainText(RN07);

    // Reserva: la PYME escribe un teléfono en la dirección de recojo
    const sc = await api.tripScenario();
    const pctx = page.context();
    await setSession(pctx, sc.pyme.token, BASE);
    await goto(page, `/pyme/viajes?origen=ABANCAY&destino=AYACUCHO&desde=${sc.date}`);
    await page.getByTestId('trip-row').filter({ hasText: sc.carrier.name }).getByRole('button', { name: /Reservar espacio/ }).click();
    const sheet = page.getByRole('dialog');
    await sheet.getByLabel('Peso a enviar').fill('1000');
    await sheet.getByLabel('Descripción de la carga').fill('Cajas de bazar');
    await sheet.getByLabel('Dirección de recojo').fill('Av. Argentina 1234, llamar al 987654321');
    await sheet.getByLabel('Dirección de entrega').fill('Jr. San Román 456, Juliaca');
    await sheet.getByRole('button', { name: 'Reservar espacio' }).click();
    await expect(sheet.locator('#bk-pickup-error')).toContainText(RN07);
  });

  test('incidencia: descripción y comentario', async ({ page }) => {
    const sc = await api.openBidScenario();
    const shipmentId = (await api.acceptBid(sc.pyme.token, sc.bid.id)).shipmentId as string;
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${shipmentId}`);
    await page.getByRole('tab', { name: 'Incidencias' }).click();
    await page.getByRole('button', { name: 'Reportar incidencia' }).click();
    const dlg = page.getByRole('dialog', { name: 'Reportar incidencia' });
    await pick(page, dlg.getByRole('combobox', { name: 'Categoría' }), 'Retraso');
    await dlg.getByLabel('Descripción').fill(PHONE);
    await dlg.getByRole('button', { name: 'Reportar incidencia' }).click();
    await expect(dlg.locator('#inc-description-error')).toContainText(RN07);
  });

  test('reseña (comentario público)', async ({ page }) => {
    const s = await api.deliveredShipment();
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${s.shipmentId}`);
    await page.getByRole('tab', { name: 'Calificación' }).click();
    const form = page.getByRole('form', { name: 'Calificar envío' });
    const groups = form.getByRole('radiogroup');
    for (let i = 0; i < 3; i++) await groups.nth(i).getByRole('radio', { name: '5 de 5' }).click();
    await form.getByLabel('Comentario').fill('Excelente, escríbeme a ana@correo.pe');
    await form.getByRole('button', { name: 'Enviar calificación' }).click();
    await expect(form.getByText(RN07)).toBeVisible();
    await form.getByLabel('Comentario').fill('Excelente servicio');
    await form.getByRole('button', { name: 'Enviar calificación' }).click();
    await expect(toastWith(page, 'Calificación enviada')).toBeVisible();
  });

  test('registro: razón social', async ({ page }) => {
    await goto(page, '/registro');
    await page.getByLabel('Razón social').fill('Transportes 987654321 SAC');
    await page.getByLabel('RUC').fill(api.validRuc());
    await page.getByLabel('Tu nombre').fill('Ana Quispe');
    await page.getByLabel('Correo electrónico').fill(`e2e-${api.uid()}@prueba.pe`);
    await page.getByLabel('Contraseña', { exact: true }).fill(api.PASSWORD);
    await page.getByRole('checkbox').check();
    await page.getByRole('button', { name: /Crear cuenta/ }).click();
    await expect(page.locator('#legalName-error')).toContainText('La razón social no puede incluir teléfonos');
  });

  test('límites alineados con la API: notas de oferta y viaje de 300, no 500 (F37)', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token);
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/viajes/nuevo');
    await expect(page.getByLabel('Notas para las PYMEs')).toHaveAttribute('maxlength', '300');
    await expect(page.getByText(/Máximo 300 caracteres/)).toBeVisible();
  });
});
