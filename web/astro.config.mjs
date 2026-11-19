// @ts-check
import { defineConfig } from 'astro/config';
import node from '@astrojs/node';
import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  output: 'server',
  adapter: node({ mode: 'standalone' }),
  integrations: [react()],
  vite: { cacheDir: process.env.VITE_CACHE_DIR || undefined, plugins: [tailwindcss()], optimizeDeps: { exclude: ['maplibre-gl'] } },
  site: 'https://oncargo.tech',
  server: { port: 4321 },
  security: {
    checkOrigin: true,
    // H-10: detrás de Caddy, Astro solo usa X-Forwarded-For/-Proto/-Host si el Host está en esta lista;
    // sin ella `clientAddress` era la IP del contenedor caddy y los límites por IP de la API se compartían
    // entre todos los usuarios de la web. Caddy reemplaza el X-Forwarded-For que mande el cliente (no hay
    // trusted_proxies), así que el primer valor es la IP real. www.* solo redirige en Caddy, pero se admite.
    allowedDomains: [
      { hostname: 'oncargo.tech', protocol: 'https' },
      { hostname: 'www.oncargo.tech', protocol: 'https' },
    ],
  },
});
