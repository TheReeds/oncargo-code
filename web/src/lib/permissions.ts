// Permisos por rol de organización (V14, ADR-0009). Seguro para servidor e islas.
//
// El backend es quien manda (403 `LOGYX-AUTH-007` / `LOGYX-AUTH-008`); esto solo decide qué mostrar. La tabla espeja
// `identity/domain/OrgAccessPolicy.permissions` y se calcula con los claims `role` + `org_type` del JWT, así no hace
// falta una llamada extra a `/auth/me` en cada página (`UserInfo.permissions` devuelve exactamente lo mismo; el spec
// `roles.spec.ts` lo comprueba contra la API).

export type Permission = 'READ' | 'OPERATE' | 'MANAGE_USERS' | 'MANAGE_ORGANIZATION' | 'MANAGE_DRIVERS';
export type OrgRole = 'ADMIN' | 'OPS' | 'VIEWER' | 'DRIVER';

export const ROLE_LABEL: Record<OrgRole, string> = { ADMIN: 'Administrador', OPS: 'Operaciones', VIEWER: 'Lector', DRIVER: 'Conductor' };
export const ROLE_DESCRIPTION: Record<Exclude<OrgRole, 'DRIVER'>, string> = {
  ADMIN: 'Puede hacer todo: operar, invitar usuarios, cambiar roles y editar los datos de la empresa.',
  OPS: 'Opera el día a día: publica, oferta, acepta, negocia y gestiona envíos. No administra usuarios ni la empresa.',
  VIEWER: 'Solo consulta: ve solicitudes, ofertas y envíos, pero no puede cambiar nada.',
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

/** Aviso discreto para quien solo puede consultar (VIEWER). */
export const READ_ONLY_NOTICE = 'Tu rol Lector solo permite consultar. Pide a un administrador de tu empresa que cambie tu rol si necesitas operar.';
