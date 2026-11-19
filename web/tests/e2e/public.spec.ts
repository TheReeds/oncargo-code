import { test, expect } from '@playwright/test';
import { goto } from './helpers/ui';
import { PASSWORD, uid, validRuc } from './helpers/api';

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

  test('SEO: title y description únicos, canónica, JSON-LD válido, noindex fuera del sitemap, robots.txt y sitemap.xml', async ({ page, request }) => {
    test.slow(); // 8 páginas SSR
    const seen = new Set<string>();
    for (const path of ['/', '/precios', '/contacto', '/registro', '/terminos', '/privacidad']) {
      await page.goto(path);
      const title = await page.title();
      const desc = await page.locator('meta[name="description"]').getAttribute('content');
      expect(title.length, `${path}: title ≤ 60`).toBeLessThanOrEqual(60);
      expect(desc!.length, `${path}: description 110–160`).toBeGreaterThanOrEqual(110);
      expect(desc!.length).toBeLessThanOrEqual(160);
      expect(seen.has(title) || seen.has(desc!), `${path}: title/description repetidos`).toBe(false);
      seen.add(title); seen.add(desc!);
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', `https://oncargo.tech${path}`);
      await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
      // Solo el DOM de la página: `locator()` atraviesa el shadow DOM (la barra de herramientas de `astro dev` trae sus propios h1).
      expect(await page.evaluate(() => document.querySelectorAll('h1').length), `${path}: un solo h1`).toBe(1);
      const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!);
      const types = ld['@graph'].map((n: { '@type': string }) => n['@type']);
      expect(types).toEqual(path === '/' ? ['Organization', 'WebSite', 'Service'] : ['BreadcrumbList']);
    }
    for (const path of ['/login', '/olvide']) {
      await page.goto(path);
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex');
      await expect(page.locator('link[rel="canonical"]')).toHaveCount(0);
    }
    const robots = await (await request.get('/robots.txt')).text();
    for (const rule of ['Disallow: /s/', 'Disallow: /app/', 'Disallow: /dev/', 'Disallow: /exportar/', 'Sitemap: https://oncargo.tech/sitemap.xml']) expect(robots).toContain(rule);
    const sitemap = await (await request.get('/sitemap.xml')).text();
    expect(sitemap.match(/<loc>/g)).toHaveLength(6);
    expect(sitemap).not.toContain('/login');
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

  test('términos: borrador marcado, versión visible, índice lateral y enlaces en el pie', async ({ page }) => {
    await goto(page, '/terminos');
    await expect(page.getByRole('heading', { level: 1, name: 'Términos y condiciones' })).toBeVisible();
    await expect(page.getByText('Borrador — pendiente de revisión legal').first()).toBeVisible();
    await expect(page.getByText(/todavía no fue revisado por asesoría legal/)).toBeVisible();
    await expect(page.getByTestId('terms-version')).not.toBeEmpty();
    // Responsabilidad por la carga (decisión del dueño): el transportista según su póliza; la PYME declara valor y naturaleza
    const resp = page.locator('#responsabilidad');
    await expect(resp).toContainText('póliza de seguro');
    await expect(resp).toContainText('valor');
    await page.getByRole('navigation', { name: 'Contenido de los términos' }).getByRole('link', { name: /Responsabilidad/ }).click();
    await expect(page).toHaveURL(/#responsabilidad$/);
    const footer = page.getByRole('contentinfo');
    await expect(footer.getByRole('link', { name: 'Política de privacidad' })).toBeVisible();
    await expect(footer.getByRole('link', { name: /soporte@oncargo\.tech/ })).toHaveAttribute('href', 'mailto:soporte@oncargo.tech');
  });

  test('los enlaces de términos están en el pie de la landing y del registro; privacidad usa privacidad@oncargo.tech', async ({ page }) => {
    await goto(page, '/');
    await expect(page.getByRole('contentinfo').getByRole('link', { name: 'Términos y condiciones' })).toHaveAttribute('href', '/terminos');
    await expect(page.getByRole('contentinfo').getByRole('link', { name: 'Precios y comisión' })).toHaveAttribute('href', '/precios');
    await expect(page.getByRole('contentinfo').getByRole('link', { name: /privacidad@oncargo\.tech/ })).toBeVisible();
    await goto(page, '/registro');
    await expect(page.getByRole('contentinfo').getByRole('link', { name: 'Términos y condiciones' })).toHaveAttribute('href', '/terminos');
    await goto(page, '/privacidad');
    await expect(page.getByRole('link', { name: 'privacidad@oncargo.tech' }).first()).toBeVisible();
  });

  test('precios: comisión al transportista, IGV y un ejemplo con números de la API', async ({ page }) => {
    await goto(page, '/precios');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Precios claros');
    const cards = page.getByTestId('price-card-title');
    await expect(cards.nth(0)).toHaveText('Sin costo');
    await expect(cards.nth(1)).toContainText('% del precio');
    await expect(cards.nth(2)).toContainText('18 % incluido');
    const example = page.getByTestId('price-example');
    await expect(example).toContainText('IGV (18 %)');
    await expect(example).toContainText('Comisión de OnCargo al transportista');
    await expect(page.getByTestId('price-example-net')).toContainText('S/');
  });

  test('contacto: soporte, privacidad, horario, 11 ciudades y aviso por correo para ciudades nuevas', async ({ page }) => {
    await goto(page, '/contacto');
    const channels = page.getByRole('list', { name: 'Canales de contacto' });
    await expect(channels.getByRole('link', { name: 'soporte@oncargo.tech' })).toHaveAttribute('href', 'mailto:soporte@oncargo.tech');
    await expect(channels.getByRole('link', { name: 'privacidad@oncargo.tech' })).toBeVisible();
    await expect(page.getByTestId('support-hours')).toContainText('Lunes a viernes');
    await expect(page.getByRole('list', { name: 'Ciudades del corredor' }).getByRole('listitem')).toHaveCount(11);
    await expect(page.getByRole('link', { name: 'Avísame cuando lleguen a mi ciudad' })).toHaveAttribute('href', /^mailto:soporte@oncargo\.tech\?/);
  });

  test('FAQ: pago, responsabilidad por la carga, verificación y ciudades', async ({ page }) => {
    await goto(page, '/');
    for (const q of ['¿Cuánto cuesta y quién paga la comisión?', '¿Cómo se paga el flete?', '¿Quién responde si se daña o se pierde la carga?', '¿Cuánto demora la verificación de mi empresa?', '¿Qué ciudades cubren?']) {
      await expect(page.getByText(q)).toBeVisible();
    }
    await page.getByText('¿Quién responde si se daña o se pierde la carga?').click();
    await expect(page.getByText(/según su póliza de seguro/).last()).toBeVisible();
  });

  test('cotizador de la landing: la API confirma la estimación, muestra el IGV y conserva la cotización al registrarse', async ({ page }) => {
    await goto(page, '/');
    const quote = page.locator('#corredor');
    await quote.scrollIntoViewIfNeeded();
    // Hasta que responde la API es una estimación; luego «Cotización de referencia» (mismos números que al publicar)
    await expect(quote.getByText('Cotización de referencia')).toBeVisible({ timeout: 15_000 });
    await expect(quote.getByText('Incluye IGV')).toBeVisible();
    const href = await quote.getByRole('link', { name: /Publicar esta carga/ }).getAttribute('href');
    expect(href).toMatch(/^\/registro\?next=%2Fpyme%2Fsolicitudes%2Fnueva%3Forigen%3DLIMA%26destino%3DAREQUIPA%26peso%3D3200%26tipo%3DGENERAL$/);
  });

  test('registro con `next`: la PYME termina en la nueva solicitud; un destino externo se ignora', async ({ page }) => {
    const next = '/pyme/solicitudes/nueva?origen=LIMA&destino=JULIACA&peso=3000&tipo=GENERAL';
    for (const [target, expected] of [[next, /\/pyme\/solicitudes\/nueva\?origen=LIMA/], ['//evil.example.com', /\/pyme$/]] as const) {
      await page.context().clearCookies();
      await goto(page, `/registro?next=${encodeURIComponent(target)}`);
      await page.getByLabel('Razón social').fill(`Next ${uid()} SAC`);
      await page.getByLabel('RUC').fill(validRuc());
      await page.getByLabel('Tu nombre completo').fill('María Quispe');
      await page.getByLabel('Correo electrónico').fill(`e2e-${uid()}@prueba.pe`);
      await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
      await page.getByLabel(/Acepto la/).click();
      await page.getByLabel(/Acepto los términos/).click();
      await page.getByRole('button', { name: 'Crear cuenta' }).click();
      await expect(page).toHaveURL(expected);
    }
  });
});
