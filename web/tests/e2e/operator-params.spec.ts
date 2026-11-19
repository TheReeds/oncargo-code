import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, hydrated, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

type Param = { key: string; value: string; version: number };
const params = (op: string) => api.call<Param[]>('GET', '/operator/parameters', op);
const setParam = (op: string, key: string, value: string, reason = 'Restaurar tras la prueba e2e') => api.call('PUT', `/operator/parameters/${key}`, op, { value, reason });

/** Parámetros de sistema editables (V18), búsqueda del operador (1.13) y lectura auditada del chat. */
test.describe('Operador: parámetros del sistema', () => {
  test('cambiar la comisión con motivo: validación, valor nuevo, historial y restauración', async ({ page, context }) => {
    const op = await api.operatorToken();
    const before = (await params(op)).find((p) => p.key === 'commission_rate')!;
    const beforePct = String(Math.round(Number(before.value) * 10000) / 100);   // "0.08" → "8"
    const target = beforePct === '10' ? '12' : '10';
    await setSession(context, op, BASE);
    try {
      await goto(page, '/operador/parametros');
      await expect(page.getByRole('heading', { name: 'Parámetros del sistema', level: 1 })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Parámetros' })).toHaveAttribute('aria-current', 'page');
      const card = page.getByTestId('param-commission_rate');
      await expect(card.getByTestId('param-value')).toHaveText(`${beforePct} %`);

      // Validación en el diálogo: fuera de rango y sin motivo
      await card.getByRole('button', { name: /Cambiar/ }).click();
      const dialog = page.getByRole('dialog');
      await dialog.getByLabel('Nueva comisión').fill('45');
      await dialog.getByLabel('Motivo del cambio').fill('Prueba de rango');
      await dialog.getByRole('button', { name: 'Guardar cambio' }).click();
      await expect(dialog.getByText(/Debe estar entre 0 % y 30 %/)).toBeVisible();
      await dialog.getByLabel('Nueva comisión').fill(target);
      await dialog.getByLabel('Motivo del cambio').fill('');
      await dialog.getByRole('button', { name: 'Guardar cambio' }).click();
      await expect(dialog.getByText(/Indica el motivo/)).toBeVisible();

      // Guardar: el valor nuevo aparece al recargar, con su versión, y la API lo usa
      await dialog.getByLabel('Motivo del cambio').fill('Acuerdo del piloto e2e');
      await dialog.getByRole('button', { name: 'Guardar cambio' }).click();
      await expect(card.getByTestId('param-value')).toHaveText(`${target} %`, { timeout: 15_000 });
      await hydrated(page);
      await expect(card).toContainText('Acuerdo del piloto e2e');
      const rate = Number((await params(op)).find((p) => p.key === 'commission_rate')!.value);
      expect(rate).toBeCloseTo(Number(target) / 100, 4);
      const acc = await api.registerAccount('CARRIER');
      const c = await api.call<any>('GET', '/pricing/commission?amount=1180', acc.token);
      expect(c.commissionRate).toBeCloseTo(Number(target) / 100, 4);
      expect(c.breakdown.carrierNet).toBeCloseTo(1180 - 1180 * (Number(target) / 100), 1);

      // Historial: el cambio con su motivo y quién lo hizo
      const row = page.getByTestId('param-history-row').first();
      await expect(row).toContainText('Comisión al transportista');
      await expect(row).toContainText(`${beforePct} %`);
      await expect(row).toContainText(`${target} %`);
      await expect(row).toContainText('Acuerdo del piloto e2e');
      await expectNoSeriousViolations(page, 'main');   // sin el toast de «Parámetro actualizado», que aún se anima
    } finally {
      await setParam(op, 'commission_rate', before.value);
    }
  });

  test('un usuario que no es operador no entra a los parámetros', async ({ page, context }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(context, pyme.token, BASE);
    await page.goto('/operador/parametros');
    await expect(page.getByRole('heading', { name: 'No tienes acceso a esta sección' })).toBeVisible();
    await api.raw('PUT', '/operator/parameters/commission_rate', pyme.token, { value: '0.01', reason: 'intento' }).then((r) => expect(r.status).toBe(403));
  });
});

test.describe('Operador: búsqueda y conversaciones', () => {
  test('busca solicitudes y envíos por razón social, y organizaciones por RUC; Enter aplica y la X limpia', async ({ page, context }) => {
    const sc = await api.openBidScenario();
    const op = await api.operatorToken();
    await setSession(context, op, BASE);
    await goto(page, '/operador/solicitudes');

    const search = page.getByRole('searchbox', { name: 'Buscar' });
    await search.fill(sc.pyme.name);
    await search.press('Enter');
    await expect(page).toHaveURL(/q=E2E/);
    await hydrated(page);
    const rows = page.getByTestId('operator-row');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText(sc.pyme.name);

    // Sin coincidencias: mensaje con el texto buscado; la X vuelve al listado completo
    await page.getByRole('searchbox', { name: 'Buscar' }).fill('NoExisteEstaEmpresa987');
    await page.getByRole('searchbox', { name: 'Buscar' }).press('Enter');
    await expect(page.getByText(/Nada coincide con «NoExisteEstaEmpresa987»/)).toBeVisible();
    await hydrated(page);
    await page.getByRole('button', { name: 'Borrar búsqueda' }).click();
    await expect(page).not.toHaveURL(/q=/);

    // Organizaciones: por RUC
    await goto(page, `/operador/organizaciones?estado=ALL&q=${sc.carrier.ruc}`);
    await expect(page.getByText(sc.carrier.name).first()).toBeVisible();
    // Envíos: búsqueda por placa
    await goto(page, `/operador/envios?q=${sc.vehicle.plate}`);
    await expect(page.getByTestId('operator-row')).toHaveCount(0);   // aún no hay envío (solo oferta)
  });

  test('ver conversaciones: pide motivo opcional, muestra el hilo en solo lectura y queda auditado', async ({ page, context }) => {
    const sc = await api.openBidScenario();
    const orgId = await api.orgIdOf(sc.carrier.token);
    await api.call('POST', `/shipment-requests/${sc.request.id}/threads/${orgId}/messages`, sc.carrier.token, { body: '¿A qué hora puedo recoger?' });
    await api.call('POST', `/shipment-requests/${sc.request.id}/threads/${orgId}/messages`, sc.pyme.token, { body: 'Desde las 8 de la mañana.' });
    const op = await api.operatorToken();
    await setSession(context, op, BASE);
    await goto(page, `/operador/solicitudes/${sc.request.id}`);

    const chat = page.getByTestId('operator-chat');
    await expect(chat).toContainText('Cada vez que las abres queda registrado');
    await expect(page.getByTestId('operator-thread')).toHaveCount(0);   // no se carga hasta pedirlo
    await chat.getByRole('button', { name: 'Ver conversaciones' }).click();
    await page.getByRole('dialog').getByLabel('Motivo de la consulta').fill('Disputa por el horario de recojo');
    await page.getByRole('dialog').getByRole('button', { name: 'Ver conversaciones' }).click();
    const thread = page.getByTestId('operator-thread');
    await expect(thread).toHaveCount(1);
    await expect(thread).toContainText(sc.carrier.name);
    await expect(thread).toContainText('2 mensajes');
    await thread.getByRole('button', { name: /Leer/ }).click();
    const sheet = page.getByRole('dialog');
    await expect(sheet.getByText('¿A qué hora puedo recoger?')).toBeVisible();
    await expect(sheet.getByText('Desde las 8 de la mañana.')).toBeVisible();
    await expect(sheet.getByRole('textbox')).toHaveCount(0);   // solo lectura: no hay dónde escribir
    await expectNoSeriousViolations(page);

    // Auditoría: la consulta quedó registrada con el motivo
    const audit = await api.call<any[]>('GET', `/operator/audit?action=OPERATOR_VIEWED_CHAT&entityId=${sc.request.id}`, op);
    expect(audit.length).toBeGreaterThanOrEqual(2);
    expect(audit.map((a) => a.detail).join(' ')).toContain('Disputa por el horario de recojo');
    void toastWith;
  });
});

for (const theme of ['light', 'dark']) {
  test.describe(`Operador: accesibilidad de parámetros, búsqueda y conversaciones (${theme})`, () => {
    test('parámetros, listado con búsqueda y conversaciones sin violaciones axe', async ({ page, context }) => {
      const sc = await api.openBidScenario();
      const op = await api.operatorToken();
      await setSession(context, op, BASE);
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
      await page.emulateMedia({ reducedMotion: 'reduce' });

      await goto(page, '/operador/parametros');
      await expectNoSeriousViolations(page);
      await page.getByTestId('param-commission_rate').getByRole('button', { name: /Cambiar/ }).click();
      await expectNoSeriousViolations(page);   // el diálogo abierto
      await page.keyboard.press('Escape');

      await goto(page, `/operador/solicitudes?q=${encodeURIComponent(sc.pyme.name)}`);
      await expect(page.getByTestId('operator-row')).toHaveCount(1);
      await expectNoSeriousViolations(page);

      await goto(page, `/operador/solicitudes/${sc.request.id}`);
      await page.getByTestId('operator-chat').getByRole('button', { name: 'Ver conversaciones' }).click();
      await expectNoSeriousViolations(page);
    });
  });
}
