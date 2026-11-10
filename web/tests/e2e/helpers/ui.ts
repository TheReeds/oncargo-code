import { expect, type Page, type BrowserContext, type Locator } from '@playwright/test';

/** Espera a que todas las islas de Astro estén hidratadas (los handlers de React ya existen). */
export async function hydrated(page: Page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForFunction(() => !document.querySelector('astro-island[ssr]'), undefined, { timeout: 20_000 });
}

export async function goto(page: Page, url: string) {
  await page.goto(url);
  await hydrated(page);
}

/** Inyecta la sesión (cookie httpOnly) sin pasar por el formulario. */
export async function setSession(context: BrowserContext, token: string, baseURL: string) {
  await context.addCookies([{ name: 'logyx_session', value: token, url: baseURL }]);
}

export async function loginViaUI(page: Page, email: string, password: string) {
  await goto(page, '/login');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Ingresar' }).click();
}

/** Combobox / Select (Radix + cmdk): abre y elige una opción por su texto. */
export async function pick(page: Page, trigger: Locator, option: string | RegExp) {
  await trigger.click();
  await page.getByRole('option', { name: option }).first().click();
}

/** Toast de sonner con el texto dado. */
export const toastWith = (page: Page, text: string | RegExp) => page.locator('[data-sonner-toast]').filter({ hasText: text });

/** Elige un día (número) en el calendario abierto del DatePicker/DateRangePicker. */
export async function pickDay(page: Page, isoDate: string) {
  // navega hasta el mes correcto
  for (let i = 0; i < 12; i++) {
    const cell = page.locator(`[data-day="${isoDate}"]`);
    if (await cell.count()) break;
    await page.getByRole('button', { name: /siguiente|next/i }).first().click();
  }
  await page.locator(`[data-day="${isoDate}"] button, button[data-day="${isoDate}"]`).first().click();
}

export { expect };
