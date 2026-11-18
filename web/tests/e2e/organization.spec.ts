import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, hydrated, pick, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

/** Perfil de la organización (B3): lo ven todos, lo edita el ADMIN; razón social y RUC solo con verificación PENDING/REJECTED. */
test.describe('Perfil de la empresa', () => {
  test('ADMIN edita contacto y datos legales mientras está en revisión; al verificarse quedan bloqueados', async ({ page }) => {
    const acc = await api.registerAccount('PYME');
    const op = await api.operatorToken();
    await setSession(page.context(), acc.token, BASE);
    await goto(page, '/organizacion');
    await expect(page.getByRole('heading', { name: 'Perfil de la empresa' })).toBeVisible();
    await expect(page.getByTestId('verification-summary')).toContainText('En revisión');
    await expect(page.getByText(acc.ruc).first()).toBeVisible();

    // Contacto
    const save = page.getByRole('button', { name: 'Guardar cambios' });
    await expect(save).toBeDisabled();
    await page.getByLabel('Teléfono').fill('abc');
    await page.getByLabel('Dirección').fill('Av. Argentina 1234, Callao');
    await pick(page, page.getByRole('combobox', { name: 'Ciudad' }), 'Arequipa');
    await save.click();
    await expect(page.getByText('Ingresa un teléfono válido')).toBeVisible();
    await page.getByLabel('Teléfono').fill('987 654 321');
    await save.click();
    await page.waitForLoadState('domcontentloaded');
    await hydrated(page);
    await expect(toastWith(page, 'Datos de la empresa actualizados')).toBeVisible();
    await expect(page.getByText('987 654 321').first()).toBeVisible();
    await expect(page.getByText('Av. Argentina 1234, Callao').first()).toBeVisible();

    // RN07 en la razón social (NEG-001) y RUC duplicado (ORG-002), inline
    const other = await api.registerAccount('CARRIER');
    await page.getByLabel('Razón social').fill('Llama al 987 654 321 SAC');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByRole('alert').filter({ hasText: /No puedes compartir teléfonos/ })).toBeVisible();
    await page.getByLabel('Razón social').fill(acc.name);
    await page.getByLabel('RUC').fill(other.ruc);
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByRole('alert').filter({ hasText: /RUC/ }).first()).toBeVisible();
    await expect(page.getByLabel('RUC')).toHaveAttribute('aria-invalid', 'true');

    // Corrección legal válida
    await page.getByLabel('RUC').fill(acc.ruc);
    await page.getByLabel('Razón social').fill(`${acc.name} Corregida`);
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await page.waitForLoadState('domcontentloaded');
    await hydrated(page);
    await expect(page.getByText(`${acc.name} Corregida`).first()).toBeVisible();

    // Verificada: datos legales bloqueados con explicación (ORG-007 si se fuerza)
    const orgId = await api.orgIdOf(acc.token);
    await api.verifyOrg(op, orgId);
    await page.reload();
    await hydrated(page);
    await expect(page.getByTestId('verification-summary')).toContainText('Verificada');
    await expect(page.getByTestId('legal-locked')).toContainText('no se pueden cambiar');
    await expect(page.getByLabel('RUC')).toHaveCount(0);
    expect((await api.raw('PATCH', '/organizations/me', acc.token, { legalName: 'Otro nombre SAC' })).code).toBe('LOGYX-ORG-007');
    // Aun así, el contacto sigue editable
    await page.getByLabel('Teléfono').fill('912 345 678');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await page.waitForLoadState('domcontentloaded');
    await hydrated(page);
    await expect(page.getByText('912 345 678').first()).toBeVisible();
  });

  test('desde RECHAZADA, corregir razón social o RUC la devuelve a revisión (con aviso previo)', async ({ page }) => {
    const acc = await api.registerAccount('CARRIER');
    const op = await api.operatorToken();
    const orgId = await api.orgIdOf(acc.token);
    await api.rejectOrg(op, orgId, 'El RUC no coincide con la razón social');
    await setSession(page.context(), acc.token, BASE);
    await goto(page, '/organizacion');
    await expect(page.getByTestId('verification-summary')).toContainText('Rechazada');
    await expect(page.getByTestId('verification-summary')).toContainText('El RUC no coincide');
    await expect(page.getByText('Si corriges la razón social o el RUC, tu empresa vuelve a revisión')).toBeVisible();
    await page.getByLabel('Razón social').fill(`${acc.name} Revisada`);
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await page.waitForLoadState('domcontentloaded');
    await hydrated(page);
    await expect(page.getByTestId('verification-summary')).toContainText('En revisión');
  });

  test('OPS y Lector ven el perfil pero no lo editan (la API responde AUTH-008 / AUTH-007)', async ({ page }) => {
    const admin = await api.registerAccount('PYME');
    const ops = await api.userWithRole(admin.token, 'OPS');
    const viewer = await api.userWithRole(admin.token, 'VIEWER');
    for (const [who, token, text, code] of [['OPS', ops.token, 'Tu rol Operaciones no administra', 'LOGYX-AUTH-008'], ['VIEWER', viewer.token, 'Tu rol Lector solo permite consultar', 'LOGYX-AUTH-007']] as const) {
      await page.context().clearCookies();
      await setSession(page.context(), token, BASE);
      await goto(page, '/organizacion');
      await expect(page.getByText('Solo un administrador puede editar estos datos'), who).toBeVisible();
      await expect(page.getByText(text).first()).toBeVisible();
      await expect(page.getByRole('button', { name: 'Guardar cambios' })).toHaveCount(0);
      await expect(page.getByText(admin.ruc).first()).toBeVisible();
      expect((await api.raw('PATCH', '/organizations/me', token, { phone: '999 999 999' })).code).toBe(code);
    }
  });
});

for (const theme of ['light', 'dark'] as const) {
  test.describe(`Accesibilidad perfil de la empresa (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    });
    test('perfil editable, rechazada, bloqueado y solo lectura', async ({ page }) => {
      const acc = await api.registerAccount('CARRIER');
      const op = await api.operatorToken();
      await setSession(page.context(), acc.token, BASE);
      await goto(page, '/organizacion');
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expectNoSeriousViolations(page);
      await page.screenshot({ path: `${SHOTS}/organizacion-perfil-${theme}.png`, fullPage: true });
      await api.rejectOrg(op, await api.orgIdOf(acc.token), 'Datos incompletos');
      await page.reload();
      await hydrated(page);
      await expectNoSeriousViolations(page);
      await api.verifyOrg(op, await api.orgIdOf(acc.token), 'Revisada');
      await page.reload();
      await hydrated(page);
      await expectNoSeriousViolations(page);
      const viewer = await api.userWithRole(acc.token, 'VIEWER');
      await page.context().clearCookies();
      await setSession(page.context(), viewer.token, BASE);
      await goto(page, '/organizacion');
      await expectNoSeriousViolations(page);
    });
  });
}
