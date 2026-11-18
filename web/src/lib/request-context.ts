// Solo servidor. Guarda los permisos de la petición en curso para que las islas React puedan leerlos durante el SSR
// (`useCan` en `lib/use-can.ts`). Lo activa `middleware.ts`.
import { AsyncLocalStorage } from 'node:async_hooks';

const store = new AsyncLocalStorage<string>();
(globalThis as { __logyxPerms?: () => string }).__logyxPerms = () => store.getStore() ?? '';

export const runWithPermissions = <T>(permissions: readonly string[] | undefined, fn: () => T): T => store.run((permissions ?? []).join(' '), fn);
