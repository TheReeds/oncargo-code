import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, pick, setSession, toastWith } from './helpers/ui';

// Ola 2 zona F (V24): 2.2 correo según la persona, preferencias de la organización y baja sin login; 2.17 términos.
const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const THEMES = ['light', 'dark'] as const;

async function withTheme(page: Page, theme: (typeof THEMES)[number]) {
  await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
}

/** Token de baja del correo de aviso más reciente que lo trae (`/baja?t=…`). */
async function leaveToken(email: string): Promise<string> {
  const rows = await api.mailbox(email, { timeoutMs: 15_000, matching: (m) => /\/baja\?t=/.test(m.text ?? '') });
  const m = rows[0]?.text.match(/\/baja\?t=([A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)/);
  if (!m) throw new Error(`No llegó un correo de aviso con enlace de baja a ${email}`);
  return m[1];
}

test.describe('Ola 2 zona F · preferencias, correo y términos', () => {
  for (const theme of THEMES) {
    test(`mis avisos: correo con resumen diario y horario de silencio (${theme})`, async ({ page }) => {
      await withTheme(page, theme);
      const pyme = await api.registerAccount('PYME');
      await setSession(page.context(), pyme.token, BASE);
      await goto(page, '/cuenta/avisos');
      const card = page.getByTestId('email-settings');
      await expect(card.getByRole('switch', { name: 'Recibir avisos por correo' })).toBeChecked();
      await card.getByRole('radio', { name: 'Resumen diario' }).click();
      await pick(page, card.getByRole('combobox', { name: 'Hora del resumen' }), '18:00');
      await card.getByRole('switch', { name: 'Horario de silencio' }).click();
      await expect(card.getByRole('combobox', { name: 'Desde' })).toContainText('22:00');
      await expectNoSeriousViolations(page);
      await card.getByRole('button', { name: 'Guardar correos' }).click();
      await expect(toastWith(page, 'Correos guardados')).toBeVisible();
      const saved = await api.call<any>('GET', '/notifications/email-settings', pyme.token);
      expect(saved).toMatchObject({ emailEnabled: true, mode: 'DAILY', digestHour: 18 });
      expect(saved.quietFrom).toMatch(/^22:00/);
      expect(saved.quietTo).toMatch(/^07:00/);
      await page.reload();
      await expect(page.getByTestId('email-settings').getByRole('radio', { name: 'Resumen diario' })).toBeChecked();
    });
  }

  for (const theme of THEMES) {
    test(`preferencias de la empresa: el ADMIN guarda rutas y el Lector solo las ve (${theme})`, async ({ page, browser }) => {
      await withTheme(page, theme);
      const carrier = await api.registerAccount('CARRIER');
      await setSession(page.context(), carrier.token, BASE);
      await goto(page, '/organizacion');
      await page.getByTestId('org-preferences-link').click();
      await expect(page).toHaveURL(/\/organizacion\/preferencias$/);
      await page.getByRole('button', { name: 'Agregar ruta' }).click();
      await pick(page, page.getByRole('combobox', { name: 'Origen de la ruta 1' }), 'Lima');
      await pick(page, page.getByRole('combobox', { name: 'Destino de la ruta 1' }), 'Juliaca');
      await page.getByLabel('Precio mínimo por km').fill('2.5');
      await expect(page.getByRole('switch', { name: 'Alertas por mis rutas' })).toBeChecked();
      await expectNoSeriousViolations(page);
      await page.getByRole('button', { name: 'Guardar preferencias' }).click();
      await expect(toastWith(page, 'Preferencias guardadas')).toBeVisible();
      const prefs = await api.call<any>('GET', '/organizations/me/preferences', carrier.token);
      expect(prefs.routes).toEqual([{ origin: 'LIMA', destination: 'JULIACA' }]);
      expect(prefs.minPricePerKm).toBe(2.5);

      // Origen = destino: error en la página, sin llamar a la API
      await page.getByRole('button', { name: 'Agregar ruta' }).click();
      await pick(page, page.getByRole('combobox', { name: 'Origen de la ruta 2' }), 'Puno');
      await pick(page, page.getByRole('combobox', { name: 'Destino de la ruta 2' }), 'Puno');
      await page.getByRole('button', { name: 'Guardar preferencias' }).click();
      await expect(page.getByRole('alert').filter({ hasText: 'origen y destino distintos' })).toBeVisible();

      const viewer = await api.userWithRole(carrier.token, 'VIEWER');
      const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE' });
      await ctx.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
      const vp = await ctx.newPage();
      await setSession(ctx, viewer.token, BASE);
      await goto(vp, '/organizacion/preferencias');
      const summary = vp.getByTestId('org-preferences-summary');
      await expect(summary).toContainText('Lima → Juliaca');
      await expect(summary).toContainText('S/ 2.50');
      await expect(vp.getByRole('button', { name: 'Guardar preferencias' })).toHaveCount(0);
      await expectNoSeriousViolations(vp);
      await ctx.close();
    });
  }

  test('preferencias de la PYME: horario de recojo y plazo de pago', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await goto(page, '/organizacion/preferencias');
    await pick(page, page.getByRole('combobox', { name: 'Desde' }), '08:00');
    await pick(page, page.getByRole('combobox', { name: 'Hasta' }), '12:30');
    await page.getByLabel('Plazo de pago a transportistas').fill('30');
    await expect(page.getByLabel('Precio mínimo por km')).toHaveCount(0);
    await page.getByRole('button', { name: 'Guardar preferencias' }).click();
    await expect(toastWith(page, 'Preferencias guardadas')).toBeVisible();
    const prefs = await api.call<any>('GET', '/organizations/me/preferences', pyme.token);
    expect(prefs).toMatchObject({ paymentTermsDays: 30 });
    expect(prefs.defaultPickupFrom).toMatch(/^08:00/);
  });

  for (const theme of THEMES) {
    test(`darse de baja sin iniciar sesión con el enlace del correo (${theme})`, async ({ page, request }) => {
      await withTheme(page, theme);
      const s = await api.openBidScenario();
      await api.acceptBid(s.pyme.token, s.bid.id);                      // «Adjudicaciones» llega por correo por omisión
      const token = await leaveToken(s.carrier.email);

      await goto(page, `/baja?t=${token}`);
      await expect(page.getByTestId('unsubscribe-confirm')).toContainText('«Adjudicaciones»');
      await expect(page.getByTestId('unsubscribe-confirm')).toContainText('•••@');
      await expectNoSeriousViolations(page);
      await page.getByRole('button', { name: 'Darme de baja de «Adjudicaciones»' }).click();
      await expect(page.getByTestId('unsubscribe-done')).toContainText('Ya no te enviaremos correos de «Adjudicaciones»');
      await expectNoSeriousViolations(page);
      const prefs = await api.call<any[]>('GET', '/notifications/preferences', s.carrier.token);
      expect(prefs.find((p) => p.category === 'AWARDS')).toMatchObject({ email: false, inApp: true });

      // One-click (RFC 8058) desde el cliente de correo: sin cookies ni origen
      const oneClick = await request.post(`${BASE}/baja/one-click?t=${token}`, {
        headers: { 'content-type': 'application/x-www-form-urlencoded' }, data: 'List-Unsubscribe=One-Click',
      });
      expect(oneClick.status()).toBe(200);
      expect((await request.post(`${BASE}/baja/one-click?t=${token}`, { headers: { 'content-type': 'application/x-www-form-urlencoded' }, data: 'otra=cosa' })).status()).toBe(400);

      // Un enlace alterado no revela nada
      await goto(page, `/baja?t=${token.slice(0, -3)}abc`);
      await expect(page.getByTestId('unsubscribe-invalid')).toBeVisible();
      await expectNoSeriousViolations(page);
    });
  }

  for (const theme of THEMES) {
    test(`términos nuevos: diálogo que no se cierra hasta aceptarlos (${theme})`, async ({ page }) => {
      await withTheme(page, theme);
      const pyme = await api.registerAccount('PYME');
      await api.forgetTerms(pyme.email);
      await setSession(page.context(), pyme.token, BASE);
      await goto(page, '/pyme');
      const gate = page.getByRole('alertdialog', { name: 'Actualizamos los Términos y condiciones' });
      await expect(gate).toBeVisible();
      await expect(gate.getByRole('link', { name: 'Leer los Términos y condiciones' })).toHaveAttribute('href', '/terminos');
      await expectNoSeriousViolations(page);
      await page.keyboard.press('Escape');
      await expect(gate).toBeVisible();
      await gate.getByRole('button', { name: 'Aceptar términos' }).click();
      await expect(gate.getByRole('alert')).toContainText('Marca la casilla');
      await gate.getByRole('checkbox', { name: 'Leí y acepto los Términos y condiciones' }).click();
      await gate.getByRole('button', { name: 'Aceptar términos' }).click();
      await expect(gate).toBeHidden();
      await expect(toastWith(page, 'Términos aceptados')).toBeVisible();
      expect((await api.call<any>('GET', '/auth/me', pyme.token)).termsAccepted).toBe(true);
      await page.reload();
      await expect(page.getByRole('alertdialog')).toHaveCount(0);
    });
  }

  test('seguridad y seguro, y la verificación con plazo y lo que se revisa', async ({ page }) => {
    await goto(page, '/seguridad');
    await expect(page.getByRole('heading', { level: 1, name: 'Seguridad y seguro' })).toBeVisible();
    await expect(page.getByText('Borrador — pendiente de revisión legal').first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Lo que cubre el transportista' })).toBeVisible();
    await expectNoSeriousViolations(page);

    // En dev la cuenta recién registrada queda «En revisión» (la verificación no se exige para operar, pero se muestra).
    const pending = await api.registerAccount('PYME');
    const me = await api.call<any>('GET', '/auth/me', pending.token);
    test.skip(me.verificationStatus !== 'PENDING', 'el backend de pruebas verifica las cuentas al registrarlas');
    await setSession(page.context(), pending.token, BASE);
    await goto(page, '/pyme');
    await expect(page.getByText('Lo revisamos en 1 día hábil')).toBeVisible();
    await expect(page.getByTestId('verification-checklist')).toContainText('Que el RUC exista y esté activo');
    await expectNoSeriousViolations(page);
  });
});
