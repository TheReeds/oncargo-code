import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pick, setSession, toastWith } from './helpers/ui';

/**
 * Ola 1 · zona B: contactos de parada tras adjudicar (1.3), filtros en el servidor y CSV de envíos (1.5), enlace público
 * de seguimiento (1.6) y la hora registrada con retraso del conductor (1.11).
 */
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

const CONTACTS = {
  customerReference: 'PED-7781',
  pickupContactName: 'Rosa Almacén', pickupContactPhone: '987654321', pickupWindowFrom: '08:00', pickupWindowTo: '12:00', pickupInstructions: 'Puerta 3, preguntar por Rosa',
  deliveryContactName: 'Juan Recepción', deliveryContactPhone: '912345678', deliveryWindowFrom: '09:00', deliveryWindowTo: '17:00',
};

/** Envío adjudicado con contactos, conductor asignado y el recojo completado (en tránsito). */
async function inTransitWithContacts() {
  const sc = await api.openBidScenario(2300, CONTACTS);
  const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
  const shipmentId: string = accepted.shipmentId;
  const driver = await api.addDriver(sc.carrier.token);
  await api.assignDriver(sc.carrier.token, shipmentId, driver.id);
  const dt = await api.login(driver.email);
  const ship = await api.call<any>('GET', `/shipments/${shipmentId}`, sc.pyme.token);
  const pickup = ship.stops.find((s: any) => s.type === 'PICKUP');
  // Llegó y recogió hace 40 / 30 min sin señal: la app lo sincroniza ahora (hora del dispositivo)
  const ago = (min: number) => new Date(Date.now() - min * 60_000).toISOString();
  await api.call('POST', `/shipments/${shipmentId}/stops/${pickup.id}/arrive`, dt, { occurredAt: ago(40) });
  await api.call('POST', `/shipments/${shipmentId}/stops/${pickup.id}/complete`, dt, { occurredAt: ago(30) });
  return { ...sc, shipmentId, code: ship.code as string, driver, driverToken: dt };
}

async function openShare(page: Page) {
  await page.getByRole('button', { name: 'Compartir seguimiento' }).click();
  const dlg = page.getByRole('dialog', { name: 'Compartir seguimiento' });
  await expect(dlg).toBeVisible();
  return dlg;
}

