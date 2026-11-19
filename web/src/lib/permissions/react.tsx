import { useSyncExternalStore, type ReactNode } from 'react';
import { ReadOnlyNotice } from '@/components/domain/ReadOnlyNotice';
import { hasPermission, type Permission } from './index';

/**
 * Permisos y rol del usuario dentro de una isla React. Una sola fuente por render:
 * - servidor (SSR de la isla): el middleware guarda la sesión en un `AsyncLocalStorage` (`lib/request-context.ts`);
 * - navegador: `<html data-perms="READ OPERATE …" data-role="VIEWER">`, escrito por `Base.astro` con la misma sesión.
 * `useSyncExternalStore` evita avisos de hidratación si algún día no coinciden (React re-renderiza con el valor del cliente).
 * Es solo presentación: el backend hace cumplir el rol (`AUTH-007`/`AUTH-008`).
 */
type G = { __logyxPerms?: () => string; __logyxRole?: () => string };
const subscribe = () => () => {};
const permsClient = () => document.documentElement.dataset.perms ?? '';
const permsServer = () => (typeof document !== 'undefined' ? permsClient() : ((globalThis as G).__logyxPerms?.() ?? ''));
const roleClient = () => document.documentElement.dataset.role ?? '';
const roleServer = () => (typeof document !== 'undefined' ? roleClient() : ((globalThis as G).__logyxRole?.() ?? ''));

export function usePermissions(): string[] {
  const raw = useSyncExternalStore(subscribe, permsClient, permsServer);
  return raw ? raw.split(' ') : [];
}

export function useCan(p: Permission): boolean {
  return hasPermission(usePermissions(), p);
}

/** Rol de organización (`ADMIN|OPS|VIEWER|DRIVER`) o `null` sin sesión. */
export function useRole(): string | null {
  return useSyncExternalStore(subscribe, roleClient, roleServer) || null;
}

export interface CanProps {
  /** Permiso requerido (por omisión `OPERATE`). */
  permission?: Permission;
  /**
   * Sin el permiso, muestra el aviso de solo lectura con «Pedir acceso» (`ReadOnlyNotice`). `true` = texto estándar;
   * un texto agrega contexto («No puedes publicar solicitudes.»). Úsalo una vez por pantalla/panel, no en cada botón.
   */
  readOnly?: boolean | string;
  /** Qué mostrar sin el permiso (gana sobre `readOnly`). Por omisión, nada. */
  fallback?: ReactNode;
  /** Clase del aviso de solo lectura. */
  noticeClassName?: string;
  children?: ReactNode;
}

/**
 * Muestra `children` solo si el usuario tiene `permission`. Patrón único de la web para acciones de escritura:
 * ```tsx
 * <Can><Button onClick={accept}>Aceptar oferta</Button></Can>                       // oculta sin OPERATE
 * <Can readOnly="No puedes publicar solicitudes."><RequestForm /></Can>            // y explica por qué
 * <Can permission="MANAGE_USERS" fallback={<p>…</p>}>…</Can>
 * ```
 * Funciona en el SSR de la isla y en `.astro` sin directiva `client:*` (render estático). Nunca ocultes información
 * con `<Can>`: solo acciones.
 */
export function Can({ permission = 'OPERATE', readOnly, fallback, noticeClassName, children }: CanProps) {
  const allowed = useCan(permission);
  if (allowed) return <>{children}</>;
  if (fallback !== undefined) return <>{fallback}</>;
  if (readOnly) return <ReadOnlyNotice permission={permission} className={noticeClassName}>{typeof readOnly === 'string' ? readOnly : undefined}</ReadOnlyNotice>;
  return null;
}
