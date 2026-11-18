import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, hydrated, loginViaUI, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';
const NEW_PASSWORD = 'NuevaClave2026!';

/** Contraseñas (olvido, restablecimiento, cambio, cierre global) y la página Cuenta, para todos los roles web. */
test.describe('Contraseñas y cuenta', () => {
  test('olvidé mi contraseña: confirmación idéntica, enlace del correo, nueva contraseña y vuelta al login', async ({ page }) => {
    const acc = await api.registerAccount('PYME');
    await goto(page, '/login');
    await page.getByRole('link', { name: '¿Olvidaste tu contraseña?' }).click();
    await expect(page).toHaveURL(/\/olvide$/);
    await hydrated(page);

    // Un correo que no existe recibe exactamente la misma confirmación (sin enumeración).
    const ghost = api.userEmail('fantasma');
    await page.getByLabel('Correo electrónico').fill(ghost);
    await page.getByRole('button', { name: 'Enviar enlace' }).click();
    const sent = page.getByTestId('forgot-sent');
    await expect(sent).toContainText('Revisa tu correo');
    const ghostText = (await sent.innerText()).replace(ghost, '<correo>');
    await page.getByRole('button', { name: 'Usar otro correo' }).click();

    await page.getByLabel('Correo electrónico').fill(acc.email);
    await page.getByRole('button', { name: 'Enviar enlace' }).click();
    await expect(sent).toContainText('Revisa tu correo');
    expect((await sent.innerText()).replace(acc.email, '<correo>')).toBe(ghostText);

    const { path } = await api.resetLinkFor(acc.email);
    await page.goto(path);
    await hydrated(page);
    await expect(page.getByRole('heading', { name: 'Restablece tu contraseña' })).toBeVisible();
    await expect(page.getByTestId('reset-info')).toContainText('•'); // correo enmascarado
    await expect(page.getByTestId('reset-info')).not.toContainText(acc.email);
    // El token sale de la barra de direcciones (historial y Referer).
    await expect(page).toHaveURL(/\/restablecer$/);

    const pw = page.getByLabel('Contraseña nueva', { exact: true });
    await expect(pw).toHaveAttribute('autocomplete', 'new-password');
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();
    await expect(page.getByText('Ingresa tu contraseña nueva.')).toBeVisible();
    await pw.fill('corta');
    await page.getByLabel('Repite la contraseña nueva').fill('corta');
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();
    await expect(page.getByText('La contraseña debe tener entre 8 y 72 caracteres.')).toBeVisible();
    await pw.fill(NEW_PASSWORD);
    await page.getByLabel('Repite la contraseña nueva').fill(NEW_PASSWORD + 'x');
    await expect(page.getByRole('list', { name: 'Requisitos de la contraseña' })).toContainText('Entre 8 y 72 caracteres: cumplido');
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();
    await expect(page.getByText('Las contraseñas no coinciden.')).toBeVisible();
    await page.getByLabel('Repite la contraseña nueva').fill(NEW_PASSWORD);
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();

    await expect(page).toHaveURL(/\/login\?restablecida=1/);
    await expect(page.getByText('Contraseña actualizada. Ingresa con tu contraseña nueva.')).toBeVisible();
    await page.getByLabel('Correo electrónico').fill(acc.email);
    await page.getByLabel('Contraseña', { exact: true }).fill(NEW_PASSWORD);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(page).toHaveURL(/\/pyme$/);

    // La contraseña anterior ya no sirve y el enlace usado tampoco.
    await expect(api.login(acc.email, acc.password)).rejects.toThrow();
    await page.context().clearCookies();
    await page.goto(path);
    await expect(page.getByRole('heading', { name: 'Este enlace ya no sirve' })).toBeVisible();
  });

  test('enlace inválido o ausente: estado claro con "Pedir otro enlace"', async ({ page }) => {
    await page.goto('/restablecer?token=' + 'x'.repeat(43));
    await expect(page.getByRole('heading', { name: 'Este enlace ya no sirve' })).toBeVisible();
    await expect(page.getByTestId('reset-invalid')).toContainText('venció, ya se usó o fue reemplazado');
    await page.getByRole('link', { name: 'Pedir otro enlace' }).click();
    await expect(page).toHaveURL(/\/olvide$/);
    await page.goto('/restablecer');
    await expect(page.getByTestId('reset-invalid')).toContainText('Falta el enlace');
    const res = await page.request.get('/restablecer?token=abc');
    expect(res.headers()['referrer-policy']).toBe('no-referrer');
  });

  test('cambiar contraseña: errores en línea, cookie nueva y la otra sesión queda fuera', async ({ page, browser }) => {
    const acc = await api.registerAccount('PYME');
    const other = await api.login(acc.email, acc.password); // "otro dispositivo"
    await setSession(page.context(), acc.token, BASE);
    await goto(page, '/cuenta');
    await expect(page.getByRole('heading', { name: 'Mi cuenta' })).toBeVisible();
    await expect(page.getByText('Administrador').first()).toBeVisible();

    const current = page.getByLabel('Contraseña actual', { exact: true });
    const next = page.getByLabel('Contraseña nueva', { exact: true });
    const confirm = page.getByLabel('Repite la contraseña nueva');
    await current.fill('incorrecta123');
    await next.fill(NEW_PASSWORD);
    await confirm.fill(NEW_PASSWORD);
    await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
    await expect(page.getByRole('alert').filter({ hasText: /contraseña actual/i })).toBeVisible(); // AUTH-006 bajo el campo
    await expect(current).toHaveAttribute('aria-invalid', 'true');

    await current.fill(acc.password);
    await next.fill(acc.password);
    await confirm.fill(acc.password);
    await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
    await expect(page.getByText('La contraseña nueva debe ser distinta de la actual.')).toBeVisible(); // AUTH-010

    await next.fill(NEW_PASSWORD);
    await confirm.fill(NEW_PASSWORD);
    await page.getByRole('button', { name: 'Cambiar contraseña' }).click();
    await expect(toastWith(page, 'Contraseña actualizada')).toBeVisible();

    // Este navegador sigue dentro (la cookie se reemplazó); el otro dispositivo, no.
    await page.goto('/pyme');
    await expect(page).toHaveURL(/\/pyme$/);
    const ctx = await browser.newContext({ baseURL: BASE });
    await setSession(ctx, other, BASE);
    const p2 = await ctx.newPage();
    await p2.goto('/pyme');
    await expect(p2).toHaveURL(/\/login\?expired=1/);
    await expect(p2.getByText('Tu sesión se cerró o venció')).toBeVisible();
    await ctx.close();
    expect((await api.raw('GET', '/auth/me', other)).status).toBe(401);
    await api.login(acc.email, NEW_PASSWORD);
  });

  test('cerrar sesión en todos los dispositivos', async ({ page }) => {
    const acc = await api.registerAccount('CARRIER');
    const other = await api.login(acc.email, acc.password);
    await setSession(page.context(), acc.token, BASE);
    await goto(page, '/cuenta');
    await page.getByRole('button', { name: 'Cerrar sesión en todos los dispositivos' }).click();
    const dialog = page.getByRole('alertdialog');
    await expect(dialog).toContainText('¿Cerrar sesión en todos los dispositivos?');
    await dialog.getByRole('button', { name: 'Volver' }).click();
    await expect(page).toHaveURL(/\/cuenta$/);
    await page.getByRole('button', { name: 'Cerrar sesión en todos los dispositivos' }).click();
    await page.getByRole('alertdialog').getByRole('button', { name: 'Cerrar todas las sesiones' }).click();
    await expect(page).toHaveURL(/\/login/);
    expect((await api.raw('GET', '/auth/me', other)).status).toBe(401);
    expect((await api.raw('GET', '/auth/me', acc.token)).status).toBe(401);
  });

  test('el menú de cuenta enlaza a "Mi cuenta" y el conductor también tiene Cuenta', async ({ page }) => {
    const carrier = await api.registerAccount('CARRIER');
    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista');
    await page.getByRole('button', { name: /Menú de cuenta/ }).first().click();
    await page.getByRole('menuitem', { name: 'Mi cuenta' }).click();
    await expect(page).toHaveURL(/\/cuenta$/);
    await expect(page.getByRole('heading', { name: 'Mi cuenta' })).toBeVisible();

    const drv = await api.addDriver(carrier.token);
    const drvToken = await api.login(drv.email);
    const ctx = await page.context().browser()!.newContext({ baseURL: BASE });
    await setSession(ctx, drvToken, BASE);
    const dp = await ctx.newPage();
    await goto(dp, '/conductor');
    await dp.getByRole('link', { name: 'Mi cuenta' }).click();
    await expect(dp).toHaveURL(/\/cuenta$/);
    await hydrated(dp);
    await expect(dp.getByRole('heading', { name: 'Mi cuenta' })).toBeVisible();
    await expect(dp.getByText('Conductor').first()).toBeVisible();
    // El conductor cambia su contraseña (todos los roles pueden) y ve su historial vacío en /conductor.
    await dp.getByLabel('Contraseña actual', { exact: true }).fill(api.PASSWORD);
    await dp.getByLabel('Contraseña nueva', { exact: true }).fill(NEW_PASSWORD);
    await dp.getByLabel('Repite la contraseña nueva').fill(NEW_PASSWORD);
    await dp.getByRole('button', { name: 'Cambiar contraseña' }).click();
    await expect(toastWith(dp, 'Contraseña actualizada')).toBeVisible();
    await dp.goto('/conductor');
    await expect(dp.getByTestId('driver-history')).toContainText('Todavía no has terminado ningún viaje');
    await dp.goto('/pyme');
    await expect(dp).toHaveURL(/\/conductor$/);
    await ctx.close();
  });
});

