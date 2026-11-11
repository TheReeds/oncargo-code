import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { goto, setSession } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4322';

test.describe('Negociación', () => {
  test('chat: el teléfono se rechaza inline (RN07) y el mensaje válido llega al otro lado', async ({ page, browser }) => {
    const sc = await api.openBidScenario();
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);

    const panel = page.locator('#conversacion').locator('xpath=ancestor::div[contains(@class,"rounded-card")][1]');
    await expect(panel.getByText('Conversación')).toBeVisible();
    const msg = panel.getByRole('textbox', { name: 'Mensaje' });
    const phone = 'Llámame al 987 654 321 para coordinar';
    await msg.fill(phone);
    await panel.getByRole('button', { name: 'Enviar mensaje' }).click();
    await expect(panel.getByText(/No puedes compartir teléfonos/).first()).toBeVisible();
    await expect(msg).toHaveValue(phone);

    const ok = `Hola, ¿el camión llega a las 8:00? ${Date.now()}`;
    await msg.fill(ok);
    await panel.getByRole('button', { name: 'Enviar mensaje' }).click();
    await expect(panel.getByRole('log').getByText(ok)).toBeVisible();
    await expect(msg).toHaveValue('');

    // El transportista lo ve en Mis ofertas > Negociar
    const ctx = await browser.newContext();
    const cpage = await ctx.newPage();
    await setSession(ctx, sc.carrier.token, BASE);
    await goto(cpage, `/transportista/ofertas?solicitud=${sc.request.id}`);
    const sheet = cpage.getByRole('dialog');
    await expect(sheet.getByRole('log').getByText(ok)).toBeVisible();
    await ctx.close();
  });

  test('contra-oferta de la PYME aceptada por el transportista desde Mis ofertas', async ({ page, browser }) => {
    const sc = await api.openBidScenario(6000);
        const current = Number(sc.bid.amount);
    const counter = 5000;
    await setSession(page.context(), sc.pyme.token, BASE);
    await goto(page, `/pyme/solicitudes/${sc.request.id}`);

    await page.getByRole('button', { name: /Contraofertar/ }).click();
    const dlg = page.getByRole('dialog');
    await dlg.getByLabel('Monto que propones').fill(String(current));
    await dlg.getByRole('button', { name: 'Enviar contra-oferta' }).click();
    await expect(dlg.getByText(/mismo monto/i)).toBeVisible();
    await dlg.getByLabel('Monto que propones').fill(String(counter));
    await dlg.getByRole('button', { name: 'Enviar contra-oferta' }).click();
    await expect(page.getByText('Contra-oferta enviada').first()).toBeVisible();

    const ctx = await browser.newContext();
    const cpage = await ctx.newPage();
    await setSession(ctx, sc.carrier.token, BASE);
    await goto(cpage, `/transportista/ofertas?solicitud=${sc.request.id}`);
    const sheet = cpage.getByRole('dialog');
    await expect(sheet.getByText('La PYME te propuso un nuevo monto')).toBeVisible();
    await sheet.getByRole('button', { name: 'Aceptar contra-oferta' }).click();
    await cpage.getByRole('alertdialog').getByRole('button', { name: 'Aceptar contra-oferta' }).click();
    await expect(cpage.getByText('Contra-oferta aceptada').first()).toBeVisible();
    await expect(cpage.getByText(`S/ ${counter.toLocaleString('en-US')}.00`).first()).toBeVisible();
    await ctx.close();

    const bids = await api.call<any[]>('GET', `/shipment-requests/${sc.request.id}/bids`, sc.pyme.token);
    expect(Number(bids[0].amount)).toBe(counter);
  });
});
