import { useCallback, useEffect, useState } from 'react';
import { Clock, PackageCheck, Route } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/card';
import { Timeline, type TimelineItem } from '@/components/ui/timeline';
import { Alert } from '@/components/ui/alert';
import { StatusPill } from '@/components/domain/StatusPill';
import { RouteLine } from '@/components/domain/RouteLine';
import { CITY_LABELS, formatDate, formatDateTime, formatKm } from '@/lib/format';
import type { PublicTrackingView } from '../server';
import PublicRouteMap from './PublicRouteMap';

/** Cada cuánto se vuelve a consultar el seguimiento público (no hay tiempo real sin sesión). */
const POLL_MS = 60_000;

const sameLimaDay = (a: string, b: string) => {
  const f = (s: string) => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date(s));
  return f(a) === f(b);
};
const hour = (s: string) => formatDateTime(s).split(' · ')[1];

/** «19 nov, entre las 09:00 y las 18:30» o, si cruza de día, «entre el 19 nov · 21:00 y el 20 nov · 06:00». */
export function etaText(from: string, to: string) {
  if (sameLimaDay(from, to)) return `${formatDateTime(from).split(' · ')[0]}, entre las ${hour(from)} y las ${hour(to)}`;
  return `entre el ${formatDateTime(from)} y el ${formatDateTime(to)}`;
}

function items(t: PublicTrackingView): TimelineItem[] {
  return [...(t.stops ?? [])].sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0)).map((s, i) => {
    const done = s.status === 'COMPLETED';
    const here = s.status === 'ARRIVED';
    const pickup = s.type === 'PICKUP';
    const meta = done
      ? `${pickup ? 'Carga recogida' : 'Entregado'} · ${formatDateTime(s.completedAt)}`
      : here ? `El conductor llegó · ${formatDateTime(s.arrivedAt)}` : 'Pendiente';
    return { id: `${s.sequence ?? i}`, title: `${pickup ? 'Recojo' : 'Entrega'} · ${CITY_LABELS[s.city!] ?? s.city}`, meta, status: done ? 'done' : here ? 'current' : 'pending' };
  });
}

/**
 * Seguimiento público para el cliente final de la PYME (roadmap 1.6, ADR-0011). Solo lo que la API pública entrega
 * (estado, ciudades, hitos con hora, ETA aproximada y ruta): nada de precio, contactos, placa ni empresas. Sin sesión
 * no hay tiempo real: vuelve a consultar cada 60 s mientras la pestaña está visible (`/s/<token>/estado`).
 */
export default function PublicTracking({ token, initial }: { token: string; initial: PublicTrackingView }) {
  const [t, setT] = useState(initial);
  const [gone, setGone] = useState(false);

  const poll = useCallback(async () => {
    try {
      const res = await fetch(`/s/${encodeURIComponent(token)}/estado`, { headers: { accept: 'application/json' }, cache: 'no-store' });
      if (res.status === 404) { setGone(true); return; }
      if (res.ok) setT((await res.json()) as PublicTrackingView);
    } catch { /* sin red: se reintenta en el siguiente ciclo */ }
  }, [token]);

  useEffect(() => {
    if (gone) return;
    let timer: ReturnType<typeof setInterval> | undefined;
    const start = () => { if (!timer) timer = setInterval(poll, POLL_MS); };
    const stop = () => { clearInterval(timer); timer = undefined; };
    const onVis = () => { if (document.visibilityState === 'visible') { void poll(); start(); } else stop(); };
    if (document.visibilityState === 'visible') start();
    document.addEventListener('visibilitychange', onVis);
    return () => { stop(); document.removeEventListener('visibilitychange', onVis); };
  }, [poll, gone]);

  if (gone) return <Alert tone="info" title="Este enlace ya no está disponible">Quien te lo envió puede compartirte uno nuevo.</Alert>;

  const delivered = t.status === 'DELIVERED';
  const cancelled = t.status === 'CANCELLED';
  const atDestination = t.status === 'IN_TRANSIT' && (t.stops ?? []).some((s) => s.type === 'DELIVERY' && s.status === 'ARRIVED');
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="items-start">
          <div className="min-w-0 space-y-1.5">
            <div className="flex flex-wrap items-center gap-2"><CardTitle className="text-base">Envío <span className="font-mono">{t.code}</span></CardTitle><StatusPill status={t.status!} /></div>
            {t.originCity && t.destinationCity && <RouteLine origin={t.originCity} destination={t.destinationCity} />}
          </div>
        </CardHeader>
        <CardBody className="space-y-5">
          <div className="flex items-start gap-3 rounded-input bg-petrol-50 px-4 py-3 text-sm" role="status">
            {delivered ? <PackageCheck className="mt-0.5 size-5 shrink-0 text-emerald-700" aria-hidden="true" /> : <Clock className="mt-0.5 size-5 shrink-0 text-petrol-700" aria-hidden="true" />}
            <div className="min-w-0">
              {delivered ? (
                <><div className="font-medium text-ink-900">Entregado</div><div className="text-ink-700">{formatDateTime(t.deliveredAt)}</div></>
              ) : cancelled ? (
                <div className="font-medium text-ink-900">Este envío se canceló</div>
              ) : atDestination ? (
                <><div className="font-medium text-ink-900">El conductor llegó a destino</div><div className="text-ink-700">La entrega se confirmará en unos minutos.</div></>
              ) : t.etaFrom && t.etaTo ? (
                <><div className="font-medium text-ink-900">Llegada estimada: {etaText(t.etaFrom, t.etaTo)}</div><div className="text-xs text-ink-500">Es una estimación según la ruta y el último avance del conductor; puede variar por el clima o la carretera.</div></>
              ) : t.status === 'PENDING' ? (
                <><div className="font-medium text-ink-900">Esperando el recojo</div>{t.pickupDate && <div className="text-ink-700">Recojo programado para el {formatDate(t.pickupDate)}</div>}</>
              ) : (
                <div className="font-medium text-ink-900">En camino: llegando pronto</div>
              )}
            </div>
          </div>
          <div>
            <h2 className="mb-3 text-sm font-semibold text-ink-900">Hitos</h2>
            <Timeline label="Hitos del envío" items={items(t)} />
          </div>
          <p className="text-xs text-ink-500">Actualizado {formatDateTime(t.updatedAt)} · se actualiza solo cada minuto.</p>
        </CardBody>
      </Card>
      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2 text-base"><Route className="size-4 text-ink-500" aria-hidden="true" />Ruta por carretera{t.route?.distanceKm ? <span className="font-mono text-sm font-normal text-ink-500">{formatKm(t.route.distanceKm)}</span> : null}</CardTitle></CardHeader>
        <CardBody><PublicRouteMap polyline={t.route?.polyline} precision={t.route?.polylinePrecision} label="Mapa de la ruta del envío" /></CardBody>
      </Card>
    </div>
  );
}
