import { defineConfig, devices } from '@playwright/test';

/**
 * Suite e2e de LOGYX Web.
 *  - E2E_BASE_URL  URL de la web (si se define, NO se levanta servidor propio). Default http://localhost:4322
 *  - E2E_API_URL   URL de la API (backend). Default http://localhost:8180
 *  - E2E_SERVER    'dev' (astro dev, default) o 'prod' (build + node dist/server/entry.mjs)
 * La API debe estar corriendo con el perfil dev (datos demo). Ver web/AGENTS.md.
 */
const apiUrl = process.env.E2E_API_URL ?? 'http://localhost:8180';
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:4322';
const port = new URL(baseURL).port || '4322';
const prod = process.env.E2E_SERVER === 'prod';

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 45_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL,
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    viewport: { width: 1440, height: 900 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: prod ? `npm run build && node ./dist/server/entry.mjs` : `npm run dev -- --port ${port}`,
        url: baseURL,
        reuseExistingServer: true,
        timeout: 180_000,
        env: { API_URL: apiUrl, INSECURE_COOKIES: '1', HOST: '127.0.0.1', PORT: port },
      },
});
