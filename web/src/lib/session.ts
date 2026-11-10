import type { AstroCookies } from 'astro';
import type { components } from './api/schema';

export type OrgType = components['schemas']['OrgType'];
export const COOKIE = 'logyx_session';

export interface Session {
  token: string;
  userId: string;
  orgId: string;
  orgType: OrgType | null;
  role: string | null;
  groups: string[];
  email: string;
  exp: number;
}

function decode(token: string): Record<string, any> | null {
  try {
    const payload = token.split('.')[1];
    return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
  } catch {
    return null;
  }
}

/** Lee la sesión desde la cookie httpOnly. No verifica firma (lo hace la API en cada llamada). */
export function readSession(cookies: AstroCookies): Session | null {
  const token = cookies.get(COOKIE)?.value;
  if (!token) return null;
  const c = decode(token);
  if (!c || (c.exp && c.exp * 1000 < Date.now())) return null;
  return {
    token,
    userId: c.sub,
    orgId: c.org_id,
    orgType: c.org_type ?? null,
    role: c.role ?? null,
    groups: c.groups ?? [],
    email: c.upn ?? '',
    exp: c.exp,
  };
}

export function writeSession(cookies: AstroCookies, token: string, expiresInSec: number) {
  cookies.set(COOKIE, token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: import.meta.env.PROD && process.env.INSECURE_COOKIES !== '1',
    path: '/',
    maxAge: expiresInSec,
  });
}

export function clearSession(cookies: AstroCookies) {
  cookies.delete(COOKIE, { path: '/' });
}

export const homeFor = (s: Pick<Session, 'orgType' | 'groups'>) =>
  s.groups.includes('DRIVER') && !s.groups.includes('PYME') && !s.groups.includes('CARRIER')
    ? '/conductor'
    : s.orgType === 'CARRIER'
      ? '/transportista/marketplace'
      : s.orgType === 'PYME'
        ? '/pyme'
        : '/login';
