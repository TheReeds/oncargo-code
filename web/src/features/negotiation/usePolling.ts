import { useEffect, useRef } from 'react';
import { useLiveRefresh, type RealtimeEvent } from '@/lib/realtime';
import { RN07_FIELD_MESSAGE } from '@/lib/action-error';

/** Evento de ventana: algo cambió en la negociación (mensaje o contra-oferta); los paneles refrescan de inmediato. */
export const NEGOTIATION_EVENT = 'logyx:negotiation-changed';
/** Evento de ventana: abre el hilo de un transportista en el panel de conversación (`detail: { carrierOrgId }`). */
export const OPEN_THREAD_EVENT = 'logyx:open-thread';
export const notifyNegotiationChanged = () => window.dispatchEvent(new Event(NEGOTIATION_EVENT));

/**
 * Refresca la negociación en vivo: eventos SSE de `types` que cumplan `match`, el evento de ventana
 * `NEGOTIATION_EVENT` (cambio propio) y, solo si no hay stream en vivo, un polling lento de respaldo (`fallbackMs`).
 */
export function useNegotiationLive(fn: () => void, opts: { types: string[]; match?: (e: RealtimeEvent) => boolean; fallbackMs?: number; enabled?: boolean }) {
  const ref = useRef(fn);
  ref.current = fn;
  const enabled = opts.enabled ?? true;
  useLiveRefresh({ types: opts.types, match: opts.match, refresh: () => ref.current(), fallbackMs: opts.fallbackMs ?? 30_000, debounceMs: 150, enabled });
  useEffect(() => {
    if (!enabled) return;
    const run = () => ref.current();
    window.addEventListener(NEGOTIATION_EVENT, run);
    return () => window.removeEventListener(NEGOTIATION_EVENT, run);
  }, [enabled]);
}

/** RN07: texto del campo cuando la API responde `LOGYX-NEG-001` (ver `lib/action-error.ts`). */
export const RN07_COPY = RN07_FIELD_MESSAGE;
