import { test, expect } from '@playwright/test';
import { goto } from './helpers/ui';
import { validRuc } from './helpers/api';

test.describe('Landing y páginas públicas', () => {
  test('landing: propuesta de valor, CTAs por rol, FAQ y navegación por secciones', async ({ page }) => {
    await goto(page, '/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Contrata fletes');
    await expect(page.getByRole('link', { name: /Crear cuenta de empresa/ }).first()).toHaveAttribute('href', '/registro');
    await expect(page.getByRole('link', { name: /Soy transportista/ }).first()).toHaveAttribute('href', '/registro?tipo=transportista');
    for (const id of ['para-quien', 'como-funciona', 'retornos', 'confianza', 'corredor', 'preguntas']) await expect(page.locator(`#${id}`)).toBeAttached();
    const q = page.getByText('¿Cuánto dura la subasta y qué pasa al terminar?');
    await q.click();
    await expect(page.getByText(/se cierra al llegar a 5 ofertas/).last()).toBeVisible();
  });

  test('privacidad: índice lateral y secciones con ancla', async ({ page }) => {
    await goto(page, '/privacidad');
    await expect(page.getByRole('heading', { level: 1, name: 'Política de privacidad' })).toBeVisible();
    await page.getByRole('navigation', { name: 'Contenido de la política' }).getByRole('link', { name: /Tus derechos/ }).click();
    await expect(page).toHaveURL(/#derechos$/);
  });

  test('registro: el RUC acepta solo dígitos y valida el dígito verificador al escribir', async ({ page }) => {
    await goto(page, '/registro');
    const ruc = page.locator('#ruc');
    await ruc.fill('20a1b2');
    await expect(ruc).toHaveValue('2012');
    await ruc.fill(validRuc());
    await expect(page.getByText(/Dígito verificador correcto/)).toBeVisible();
    await ruc.fill('20123456780');
    await ruc.blur();
    await expect(page.getByRole('alert').filter({ hasText: /RUC no es válido/ })).toBeVisible();
  });

  test('login: el correo se valida al salir del campo y el error se limpia al corregir', async ({ page }) => {
    await goto(page, '/login');
    await page.getByLabel('Correo electrónico').fill('correo-sin-arroba');
    await page.getByLabel('Contraseña', { exact: true }).focus();
    await expect(page.getByText(/formato nombre@empresa\.pe/)).toBeVisible();
    await page.getByLabel('Correo electrónico').fill('pyme@logyx.pe');
    await expect(page.getByText(/formato nombre@empresa\.pe/)).toHaveCount(0);
  });
});
