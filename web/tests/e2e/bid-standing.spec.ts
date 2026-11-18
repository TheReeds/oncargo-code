import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { expectNoSeriousViolations, goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const money = (n: number) => `S/ ${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

/** Mis ofertas (transportista): posición en la subasta (V15, sin montos de otros), Perdida/Cancelada/Retirada y cierre (F15/F51); panel de la PYME. */
async function threeCarriers() {
  const pyme = await api.registerAccount('PYME');
  const [a, b, c] = await Promise.all([api.registerAccount('CARRIER'), api.registerAccount('CARRIER'), api.registerAccount('CARRIER')]);
  const [va, vb, vc] = await Promise.all([a, b, c].map((x) => api.addVehicle(x.token)));
  const pickupDate = api.isoDate(20 + Math.floor(Math.random() * 20));
  const request = await api.createRequest(pyme.token, { pickupDate });
  const floor = Math.ceil(request.priceFloor);
  const best = floor + 100;
  const bids = {
    a: await api.placeBid(a.token, request.id, va.id, best),
    b: await api.placeBid(b.token, request.id, vb.id, Math.ceil(best * 1.03)),
    c: await api.placeBid(c.token, request.id, vc.id, Math.ceil(best * 1.2)),
  };
  return { pyme, a, b, c, request, bids, best, pickupDate };
}

test.describe('Mis ofertas: posición y estados', () => {
  test('posición cualitativa por oferta, sin revelar montos ajenos; Perdida con motivo y fecha de cierre', async ({ page }) => {
    const s = await threeCarriers();
    // El API no entrega montos ajenos
    const mine = await api.call<any[]>('GET', '/shipment-requests/my-bids', s.b.token);
    const row = mine.find((r) => r.id === s.request.id);
    expect(row.myBidStanding).toMatchObject({ rank: 2, bidCount: 3, isBest: false, vsBest: 'WITHIN_5_PCT', finalStanding: false });
    expect(JSON.stringify(row.myBidStanding)).not.toContain(String(s.best));

    const expected = [
      [s.a, 'Vas primero de 3', 'Tu oferta es la mejor'],
      [s.b, 'Vas 2.º de 3', 'A menos de 5 % de la mejor'],
      [s.c, 'Vas 3.º de 3', 'A más de 15 % de la mejor'],
    ] as const;
    for (const [acc, headline, band] of expected) {
      await page.context().clearCookies();
      await setSession(page.context(), acc.token, BASE);
      await goto(page, '/transportista/ofertas');
      const standing = page.getByTestId('bid-standing');
      await expect(standing).toContainText(headline);
      await expect(standing).toContainText(band);
      await expect(page.getByText(money(s.best + 1000)), 'no se muestran montos ajenos').toHaveCount(0);
    }

    // Cargas disponibles: la posición también sale en la fila "Ofertada"
    await page.context().clearCookies();
    await setSession(page.context(), s.b.token, BASE);
    await goto(page, `/transportista/marketplace?desde=${s.pickupDate}&hasta=${s.pickupDate}`);
    await expect(page.getByTestId('market-standing')).toContainText('Vas 2.º de 3');

    // La PYME ve la mejor oferta en su panel (bestBidAmount)
    await page.context().clearCookies();
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, '/pyme');
    await expect(page.getByText(money(s.best)).first()).toBeVisible();

    // La PYME acepta a A: B pierde y ve cuándo y por qué cerró la subasta
    await api.acceptBid(s.pyme.token, s.bids.a.id);
    await page.context().clearCookies();
    await setSession(page.context(), s.b.token, BASE);
    await goto(page, '/transportista/ofertas');
    const tr = page.getByRole('row').filter({ has: page.getByTestId('bid-standing') });
    await expect(tr.getByText('Perdida')).toBeVisible();
    await expect(tr).toContainText('La PYME eligió otra oferta');
    await expect(page.getByTestId('bid-closure')).toContainText('Subasta cerrada');
    await expect(page.getByTestId('bid-closure')).toContainText('La PYME eligió una oferta');
    await expect(page.getByTestId('bid-standing')).toContainText('Quedaste 2.º de 3');
    await page.getByRole('button', { name: /Perdidas/ }).click();
    await expect(page.getByRole('row').filter({ has: page.getByTestId('bid-standing') })).toHaveCount(1);
    await expect(page.getByText('Perdidas', { exact: false }).first()).toBeVisible();

    await page.context().clearCookies();
    await setSession(page.context(), s.a.token, BASE);
    await goto(page, '/transportista/ofertas');
    await expect(page.getByRole('row').filter({ hasText: 'Aceptada' }).first()).toBeVisible();
  });

  test('Cancelada por la PYME (con motivo) y Retirada por el transportista', async ({ page }) => {
    const s = await threeCarriers();
    await api.withdrawBid(s.c.token, s.bids.c.id);
    await api.call('POST', `/shipment-requests/${s.request.id}/cancel`, s.pyme.token, { reason: 'Cambió la fecha de la carga' });

    await setSession(page.context(), s.b.token, BASE);
    await goto(page, '/transportista/ofertas');
    const cancelled = page.getByRole('row').filter({ hasText: 'La PYME canceló la solicitud' });
    await expect(cancelled).toContainText('Cambió la fecha de la carga');
    await expect(cancelled.getByText('Cancelada').first()).toBeVisible();
    await page.getByRole('button', { name: /Canceladas/ }).click();
    await expect(page.getByTestId('bid-closure')).toContainText('La PYME canceló la solicitud');

    await page.context().clearCookies();
    await setSession(page.context(), s.c.token, BASE);
    await goto(page, '/transportista/ofertas');
    await page.getByRole('button', { name: /Retiradas/ }).click();
    await expect(page.getByRole('row').filter({ hasText: 'Retirada' }).first()).toBeVisible();
  });
});

for (const theme of ['light', 'dark'] as const) {
  test.describe(`Accesibilidad mis ofertas con posición (${theme})`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    });
    test('pendiente con posición y perdida', async ({ page }) => {
      const s = await threeCarriers();
      await setSession(page.context(), s.b.token, BASE);
      await goto(page, '/transportista/ofertas');
      await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
      await expect(page.getByTestId('bid-standing')).toBeVisible();
      await expectNoSeriousViolations(page);
      await api.acceptBid(s.pyme.token, s.bids.a.id);
      await page.reload();
      await expect(page.getByTestId('bid-closure')).toBeVisible();
      await expectNoSeriousViolations(page);
    });
  });
}
