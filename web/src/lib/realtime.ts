/**
 * Cliente de tiempo real (ADR-0007). Una sola conexión `EventSource` por pestaña contra `/eventos` (proxy autenticado
 * de Astro → `GET /api/v1/events/stream`; el JWT nunca sale del servidor), compartida por todas las islas de la página
 * (el módulo es un singleton del bundle).
 *
 * - Reconexión: EventSource reintenta solo (y envía `Last-Event-ID`); si la conexión queda cerrada (401/429/502) se
 *   reabre a mano con espera exponencial y `?lastEventId=`.
 * - Los eventos son **avisos para refrescar** (payload mínimo): las islas vuelven a pedir sus datos por Astro Actions.
 *   Al (re)conectar se avisa con `onResync` para que todos refresquen (pudo haberse perdido algo).
 * - Estado (`connecting | live | reconnecting | offline`) para el indicador "En vivo" y para activar el polling de
 *   respaldo lento solo cuando no hay conexión (`useLiveRefresh`).
 */
import { useEffect, useRef, useState } from 'react';

export type RealtimeEvent = {
  type: string;
  seq: number | null;
  occurredAt: string | null;
  data: Record<string, unknown>;
};
export type RealtimeStatus = 'connecting' | 'live' | 'reconnecting' | 'offline';
type Handler = (e: RealtimeEvent) => void;

const ENDPOINT = '/eventos';
const MAX_BACKOFF = 30_000;

const handlers = new Map<number, { types: Set<string> | null; fn: Handler }>();
const statusListeners = new Set<(s: RealtimeStatus) => void>();
const resyncListeners = new Set<() => void>();
let nextId = 1;
let es: EventSource | null = null;
let status: RealtimeStatus = 'connecting';
let lastEventId: string | null = null;
let backoff = 1000;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
let closeTimer: ReturnType<typeof setTimeout> | null = null;
let everLive = false;
let stopped = false;

const isBrowser = () => typeof window !== 'undefined' && typeof EventSource !== 'undefined';

function setStatus(s: RealtimeStatus) {
  if (s === status) return;
  status = s;
  statusListeners.forEach((l) => l(s));
  if (isBrowser()) document.documentElement.dataset.realtime = s;
}

function scheduleReconnect() {
  if (stopped || retryTimer || !isBrowser()) return;
  setStatus(everLive ? 'reconnecting' : 'offline');
  const wait = backoff + Math.round(Math.random() * 500);
  backoff = Math.min(backoff * 2, MAX_BACKOFF);
  retryTimer = setTimeout(() => { retryTimer = null; open(); }, wait);
}

function dispatch(e: RealtimeEvent) {
  handlers.forEach(({ types, fn }) => {
    if (types && !types.has(e.type)) return;
    try { fn(e); } catch (err) { console.error('[realtime]', err); }
  });
}

function open() {
  if (!isBrowser() || es || stopped) return;
  const url = lastEventId ? `${ENDPOINT}?lastEventId=${encodeURIComponent(lastEventId)}` : ENDPOINT;
  const source = new EventSource(url);
  es = source;
  source.onmessage = (msg) => {
    if (msg.lastEventId) lastEventId = msg.lastEventId;
    let e: RealtimeEvent;
    try { e = JSON.parse(msg.data); } catch { return; }
    if (!e || typeof e.type !== 'string') return;
    if (e.type === 'stream.ready' || e.type === 'stream.resync') {
      const reconnected = everLive || e.type === 'stream.resync';
      everLive = true;
      backoff = 1000;
      setStatus('live');
      if (reconnected) resyncListeners.forEach((l) => l());
      return;
    }
    if (e.type === 'stream.expired') {
      // El JWT de la cookie venció: el proxy responderá 401 y quedará el polling de respaldo (que lleva al login).
      source.close();
      es = null;
      scheduleReconnect();
      return;
    }
    dispatch(e);
  };
  // Los latidos (`: ping`, cada ~20 s) no llegan a JS: mantienen viva la conexión en proxies; si el TCP se corta,
  // EventSource lo detecta y dispara onerror.
  source.onerror = () => {
    if (source.readyState === EventSource.CLOSED) {
      // Respuesta no-200 (401, 429, 502…): EventSource no reintenta; lo hacemos nosotros con espera.
      if (es === source) es = null;
      scheduleReconnect();
    } else {
      // Reintento automático del navegador (con Last-Event-ID).
      setStatus(everLive ? 'reconnecting' : 'connecting');
    }
  };
}

