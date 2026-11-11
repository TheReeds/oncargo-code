import { useEffect, useRef } from 'react';

/** Evento de ventana: algo cambió en la negociación (mensaje o contra-oferta); los paneles refrescan de inmediato. */
export const NEGOTIATION_EVENT = 'logyx:negotiation-changed';
/** Evento de ventana: abre el hilo de un transportista en el panel de conversación (`detail: { carrierOrgId }`). */
export const OPEN_THREAD_EVENT = 'logyx:open-thread';
export const notifyNegotiationChanged = () => window.dispatchEvent(new Event(NEGOTIATION_EVENT));

/** Ejecuta `fn` cada `ms`, solo con la pestaña visible; al volver a verla refresca de inmediato. */
export function usePolling(fn: () => void, ms: number, enabled = true) {
  const ref = useRef(fn);
  ref.current = fn;
  useEffect(() => {
    if (!enabled || ms <= 0) return;
    const run = () => { if (document.visibilityState === 'visible') ref.current(); };
    const id = setInterval(run, ms);
    document.addEventListener('visibilitychange', run);
    window.addEventListener(NEGOTIATION_EVENT, run);
    return () => {
      clearInterval(id);
      document.removeEventListener('visibilitychange', run);
      window.removeEventListener(NEGOTIATION_EVENT, run);
    };
  }, [ms, enabled]);
}

/** El mensaje de la API para LOGYX-NEG-001 (datos de contacto) se reemplaza por una explicación con siguiente paso. */
export const RN07_COPY = 'No puedes compartir teléfonos, correos, enlaces ni usuarios de redes. Coordina todo por este chat: así ambas partes quedan protegidas.';
export const isContactError = (field: string | null, message: string) => (field === 'body' || field === 'message') && /contacto|chat de logyx|rn07/i.test(message);
