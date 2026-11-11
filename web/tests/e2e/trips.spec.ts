import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { goto, pick, pickDay, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';
const as = (page: Page, token: string) => setSession(page.context(), token, BASE);

test.describe('Transportista: publicar viaje', () => {
  test('valida, muestra la vista previa y publica', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    const vehicle = await api.addVehicle(carrier.token, 12000);
    await as(page, carrier.token);
    await goto(page, '/transportista/viajes/nuevo');

    // validación inline
    await page.getByRole('button', { name: 'Publicar viaje' }).click();
    await expect(page.getByText('Selecciona la ciudad de origen.')).toBeVisible();
    await expect(page.getByText('Selecciona la fecha de salida.')).toBeVisible();
    await expect(page.getByText('Selecciona el vehículo que hará el viaje.')).toBeVisible();
    await expect(page.getByText('Ingresa la tarifa por kg.')).toBeVisible();

    await pick(page, page.getByRole('combobox', { name: 'Ciudad de origen' }), 'Lima');
    await pick(page, page.getByRole('combobox', { name: 'Ciudad de destino' }), 'Juliaca');
    await page.getByRole('button', { name: 'Fecha de salida' }).click();
    await pickDay(page, api.isoDate(3));
    await pick(page, page.getByRole('combobox', { name: 'Hora de salida' }), '08:00');
    await pick(page, page.getByRole('combobox', { name: 'Vehículo' }), vehicle.plate);

    // RN12: capacidad mayor a la del vehículo
    await page.getByLabel('Capacidad que ofreces').fill('15000');
    await page.getByLabel('Tarifa por kg').fill('0.48');
    await page.getByRole('button', { name: 'Publicar viaje' }).click();
    await expect(page.getByText(/Tu vehículo lleva hasta 12,000 kg/)).toBeVisible();

    // vista previa en vivo
    await page.getByLabel('Capacidad que ofreces').fill('10000');
    await expect(page.getByTestId('trip-preview-full')).toHaveText('S/ 4,800.00');
    await page.getByLabel('Por tramo completo').check();
    await page.getByLabel('Tarifa del tramo').fill('3000');
    await expect(page.getByTestId('trip-preview-full')).toHaveText('S/ 3,000.00');
    await page.setViewportSize({ width: 1440, height: 1150 });
    await page.screenshot({ path: `${SHOTS}/viajes-publicar.png` });
    await page.setViewportSize({ width: 1440, height: 900 });

    await page.getByRole('button', { name: 'Publicar viaje' }).click();
    await expect(page).toHaveURL(/\/transportista\/viajes\/[0-9a-f-]{36}$/);
    await expect(toastWith(page, 'Viaje publicado')).toBeVisible();
    await expect(page.getByText('Aún no hay reservas')).toBeVisible();
    await expect(page.getByText('Abierto', { exact: true })).toBeVisible();

    await goto(page, '/transportista/viajes');
    await expect(page.getByRole('row').filter({ hasText: 'Lima' }).filter({ hasText: 'Juliaca' })).toBeVisible();
  });

  test('sin vehículos disponibles pide agregar uno', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await as(page, carrier.token);
    await goto(page, '/transportista/viajes/nuevo');
    await expect(page.getByText('Necesitas un vehículo disponible')).toBeVisible();
  });

  test('lista vacía', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await as(page, carrier.token);
    await goto(page, '/transportista/viajes');
    await expect(page.getByText('Aún no publicaste viajes')).toBeVisible();
  });
});

