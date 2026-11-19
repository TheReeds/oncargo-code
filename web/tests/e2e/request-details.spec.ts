import { readFileSync } from 'node:fs';
import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pick, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

const DETAILS = {
  customerReference: 'OC 4500123', declaredValue: 18500.5,
  pickupContactName: 'María Quispe', pickupContactPhone: '987654321', pickupWindowFrom: '08:00', pickupWindowTo: '12:30', pickupInstructions: 'Muelle 2, pedir la guía en garita',
  deliveryContactName: 'Luis Mamani', deliveryContactPhone: '951234567', deliveryWindowFrom: '14:00', deliveryWindowTo: '18:00', deliveryInstructions: 'Portón verde',
};

test.describe('Solicitudes PYME: duplicar, protección contra doble publicación y datos opcionales', () => {
  test('Duplicar desde la lista precarga todo con la fecha de la semana siguiente y publica una nueva', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const src = await api.createRequest(pyme.token, { ...DETAILS, pickupDate: api.isoDate(3), cargoDescription: 'Rollos de tela paletizados' });
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes');
    await expect(page.getByTestId('request-code').or(page.getByText(src.code).first())).toBeVisible();
    await page.getByRole('button', { name: /Más acciones de Lima a Juliaca/ }).click();
    await page.getByRole('menuitem', { name: 'Duplicar' }).click();
    await expect(page).toHaveURL(new RegExp(`/pyme/solicitudes/nueva\\?from=${src.id}`));
    await expect(page.getByRole('heading', { name: 'Publicar otra igual' })).toBeVisible();
    await expect(page.getByText(src.code).first()).toBeVisible();   // «Copiamos los datos de OC-XXXXX»
    // Todo precargado, y la cotización se recalcula al abrir
    await expect(page.getByLabel('Dirección de recojo')).toHaveValue('Av. Argentina 1234, Callao');
    await expect(page.getByLabel('Descripción de la carga')).toHaveValue('Rollos de tela paletizados');
    await expect(page.getByLabel('Tu referencia')).toHaveValue('OC 4500123');
    await expect(page.getByLabel('Nombre de contacto').first()).toHaveValue('María Quispe');
    await expect(page.getByText('Precio sugerido')).toBeVisible();
    // Fecha: mismo día de la semana, una semana después (nunca pasada)
    const nextWeek = api.isoDate(10);
    const [, m, d] = nextWeek.split('-').map(Number);
    await expect(page.getByRole('button', { name: 'Fecha de recojo' })).toContainText(String(d));
    expect(m).toBeGreaterThan(0);
    await page.screenshot({ path: `${SHOTS}/solicitud-duplicar.png`, fullPage: true });
    await expectNoSeriousViolations(page);

    await page.getByRole('button', { name: 'Publicar solicitud' }).click();
    await expect(page).toHaveURL(/\/pyme\/solicitudes\/(?!nueva)[0-9a-f-]{36}/);
    await expect(toastWith(page, 'Solicitud publicada')).toBeVisible();
    const mine = await api.call<any[]>('GET', '/shipment-requests/mine', pyme.token);
    expect(mine).toHaveLength(2);
    const copy = mine.find((r) => r.id !== src.id);
    expect(copy.code).not.toBe(src.code);
    expect(copy.pickupDate).toBe(api.isoDate(10));
    expect(copy.customerReference).toBe('OC 4500123');
  });

  test('el cotizador de la landing precarga ruta, peso y tipo (?origen=&destino=&peso=&tipo=)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes/nueva?origen=LIMA&destino=PUNO&peso=4200&tipo=FRAGILE');
    await expect(page.getByRole('combobox', { name: 'Ciudad de origen' })).toContainText('Lima');
    await expect(page.getByRole('combobox', { name: 'Ciudad de destino' })).toContainText('Puno');
    await expect(page.getByLabel('Peso')).toHaveValue(/4.?200/);
    await expect(page.getByRole('combobox', { name: 'Tipo de carga' })).toContainText('Frágil');
    await expect(page.getByText('Precio sugerido')).toBeVisible();
    // Valores inválidos se ignoran sin romper
    await goto(page, '/pyme/solicitudes/nueva?origen=MARTE&destino=LIMA&peso=-5&tipo=XX');
    await expect(page.getByRole('combobox', { name: 'Ciudad de origen' })).toContainText('Selecciona');
    await expect(page.getByRole('combobox', { name: 'Ciudad de destino' })).toContainText('Lima');
  });

  test('doble clic en Publicar crea una sola solicitud', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes/nueva');
    const { fillRequestForm } = await import('./helpers/ui');
    await fillRequestForm(page, { pickupIso: api.isoDate(4) });
    const publish = page.getByRole('button', { name: 'Publicar solicitud' });
    await publish.dblclick();
    await expect(page).toHaveURL(/\/pyme\/solicitudes\/(?!nueva)[0-9a-f-]{36}/, { timeout: 20_000 });
    expect(await api.call<any[]>('GET', '/shipment-requests/mine', pyme.token)).toHaveLength(1);
  });

  test('avisa «Ya publicaste una igual hoy» y deja continuar', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await api.createRequest(pyme.token, { weightKg: 3200, pickupDate: api.isoDate(4) });
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes/nueva');
    const { fillRequestForm } = await import('./helpers/ui');
    await fillRequestForm(page, { pickupIso: api.isoDate(4) });
    await page.getByRole('button', { name: 'Publicar solicitud' }).click();
    const warn = page.getByRole('alert').filter({ hasText: 'Ya publicaste una igual hoy' });
    await expect(warn).toBeVisible();
    await expectNoSeriousViolations(page);
    expect(await api.call<any[]>('GET', '/shipment-requests/mine', pyme.token)).toHaveLength(1);
    await warn.getByRole('button', { name: 'Publicar de todos modos' }).click();
    await expect(page).toHaveURL(/\/pyme\/solicitudes\/(?!nueva)[0-9a-f-]{36}/);
    expect(await api.call<any[]>('GET', '/shipment-requests/mine', pyme.token)).toHaveLength(2);
  });

  test('Contactos y horarios: errores bajo el campo y detalle con los datos (privados para el transportista)', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes/nueva');
    const { fillRequestForm } = await import('./helpers/ui');
    await fillRequestForm(page, { pickupIso: api.isoDate(4) });
    await page.getByLabel('Tu referencia').fill('PED-77');
    await page.getByLabel('Valor declarado de la carga').fill('18500');
    await page.locator('summary', { hasText: 'Contactos y horarios' }).click();
    await page.getByLabel('Teléfono de contacto').first().fill('12345');
    await page.getByRole('button', { name: 'Publicar solicitud' }).click();
    await expect(page.getByText('Ingresa un teléfono de 9 dígitos.')).toBeVisible();
    await page.getByLabel('Teléfono de contacto').first().fill('+51 987 654 321');
    await page.getByLabel('Nombre de contacto').first().fill('María Quispe');
    await expectNoSeriousViolations(page);
    await page.screenshot({ path: `${SHOTS}/solicitud-nueva-contactos.png`, fullPage: true });
    await page.getByRole('button', { name: 'Publicar solicitud' }).click();
    await expect(page).toHaveURL(/\/pyme\/solicitudes\/(?!nueva)[0-9a-f-]{36}/);
    await expect(page.getByText('María Quispe')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Contactos y horarios' })).toBeVisible();
    await expect(page.getByText('PED-77').first()).toBeVisible();
    await expect(page.getByTestId('request-code')).toContainText(/OC-[A-Z0-9]{5}/);
    await page.waitForTimeout(800);   // que termine la animación del toast «Solicitud publicada» (axe lo lee a medio fundir)
    await expectNoSeriousViolations(page);
    // Un transportista no ve la referencia ni los contactos antes de adjudicar
    const carrier = await api.registerAccount('CARRIER');
    const id = page.url().split('/').pop()!;
    const asCarrier = await api.call<any>('GET', `/shipment-requests/${id}`, carrier.token);
    expect(asCarrier.customerReference ?? null).toBeNull();
    expect(asCarrier.pickupContactName ?? null).toBeNull();
    expect(asCarrier.declaredValue).toBe(18500);
  });
});

