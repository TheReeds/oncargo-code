// Config para la suite e2e: igual que la base pero sin la barra de herramientas de dev de Astro
// (así no aparece en las capturas de docs/screenshots).
import base from './astro.config.mjs';

export default { ...base, devToolbar: { enabled: false } };
