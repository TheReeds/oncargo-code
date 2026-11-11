import { test, expect } from '@playwright/test';
import * as api from './helpers/api';
import { goto, setSession, toastWith } from './helpers/ui';

const BASE = process.env.E2E_BASE_URL ?? 'http://localhost:4332';
const SHOTS = 'docs/screenshots';
const PDF = Buffer.from('%PDF-1.4\n%%EOF');

test.describe('Documentos', () => {
  test('foto de entrega con previsualización y descarga autenticada', async ({ page }) => {
    const s = await api.deliveredShipment();
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${s.shipmentId}`);

    await expect(page.getByText('Evidencia de entrega')).toBeVisible();
    const thumb = page.getByRole('button', { name: /Ver evidencia\.png/ }).first();
    await expect(thumb).toBeVisible();
    await page.getByText('Evidencia de entrega').scrollIntoViewIfNeeded();
    await page.screenshot({ path: `${SHOTS}/documentos-evidencia.png` });
    await thumb.click();
    const dlg = page.getByRole('dialog', { name: 'evidencia.png' });
    await expect(dlg).toBeVisible();
    await expect(dlg.getByRole('img')).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/documentos-previsualizacion.png` });
    await page.keyboard.press('Escape');
    await expect(dlg).toBeHidden();

    const row = page.getByTestId('document-row').first();
    const href = await row.getByRole('link', { name: /Descargar/ }).getAttribute('href');
    expect(href).toMatch(/^\/documentos\/[0-9a-f-]{36}/);
    const res = await page.request.get(href!.split('?')[0]);
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toMatch(/^image\//);
  });

  test('subir un PDF válido y rechazar uno de más de 10 MB', async ({ page }) => {
    const s = await api.deliveredShipment();
    await setSession(page.context(), s.carrier.token, BASE);
    await goto(page, `/transportista/envios?id=${s.shipmentId}`);
    const form = page.getByRole('form', { name: 'Subir documento' });

    // Sin archivo
    await form.getByRole('button', { name: 'Subir documento' }).click();
    await expect(form.getByText('Elige el archivo que quieres subir.')).toBeVisible();

    // > 10 MB: rechazado en el cliente
    await form.locator('input[type=file]').setInputFiles({ name: 'enorme.pdf', mimeType: 'application/pdf', buffer: Buffer.alloc(10 * 1024 * 1024 + 10, 1) });
    await expect(form.getByRole('alert').filter({ hasText: 'supera el límite de 10 MB' })).toBeVisible();

    // Formato no permitido
    await form.locator('input[type=file]').setInputFiles({ name: 'nota.txt', mimeType: 'text/plain', buffer: Buffer.from('hola') });
    await expect(form.getByRole('alert').filter({ hasText: 'no es un formato permitido' })).toBeVisible();

    // PDF mínimo válido
    await form.locator('input[type=file]').setInputFiles({ name: 'guia.pdf', mimeType: 'application/pdf', buffer: PDF });
    await expect(form.getByText('guia.pdf', { exact: true })).toBeVisible();
    await form.getByRole('button', { name: 'Subir documento' }).click();
    await expect(toastWith(page, 'Documento subido')).toBeVisible();
    const row = page.getByTestId('document-row').filter({ hasText: 'guia.pdf' });
    await expect(row).toBeVisible();
    await expect(row).toContainText('Guía de remisión');
    await page.screenshot({ path: `${SHOTS}/documentos-lista.png` });

    const docs = await api.call<any[]>('GET', `/shipments/${s.shipmentId}/documents`, s.pyme.token);
    const pdf = docs.find((d) => d.fileName === 'guia.pdf');
    expect(pdf?.contentType).toBe('application/pdf');
    const res = await page.request.get(`/documentos/${pdf.id}`);
    expect(res.status()).toBe(200);
    expect(res.headers()['content-type']).toContain('application/pdf');
  });

  test('un archivo con firma inválida muestra el error de la API en el campo', async ({ page }) => {
    const s = await api.deliveredShipment();
    await setSession(page.context(), s.pyme.token, BASE);
    await goto(page, `/pyme/envios?id=${s.shipmentId}`);
    const form = page.getByRole('form', { name: 'Subir documento' });
    await form.locator('input[type=file]').setInputFiles({ name: 'falso.pdf', mimeType: 'application/pdf', buffer: Buffer.from('esto no es un pdf') });
    await form.getByRole('button', { name: 'Subir documento' }).click();
    await expect(form.getByRole('alert')).toBeVisible();
    await expect(form.getByText('Elige el archivo')).toBeHidden();
  });

  test('sin sesión no se accede al documento', async ({ browser }) => {
    const s = await api.deliveredShipment();
    const docs = await api.call<any[]>('GET', `/shipments/${s.shipmentId}/documents`, s.pyme.token);
    const ctx = await browser.newContext({ baseURL: BASE });
    const res = await ctx.request.get(`/documentos/${docs[0].id}`, { maxRedirects: 0 });
    expect([301, 302, 303, 307, 401]).toContain(res.status());
    await ctx.close();
  });

  test('otra organización no accede al documento', async ({ browser }) => {
    const s = await api.deliveredShipment();
    const docs = await api.call<any[]>('GET', `/shipments/${s.shipmentId}/documents`, s.pyme.token);
    const other = await api.registerAccount('PYME');
    const ctx = await browser.newContext({ baseURL: BASE });
    await setSession(ctx, other.token, BASE);
    const res = await ctx.request.get(`/documentos/${docs[0].id}`);
    expect(res.status()).toBe(404);
    await ctx.close();
  });
});
