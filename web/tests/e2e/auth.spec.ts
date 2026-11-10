import { test, expect } from '@playwright/test';
import { DEMO, PASSWORD, uid, validRuc } from './helpers/api';
import { goto, loginViaUI, pick, hydrated } from './helpers/ui';

test.describe('Registro', () => {
  test('valida campos vacíos, RUC inválido y consentimiento', async ({ page }) => {
    await goto(page, '/registro');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expect(page.getByText('Ingresa la razón social de tu empresa.')).toBeVisible();
    await expect(page.getByText('Ingresa un RUC de 11 dígitos.')).toBeVisible();
    await expect(page.getByText('Acepta la política de privacidad para continuar.')).toBeVisible();

    // RUC con 11 dígitos pero dígito verificador incorrecto -> error de la API, inline bajo el campo RUC
    const id = uid();
    await page.getByLabel('Razón social').fill(`Prueba ${id} SAC`);
    await page.getByLabel('RUC').fill('20123456780');
    await page.getByLabel('Tu nombre completo').fill('María Quispe');
    await page.getByLabel('Correo electrónico').fill(`e2e-${id}@prueba.pe`);
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
    await page.getByLabel(/Acepto la/).click();
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    const rucField = page.locator('#ruc');
    await expect(rucField).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByRole('alert').filter({ hasText: /RUC no es válido/ })).toBeVisible();
    await expect(page).toHaveURL(/\/registro/);
  });

  test('sin consentimiento no se crea la cuenta', async ({ page }) => {
    await goto(page, '/registro');
    await page.getByLabel('Razón social').fill('Sin Consentimiento SAC');
    await page.getByLabel('RUC').fill(validRuc());
    await page.getByLabel('Tu nombre completo').fill('María Quispe');
    await page.getByLabel('Correo electrónico').fill(`e2e-${uid()}@prueba.pe`);
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Acepta la política de privacidad' })).toBeVisible();
    await expect(page).toHaveURL(/\/registro/);
  });

  test('PYME se registra y llega a su panel; correo repetido se marca en el campo', async ({ page, context }) => {
    const id = uid();
    const email = `e2e-${id}@prueba.pe`;
    await goto(page, '/registro');
    await page.getByLabel('Razón social').fill(`Textiles ${id} SAC`);
    await page.getByLabel('RUC').fill(validRuc());
    await pick(page, page.getByRole('combobox', { name: 'Ciudad' }), 'Lima');
    await page.getByLabel('Tu nombre completo').fill('María Quispe Huamán');
    await page.getByLabel('Correo electrónico').fill(email);
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
    await page.getByLabel(/Acepto la/).click();
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expect(page).toHaveURL(/\/pyme$/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    // Mismo correo con otro RUC -> error en el campo correo
    await context.clearCookies();
    await goto(page, '/registro');
    await page.getByLabel('Razón social').fill(`Otra ${id} SAC`);
    await page.getByLabel('RUC').fill(validRuc());
    await page.getByLabel('Tu nombre completo').fill('María Quispe');
    await page.getByLabel('Correo electrónico').fill(email);
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
    await page.getByLabel(/Acepto la/).click();
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expect(page.locator('#email')).toHaveAttribute('aria-invalid', 'true');
    await expect(page.locator('#ruc')).not.toHaveAttribute('aria-invalid', 'true');
  });

  test('transportista se registra desde ?tipo=transportista', async ({ page }) => {
    const id = uid();
    await goto(page, '/registro?tipo=transportista');
    await expect(page.getByRole('radio', { name: /transportista/i })).toBeChecked();
    await page.getByLabel('Razón social').fill(`Fletes ${id} SAC`);
    await page.getByLabel('RUC').fill(validRuc());
    await page.getByLabel('Tu nombre completo').fill('Luis Mamani');
    await page.getByLabel('Correo electrónico').fill(`e2e-${id}@prueba.pe`);
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
    await page.getByLabel(/Acepto la/).click();
    await page.getByRole('button', { name: 'Crear cuenta' }).click();
    await expect(page).toHaveURL(/\/transportista\/marketplace$/);
  });
});

test.describe('Login, logout y redirección por rol', () => {
  test('credenciales incorrectas muestran el error', async ({ page }) => {
    await loginViaUI(page, DEMO.pyme, 'incorrecta123');
    await expect(page.getByRole('alert').filter({ hasText: /Credenciales inválidas|incorrectos/i })).toBeVisible();
    await expect(page).toHaveURL(/\/login/);
  });

  test('validación local de login', async ({ page }) => {
    await goto(page, '/login');
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(page.getByText('Ingresa tu correo electrónico.')).toBeVisible();
    await expect(page.getByText('Ingresa tu contraseña.')).toBeVisible();
  });

  const cases: [string, string, RegExp][] = [
    ['PYME', DEMO.pyme, /\/pyme$/],
    ['transportista', DEMO.carrier, /\/transportista\/marketplace$/],
    ['conductor', DEMO.driver, /\/conductor$/],
  ];
  for (const [name, email, url] of cases) {
    test(`${name} entra a su home, no accede a rutas de otro rol y cierra sesión`, async ({ page }) => {
      await loginViaUI(page, email, PASSWORD);
      await expect(page).toHaveURL(url);
      if (name === 'conductor') {
        await page.goto('/pyme');
        await expect(page).toHaveURL(/\/conductor$/);
      } else if (name === 'PYME') {
        await page.goto('/transportista/marketplace');
        await expect(page).toHaveURL(/\/pyme$/);
      } else {
        await page.goto('/pyme/solicitudes');
        await expect(page).toHaveURL(/\/transportista\/marketplace$/);
      }
      // logout
      if (name === 'conductor') {
        await page.getByRole('button', { name: 'Cerrar sesión' }).click();
      } else {
        await hydrated(page);
        await page.getByRole('button', { name: /Menú de cuenta/ }).first().click();
        await page.getByRole('menuitem', { name: 'Cerrar sesión' }).click();
      }
      await expect(page).toHaveURL(/\/(login)?$/);
      await page.goto('/pyme');
      await expect(page).toHaveURL(/\/login/);
    });
  }

  test('ruta protegida sin sesión redirige a login conservando el destino', async ({ page }) => {
    await page.goto('/pyme/solicitudes');
    await expect(page).toHaveURL(/\/login\?next=%2Fpyme%2Fsolicitudes/);
    await hydrated(page);
    await page.getByLabel('Correo electrónico').fill(DEMO.pyme);
    await page.getByLabel('Contraseña', { exact: true }).fill(PASSWORD);
    await page.getByRole('button', { name: 'Ingresar' }).click();
    await expect(page).toHaveURL(/\/pyme\/solicitudes$/);
  });

  test('mostrar/ocultar contraseña', async ({ page }) => {
    await goto(page, '/login');
    const pw = page.getByLabel('Contraseña', { exact: true });
    await pw.fill('secreto123');
    await expect(pw).toHaveAttribute('type', 'password');
    await page.getByRole('button', { name: 'Mostrar contraseña' }).click();
    await expect(pw).toHaveAttribute('type', 'text');
  });
});
