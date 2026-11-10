import { defineMiddleware } from 'astro:middleware';
import { ApiError } from './lib/api/client';
import { clearSession, homeFor, readSession } from './lib/session';

const PUBLIC = ['/', '/login', '/registro', '/privacidad'];
const isAsset = (p: string) => !p.startsWith('/_actions') && (p.startsWith('/_') || p.startsWith('/favicon') || p.includes('.'));

export const onRequest = defineMiddleware(async (ctx, next) => {
  const { pathname } = ctx.url;
  if (isAsset(pathname)) return next();

  const session = readSession(ctx.cookies);
  ctx.locals.session = session;

  if (pathname.startsWith('/_actions')) return next();

  const isPublic = PUBLIC.includes(pathname);
  if (!session) {
    if (isPublic) return next();
    return ctx.redirect(`/login?next=${encodeURIComponent(pathname)}`);
  }

  const home = homeFor(session);
  if (isPublic) {
    // Usuarios con sesión: / y /login van a su home (privacidad es siempre visible).
    if (pathname === '/privacidad') return next();
    return ctx.redirect(home);
  }

  // Control de acceso por rol
  const driverOnly = home === '/conductor';
  if (driverOnly && pathname !== '/conductor') return ctx.redirect('/conductor');
  if (pathname.startsWith('/pyme') && session.orgType !== 'PYME') return ctx.redirect(home);
  if (pathname.startsWith('/transportista') && session.orgType !== 'CARRIER') return ctx.redirect(home);
  if (pathname === '/conductor' && !driverOnly) return ctx.redirect(home);

  try {
    return await next();
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      clearSession(ctx.cookies);
      return ctx.redirect('/login?expired=1');
    }
    throw e;
  }
});
