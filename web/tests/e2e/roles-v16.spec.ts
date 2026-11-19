import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, setSession } from './helpers/ui';

// Ola 1 zona D (roadmap v2 1.7, 1.15, 1.8): interfaz según el rol, «Pedir acceso», avisos por persona, preferencias y autoría.
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

async function pymeTeam() {
  const admin = await api.registerAccount('PYME');
  const viewer = await api.userWithRole(admin.token, 'VIEWER');
  const carrier = await api.registerAccount('CARRIER');
  const vehicle = await api.addVehicle(carrier.token);
  const req = await api.createRequest(admin.token);
  await api.placeBid(carrier.token, req.id, vehicle.id, Math.ceil(req.priceFloor) + 40);
  return { admin, viewer, carrier, req };
}

test.describe('Interfaz según el rol y avisos por persona (V16)', () => {
  test('Lector: rol visible, rutas de escritura redirigidas, sin acciones y «Pedir acceso» avisa al ADMIN', async ({ page }) => {
    const { admin, viewer, req } = await pymeTeam();
    await setSession(page.context(), viewer.token, BASE);

    await goto(page, '/transportista/viajes/nuevo'); // otra sección: 403 amable, no redirección
    await expect(page.getByTestId('forbidden')).toBeVisible();
    await goto(page, '/pyme/solicitudes/nueva');
    await expect(page).toHaveURL(/sinPermiso=nueva-solicitud/);
    const notice = page.getByTestId('read-only-notice').filter({ hasText: 'No puedes publicar solicitudes' });
    await expect(notice).toBeVisible();
    await expect(page.getByTestId('topbar-role')).toHaveText('Lector');
    await expect(page.getByTestId('sidebar-role')).toContainText('Lector');

    // Detalle: sin botones de escritura, con autoría y actividad visibles
    await goto(page, `/pyme/solicitudes/${req.id}`);
    await expect(page.getByRole('button', { name: 'Aceptar oferta' })).toHaveCount(0);
    await expect(page.getByTestId('author-line').first()).toContainText('Publicada por');
    await expect(page.getByTestId('activity-trail')).toContainText('Solicitud publicada');

    // Pedir acceso desde el menú de cuenta
    await page.getByRole('button', { name: /Menú de cuenta/ }).first().click();
    await page.getByRole('menuitem', { name: 'Pedir acceso' }).click();
    const dlg = page.getByRole('dialog', { name: 'Pedir acceso' });
    await expect(dlg).toContainText('Operar');
    await dlg.getByLabel('Motivo (opcional)').fill('Publico las cargas de la planta de Arequipa.');
    await expectNoSeriousViolations(page, '[role=dialog]');
    await dlg.getByRole('button', { name: 'Enviar pedido' }).click();
    await expect(dlg).toBeHidden();
    await expect(page.getByText('Pedido enviado')).toBeVisible();

    const adminNotes = await api.notificationsOf(admin.token);
    const ask = adminNotes.find((n) => n.type === 'ACCESS_REQUESTED');
    expect(ask?.body).toContain('Arequipa');
    expect(ask?.relatedType).toBe('USER');
    // Un segundo pedido enseguida: 429 con mensaje en español
    expect((await api.raw('POST', '/organizations/me/access-requests', viewer.token, { permission: 'OPERATE' })).code).toBe('LOGYX-ACC-002');
  });

  test('un aviso leído por el Lector sigue sin leer para el ADMIN; «Tuyo» y «Solo míos»', async ({ page }) => {
    const { admin, viewer } = await pymeTeam();
    await setSession(page.context(), viewer.token, BASE);
    await goto(page, '/notificaciones');
    await expect(page.getByText('Nueva oferta recibida').first()).toBeVisible();
    await page.getByRole('button', { name: 'Marcar todas como leídas' }).click();
    await expect(page.getByText('Notificaciones marcadas como leídas')).toBeVisible();
    expect((await api.call<{ unread: number }>('GET', '/notifications/unread-count', viewer.token)).unread).toBe(0);
    expect((await api.call<{ unread: number }>('GET', '/notifications/unread-count', admin.token)).unread).toBeGreaterThan(0);

    // El ADMIN publicó la solicitud: su aviso es «Tuyo» y aparece en «Solo míos»
    await page.context().clearCookies();
    await setSession(page.context(), admin.token, BASE);
    await goto(page, '/notificaciones');
    await expect(page.getByTestId('notification-mine').first()).toHaveText('Tuyo');
    await page.getByRole('tab', { name: 'Solo míos' }).click();
    await expect(page.getByText('Nueva oferta recibida').first()).toBeVisible();
  });

  test('preferencias de aviso en /cuenta/avisos (también el Lector)', async ({ page }) => {
    const { viewer } = await pymeTeam();
    await setSession(page.context(), viewer.token, BASE);
    await goto(page, '/cuenta/avisos');
    await expect(page.getByRole('heading', { name: 'Mis avisos' })).toBeVisible();
    await expect(page.getByRole('switch', { name: 'Cuenta y accesos: en la app', exact: true })).toBeDisabled();
    await page.getByRole('switch', { name: 'Ofertas: por correo', exact: true }).click();
    await page.getByRole('button', { name: 'Guardar avisos' }).click();
    await expect(page.getByText('Avisos guardados')).toBeVisible();
    const prefs = await api.call<any[]>('GET', '/notifications/preferences', viewer.token);
    expect(prefs.find((p) => p.category === 'BIDS')?.email).toBe(true);
  });
});

for (const theme of ['light', 'dark'] as const) {
  test.describe(`Accesibilidad zona D (${theme})`, () => {
    test('avisos, preferencias y aviso de solo lectura sin violaciones', async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
      const { viewer, req } = await pymeTeam();
      await setSession(page.context(), viewer.token, BASE);
      await goto(page, '/pyme/solicitudes/nueva');
      await expect(page.getByTestId('read-only-notice').first()).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
      await goto(page, '/cuenta/avisos');
      await expectNoSeriousViolations(page);
      await goto(page, '/notificaciones');
      await expectNoSeriousViolations(page);
      await goto(page, `/pyme/solicitudes/${req.id}`);
      await page.getByTestId('activity-trail').scrollIntoViewIfNeeded();
      await expect(page.getByTestId('activity-trail')).toContainText('Solicitud publicada');
      await expectNoSeriousViolations(page);
    });
  });
}