test.describe('Libreta de direcciones', () => {
  test('alta, edición y archivo; se ofrece al publicar y copia contacto, horario e instrucciones', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/direcciones');
    await expect(page.getByText('Guarda tus almacenes y direcciones frecuentes')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('button', { name: 'Nueva dirección' }).click();
    const dlg = page.getByRole('dialog', { name: 'Nueva dirección' });
    await dlg.getByRole('button', { name: 'Guardar dirección' }).click();
    await expect(dlg.getByText('Ponle un nombre a la dirección.')).toBeVisible();
    await dlg.getByLabel('Nombre', { exact: true }).fill('Almacén Callao');
    await pick(page, dlg.getByRole('combobox', { name: 'Ciudad' }), 'Lima');
    await dlg.getByLabel('Dirección', { exact: true }).fill('Av. Argentina 1234, Callao');
    await dlg.getByLabel('Nombre de contacto').fill('Rosa Huanca');
    await dlg.getByLabel('Teléfono de contacto').fill('912 345 678');
    await dlg.getByLabel('Instrucciones').fill('Muelle 2, pedir la guía');
    await pick(page, dlg.getByRole('combobox', { name: 'Atienden desde' }), '08:00');
    await pick(page, dlg.getByRole('combobox', { name: 'Hasta' }), '17:00');
    await expectNoSeriousViolations(page, '[role=dialog]');
    await dlg.getByRole('button', { name: 'Guardar dirección' }).click();
    await expect(toastWith(page, 'Dirección guardada')).toBeVisible();
    const row = page.getByTestId('place-row').filter({ hasText: 'Almacén Callao' });
    await expect(row).toContainText('912345678');
    await page.screenshot({ path: `${SHOTS}/direcciones.png`, fullPage: true });

    // Editar
    await row.getByRole('button', { name: 'Acciones de Almacén Callao' }).click();
    await page.getByRole('menuitem', { name: 'Editar' }).click();
    const edit = page.getByRole('dialog', { name: 'Editar dirección' });
    await edit.getByLabel('Nombre', { exact: true }).fill('Almacén Callao Norte');
    await edit.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByTestId('place-row').filter({ hasText: 'Almacén Callao Norte' })).toBeVisible();

    // Al publicar: «Mis direcciones» filtra por tipo y copia los datos
    await goto(page, '/pyme/solicitudes/nueva');
    await pick(page, page.getByRole('combobox', { name: 'Mis direcciones' }).first(), 'Almacén Callao Norte');
    await expect(page.getByLabel('Dirección de recojo')).toHaveValue('Av. Argentina 1234, Callao');
    await expect(page.getByRole('combobox', { name: 'Ciudad de origen' })).toContainText('Lima');
    await expect(page.getByLabel('Nombre de contacto').first()).toHaveValue('Rosa Huanca');
    await expect(page.getByLabel('Instrucciones').first()).toHaveValue('Muelle 2, pedir la guía');
    await pick(page, page.getByRole('combobox', { name: 'Ciudad de destino' }), 'Juliaca');
    await page.getByLabel('Peso').fill('1500');
    await page.getByLabel('Dirección de entrega').fill('Jr. Lima 456, Juliaca');
    await page.getByLabel('Descripción de la carga').fill('Cajas de abarrotes');
    await page.getByRole('button', { name: 'Fecha de recojo' }).click();
    const { pickDay } = await import('./helpers/ui');
    await pickDay(page, api.isoDate(4));
    // «Guardar esta dirección» para la entrega nueva
    await page.getByLabel('Guardar esta dirección en «Mis direcciones»').check();
    await page.getByLabel('Nombre de la dirección').fill('Tienda Juliaca');
    await page.getByRole('button', { name: 'Publicar solicitud' }).click();
    await expect(page).toHaveURL(/\/pyme\/solicitudes\/(?!nueva)[0-9a-f-]{36}/);
    const places = await api.call<any[]>('GET', '/places', pyme.token);
    expect(places.map((p) => p.label)).toEqual(expect.arrayContaining(['Almacén Callao Norte', 'Tienda Juliaca']));
    expect(places[0].label).toBe('Almacén Callao Norte');   // la usada más recientemente primero
    expect(places.find((p) => p.label === 'Tienda Juliaca').kind).toBe('DELIVERY');

    // Archivar
    await goto(page, '/pyme/direcciones');
    await page.getByTestId('place-row').filter({ hasText: 'Tienda Juliaca' }).getByRole('button', { name: /Acciones de/ }).click();
    await page.getByRole('menuitem', { name: 'Archivar' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Archivar' }).click();
    await expect(toastWith(page, 'Dirección archivada')).toBeVisible();
    await expect(page.getByTestId('place-row').filter({ hasText: 'Tienda Juliaca' })).toHaveCount(0);
    await page.getByRole('button', { name: /Archivadas/ }).click();
    await expect(page.getByTestId('place-row').filter({ hasText: 'Tienda Juliaca' })).toBeVisible();
  });

  test('un lector (VIEWER) ve la libreta sin poder escribir', async ({ page }) => {
    const admin = await api.registerAccount('PYME');
    await api.call('POST', '/places', admin.token, { label: 'Almacén', city: 'LIMA', address: 'Av. Argentina 1234' });
    const viewer = await api.userWithRole(admin.token, 'VIEWER');
    await setSession(page.context(), viewer.token, BASE);
    await goto(page, '/pyme/direcciones');
    await expect(page.getByTestId('place-row')).toHaveCount(1);
    await expect(page.getByRole('button', { name: 'Nueva dirección' })).toHaveCount(0);
    await expect(page.getByTestId('read-only-notice')).toBeVisible();
  });
});

test.describe('Lista de solicitudes: filtros en el servidor, paginación y CSV', () => {
  test('filtra por estado y por código, con la URL como estado; exporta lo que se ve', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const a = await api.createRequest(pyme.token, { destinationCity: 'JULIACA', customerReference: 'PED-A1', pickupDate: api.isoDate(3) });
    const b = await api.createRequest(pyme.token, { destinationCity: 'PUNO', pickupDate: api.isoDate(6) });
    const c = await api.createRequest(pyme.token, { destinationCity: 'CUSCO', pickupDate: api.isoDate(8) });
    await api.call('POST', `/shipment-requests/${c.id}/cancel`, pyme.token, {});
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes');
    await expect(page.getByText('3 solicitudes')).toBeVisible();
    await expectNoSeriousViolations(page);

    await page.getByRole('button', { name: /Canceladas/ }).click();
    await expect(page).toHaveURL(/estado=CANCELLED/);
    await expect(page.getByText('1 solicitud', { exact: false }).first()).toBeVisible();
    await expect(page.getByRole('link', { name: /Lima → Cusco/ })).toBeVisible();

    // Búsqueda por código (con o sin guion, en minúsculas) y por referencia propia
    await goto(page, `/pyme/solicitudes?q=${encodeURIComponent(a.code.toLowerCase())}`);
    await expect(page.getByRole('link', { name: /Lima → Juliaca/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Lima → Puno/ })).toHaveCount(0);
    await goto(page, '/pyme/solicitudes?q=PED-A1');
    await expect(page.getByRole('link', { name: /Lima → Juliaca/ })).toBeVisible();
    await expect(page.getByText(b.code)).toHaveCount(0);

    // Exportar CSV: respeta los filtros activos y neutraliza fórmulas
    await goto(page, `/pyme/solicitudes?destino=PUNO`);
    const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: 'Exportar CSV' }).click()]);
    expect(download.suggestedFilename()).toMatch(/^solicitudes-\d{4}-\d{2}-\d{2}\.csv$/);
    const csv = readFileSync((await download.path())!, 'utf8');
    expect(csv.startsWith('﻿Código;Referencia;')).toBe(true);
    const lines = csv.trim().split('\r\n');
    expect(lines).toHaveLength(2);
    expect(lines[1]).toContain(b.code);
    expect(lines[1]).toContain(';Puno;');
  });

  test('pagina de 20 en 20 y conserva los filtros', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    for (let i = 0; i < 22; i++) await api.createRequest(pyme.token, { weightKg: 500 + i, pickupDate: api.isoDate(3 + (i % 5)) });
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme/solicitudes');
    await expect(page.getByText('22 solicitudes')).toBeVisible();
    await expect(page.locator('tbody tr')).toHaveCount(20);
    await page.getByRole('button', { name: 'Página siguiente' }).click();
    await expect(page).toHaveURL(/pagina=2/);
    await expect(page.locator('tbody tr')).toHaveCount(2);
  });
});

