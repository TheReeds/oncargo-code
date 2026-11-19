import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const SHOTS = 'docs/screenshots';

test.describe('Notificaciones', () => {
  test('campana: contador, popover, clic al recurso y marcar todas como leídas', async ({ page, context }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    const vehicle = await api.addVehicle(carrier.token);
    await setSession(context, pyme.token, BASE);
    await goto(page, '/pyme');
    const bell = page.getByRole('button', { name: /^Notificaciones/ });
    await expect(bell).toHaveAccessibleName('Notificaciones');

    const request = await api.createRequest(pyme.token);
    await api.placeBid(carrier.token, request.id, vehicle.id, Math.ceil(request.priceFloor) + 50);
    await goto(page, '/pyme');
    await expect(bell).toHaveAccessibleName(/1 sin leer/);

    await bell.click();
    const pop = page.getByRole('dialog', { name: 'Notificaciones' });
    await expect(pop.getByText('Nueva oferta').first()).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/notificaciones-popover.png` });
    await pop.getByRole('link', { name: /Nueva oferta/ }).first().click();
    await expect(page).toHaveURL(new RegExp(`/pyme/solicitudes/${request.id}`));

    // la lectura se registró al hacer clic
    await goto(page, '/pyme');
    await expect(bell).toHaveAccessibleName('Notificaciones');

    // otra oferta -> marcar todas leídas desde el popover
    const carrier2 = await api.registerAccount('CARRIER');
    const v2 = await api.addVehicle(carrier2.token);
    await api.placeBid(carrier2.token, request.id, v2.id, Math.ceil(request.priceFloor) + 20);
    await goto(page, '/pyme');
    await expect(bell).toHaveAccessibleName(/1 sin leer/);
    await bell.click();
    await pop.getByRole('button', { name: 'Marcar todas como leídas' }).click();
    await expect(bell).toHaveAccessibleName('Notificaciones');
  });

  test('/notificaciones pagina de a 20 y filtra no leídas', async ({ page, context }) => {
    const pyme = await api.registerAccount('PYME');
    const carrier = await api.registerAccount('CARRIER');
    // 22 solicitudes con oferta -> 22 notificaciones NEW_BID. Un vehículo nuevo por oferta: un mismo vehículo
    // admite pocas ofertas solapadas en esas fechas (CAP-007), regla que no se relaja.
    for (let i = 0; i < 22; i++) {
      const vehicle = await api.addVehicle(carrier.token);
      const r = await api.createRequest(pyme.token);
      await api.placeBid(carrier.token, r.id, vehicle.id, Math.ceil(r.priceFloor) + 10);
    }
    await setSession(context, pyme.token, BASE);
    await goto(page, '/notificaciones');
    await expect(page.getByRole('heading', { name: 'Notificaciones', level: 1 })).toBeVisible();
    await expect(page.locator('main ul.divide-y > li')).toHaveCount(20);
    await page.screenshot({ path: `${SHOTS}/notificaciones-pagina.png` });
    await page.getByRole('button', { name: /Siguiente|Página siguiente/ }).first().click();
    await expect(page.locator('main ul.divide-y > li')).toHaveCount(2);

    await page.getByRole('button', { name: 'Marcar todas como leídas' }).click();
    await page.getByRole('tab', { name: 'No leídas' }).click();
    await expect(page.getByText('No tienes notificaciones sin leer')).toBeVisible();
  });
});