test.describe('Envíos: contactos, filtros, CSV y enlace público', () => {
  test('la PYME ve contactos, filtra en el servidor, exporta y duplica', async ({ page }) => {
    const s = await inTransitWithContacts();
    const other = await inTransitWithContacts();
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${s.shipmentId}`);
    await expect(page.getByText(s.code).first()).toBeVisible();
    await expect(page.getByText('Tu referencia')).toBeVisible();
    // Contactos de las paradas (RN07 post-adjudicación) con enlace tel:
    const contacts = page.getByRole('region', { name: 'Contactos de las paradas' }).or(page.locator('section[aria-labelledby="stop-contacts-title"]'));
    await expect(contacts).toContainText('Rosa Almacén');
    await expect(contacts.getByRole('link', { name: /Llamar a Rosa Almacén/ })).toHaveAttribute('href', 'tel:+51987654321');
    await expect(contacts).toContainText('Recibe de 08:00 a 12:00');
    // «registrado con retraso» (llegó al servidor 30+ min después de ocurrir)
    await expect(page.getByRole('list', { name: 'Paradas del envío' })).toContainText('registrado con retraso');
    // Duplicar → nueva solicitud precargada desde la solicitud de origen
    await expect(page.getByRole('link', { name: 'Duplicar' })).toHaveAttribute('href', `/pyme/solicitudes/nueva?from=${s.request.id}`);
    // Autoría y rastro del envío
    await expect(page.getByTestId('activity-trail')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/pyme-envios-contactos.png`, fullPage: true });

    // Filtro en el servidor: buscar por código (con la URL como estado)
    await page.getByLabel('Buscar').fill(s.code);
    await page.getByLabel('Buscar').press('Enter');
    await expect(page).toHaveURL(new RegExp(`q=${s.code}`));
    await expect(page.getByRole('region', { name: 'Lista de envíos' }).getByRole('button')).toHaveCount(1);
    await expect(page.getByRole('region', { name: 'Lista de envíos' })).not.toContainText(other.code);
    // Pestaña «Entregados»: ninguno
    await page.getByRole('button', { name: 'Entregados', pressed: false }).click();
    await expect(page).toHaveURL(/estado=DELIVERED/);
    await expect(page.getByText('No hay envíos con estos filtros').or(page.getByText('No hay envíos en este estado'))).toBeVisible();

    // CSV con los mismos filtros (proxy autenticado)
    const res = await page.request.get(`/exportar/envios?estado=todos&q=${s.code}`);
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('text/csv');
    const csv = await res.text();
    expect(csv).toContain(s.code);
    expect(csv).toContain('Transportista');
    expect(csv).not.toContain(other.code);
  });

  test('compartir seguimiento: crear, copiar, WhatsApp, página pública sin datos sensibles y revocar', async ({ page, browser }) => {
    const s = await inTransitWithContacts();
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${s.shipmentId}`);
    const dlg = await openShare(page);
    await expect(dlg).toContainText('Aún no compartiste este envío.');
    await pick(page, dlg.getByRole('combobox', { name: 'Vence' }), '7 días');
    await dlg.getByRole('button', { name: 'Crear enlace' }).click();
    await expect(toastWith(page, 'Enlace de seguimiento creado')).toBeVisible();
    const url = await dlg.getByLabel('Enlace de seguimiento').inputValue();
    expect(url).toMatch(/\/s\/[A-Za-z0-9_-]{22}$/);
    await expect(dlg.getByRole('button', { name: 'Copiar enlace' })).toBeVisible();
    const wa = await dlg.getByRole('link', { name: 'Enviar por WhatsApp' }).getAttribute('href');
    expect(decodeURIComponent(wa ?? '')).toContain(url);
    await expect(dlg.getByRole('listitem').first()).toContainText('Activo');
    await expectNoSeriousViolations(page, '[role=dialog]');
    await page.screenshot({ path: `${SHOTS}/pyme-compartir-seguimiento.png` });

    // El cliente final abre el enlace sin cuenta
    const path = new URL(url).pathname;
    const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE' });
    const pub = await ctx.newPage();
    await goto(pub, path);
    await expect(pub.getByRole('heading', { name: 'Seguimiento de tu envío' })).toBeVisible();
    await expect(pub.getByText(s.code).first()).toBeVisible();
    await expect(pub.getByText(/Llegada estimada/)).toBeVisible();
    await expect(pub.getByRole('list', { name: 'Hitos del envío' })).toContainText('Carga recogida');
    await expect(pub.locator('meta[name="robots"]')).toHaveAttribute('content', /noindex/);
    await expect(pub.locator('meta[name="referrer"]')).toHaveAttribute('content', 'no-referrer');
    const html = await pub.content();
    for (const secret of [s.vehicle.plate, '987654321', '912345678', 'Rosa Almacén', 'PED-7781', 'Av. Argentina', s.pyme.name, s.carrier.name, s.shipmentId, 'S/ ']) {
      expect(html, `la página pública muestra ${secret}`).not.toContain(secret);
    }
    await expect(pub.locator('.maplibregl-canvas').first()).toBeVisible({ timeout: 15_000 });
    await pub.screenshot({ path: `${SHOTS}/seguimiento-publico.png`, fullPage: true });

    // Revocar: el enlace deja de funcionar
    await dlg.getByRole('button', { name: /Revocar el enlace/ }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Revocar enlace' }).click();
    await expect(toastWith(page, 'Enlace revocado')).toBeVisible();
    await expect(dlg.getByRole('listitem').first()).toContainText('Revocado');
    const gone = await pub.goto(path);
    expect(gone?.status()).toBe(404);
    await expect(pub.getByText('Este enlace ya no está disponible')).toBeVisible();
    await ctx.close();
  });

  test('el Lector ve los enlaces pero no los crea ni revoca, ni duplica', async ({ page }) => {
    const s = await inTransitWithContacts();
    await api.call('POST', `/shipments/${s.shipmentId}/share-links`, s.pyme.token, {});
    const viewer = await api.userWithRole(s.pyme.token, 'VIEWER');
    await setSession(page.context(), viewer.token, BASE);
    await goto(page, `/pyme/envios?id=${s.shipmentId}`);
    await expect(page.getByRole('link', { name: 'Duplicar' })).toHaveCount(0);
    const dlg = await openShare(page);
    await expect(dlg.getByRole('listitem')).toHaveCount(1);
    await expect(dlg.getByRole('button', { name: 'Crear enlace' })).toHaveCount(0);
    await expect(dlg.getByRole('button', { name: /Revocar/ })).toHaveCount(0);
  });

  test('el conductor ve el contacto de sus paradas en /conductor', async ({ page }) => {
    const s = await inTransitWithContacts();
    await setSession(page.context(), s.driverToken, BASE);
    await goto(page, '/conductor');
    await expect(page.getByText('Juan Recepción')).toBeVisible();
    await expect(page.getByRole('link', { name: /Llamar a Juan Recepción/ })).toHaveAttribute('href', 'tel:+51912345678');
  });
});

for (const theme of ['light', 'dark'] as const) {
  test(`Accesibilidad zona B (${theme})`, async ({ page, browser }) => {
    await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    const s = await inTransitWithContacts();
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${s.shipmentId}`);
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
    await expectNoSeriousViolations(page);
    const dlg = await openShare(page);
    await dlg.getByRole('button', { name: 'Crear enlace' }).click();
    const url = await dlg.getByLabel('Enlace de seguimiento').inputValue();
    await expectNoSeriousViolations(page, '[role=dialog]');
    await page.keyboard.press('Escape');

    await setSession(page.context(), s.carrier.token, BASE);
    await goto(page, `/transportista/envios?id=${s.shipmentId}`);
    await expectNoSeriousViolations(page);

    const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE' });
    await ctx.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    const pub = await ctx.newPage();
    await goto(pub, new URL(url).pathname);
    await expect(pub.locator('html')).toHaveAttribute('data-theme', theme);
    await expectNoSeriousViolations(pub);
    if (theme === 'dark') await pub.screenshot({ path: `${SHOTS}/seguimiento-publico-dark.png`, fullPage: true });
    await goto(pub, '/s/AAAAAAAAAAAAAAAAAAAAAA');
    await expect(pub.getByText('Este enlace ya no está disponible')).toBeVisible();
    await expectNoSeriousViolations(pub);
    await ctx.close();
  });
}
