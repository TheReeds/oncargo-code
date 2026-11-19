/**
 * SEO de la web pública (fuente única). Ver `.claude/skills/logyx-design-system/references/web.md` → «SEO de la web pública».
 * - `INDEXABLE_PATHS` = las URL de `public/sitemap.xml`: solo ellas llevan canónica y JSON-LD; el resto sale con `noindex` (Base.astro).
 * - JSON-LD: `Organization` + `WebSite` + `Service` en la home, `BreadcrumbList` en las internas. Sin `LocalBusiness` (no hay local
 *   abierto al público) ni `FAQPage` (Google ya no muestra ese resultado enriquecido).
 */
import { SUPPORT_EMAIL } from '@/lib/contact';
import { CITIES } from './corridor-data';

export const SITE_URL = 'https://oncargo.tech';
export const INDEXABLE_PATHS = ['/', '/precios', '/contacto', '/registro', '/terminos', '/privacidad'];

const ORG_ID = `${SITE_URL}/#organizacion`;

const organization = {
  '@type': 'Organization',
  '@id': ORG_ID,
  name: 'OnCargo',
  url: `${SITE_URL}/`,
  logo: { '@type': 'ImageObject', url: `${SITE_URL}/icon-512.png`, width: 512, height: 512 },
  email: SUPPORT_EMAIL,
  areaServed: { '@type': 'Country', name: 'Perú' },
  contactPoint: { '@type': 'ContactPoint', contactType: 'customer support', email: SUPPORT_EMAIL, availableLanguage: 'es', areaServed: 'PE' },
};

const website = {
  '@type': 'WebSite',
  '@id': `${SITE_URL}/#web`,
  name: 'OnCargo',
  url: `${SITE_URL}/`,
  inLanguage: 'es-PE',
  publisher: { '@id': ORG_ID },
};

const service = {
  '@type': 'Service',
  '@id': `${SITE_URL}/#servicio`,
  name: 'Fletes y transporte de carga Lima – Sierra Sur',
  serviceType: 'Transporte de carga por carretera',
  description: 'Marketplace B2B de fletes: la empresa publica su carga, transportistas verificados ofertan en una subasta de hasta 20 minutos o 5 ofertas y el envío se sigue parada por parada.',
  provider: { '@id': ORG_ID },
  audience: { '@type': 'BusinessAudience', name: 'PYMEs y empresas de transporte' },
  areaServed: CITIES.map((c) => ({ '@type': 'City', name: c.label, containedInPlace: { '@type': 'Country', name: 'Perú' } })),
};

/** Grafo de la home. */
export const homeJsonLd = () => [organization, website, service];

/** Migas de pan de una página interna (Inicio → página). */
export const breadcrumbJsonLd = (name: string, path: string) => [
  {
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Inicio', item: `${SITE_URL}/` },
      { '@type': 'ListItem', position: 2, name, item: `${SITE_URL}${path}` },
    ],
  },
];
