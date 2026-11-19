// Solo servidor. Guarda los permisos y el rol de la petición en curso para que las islas React puedan leerlos durante
// el SSR (`useCan`/`useRole`/`<Can>` en `lib/permissions/react.tsx`). Lo activa `middleware.ts`.
import { AsyncLocalStorage } from 'node:async_hooks';

type Ctx = { perms: string; role: string };
const store = new AsyncLocalStorage<Ctx>();
const g = globalThis as { __logyxPerms?: () => string; __logyxRole?: () => string };
g.__logyxPerms = () => store.getStore()?.perms ?? '';
g.__logyxRole = () => store.getStore()?.role ?? '';

export const runWithSession = <T>(session: { permissions?: readonly string[]; role?: string | null } | null | undefined, fn: () => T): T =>
  store.run({ perms: (session?.permissions ?? []).join(' '), role: session?.role ?? '' }, fn);

/** Compatibilidad: solo permisos (sin rol). Prefiere `runWithSession`. */
export const runWithPermissions = <T>(permissions: readonly string[] | undefined, fn: () => T): T => runWithSession({ permissions }, fn);
