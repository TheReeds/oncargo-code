import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import * as api from './helpers/api';
import { goto, pick, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

test.describe('Operador: listados globales', () => {
  test('solicitudes: tabla, filtros por estado y ciudad, enlace al detalle y navegación', async ({ page, context }) => {
    const sc = await api.openBidScenario(2300, { originCity: 'AREQUIPA', destinationCity: 'TACNA' });
    const other = await api.createRequest(sc.pyme.token, { originCity: 'LIMA', destinationCity: 'ICA' });
    const op = await api.operatorToken();
    await setSession(context, op, BASE);

    await goto(page, '/operador');
    await page.getByRole('link', { name: 'Solicitudes', exact: true }).first().click();
    await expect(page).toHaveURL(/\/operador\/solicitudes$/);
    await expect(page.getByRole('heading', { name: 'Solicitudes', level: 1 })).toBeVisible();

    const rows = page.getByTestId('operator-row');
    await expect(rows.filter({ hasText: sc.pyme.name }).first()).toBeVisible();
    await expect(rows.filter({ hasText: 'Arequipa → Tacna' })).not.toHaveCount(0);

    // Filtro por origen/destino desde la UI (se refleja en la URL)
    await pick(page, page.getByLabel('Origen'), 'Arequipa');
    await expect(page).toHaveURL(/origen=AREQUIPA/);
    await pick(page, page.getByLabel('Destino'), 'Tacna');
    await expect(page).toHaveURL(/destino=TACNA/);
    await expect(page.getByTestId('operator-row').first()).toContainText('Arequipa → Tacna');
    await expect(page.getByTestId('operator-row').filter({ hasText: 'Lima → Ica' })).toHaveCount(0);

    // Estado: la solicitud sigue publicada; "Adjudicadas" no la muestra
    await goto(page, '/operador/solicitudes?origen=AREQUIPA&destino=TACNA&estado=MATCHED');
    await expect(page.getByTestId('operator-row').filter({ hasText: sc.pyme.name })).toHaveCount(0);
    await goto(page, '/operador/solicitudes?origen=AREQUIPA&destino=TACNA&estado=PUBLISHED');
    await expect(page.getByTestId('operator-row').filter({ hasText: sc.pyme.name })).toHaveCount(1);

    // Fechas: un rango pasado no devuelve nada y ofrece limpiar
    await goto(page, '/operador/solicitudes?desde=2000-01-01&hasta=2000-01-02');
    await expect(page.getByText('No hay solicitudes con estos filtros')).toBeVisible();
    await page.getByRole('button', { name: 'Limpiar filtros' }).last().click();
    await expect(page).toHaveURL(/\/operador\/solicitudes$/);

    // Enlace al detalle existente
    await goto(page, `/operador/solicitudes?origen=LIMA&destino=ICA`);
    await expect(page.getByTestId('operator-row').first()).toBeVisible();
    await page.getByTestId('operator-row').first().getByRole('link').first().click();
    await expect(page).toHaveURL(/\/operador\/solicitudes\/[0-9a-f-]{36}$/);
    expect(other.id).toBeTruthy();
  });

  test('envíos: tabla con filtros y enlace al detalle', async ({ page, context }) => {
    const sc = await api.openBidScenario(2300, { originCity: 'MOQUEGUA', destinationCity: 'PUNO' });
    const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
    const shipmentId = accepted.shipmentId;
    const op = await api.operatorToken();
    await setSession(context, op, BASE);

    await goto(page, '/operador/envios');
    await expect(page.getByRole('heading', { name: 'Envíos', level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Envíos', exact: true }).first()).toBeVisible();

    await goto(page, '/operador/envios?origen=MOQUEGUA&destino=PUNO&estado=PENDING');
    const row = page.getByTestId('operator-row').filter({ hasText: sc.pyme.name });
    await expect(row).toHaveCount(1);
    await expect(row).toContainText('Moquegua → Puno');
    await expect(row).toContainText(sc.vehicle.plate);

    await goto(page, '/operador/envios?origen=MOQUEGUA&destino=PUNO&estado=DELIVERED');
    await expect(page.getByText('No hay envíos con estos filtros')).toBeVisible();

    await goto(page, '/operador/envios?origen=MOQUEGUA&destino=PUNO');
    await page.getByTestId('operator-row').filter({ hasText: sc.pyme.name }).getByRole('link').first().click();
    await expect(page).toHaveURL(new RegExp(`/operador/envios/${shipmentId}$`));
  });

  test('paginación: una página fuera de rango no rompe la vista', async ({ page, context }) => {
    await setSession(context, await api.operatorToken(), BASE);
    await goto(page, '/operador/solicitudes?pagina=9999');
    await expect(page.getByRole('heading', { name: 'Solicitudes', level: 1 })).toBeVisible();
    await expect(page.getByRole('alert')).toHaveCount(0);
  });

  test('una PYME y un transportista no pueden abrir los listados', async ({ page, context }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(context, pyme.token, BASE);
    for (const path of ['/operador/solicitudes', '/operador/envios']) {
      const res = await page.goto(path);
      expect(res?.status()).toBe(403); // página "Sin acceso" (F58), en la misma URL
      await expect(page.getByRole('heading', { name: 'No tienes acceso a esta sección' })).toBeVisible();
    }
    const carrier = await api.registerAccount('CARRIER');
    await context.clearCookies();
    await setSession(context, carrier.token, BASE);
    for (const path of ['/operador/solicitudes', '/operador/envios']) {
      const res = await page.goto(path);
      expect(res?.status()).toBe(403);
      await expect(page.getByRole('heading', { name: 'No tienes acceso a esta sección' })).toBeVisible();
    }
    // y la API tampoco se los entrega
    for (const path of ['/operator/requests', '/operator/shipments']) {
      await expect(api.call('GET', path, pyme.token)).rejects.toThrow();
      await expect(api.call('GET', path, carrier.token)).rejects.toThrow();
    }
  });
});

test.describe('Accesibilidad: listados del operador (axe)', () => {
  for (const path of ['/operador/solicitudes', '/operador/envios']) {
    test(`${path}`, async ({ page, context }) => {
      await api.openBidScenario();
      await setSession(context, await api.operatorToken(), BASE);
      await goto(page, path);
      await expect(page.getByTestId('operator-row').first()).toBeVisible();
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
      expect(bad.map((v) => `${v.id}: ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
    });
  }
});
