import { test, expect, type Page } from '@playwright/test';
import * as api from './helpers/api';
import { goto, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4332';
const SHOTS = 'docs/screenshots';

async function openRating(page: Page, token: string, path: string, id: string) {
  await setSession(page.context(), token, BASE);
  await goto(page, `${path}?id=${id}`);
  await page.getByRole('tab', { name: 'Calificación' }).click();
}

async function rate(page: Page, stars: [number, number, number], comment?: string) {
  const form = page.getByRole('form', { name: 'Calificar envío' });
  const groups = form.getByRole('radiogroup');
  for (let i = 0; i < 3; i++) await groups.nth(i).getByRole('radio', { name: `${stars[i]} de 5` }).click();
  if (comment) await form.getByLabel('Comentario').fill(comment);
  await form.getByRole('button', { name: 'Enviar calificación' }).click();
}

test.describe('Reputación', () => {
  test('ambas partes califican y el perfil público muestra score y reseña', async ({ page, browser }) => {
    const s = await api.deliveredShipment();
    const carrierOrg = await api.me(s.carrier.token);
    const pymeOrg = await api.me(s.pyme.token);

    // PYME: validación de dimensiones vacías
    await openRating(page, s.pyme.token, '/pyme/envios', s.shipmentId);
    await expect(page.getByText('Califica este envío')).toBeVisible();
    await page.getByRole('button', { name: 'Enviar calificación' }).click();
    await expect(page.getByText('Califica la puntualidad.')).toBeVisible();
    await expect(page.getByText('Califica el estado de la carga.')).toBeVisible();
    await expect(page.getByText('Califica la comunicación.')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/reputacion-formulario.png` });

    await rate(page, [5, 4, 5], 'Puntual y cuidadoso con la carga');
    await expect(toastWith(page, 'Calificación enviada')).toBeVisible();
    await expect(page.getByText('Califica este envío')).toBeHidden();
    await expect(page.getByTestId('review-card')).toHaveCount(1);
    await expect(page.getByText('Puntual y cuidadoso con la carga')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/reputacion-envio-calificado.png` });

    // Transportista califica a la PYME
    const ctx = await browser.newContext({ baseURL: BASE, locale: 'es-PE', viewport: { width: 1440, height: 900 } });
    const cp = await ctx.newPage();
    await openRating(cp, s.carrier.token, '/transportista/envios', s.shipmentId);
    await expect(cp.getByRole('form', { name: 'Calificar envío' }).getByText('Exactitud de la carga declarada').first()).toBeVisible();
    await rate(cp, [4, 4, 5], 'Carga tal como se declaró');
    await expect(toastWith(cp, 'Calificación enviada')).toBeVisible();
    await expect(cp.getByTestId('review-card')).toHaveCount(2);
    await ctx.close();

    // Perfil público del transportista: 4.67 -> 77.8
    await goto(page, `/organizaciones/${carrierOrg.organizationId}`);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(carrierOrg.organizationName);
    await expect(page.getByRole('meter', { name: 'Trust Score' })).toHaveAttribute('aria-valuenow', '77.8');
    await expect(page.getByText('Puntual y cuidadoso con la carga')).toBeVisible();
    await expect(page.getByText('1 reseña').first()).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/reputacion-perfil-transportista.png` });

    // Perfil de la PYME
    await goto(page, `/organizaciones/${pymeOrg.organizationId}`);
    await expect(page.getByText('Carga tal como se declaró')).toBeVisible();
    await expect(page.getByText('Exactitud de la carga declarada').first()).toBeVisible();
  });

  test('envío no entregado muestra el motivo', async ({ page }) => {
    const sc = await api.openBidScenario();
    const accepted = await api.acceptBid(sc.pyme.token, sc.bid.id);
    await openRating(page, sc.pyme.token, '/pyme/envios', accepted.shipmentId);
    await expect(page.getByText('Aún no puedes calificar este envío')).toBeVisible();
    await expect(page.getByText(/aún no ha sido entregado/i)).toBeVisible();
    await expect(page.getByRole('form', { name: 'Calificar envío' })).toHaveCount(0);
    await page.screenshot({ path: `${SHOTS}/reputacion-bloqueada.png` });
  });

  test('organización inexistente muestra 404 amable', async ({ page }) => {
    const pyme = await api.registerAccount('PYME');
    await setSession(page.context(), pyme.token, BASE);
    await page.goto('/organizaciones/00000000-0000-0000-0000-000000000000');
    await expect(page.getByText('No encontramos esta organización')).toBeVisible();
  });
});
