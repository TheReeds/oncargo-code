// Permisos por rol de organización (V14, ADR-0009). FUENTE ÚNICA de la web: seguro para servidor e islas.
//
//   import { can, roleLabel } from '@/lib/permissions';                 // .astro, server.ts, middleware
//   import { Can, useCan, useRole } from '@/lib/permissions/react';     // islas React (y SSR de la isla)
//
// El backend es quien manda (403 `LOGYX-AUTH-007` / `LOGYX-AUTH-008`); esto solo decide qué mostrar. La tabla espeja
// `identity/domain/OrgAccessPolicy.permissions` y se calcula con los claims `role` + `org_type` del JWT, así no hace
// falta una llamada extra a `/auth/me` en cada página (`UserInfo.permissions` devuelve exactamente lo mismo; el spec
// `roles.spec.ts` lo comprueba contra la API).
//
// Regla de UX (roadmap v2, 1.7): sin el permiso se OCULTAN las acciones (botones, formularios, menús), nunca la
// información; donde se ocultó algo se muestra el aviso de solo lectura (`<Can readOnly>` / `ReadOnlyNotice`) con
// «Pedir acceso».

export type Permission = 'READ' | 'OPERATE' | 'MANAGE_USERS' | 'MANAGE_ORGANIZATION' | 'MANAGE_DRIVERS';
export type OrgRole = 'ADMIN' | 'OPS' | 'VIEWER' | 'DRIVER';

export const ROLE_LABEL: Record<OrgRole, string> = { ADMIN: 'Administrador', OPS: 'Operaciones', VIEWER: 'Lector', DRIVER: 'Conductor' };
export const ROLE_DESCRIPTION: Record<Exclude<OrgRole, 'DRIVER'>, string> = {
  ADMIN: 'Puede hacer todo: operar, invitar usuarios, cambiar roles y editar los datos de la empresa.',
  OPS: 'Opera el día a día: publica, oferta, acepta, negocia y gestiona envíos. No administra usuarios ni la empresa.',
  VIEWER: 'Solo consulta: ve solicitudes, ofertas y envíos, pero no puede cambiar nada.',
};

/** Qué habilita cada permiso, en palabras del usuario (diálogo «Pedir acceso»). */
export const PERMISSION_LABEL: Record<Permission, string> = {
  READ: 'Consultar',
  OPERATE: 'Operar (publicar, ofertar, aceptar, negociar y gestionar envíos)',
  MANAGE_USERS: 'Gestionar usuarios y roles',
  MANAGE_ORGANIZATION: 'Editar los datos de la empresa',
  MANAGE_DRIVERS: 'Gestionar las cuentas de conductores',
};

export const roleLabel = (role: string | null | undefined) => ROLE_LABEL[(role ?? '') as OrgRole] ?? 'Usuario';

const ALL: Permission[] = ['READ', 'OPERATE', 'MANAGE_USERS', 'MANAGE_ORGANIZATION', 'MANAGE_DRIVERS'];

/** Espejo de `OrgAccessPolicy.permissions(role, orgType)`. Sin rol (tokens muy antiguos) = ADMIN, como las cuentas previas a V14. */
export function permissionsFor(role: string | null | undefined, orgType: string | null | undefined): Permission[] {
  switch (role ?? 'ADMIN') {
    case 'ADMIN':
      return orgType === 'CARRIER' ? ALL : ['READ', 'OPERATE', 'MANAGE_USERS', 'MANAGE_ORGANIZATION'];
    case 'OPS':
      return ['READ', 'OPERATE'];
    default:
      return ['READ'];
  }
}

export const hasPermission = (permissions: readonly string[] | null | undefined, p: Permission) => !!permissions?.includes(p);

/** `can(session, 'OPERATE')`: acepta la sesión (o cualquier objeto con `permissions`). Sin sesión, nunca. */
export function can(subject: { permissions?: readonly string[] } | null | undefined, p: Permission): boolean {
  return hasPermission(subject?.permissions, p);
}

/** El rol que conviene pedir para obtener `p` (OPERATE → Operaciones; el resto → Administrador). */
export const roleFor = (p: Permission): OrgRole => (p === 'OPERATE' || p === 'READ' ? 'OPS' : 'ADMIN');

/** Aviso discreto para quien solo puede consultar (VIEWER). */
export const READ_ONLY_NOTICE = 'Tu rol Lector solo permite consultar. Pide a un administrador de tu empresa que cambie tu rol si necesitas operar.';

/** Explicación de por qué falta `p` según el rol actual (tooltips, avisos y 403). */
export function deniedReason(role: string | null | undefined, p: Permission = 'OPERATE'): string {
  if (p === 'OPERATE' || role === 'VIEWER') return `Tu rol ${roleLabel(role ?? 'VIEWER')} solo permite consultar.`;
  return `Esto lo hace un administrador de tu empresa; tu rol es ${roleLabel(role)}.`;
}

/**
 * Rutas de escritura que exigen un permiso (guardas de `middleware.ts`). Quien no lo tiene vuelve a `redirect` con
 * `?sinPermiso=1` (la página destino muestra el aviso). Agrega aquí las páginas nuevas que solo sirven para escribir.
 */
export const WRITE_ROUTES: { path: string; needs: Permission; redirect: string; key: string; notice: string }[] = [
  { path: '/pyme/solicitudes/nueva', needs: 'OPERATE', redirect: '/pyme/solicitudes', key: 'nueva-solicitud', notice: 'No puedes publicar solicitudes.' },
  { path: '/transportista/viajes/nuevo', needs: 'OPERATE', redirect: '/transportista/viajes', key: 'nuevo-viaje', notice: 'No puedes publicar viajes.' },
  // `/organizacion/usuarios` (MANAGE_USERS) no redirige: responde el 403 amable (`/sin-acceso`) desde la propia página.
];

/** Texto del aviso tras una redirección de `WRITE_ROUTES` (`?sinPermiso=<key>`). */
export const deniedRouteNotice = (key: string | null | undefined) => (key ? WRITE_ROUTES.find((r) => r.key === key) : undefined);

/** Evento del navegador que abre el diálogo «Pedir acceso» (lo escucha `RequestAccessHost`, montado en `AppShell`). */
export const REQUEST_ACCESS_EVENT = 'logyx:request-access';
/** Abre «Pedir acceso» desde cualquier isla o toast (solo navegador). */
export function openRequestAccess(permission: Permission = 'OPERATE') {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(REQUEST_ACCESS_EVENT, { detail: { permission } }));
}

export function writeRouteFor(pathname: string) {
  const p = pathname.length > 1 ? pathname.replace(/\/+$/, '') : pathname;
  return WRITE_ROUTES.find((r) => r.path === p);
}
