import { expect, type Page, type BrowserContext, type Locator } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

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

/** axe (WCAG 2.1 AA): sin violaciones `serious`/`critical`. `include` limita el análisis a un selector (p. ej. un diálogo abierto). */
export async function expectNoSeriousViolations(page: Page, include?: string) {
  const b = new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']);
  const results = await (include ? b.include(include) : b).analyze();
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
}

/** Llena el formulario de nueva solicitud (Lima → Juliaca) sin enviarlo. */
export async function fillRequestForm(page: Page, o: { description?: string; pickupIso: string }) {
  await pick(page, page.getByRole('combobox', { name: 'Ciudad de origen' }), 'Lima');
  await pick(page, page.getByRole('combobox', { name: 'Ciudad de destino' }), 'Juliaca');
  await page.getByLabel('Peso').fill('3200');
  await page.getByLabel('Dirección de recojo').fill('Av. Argentina 1234, Callao');
  await page.getByLabel('Dirección de entrega').fill('Jr. Lima 456, Juliaca');
  await page.getByLabel('Descripción de la carga').fill(o.description ?? '120 rollos de tela, paletizados');
  await page.getByRole('button', { name: 'Fecha de recojo' }).click();
  await pickDay(page, o.pickupIso);
}

export { expect };