function ensureOpen() {
  if (closeTimer) { clearTimeout(closeTimer); closeTimer = null; }
  stopped = false;
  open();
}

function maybeClose() {
  if (handlers.size > 0 || statusListeners.size > 0) return;
  if (closeTimer) clearTimeout(closeTimer);
  closeTimer = setTimeout(() => {
    if (handlers.size > 0 || statusListeners.size > 0) return;
    stopped = true;
    es?.close();
    es = null;
    if (retryTimer) { clearTimeout(retryTimer); retryTimer = null; }
  }, 5_000);
}

/** Suscribe `fn` a los tipos dados (o a todos con `'*'`). Devuelve la función para desuscribirse. */
export function subscribe(types: string[] | '*', fn: Handler): () => void {
  const id = nextId++;
  handlers.set(id, { types: types === '*' ? null : new Set(types), fn });
  ensureOpen();
  return () => { handlers.delete(id); maybeClose(); };
}

export function onStatus(fn: (s: RealtimeStatus) => void): () => void {
  statusListeners.add(fn);
  fn(status);
  ensureOpen();
  return () => { statusListeners.delete(fn); maybeClose(); };
}

/** Se llama al reconectar (o al pedir el servidor `stream.resync`): refresca todo lo que muestres. */
export function onResync(fn: () => void): () => void {
  resyncListeners.add(fn);
  return () => resyncListeners.delete(fn);
}

export const realtimeStatus = () => status;

// ---------------------------------------------------------------- hooks React

/** Llama a `handler` con cada evento de los tipos dados (el handler puede cambiar sin resuscribir). */
export function useRealtime(types: string[] | '*', handler: Handler, enabled = true) {
  const ref = useRef(handler);
  ref.current = handler;
  const key = types === '*' ? '*' : [...types].sort().join(',');
  useEffect(() => {
    if (!enabled) return;
    return subscribe(types, (e) => ref.current(e));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, enabled]);
}

export function useRealtimeStatus(): RealtimeStatus {
  const [s, setS] = useState<RealtimeStatus>('connecting');
  useEffect(() => onStatus(setS), []);
  return s;
}

type LiveRefreshOptions = {
  /** Tipos de evento que disparan el refresco. */
  types: string[];
  /** Filtro opcional (p. ej. solo los de esta solicitud: `e => e.data.requestId === id`). */
  match?: (e: RealtimeEvent) => boolean;
  /** Vuelve a pedir los datos (Astro Action). */
  refresh: () => void | Promise<unknown>;
  /** Polling de respaldo (ms) mientras NO hay conexión en vivo; 0 = sin respaldo. */
  fallbackMs?: number;
  /** Agrupa ráfagas de eventos (ms). */
  debounceMs?: number;
  enabled?: boolean;
};

/**
 * Refresca con los eventos en vivo, al reconectar y, solo si el stream no está conectado, con un polling lento de
 * respaldo (pausado con la pestaña oculta; al volver a verla refresca).
 */
export function useLiveRefresh({ types, match, refresh, fallbackMs = 60_000, debounceMs = 300, enabled = true }: LiveRefreshOptions) {
  const refreshRef = useRef(refresh);
  refreshRef.current = refresh;
  const matchRef = useRef(match);
  matchRef.current = match;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const live = useRealtimeStatus() === 'live';

  const trigger = () => {
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(() => { timer.current = null; void refreshRef.current(); }, debounceMs);
  };

  useRealtime(types, (e) => { if (!matchRef.current || matchRef.current(e)) trigger(); }, enabled);

  useEffect(() => {
    if (!enabled) return;
    const offResync = onResync(trigger);
    return () => { offResync(); if (timer.current) clearTimeout(timer.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  useEffect(() => {
    if (!enabled || live || fallbackMs <= 0) return;
    const run = () => { if (document.visibilityState === 'visible') void refreshRef.current(); };
    const id = setInterval(run, fallbackMs);
    document.addEventListener('visibilitychange', run);
    return () => { clearInterval(id); document.removeEventListener('visibilitychange', run); };
  }, [enabled, live, fallbackMs]);
}
