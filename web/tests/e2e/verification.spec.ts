import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { fillRequestForm, goto, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

test.describe('Verificación de empresas (RN01, F04/F05)', () => {
  test('el operador suspende y reactiva; la empresa ve el banner y recibe ORG-004', async ({ page, browser }) => {
    const op = await api.operatorToken();
    const pyme = await api.registerAccount('PYME');
    const pymeOrg = await api.orgIdOf(pyme.token);

    // La empresa nueva queda en revisión: banner informativo
    const pctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE' });
    const ppage = await pctx.newPage();
    await setSession(pctx, pyme.token, BASE);
    await goto(ppage, '/pyme');
    await expect(ppage.getByRole('status').filter({ hasText: 'Estamos verificando tu empresa' })).toBeVisible();
    await expect(ppage.getByText('Verificado por OnCargo')).toHaveCount(0);

    // Bandeja del operador: la empresa aparece como pendiente
    await setSession(page.context(), op, BASE);
    const p1 = await api.orgInboxPage(op, 'PENDING', pymeOrg);
    await goto(page, `/operador/organizaciones${p1 > 1 ? `?pagina=${p1}` : ''}`);
    await expect(page.getByRole('heading', { name: 'Verificación de empresas' })).toBeVisible();
    const row = page.getByTestId('org-row').filter({ hasText: pyme.name });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /Revisar/ }).click();
    const sheet = page.getByRole('dialog', { name: pyme.name });
    await expect(sheet.getByText(pyme.ruc)).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/operador-organizaciones-detalle.png` });

    // Suspender exige motivo: error inline y luego éxito
    await sheet.getByRole('button', { name: 'Suspender empresa' }).click();
    const dlg = page.getByRole('dialog', { name: new RegExp(`Suspender a ${pyme.name}`) });
    await dlg.getByRole('button', { name: 'Suspender empresa' }).click();
    await expect(dlg.getByText(/Indica el motivo/)).toBeVisible();
    await dlg.getByLabel('Motivo').fill('Reportes reiterados de incumplimiento');
    await dlg.getByRole('button', { name: 'Suspender empresa' }).click();
    await expect(toastWith(page, 'Empresa suspendida')).toBeVisible();
    expect((await api.call<any>('GET', '/auth/me', pyme.token)).verificationStatus).toBe('SUSPENDED');

    // La empresa ve el banner de suspensión con el motivo y no puede publicar (ORG-004 con explicación, no un toast)
    await ppage.reload();
    const banner = ppage.getByRole('alert').filter({ hasText: 'Tu empresa está suspendida' });
    await expect(banner).toBeVisible();
    await expect(banner).toContainText('Reportes reiterados de incumplimiento');
    await ppage.screenshot({ path: `${SHOTS}/verificacion-banner-suspendida.png` });
    await goto(ppage, '/pyme/solicitudes/nueva');
    await fillRequestForm(ppage, { pickupIso: api.isoDate(4) });
    await ppage.getByRole('button', { name: 'Publicar solicitud' }).click();
    const block = ppage.getByRole('alert').filter({ hasText: 'Tu empresa está suspendida' }).last();
    await expect(block).toContainText(/suspendida por LOGYX/);
    await expect(block).toContainText(/Escribe a/);
    await expect(ppage).toHaveURL(/\/pyme\/solicitudes\/nueva/);

    // Reactivar: la empresa queda verificada, el banner desaparece y llega el aviso
    await goto(page, `/operador/organizaciones?estado=SUSPENDED&pagina=${await api.orgInboxPage(op, 'SUSPENDED', pymeOrg)}`);
    await page.getByTestId('org-row').filter({ hasText: pyme.name }).getByRole('button', { name: /Revisar/ }).click();
    await page.getByRole('dialog', { name: pyme.name }).getByRole('button', { name: 'Reactivar y verificar' }).click();
    const dlg2 = page.getByRole('dialog', { name: new RegExp(`Verificar ${pyme.name}`) });
    await dlg2.getByLabel('Nota para la empresa').fill('Documentación revisada');
    await dlg2.getByRole('button', { name: 'Verificar empresa' }).click();
    await expect(toastWith(page, 'Empresa verificada')).toBeVisible();
    await ppage.reload();
    await expect(ppage.getByRole('alert').filter({ hasText: 'Tu empresa está suspendida' })).toHaveCount(0);
    await expect(ppage.getByText('Verificado por OnCargo').first()).toBeVisible();
    const notes = await api.notificationsOf(pyme.token);
    expect(notes.some((n) => n.type === 'ORGANIZATION_VERIFICATION' && n.relatedType === 'ORGANIZATION')).toBe(true);
    await pctx.close();
  });

  test('el transportista suspendido recibe ORG-004 al ofertar con el estado y el siguiente paso', async ({ page }) => {
    const op = await api.operatorToken();
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    await api.addVehicle(carrier.token);
    const req = await api.createRequest(pyme.token, { originCity: 'ABANCAY', destinationCity: 'AYACUCHO', weightKg: 3100 });
    await api.suspendOrg(op, await api.orgIdOf(carrier.token), 'Documentos vencidos');

    await setSession(page.context(), carrier.token, BASE);
    await goto(page, '/transportista/marketplace?origen=ABANCAY&destino=AYACUCHO');
    await expect(page.getByRole('alert').filter({ hasText: 'Tu empresa está suspendida' })).toContainText('Documentos vencidos');
    await page.getByRole('button', { name: 'Ofertar' }).first().click();
    const sheet = page.getByRole('dialog', { name: 'Enviar oferta' });
    await sheet.getByLabel('Tu oferta').fill(String(Math.ceil(req.priceFloor) + 100));
    await sheet.getByRole('button', { name: 'Enviar oferta' }).click();
    const block = sheet.getByRole('status').or(sheet.getByRole('alert')).filter({ hasText: 'Tu empresa está suspendida' });
    await expect(block).toContainText('No puedes realizar esta acción mientras dure la suspensión');
  });

  test('bandeja: filtros en la URL, rechazo con motivo y contadores', async ({ page }) => {
    const op = await api.operatorToken();
    const carrier = await api.registerAccount('CARRIER');
    const id = await api.orgIdOf(carrier.token);
    await setSession(page.context(), op, BASE);
    const pg = await api.orgInboxPage(op, 'PENDING', id, 'CARRIER');
    await goto(page, `/operador/organizaciones?tipo=CARRIER${pg > 1 ? `&pagina=${pg}` : ''}`);
    await expect(page.getByRole('group', { name: 'Estado de verificación' }).getByRole('button', { name: /Pendientes/ })).toHaveAttribute('aria-pressed', 'true');
    await page.getByTestId('org-row').filter({ hasText: carrier.name }).getByRole('button', { name: /Revisar/ }).click();
    await page.getByRole('dialog', { name: carrier.name }).getByRole('button', { name: 'Rechazar verificación' }).click();
    const dlg = page.getByRole('dialog', { name: new RegExp(`Rechazar la verificación de ${carrier.name}`) });
    await dlg.getByLabel('Motivo').fill('La razón social no coincide con el RUC');
    await dlg.getByRole('button', { name: 'Rechazar verificación' }).click();
    await expect(toastWith(page, 'Verificación rechazada')).toBeVisible();
    const me = await api.call<any>('GET', '/auth/me', carrier.token);
    expect(me.verificationStatus).toBe('REJECTED');
    expect(me.verificationNote).toBe('La razón social no coincide con el RUC');
    // Rechazadas: filtro por URL
    await goto(page, `/operador/organizaciones?estado=REJECTED&pagina=${await api.orgInboxPage(op, 'REJECTED', id)}`);
    await expect(page.getByTestId('org-row').filter({ hasText: carrier.name }).getByText('Rechazada')).toBeVisible();
  });

  test('PYME y transportista no entran a la bandeja', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    expect((await page.goto('/operador/organizaciones'))?.status()).toBe(403); // "Sin acceso" (F58)
    await expect(page.getByRole('heading', { name: 'No tienes acceso a esta sección' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ir a mi panel' })).toHaveAttribute('href', '/pyme');
  });
});
