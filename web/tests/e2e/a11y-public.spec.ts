import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { goto } from './helpers/ui';

/** Páginas públicas (landing, login, registro, privacidad, términos, precios, contacto) en claro y oscuro: cero violaciones WCAG 2.1 AA. */
for (const theme of ['light', 'dark']) for (const path of ['/', '/login', '/registro', '/privacidad', '/terminos', '/seguridad', '/precios', '/contacto']) {
  test(`axe ${path} ${theme}`, async ({ page }) => {
    await page.addInitScript((t) => localStorage.setItem('logyx-theme', t), theme);
    await page.emulateMedia({ reducedMotion: 'reduce' }); // las animaciones de entrada alteran el color medido por axe
    await goto(page, path);
    await page.waitForLoadState('networkidle');
    const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(r.violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([]);
  });
}
