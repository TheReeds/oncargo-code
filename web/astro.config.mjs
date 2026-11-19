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
  security: { checkOrigin: true },
});
