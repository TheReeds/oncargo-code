import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, hydrated, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

async function newContextWith(page: import('@playwright/test').Page, token: string) {
  const ctx = await page.context().browser()!.newContext({ baseURL: BASE, locale: 'es-PE', timezoneId: 'America/Lima', viewport: { width: 1440, height: 900 } });
  await setSession(ctx, token, BASE);
  return { ctx, p: await ctx.newPage() };
}

test.describe('Usuarios y roles', () => {
  test('invitar → correo → activar → ingresar con su rol; cambiar rol, desactivar y reactivar', async ({ page }) => {
    const admin = await api.registerAccount('PYME');
    await setSession(page.context(), admin.token, BASE);
    await goto(page, '/organizacion/usuarios');
    await expect(page.getByRole('heading', { name: 'Usuarios y roles' })).toBeVisible();
    await expect(page.getByTestId('user-row')).toHaveCount(1);
    await expect(page.getByTestId('user-row').first()).toContainText('Tú');

    // Invitar (rol Operaciones)
    const email = api.userEmail('ops');
    await page.getByRole('button', { name: 'Invitar usuario' }).first().click();
    const dialog = page.getByRole('dialog', { name: 'Invitar usuario' });
    await dialog.getByRole('button', { name: 'Enviar invitación' }).click();
    await expect(dialog.getByText('Ingresa el nombre completo.')).toBeVisible();
    await dialog.getByLabel('Nombre completo').fill('Marisol Operaciones');
    await dialog.getByLabel('Correo electrónico').fill(admin.email); // ya registrado → AUTH-002 bajo el campo
    await expect(dialog.getByText('Solo consulta: ve solicitudes')).toBeVisible();
    await dialog.getByRole('radio', { name: /Operaciones/ }).click();
    await dialog.getByRole('button', { name: 'Enviar invitación' }).click();
    await expect(dialog.getByRole('alert').filter({ hasText: /registrado/i }).first()).toBeVisible();
    await dialog.getByLabel('Correo electrónico').fill(email);
    await dialog.getByRole('button', { name: 'Enviar invitación' }).click();
    await expect(toastWith(page, 'Invitación enviada')).toBeVisible();
    const row = page.getByTestId('user-row').filter({ hasText: 'Marisol Operaciones' });
    await expect(row).toContainText('Invitación pendiente');
    await expect(row).toContainText('Operaciones');

    // Reenviar la invitación deja un enlace nuevo (el anterior deja de valer)
    const first = await api.resetLinkFor(email);
    await row.getByRole('button', { name: /Acciones de Marisol/ }).click();
    await page.getByRole('menuitem', { name: 'Reenviar invitación' }).click();
    await expect(toastWith(page, 'Invitación reenviada')).toBeVisible();
    await expect.poll(async () => (await api.mailbox(email)).length).toBeGreaterThan(1);
    const second = await api.resetLinkFor(email);
    expect(second.token).not.toBe(first.token);
    await expect(api.resetPasswordApi(first.token, 'Cualquiera2026!', true)).rejects.toThrow();

    // La persona invitada activa su cuenta (acepta la política) y entra con su rol
    const guest = await page.context().browser()!.newContext({ baseURL: BASE, locale: 'es-PE', viewport: { width: 1440, height: 900 } });
    const gp = await guest.newPage();
    await gp.goto(second.path);
    await hydrated(gp);
    await expect(gp.getByRole('heading', { name: `Activa tu cuenta en ${admin.name}` })).toBeVisible();
    await gp.getByLabel('Contraseña nueva', { exact: true }).fill('ClaveInvitada26!');
    await gp.getByLabel('Repite la contraseña nueva').fill('ClaveInvitada26!');
    await gp.getByRole('button', { name: 'Activar cuenta' }).click();
    await expect(gp.getByText('Acepta la política de privacidad para activar tu cuenta.')).toBeVisible();
    await gp.getByRole('link', { name: 'política de privacidad' }).first().focus();
    await gp.getByRole('checkbox', { name: /Acepto la política de privacidad/ }).click();
    await gp.getByRole('button', { name: 'Activar cuenta' }).click();
    await expect(gp).toHaveURL(/\/login\?activada=1/);
    await expect(gp.getByText('Tu cuenta quedó activa')).toBeVisible();
    await gp.getByLabel('Correo electrónico').fill(email);
    await gp.getByLabel('Contraseña', { exact: true }).fill('ClaveInvitada26!');
    await gp.getByRole('button', { name: 'Ingresar' }).click();
    await expect(gp).toHaveURL(/\/pyme$/);
    await hydrated(gp);
    // OPS: opera, pero no ve Usuarios ni edita el perfil de la empresa
    await expect(gp.getByRole('link', { name: 'Nueva solicitud' })).toBeVisible();
    await expect(gp.getByRole('link', { name: 'Usuarios y roles' })).toHaveCount(0);
    await expect(gp.getByRole('link', { name: 'Perfil de la empresa' })).toBeVisible();
    await gp.goto('/organizacion/usuarios');
    await expect(gp.getByTestId('forbidden')).toBeVisible();
    expect((await gp.request.get('/organizacion/usuarios')).status()).toBe(403);
    await gp.goto('/organizacion');
    await expect(gp.getByText('Solo un administrador puede editar estos datos')).toBeVisible();
    await expect(gp.getByRole('button', { name: 'Guardar cambios' })).toHaveCount(0);
    await guest.close();

    // El administrador ve el estado actualizado y cambia el rol a Lector
    await page.reload();
    await hydrated(page);
    await expect(page.getByTestId('user-row').filter({ hasText: 'Marisol Operaciones' })).toContainText('Activo');
    const r2 = page.getByTestId('user-row').filter({ hasText: 'Marisol Operaciones' });
    await r2.getByRole('button', { name: /Acciones de Marisol/ }).click();
    await page.getByRole('menuitem', { name: 'Cambiar rol' }).click();
    const roleDlg = page.getByRole('dialog', { name: 'Cambiar rol' });
    await roleDlg.getByRole('radio', { name: /Lector/ }).click();
    await roleDlg.getByRole('button', { name: 'Guardar rol' }).click();
    await expect(toastWith(page, 'Rol actualizado')).toBeVisible();
    await expect(r2).toContainText('Lector');
    expect((await api.orgUsers(admin.token)).find((u) => u.email === email)?.role).toBe('VIEWER');

    // Desactivar (con confirmación) y reactivar
    await r2.getByRole('button', { name: /Acciones de Marisol/ }).click();
    await page.getByRole('menuitem', { name: 'Desactivar usuario' }).click();
    const confirmDlg = page.getByRole('alertdialog');
    await expect(confirmDlg).toContainText('¿Desactivar a Marisol Operaciones?');
    await confirmDlg.getByRole('button', { name: 'Desactivar usuario' }).click();
    await expect(r2).toContainText('Desactivado');
    await expect(api.login(email, 'ClaveInvitada26!')).rejects.toThrow();
    await r2.getByRole('button', { name: /Acciones de Marisol/ }).click();
    await page.getByRole('menuitem', { name: 'Reactivar usuario' }).click();
    await expect(r2).toContainText('Activo');
    await api.login(email, 'ClaveInvitada26!');

    // Último administrador: no puede degradarse (USR-001) ni desactivarse
    const self = page.getByTestId('user-row').filter({ hasText: 'Tú' });
    await self.getByRole('button', { name: /Acciones de/ }).click();
    await expect(page.getByRole('menuitem', { name: 'No puedes desactivarte' })).toHaveAttribute('data-disabled', '');
    await page.getByRole('menuitem', { name: 'Cambiar rol' }).click();
    const selfDlg = page.getByRole('dialog', { name: 'Cambiar rol' });
    await expect(selfDlg).toContainText('Estás cambiando tu propio rol');
    await selfDlg.getByRole('radio', { name: /Operaciones/ }).click();
    await selfDlg.getByRole('button', { name: 'Guardar rol' }).click();
    await expect(selfDlg.getByRole('alert')).toContainText('administrador');
  });

  test('Lector: sin acciones de escritura y la API responde AUTH-007 si se fuerza una', async ({ page }) => {
    const admin = await api.registerAccount('PYME');
    const viewer = await api.userWithRole(admin.token, 'VIEWER');
    const carrier = await api.registerAccount('CARRIER');
    const vehicle = await api.addVehicle(carrier.token);
    const req = await api.createRequest(admin.token);
    await api.placeBid(carrier.token, req.id, vehicle.id, Math.ceil(req.priceFloor) + 30);

    await setSession(page.context(), viewer.token, BASE);
    await goto(page, '/pyme/solicitudes');
    await expect(page.getByRole('link', { name: 'Publicar solicitud' })).toHaveCount(0);
    await expect(page.getByRole('link', { name: 'Nueva solicitud' })).toHaveCount(0);
    await expect(page.getByTestId('read-only-notice').first()).toContainText('Tu rol Lector solo permite consultar');
    await expect(page.getByRole('button', { name: /Más acciones/ })).toHaveCount(0);

    await goto(page, `/pyme/solicitudes/${req.id}`);
    await expect(page.getByText('Comparación de ofertas').or(page.getByText(/oferta/i)).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cancelar solicitud' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Aceptar oferta' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Contraofertar/ })).toHaveCount(0);

    // V16 (roadmap 1.7): /nueva redirige al listado con el aviso (y «Pedir acceso»)
    await goto(page, '/pyme/solicitudes/nueva');
    await expect(page).toHaveURL(/\/pyme\/solicitudes\?sinPermiso=nueva-solicitud/);
    await expect(page.getByTestId('read-only-notice').filter({ hasText: 'No puedes publicar solicitudes' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Publicar solicitud' })).toHaveCount(0);

    // Forzar una action de escritura: el backend rechaza con AUTH-007 y el mensaje lo dice
    const res = await page.request.post('/_actions/requests.cancel/', { data: { id: req.id }, headers: { 'content-type': 'application/json' } });
    expect(res.status()).toBe(403);
    expect((await res.json()).message).toContain('Tu rol Lector solo permite consultar');
    expect((await api.raw('POST', `/shipment-requests/${req.id}/cancel`, viewer.token, {})).code).toBe('LOGYX-AUTH-007');
    expect((await api.raw('GET', '/organizations/me/users', viewer.token)).code).toBe('LOGYX-AUTH-008');
    await expect(page.getByRole('link', { name: 'Usuarios y roles' })).toHaveCount(0);

    // Cuenta propia: el Lector sí cambia su contraseña
    await goto(page, '/cuenta');
    await expect(page.getByRole('button', { name: 'Cambiar contraseña' })).toBeVisible();
  });

  test('cuentas demo: Operaciones sin Usuarios; Lector sin escritura (carga disponible)', async ({ page }) => {
    const ops = await api.login('pyme.ops@logyx.pe');
    const viewer = await api.login('pyme.viewer@logyx.pe');
    await setSession(page.context(), ops, BASE);
    await goto(page, '/pyme');
    await expect(page.getByRole('link', { name: 'Nueva solicitud' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Usuarios y roles' })).toHaveCount(0);
    expect((await page.request.get('/organizacion/usuarios')).status()).toBe(403);
    await page.context().clearCookies();
    await setSession(page.context(), viewer, BASE);
    await goto(page, '/pyme');
    await expect(page.getByRole('link', { name: 'Nueva solicitud' })).toHaveCount(0);
    await page.getByRole('button', { name: /Menú de cuenta/ }).first().click();
    await expect(page.getByTestId('user-role')).toHaveText('Lector');
  });

  test('transportista: Lector no opera la flota; Operaciones no gestiona conductores; ADMIN sí (desactivar, reactivar, restablecer)', async ({ page }) => {
    const admin = await api.registerAccount('CARRIER');
    const vehicle = await api.addVehicle(admin.token);
    const drv = await api.addDriver(admin.token, 'Conductor Gestionado');
    const viewer = await api.userWithRole(admin.token, 'VIEWER');
    const ops = await api.userWithRole(admin.token, 'OPS');

    // Lector: solo mira
    await setSession(page.context(), viewer.token, BASE);
    await goto(page, '/transportista/flota');
    await expect(page.getByRole('button', { name: 'Agregar vehículo' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Registrar conductor' })).toHaveCount(0);
    await expect(page.getByTestId('read-only-notice')).toContainText('No puedes agregar vehículos');
    await goto(page, '/transportista/viajes');
    await expect(page.getByRole('link', { name: 'Publicar viaje' })).toHaveCount(0);
    await goto(page, '/transportista/marketplace');
    await expect(page.getByRole('button', { name: 'Ofertar' })).toHaveCount(0);

    // Operaciones: vehículos sí, cuentas de conductores no
    await page.context().clearCookies();
    await setSession(page.context(), ops.token, BASE);
    await goto(page, '/transportista/flota');
    await expect(page.getByRole('button', { name: 'Agregar vehículo' }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Registrar conductor' })).toHaveCount(0);
    await expect(page.getByTestId('drivers-admin-only')).toBeVisible();
    await expect(page.getByRole('button', { name: /Acciones de Conductor Gestionado/ })).toHaveCount(0);
    expect((await api.raw('POST', `/drivers/${drv.id}/deactivate`, ops.token)).code).toBe('LOGYX-AUTH-008');

    // ADMIN: ciclo completo
    await page.context().clearCookies();
    await setSession(page.context(), admin.token, BASE);
    await goto(page, '/transportista/flota');
    const row = page.getByTestId('driver-row').filter({ hasText: 'Conductor Gestionado' });
    await expect(row).toContainText('Activo');
    await row.getByRole('button', { name: /Acciones de Conductor Gestionado/ }).click();
    await page.getByRole('menuitem', { name: 'Restablecer contraseña' }).click();
    const dlg = page.getByRole('dialog', { name: 'Restablecer contraseña' });
    await dlg.getByRole('button', { name: 'Restablecer contraseña' }).click();
    await expect(dlg.getByText('La contraseña debe tener al menos 8 caracteres.')).toBeVisible();
    await dlg.getByLabel('Contraseña nueva').fill('ClaveConductor26!');
    await dlg.getByRole('button', { name: 'Restablecer contraseña' }).click();
    await expect(toastWith(page, 'Contraseña restablecida')).toBeVisible();
    await expect(api.login(drv.email, api.PASSWORD)).rejects.toThrow();
    await api.login(drv.email, 'ClaveConductor26!');

    await row.getByRole('button', { name: /Acciones de Conductor Gestionado/ }).click();
    await page.getByRole('menuitem', { name: 'Desactivar conductor' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Desactivar conductor' }).click();
    await page.waitForLoadState('domcontentloaded');
    await hydrated(page);
    const row2 = page.getByTestId('driver-row').filter({ hasText: 'Conductor Gestionado' });
    await expect(row2).toContainText('Desactivado');
    await expect(api.login(drv.email, 'ClaveConductor26!')).rejects.toThrow();

    // Un conductor desactivado no se puede asignar (FLT-003)
    const pyme = await api.registerAccount('PYME');
    const req = await api.createRequest(pyme.token);
    const bid = await api.placeBid(admin.token, req.id, vehicle.id, Math.ceil(req.priceFloor) + 50);
    const accepted = await api.acceptBid(pyme.token, bid.id);
    const res = await api.raw('PUT', `/shipments/${accepted.shipmentId}/driver`, admin.token, { driverId: drv.id });
    expect(res.code).toBe('LOGYX-FLT-003');

    await row2.getByRole('button', { name: /Acciones de Conductor Gestionado/ }).click();
    await page.getByRole('menuitem', { name: 'Reactivar conductor' }).click();
    await page.waitForLoadState('domcontentloaded');
    await hydrated(page);
    await expect(page.getByTestId('driver-row').filter({ hasText: 'Conductor Gestionado' })).toContainText('Activo');
    await api.login(drv.email, 'ClaveConductor26!');
  });

  test('un 403 de otro tipo de cuenta muestra la página "Sin acceso" en vez de redirigir en silencio', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    const res = await page.goto('/transportista/marketplace');
    expect(res?.status()).toBe(403);
    await hydrated(page);
    await expect(page.getByRole('heading', { name: 'No tienes acceso a esta sección' })).toBeVisible();
    await page.getByRole('link', { name: 'Ir a mi panel' }).click();
    await expect(page).toHaveURL(/\/pyme$/);
    expect((await page.request.get('/operador')).status()).toBe(403);
  });
});

for (const theme of ['light', 'dark'] as const) {
  test.describe(`Accesibilidad usuarios y roles (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    });

    test('usuarios (tabla, invitar, cambiar rol), flota con conductores y 403', async ({ page }) => {
      const admin = await api.registerAccount('PYME');
      await api.inviteUser(admin.token, 'Invitada Pendiente', api.userEmail('p'), 'OPS');
      await api.userWithRole(admin.token, 'VIEWER');
      await setSession(page.context(), admin.token, BASE);
      await goto(page, '/organizacion/usuarios');
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/usuarios-${theme}.png`, fullPage: true });
      await page.getByRole('button', { name: 'Invitar usuario' }).first().click();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.keyboard.press('Escape');
      await page.getByRole('button', { name: /Acciones de Invitada/ }).click();
      await page.getByRole('menuitem', { name: 'Cambiar rol' }).click();
      await expectNoSeriousViolations(page, '[role=dialog]');
      await page.keyboard.press('Escape');

      const carrier = await api.registerAccount('CARRIER');
      await api.addDriver(carrier.token);
      await page.context().clearCookies();
      await setSession(page.context(), carrier.token, BASE);
      await goto(page, '/transportista/flota');
      await expectNoSeriousViolations(page);
      await page.getByRole('button', { name: /Acciones de Conductor/ }).click();
      await page.getByRole('menuitem', { name: 'Restablecer contraseña' }).click();
      await expectNoSeriousViolations(page, '[role=dialog]');

      await page.context().clearCookies();
      await setSession(page.context(), admin.token, BASE);
      await page.goto('/transportista/flota');
      await expect(page.getByTestId('forbidden')).toBeVisible();
      await expectNoSeriousViolations(page);
    });

    test('vistas de Lector (solicitudes y flota) sin violaciones', async ({ page }) => {
      const admin = await api.registerAccount('CARRIER');
      const viewer = await api.userWithRole(admin.token, 'VIEWER');
      await setSession(page.context(), viewer.token, BASE);
      await goto(page, '/transportista/flota');
      await expect(page.getByTestId('read-only-notice')).toBeVisible();
      await expectNoSeriousViolations(page);
      await goto(page, '/transportista/marketplace');
      await expectNoSeriousViolations(page);
    });
  });
}
