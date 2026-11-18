import { useSyncExternalStore } from 'react';
import { hasPermission, type Permission } from './permissions';

/**
 * Permisos del usuario dentro de una isla React. Una sola fuente por render:
 * - servidor (SSR de la isla): el middleware guarda la sesión en un `AsyncLocalStorage` (`lib/request-context.ts`);
 * - navegador: `<html data-perms="READ OPERATE …">`, escrito por `Base.astro` con la misma sesión.
 * `useSyncExternalStore` evita avisos de hidratación si algún día no coinciden (React re-renderiza con el valor del cliente).
 * Es solo presentación: el backend hace cumplir el rol (`AUTH-007`/`AUTH-008`).
 */
const subscribe = () => () => {};
const clientSnapshot = () => document.documentElement.dataset.perms ?? '';
const serverSnapshot = () => (typeof document !== 'undefined' ? clientSnapshot() : ((globalThis as { __logyxPerms?: () => string }).__logyxPerms?.() ?? ''));

export function usePermissions(): string[] {
  const raw = useSyncExternalStore(subscribe, clientSnapshot, serverSnapshot);
  return raw ? raw.split(' ') : [];
}

export function useCan(p: Permission): boolean {
  return hasPermission(usePermissions(), p);
}
