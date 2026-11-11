import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { goto, loginViaUI, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

test.describe('Operador', () => {
  test('login cae en /operador con métricas y alertas; otros roles no entran', async ({ page, context }) => {
    await loginViaUI(page, api.DEMO.operator, api.PASSWORD);
    await page.waitForURL('**/operador');
    await expect(page.getByRole('heading', { name: 'Panel de operación', level: 1 })).toBeVisible();
    await expect(page.getByText('Solicitudes abiertas')).toBeVisible();
    await expect(page.getByText('Transportistas registrados')).toBeVisible();
    await expect(page.getByRole('heading', { name: /Solicitudes sin ofertas/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Subastas por vencer/ })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Envíos con incidencias/ })).toBeVisible();
    await expect(page.getByText(/Actualizado/)).toBeVisible();
    // el operador no entra al área de PYME
    await page.goto('/pyme');
    await expect(page).toHaveURL(/\/operador$/);

    // PYME y transportista no entran a /operador
    const pyme = await api.registerAccount('PYME');
    await context.clearCookies();
    await setSession(context, pyme.token, BASE);
    await page.goto('/operador');
    await expect(page).toHaveURL(/\/pyme$/);
    const carrier = await api.registerAccount('CARRIER');
    await context.clearCookies();
    await setSession(context, carrier.token, BASE);
    await page.goto('/operador');
    await expect(page).toHaveURL(/\/transportista\//);
  });

  test('asignación manual: candidatos, confirmación y envío generado', async ({ page, context }) => {
    const [pyme, carrier] = await Promise.all([api.registerAccount('PYME'), api.registerAccount('CARRIER')]);
    const vehicle = await api.addVehicle(carrier.token, 30000, 'TRUCK_LARGE');
    const request = await api.createRequest(pyme.token, { weightKg: 4100 });
    const op = await api.operatorToken();
    const alerts = await api.operatorAlerts(op);
    const mine = alerts.requestsWithoutBids.find((r: any) => r.id === request.id);
    test.skip(!mine, 'El backend no corre con logyx.operator.sla=PT0S: la solicitud recién creada aún no tiene el SLA vencido.');

    await setSession(context, op, BASE);
    await goto(page, '/operador');
    const row = page.locator('tbody tr').filter({ hasText: pyme.name });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: /Asignar transportista/ }).click();

    const sheet = page.getByRole('dialog', { name: 'Asignar transportista' });
    await expect(sheet.getByText('Piso (monto mínimo)')).toBeVisible();
    const cand = sheet.getByRole('radio').filter({ has: page.locator('xpath=self::*') });
    await expect(sheet.getByText(vehicle.plate)).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/operador-asignar-candidatos.png` });

    // sin elegir: botón deshabilitado
    await expect(sheet.getByRole('button', { name: 'Asignar solicitud' })).toBeDisabled();
    await sheet.getByText(vehicle.plate).click();
    // monto bajo el piso -> error inline
    const amount = sheet.getByLabel('Monto de la asignación');
    await amount.fill('10');
    await sheet.getByRole('button', { name: 'Asignar solicitud' }).click();
    await expect(sheet.getByRole('alert').filter({ hasText: 'piso' })).toBeVisible();
    await amount.fill(String(Math.ceil(request.suggestedPrice)));

    await sheet.getByRole('button', { name: 'Asignar solicitud' }).click();
    const dlg = page.getByRole('alertdialog');
    await expect(dlg).toContainText(`¿Asignar la solicitud a ${carrier.name}`);
    await expect(dlg).toContainText('se generará el envío');
    await page.screenshot({ path: `${SHOTS}/operador-asignar-confirmacion.png` });
    await dlg.getByRole('button', { name: 'Asignar solicitud' }).click();

    await expect(toastWith(page, 'Solicitud asignada')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ver envío', exact: true })).toBeVisible();
    await expect(row).toHaveCount(0);

    const shipments = await api.listShipments(carrier.token);
    expect(shipments.some((s: any) => s.requestId === request.id && s.vehiclePlate === vehicle.plate)).toBe(true);
    const req = await api.getRequest(pyme.token, request.id);
    expect(req.status).toBe('MATCHED');
    const bids = await api.call<any[]>('GET', `/shipment-requests/${request.id}/bids`, pyme.token);
    expect(bids.some((b: any) => b.assignedByOperator === true && b.status === 'ACCEPTED')).toBe(true);
  });

  test('detalle de solicitud y envío en solo lectura', async ({ page, context }) => {
    const op = await api.operatorToken();
    const alerts = await api.operatorAlerts(op);
    test.skip(!alerts.requestsWithoutBids.length, 'No hay solicitudes en las alertas.');
    await setSession(context, op, BASE);
    await goto(page, `/operador/solicitudes/${alerts.requestsWithoutBids[0].id}`);
    await expect(page.getByText('Ruta y carga')).toBeVisible();
    await expect(page.getByText('Piso (monto mínimo)')).toBeVisible();
  });
});
