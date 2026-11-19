import { defineMiddleware } from 'astro:middleware';
import { ApiError } from './lib/api/client';
import type { MiddlewareHandler } from 'astro';
import { clearSession, homeFor, readSession } from './lib/session';
import { runWithSession } from './lib/request-context';
import { can, writeRouteFor } from './lib/permissions';

const PUBLIC = ['/', '/login', '/registro', '/privacidad', '/terminos', '/seguridad', '/precios', '/contacto', '/olvide', '/restablecer', '/baja', '/baja/one-click'];
/** Públicas que también ve quien ya tiene sesión (no se redirigen a su home). */
const OPEN_WITH_SESSION = ['/privacidad', '/terminos', '/seguridad', '/precios', '/contacto', '/olvide', '/restablecer', '/baja', '/baja/one-click'];
/** Páginas con un token en la URL: sin `Referer` hacia ningún sitio (V24: `/baja?t=` del correo). */
const NO_REFERRER = ['/restablecer', '/olvide', '/baja', '/baja/one-click'];
/** Seguimiento público por enlace (roadmap 1.6, ADR-0011): `/s/<token>` y su consulta `/s/<token>/estado`, sin sesión y con o sin ella. */
const isTrackingLink = (p: string) => p.startsWith('/s/');
const isAsset = (p: string) => !p.startsWith('/_actions') && (p.startsWith('/_') || p.startsWith('/favicon') || p.includes('.'));

const handle = defineMiddleware(async (ctx, next) => {
  const { pathname } = ctx.url;
  if (isAsset(pathname)) return next();
  // Catálogo de componentes: solo en desarrollo, sin sesión (la página devuelve 404 en producción).
  if (pathname === '/dev/ui') return next();

  const session = readSession(ctx.cookies);
  ctx.locals.session = session;

  if (pathname.startsWith('/_actions')) return next();
  // Stream en tiempo real (proxy SSE): sin sesión responde 401 (no redirige) y lo usan todos los roles, incluido el conductor.
  if (pathname === '/eventos') return next();

  const isPublic = PUBLIC.includes(pathname) || isTrackingLink(pathname);
  if (!session) {
    if (isPublic) return next();
    return ctx.redirect(`/login?next=${encodeURIComponent(pathname)}`);
  }

  const home = homeFor(session);
  if (isPublic) {
    // Usuarios con sesión: / y /login van a su home (privacidad y el restablecimiento de contraseña se ven siempre).
    if (OPEN_WITH_SESSION.includes(pathname) || isTrackingLink(pathname)) return runWithSession(session, next);
    return ctx.redirect(home);
  }

  // Control de acceso por rol
  const driverOnly = home === '/conductor';
  if (driverOnly && pathname !== '/conductor' && pathname !== '/logout' && pathname !== '/cuenta') return ctx.redirect('/conductor');
  // Secciones de otro tipo de cuenta: página 403 amable (F58) en lugar de redirigir en silencio. El conductor solo tiene /conductor.
  const denied = (pathname === '/pyme' || pathname.startsWith('/pyme/')) && session.orgType !== 'PYME'
    || (pathname === '/transportista' || pathname.startsWith('/transportista/')) && session.orgType !== 'CARRIER'
    || (pathname === '/operador' || pathname.startsWith('/operador/')) && session.orgType !== 'OPERATOR'
    || (pathname === '/organizacion' || pathname.startsWith('/organizacion/')) && (session.orgType !== 'PYME' && session.orgType !== 'CARRIER');
  if (denied) return runWithSession(session, () => next('/sin-acceso'));
  if (pathname === '/conductor' && !driverOnly) return ctx.redirect(home);
  // Páginas que solo sirven para escribir (roadmap v2 1.7): sin el permiso se vuelve al listado con un aviso
  // (`AppShell` lo muestra con «Pedir acceso»). El backend igual respondería 403 (`AUTH-007`).
  const writeRoute = writeRouteFor(pathname);
  if (writeRoute && !can(session, writeRoute.needs)) return ctx.redirect(`${writeRoute.redirect}?sinPermiso=${writeRoute.key}`);

  try {
    // Permisos y rol viajan en un AsyncLocalStorage para que las islas React los lean durante el SSR (`lib/permissions/react.tsx`).
    return await runWithSession(session, next);
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      clearSession(ctx.cookies);
      return ctx.redirect('/login?expired=1');
    }
    throw e;
  }
});

// --- Cabeceras de seguridad -------------------------------------------------
// CSP por cabecera (no `security.csp` de Astro: sus hashes anulan 'unsafe-inline' en style-src y rompen los estilos
// inline de Radix/Tailwind). script-src lleva 'unsafe-inline' porque Astro inyecta scripts inline de hidratación
// (<astro-island>, client:*); es la única excepción de script. Sin eval. Mejora futura: nonces o security.csp.
const CSP = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https://tiles.openfreemap.org",
  "connect-src 'self' https://tiles.openfreemap.org",
  "worker-src 'self' blob:",
  "font-src 'self' data:",
  "object-src 'none'",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ');

const isProd = import.meta.env.PROD || process.env.NODE_ENV === 'production';

function secure(res: Response, url: URL, req: Request): Response {
  const set: Record<string, string> = {
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': NO_REFERRER.includes(url.pathname) || isTrackingLink(url.pathname) ? 'no-referrer' : 'strict-origin-when-cross-origin',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
    'X-Frame-Options': 'DENY',
  };
  // En dev Vite inyecta scripts y un websocket de HMR: CSP solo en producción.
  if (isProd) set['Content-Security-Policy'] = CSP;
  if (isTrackingLink(url.pathname) || url.pathname.startsWith('/baja')) set['X-Robots-Tag'] = 'noindex, nofollow, noarchive';
  const https = url.protocol === 'https:' || req.headers.get('x-forwarded-proto')?.split(',')[0].trim() === 'https';
  if (isProd && (https || process.env.HSTS === 'true')) set['Strict-Transport-Security'] = 'max-age=31536000; includeSubDomains';
  try {
    for (const [k, v] of Object.entries(set)) res.headers.set(k, v);
    return res;
  } catch {
    // Headers inmutables (respuesta de fetch proxy): se reconstruye.
    const h = new Headers(res.headers);
    for (const [k, v] of Object.entries(set)) h.set(k, v);
    return new Response(res.body, { status: res.status, statusText: res.statusText, headers: h });
  }
}

export const onRequest: MiddlewareHandler = async (ctx, next) => {
  const res = await handle(ctx, next);
  // `handle` puede no devolver nada (mismo comportamiento que antes de envolverlo).
  if (!(res instanceof Response) || isAsset(ctx.url.pathname)) return res as Response;
  const type = res.headers.get('content-type') ?? '';
  // HTML y redirecciones; no tocamos descargas de documentos ni JSON de actions.
  if (type.includes('text/html') || (res.status >= 300 && res.status < 400)) return secure(res, ctx.url, ctx.request);
  return res;
};