for (const theme of ['light', 'dark'] as const) {
  test.describe(`Accesibilidad contraseñas y cuenta (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    });

    test('olvido, restablecer (RESET e INVITE), enlace inválido y cuenta', async ({ page }) => {
      await goto(page, '/olvide');
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
      await page.getByLabel('Correo electrónico').fill(api.userEmail('a11y'));
      await page.getByRole('button', { name: 'Enviar enlace' }).click();
      await expect(page.getByTestId('forgot-sent')).toBeVisible();
      await expectNoSeriousViolations(page);

      const admin = await api.registerAccount('PYME');
      await api.forgotPassword(admin.email);
      const reset = await api.resetLinkFor(admin.email);
      await page.goto(reset.path);
      await hydrated(page);
      await expect(page.getByRole('heading', { name: 'Restablece tu contraseña' })).toBeVisible();
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/restablecer-${theme}.png` });

      const email = api.userEmail('inv');
      await api.inviteUser(admin.token, 'Invitada A11y', email, 'OPS');
      const invite = await api.resetLinkFor(email);
      await page.goto(invite.path);
      await hydrated(page);
      await expect(page.getByRole('heading', { name: /Activa tu cuenta en/ })).toContainText(admin.name);
      await page.getByRole('button', { name: 'Activar cuenta' }).click();
      await expect(page.getByText('Acepta la política de privacidad para activar tu cuenta.')).toBeVisible();
      await expectNoSeriousViolations(page);

      await page.goto('/restablecer?token=' + 'y'.repeat(43));
      await expect(page.getByTestId('reset-invalid')).toBeVisible();
      await expectNoSeriousViolations(page);

      await setSession(page.context(), admin.token, BASE);
      await goto(page, '/cuenta');
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/cuenta-${theme}.png`, fullPage: true });
    });
  });
}