test.describe('PYME: buscar viajes y reservar', () => {
  test('busca por ruta, ve el precio estimado y reserva; luego cancela', async ({ page }) => {
    const sc = await api.tripScenario();
    await as(page, sc.pyme.token);
    await goto(page, '/pyme/viajes');
    await pick(page, page.getByRole('combobox', { name: 'Origen' }), 'Abancay');
    await page.waitForURL(/origen=ABANCAY/);
    await hydratedFilters(page);
    await pick(page, page.getByRole('combobox', { name: 'Destino' }), 'Ayacucho');
    await page.waitForURL(/destino=AYACUCHO/);
    await hydratedFilters(page);
    const row = page.getByTestId('trip-row').filter({ hasText: sc.carrier.name });
    await expect(row).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/pyme-buscar-viajes.png` });

    await row.getByRole('button', { name: /Reservar espacio/ }).click();
    const sheet = page.getByRole('dialog');
    // validación
    await sheet.getByRole('button', { name: 'Reservar espacio' }).click();
    await expect(sheet.getByText('Ingresa el peso que quieres enviar en kg.')).toBeVisible();
    await expect(sheet.getByText('Ingresa la dirección de recojo.')).toBeVisible();
    // más que lo disponible
    await sheet.getByLabel('Peso a enviar').fill('9000');
    await sheet.getByRole('button', { name: 'Reservar espacio' }).click();
    await expect(sheet.getByText(/Solo quedan 8,000 kg libres/)).toBeVisible();

    await sheet.getByLabel('Peso a enviar').fill('2500');
    await expect(sheet.getByTestId('booking-price')).toHaveText('S/ 1,250.00');
    await sheet.getByLabel('Descripción de la carga').fill('Mercadería de bazar en cajas');
    await sheet.getByLabel('Dirección de recojo').fill('Av. Argentina 1234, Callao');
    await sheet.getByLabel('Dirección de entrega').fill('Jr. San Román 456, Ayacucho');
    await page.screenshot({ path: `${SHOTS}/pyme-reservar-sheet.png` });
    await sheet.getByRole('button', { name: 'Reservar espacio' }).click();

    await expect(page).toHaveURL(/\/pyme\/reservas$/);
    await expect(toastWith(page, 'Reserva enviada')).toBeVisible();
    const card = page.getByTestId('booking-card').first();
    await expect(card.getByText('Por confirmar')).toBeVisible();
    await expect(card.getByText('S/ 1,250.00')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/pyme-mis-reservas.png` });

    await card.getByRole('button', { name: /Cancelar reserva/ }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Cancelar reserva' }).click();
    await expect(toastWith(page, 'Reserva cancelada')).toBeVisible();
    await expect(page.getByText('Aún no tienes reservas activas')).toBeVisible();
    await page.getByRole('tab', { name: /Historial/ }).click();
    await expect(page.getByText('Cancelada', { exact: true })).toBeVisible();
  });

  test('sin resultados con filtros y estado vacío de reservas', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await as(page, pyme.token);
    await goto(page, '/pyme/viajes?origen=NAZCA&destino=TACNA&peso=999999');
    await expect(page.getByText('No hay viajes con estos filtros')).toBeVisible();
    await goto(page, '/pyme/reservas');
    await expect(page.getByText('Aún no tienes reservas activas')).toBeVisible();
  });

  test('el panel muestra las reservas activas', async ({ page }) => {
    const sc = await api.tripScenario();
    await api.bookTrip(sc.pyme.token, sc.trip.id);
    await as(page, sc.pyme.token);
    await goto(page, '/pyme');
    await expect(page.getByRole('link', { name: /Reservas activas/ })).toContainText('1');
  });
});