test.describe('Búsqueda global (Ctrl+K)', () => {
  test('encuentra una solicitud por código y por referencia, y respeta lo que el usuario puede ver', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    const req = await api.createRequest(pyme.token, { customerReference: 'PED-9988', destinationCity: 'TACNA' });
    const other = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/pyme');
    await page.keyboard.press('Control+k');
    const dlg = page.getByRole('dialog');
    await dlg.getByPlaceholder(/Busca un código/).fill(req.code.toLowerCase());
    const hit = dlg.getByRole('option', { name: new RegExp(req.code) });
    await expect(hit).toBeVisible();
    await expect(dlg.getByText('Solicitudes', { exact: true })).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/busqueda-global.png` });
    await expectNoSeriousViolations(page, '[role=dialog]');
    await dlg.getByPlaceholder(/Busca un código/).fill('ped-9988');
    await expect(dlg.getByRole('option', { name: new RegExp(req.code) })).toBeVisible();
    await dlg.getByPlaceholder(/Busca un código/).fill('xx-sin-resultado');
    await expect(dlg.getByText(/No encontramos resultados/)).toBeVisible();
    // Las secciones también se buscan
    await dlg.getByPlaceholder(/Busca un código/).fill('direcciones');
    await expect(dlg.getByRole('option', { name: 'Mis direcciones' })).toBeVisible();
    // Enter abre el resultado
    await dlg.getByPlaceholder(/Busca un código/).fill(req.code);
    await dlg.getByRole('option', { name: new RegExp(req.code) }).click();
    await expect(page).toHaveURL(`${BASE}/pyme/solicitudes/${req.id}`);

    // Otra PYME no encuentra nada de la primera
    await setSession(page.context(), other.token, BASE);
    await goto(page, '/pyme');
    await page.keyboard.press('Control+k');
    await page.getByRole('dialog').getByPlaceholder(/Busca un código/).fill(req.code);
    await expect(page.getByRole('dialog').getByText(/No encontramos resultados/)).toBeVisible();
    expect(await api.call<any[]>('GET', `/search?q=${encodeURIComponent('PED-9988')}`, other.token)).toHaveLength(0);
  });

  test('la búsqueda del transportista no muestra la referencia privada; el operador busca por RUC', async () => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const req = await api.createRequest(pyme.token, { customerReference: 'PRIVADA-1' });
    expect(await api.call<any[]>('GET', `/search?q=${req.code}`, carrier.token)).toHaveLength(1);
    expect(await api.call<any[]>('GET', '/search?q=PRIVADA-1', carrier.token)).toHaveLength(0);
    const op = await api.operatorToken();
    expect((await api.call<any[]>('GET', '/search?q=PRIVADA-1', op)).map((h) => h.id)).toContain(req.id);
    const byRuc = await api.call<any[]>('GET', `/search?q=${pyme.ruc}&types=COUNTERPART`, op);
    expect(byRuc.map((h) => h.title)).toContain(pyme.name);
  });
});

for (const theme of ['light', 'dark'] as const) {
  test(`Accesibilidad zona A (${theme})`, async ({ page }) => {
    await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    const pyme = await api.registerAccount('PYME');
    await api.call('POST', '/places', pyme.token, { label: 'Almacén Callao', city: 'LIMA', address: 'Av. Argentina 1234', contactPhone: '987654321', kind: 'PICKUP' });
    await api.createRequest(pyme.token, DETAILS);
    await setSession(page.context(), pyme.token, BASE);
    for (const path of ['/pyme/solicitudes', '/pyme/solicitudes/nueva', '/pyme/direcciones']) {
      await goto(page, path);
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
    }
    await goto(page, '/pyme/solicitudes/nueva');
    await page.locator('summary', { hasText: 'Contactos y horarios' }).click();
    await expectNoSeriousViolations(page);
    await page.keyboard.press('Control+k');
    await page.getByRole('dialog').getByPlaceholder(/Busca un código/).fill('lima');
    await expect(page.getByRole('dialog').getByRole('option').first()).toBeVisible();
    await expectNoSeriousViolations(page, '[role=dialog]');
  });
}