test.describe('Transportista: confirmar y rechazar reservas', () => {
  test('confirma una reserva y la PYME ve el envío', async ({ page }) => {
    const sc = await api.tripScenario();
    await api.bookTrip(sc.pyme.token, sc.trip.id, { weightKg: 2000 });
    await as(page, sc.carrier.token);
    await goto(page, `/transportista/viajes/${sc.trip.id}`);
    const row = page.getByTestId('booking-row');
    await expect(row.getByText('Por confirmar')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/viajes-detalle-reservas.png`, fullPage: true });
    await row.getByRole('button', { name: /Confirmar reserva/ }).click();
    const dlg = page.getByRole('alertdialog');
    await expect(dlg.getByText(/Al confirmar se genera el envío/)).toBeVisible();
    await dlg.getByRole('button', { name: 'Confirmar reserva' }).click();
    await expect(toastWith(page, 'Reserva confirmada')).toBeVisible();
    await expect(row.getByText('Confirmada')).toBeVisible();
    await expect(row.getByRole('link', { name: 'Ver envío' })).toBeVisible();
    await expect(page.getByText('Con reservas', { exact: true })).toBeVisible();
    await expect(page.getByRole('meter', { name: /Ocupación confirmada/ })).toHaveAttribute('aria-valuenow', '2000');

    // la PYME ve la reserva confirmada con enlace al envío
    await page.context().clearCookies();
    await as(page, sc.pyme.token);
    await goto(page, '/pyme/reservas');
    const card = page.getByTestId('booking-card');
    await expect(card.getByText('Confirmada', { exact: true })).toBeVisible();
    await card.getByRole('link', { name: 'Ver envío' }).click();
    await expect(page).toHaveURL(/\/pyme\/envios\?id=/);
  });

  test('rechaza una reserva con motivo', async ({ page }) => {
    const sc = await api.tripScenario();
    await api.bookTrip(sc.pyme.token, sc.trip.id);
    await as(page, sc.carrier.token);
    await goto(page, `/transportista/viajes/${sc.trip.id}`);
    await page.getByRole('button', { name: /Rechazar reserva/ }).click();
    const dlg = page.getByRole('dialog');
    await dlg.getByLabel('Motivo').fill('Ya completamos la capacidad');
    await dlg.getByRole('button', { name: 'Rechazar reserva' }).click();
    await expect(toastWith(page, 'Reserva rechazada')).toBeVisible();
    await expect(page.getByTestId('booking-row').getByText('Rechazada')).toBeVisible();
    await expect(page.getByText('Nota: Ya completamos la capacidad')).toBeVisible();
  });

  test('cierra y cancela el viaje', async ({ page }) => {
    const sc = await api.tripScenario();
    await as(page, sc.carrier.token);
    await goto(page, `/transportista/viajes/${sc.trip.id}`);
    await page.getByRole('button', { name: 'Cerrar reservas' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Cerrar reservas' }).click();
    await expect(toastWith(page, 'Reservas cerradas')).toBeVisible();
    await expect(page.getByText('Cerrado', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Cancelar viaje' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Cancelar viaje' }).click();
    await expect(toastWith(page, 'Viaje cancelado')).toBeVisible();
    await expect(page.getByText('Cancelado', { exact: true })).toBeVisible();
  });

  test('la campana avisa de una reserva nueva y enlaza al viaje', async ({ page }) => {
    const sc = await api.tripScenario();
    await api.bookTrip(sc.pyme.token, sc.trip.id);
    await as(page, sc.carrier.token);
    await goto(page, '/notificaciones');
    await page.getByText('Nueva reserva').first().click();
    await expect(page).toHaveURL(new RegExp(`/transportista/viajes/${sc.trip.id}`));
  });
});

test.describe('Transportista: cargas de retorno', () => {
  test('la cuenta demo ve cargas de retorno y abre el flujo de oferta', async ({ page }) => {
    const token = await api.login(api.DEMO.carrier);
    await as(page, token);
    await goto(page, '/transportista/retornos');
    const loads = page.getByTestId('return-load');
    await expect(loads.first()).toBeVisible();
    expect(await loads.count()).toBeGreaterThanOrEqual(1);
    await expect(loads.first().getByText(/Compatibilidad/)).toBeVisible();
    await expect(loads.first().getByText('Precio sugerido')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/retornos-lista.png`, fullPage: true });
    await loads.first().getByRole('button', { name: /Ofertar/ }).click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await expect(sheet).toBeVisible();
    await expect(sheet.getByText('Precio sugerido')).toBeVisible();
    await expect(sheet.getByRole('button', { name: 'Enviar oferta' })).toBeVisible();
  });

  test('transportista sin envíos ve el estado vacío; el panel muestra su actividad', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await as(page, carrier.token);
    await goto(page, '/transportista/retornos');
    await expect(page.getByText('Aún no hay cargas de retorno')).toBeVisible();
    await goto(page, '/transportista');
    await expect(page.getByRole('heading', { name: 'Tu actividad' })).toBeVisible();
    await expect(page.getByRole('link', { name: /Viajes abiertos/ })).toContainText('0');
    await expect(page.getByText('Sin cargas de retorno por ahora')).toBeVisible();
  });

  test('el panel de la cuenta demo resume viajes, reservas y retornos', async ({ page }) => {
    const token = await api.login(api.DEMO.carrier);
    await as(page, token);
    await goto(page, '/transportista');
    await expect(page.getByRole('link', { name: /Cargas de retorno/ }).first()).toBeVisible();
    await expect(page.getByText('Cargas de retorno destacadas')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/transportista-panel.png`, fullPage: true });
  });
});

test.describe('Transportista: Smart Load', () => {
  /** Transportista con un camión grande y cargas combinables publicadas por una PYME. */
  async function smartScenario() {
    const [pyme, carrier] = await Promise.all([api.registerAccount('PYME'), api.registerAccount('CARRIER')]);
    await api.addVehicle(carrier.token, 20000, 'TRUCK_LARGE');
    await api.createRequest(pyme.token, { weightKg: 7500, pickupDate: api.isoDate(5) });
    await api.createRequest(pyme.token, { destinationCity: 'PUNO', weightKg: 6000, pickupDate: api.isoDate(5) });
    return { pyme, carrier };
  }

  test('genera, acepta una sugerencia y avisa que no adjudica', async ({ page }) => {
    const sc = await smartScenario();
    await as(page, sc.carrier.token);
    await goto(page, '/transportista/smart-load');
    await expect(page.getByText('No tienes sugerencias pendientes')).toBeVisible();
    await page.getByRole('button', { name: 'Generar sugerencias' }).click();
    const card = page.getByTestId('smart-load').first();
    await expect(card).toBeVisible();
    await expect(card.getByText('Ingreso combinado')).toBeVisible();
    await expect(card.getByRole('meter')).toBeVisible();
    await expect(card.getByText('Ruta sugerida')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/smart-load-lista.png`, fullPage: true });
    await card.getByRole('button', { name: /Aceptar sugerencia/ }).click();
    const dlg = page.getByRole('alertdialog');
    await expect(dlg.getByText(/no te adjudica nada/i)).toBeVisible();
    await dlg.getByRole('button', { name: 'Aceptar sugerencia' }).click();
    await expect(toastWith(page, 'Sugerencia aceptada')).toBeVisible();
    await page.getByRole('tab', { name: /Aceptadas/ }).click();
    await expect(page.getByTestId('smart-load').first().getByText('Aceptada', { exact: true })).toBeVisible();
    await expect(page.getByText('Aceptar no es adjudicar')).toBeVisible();
  });

  test('rechaza una sugerencia', async ({ page }) => {
    const sc = await smartScenario();
    await as(page, sc.carrier.token);
    await goto(page, '/transportista/smart-load');
    await page.getByRole('button', { name: 'Generar sugerencias' }).click();
    const card = page.getByTestId('smart-load').first();
    await card.getByRole('button', { name: /Rechazar sugerencia/ }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Rechazar sugerencia' }).click();
    await expect(toastWith(page, 'Sugerencia rechazada')).toBeVisible();
    await page.getByRole('tab', { name: /Rechazadas y vencidas/ }).click();
    await expect(page.getByTestId('smart-load').first().getByText('Rechazada', { exact: true })).toBeVisible();
  });

  test('sin combinaciones muestra el aviso', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token, 1500, 'VAN');
    await as(page, carrier.token);
    await goto(page, '/transportista/smart-load');
    await page.getByRole('button', { name: 'Generar sugerencias' }).click();
    await expect(toastWith(page, 'No encontramos combinaciones nuevas')).toBeVisible();
  });
});

/** Tras un cambio de filtro la página recarga (SSR): espera a que las islas se hidraten otra vez. */
async function hydratedFilters(page: Page) {
  await page.waitForFunction(() => !document.querySelector('astro-island[ssr]'), undefined, { timeout: 20_000 });
}
