import { useMemo } from 'react';
import { ArrowRight, CircleCheck, Clock, Gavel, ShieldAlert } from 'lucide-react';
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, Th, Td, Tr } from '@/components/ui/table';
import { RouteLine } from '@/components/domain/RouteLine';
import { Countdown } from '@/components/domain/Countdown';
import { StatusPill } from '@/components/domain/StatusPill';
import { CARGO_LABELS, CITY_LABELS, INCIDENT_CATEGORY_LABELS, formatDate, formatMoney, formatTons } from '@/lib/format';
import { ageTone, formatOpenTime } from '@/features/incidents/lib';
import { cn } from '@/lib/utils';
import type { IncidentAlert, SlaAlerts as Alerts, SlaRequest } from '../server';

const AUCTION: Record<string, [string, 'petrol' | 'neutral' | 'amber']> = { OPEN: ['Abierta', 'petrol'], CLOSED: ['Cerrada', 'neutral'], CANCELLED: ['Cancelada', 'neutral'] };

function Section({ id, title, count, tone, hint, icon: Icon, link, children }: {
  id: string; title: string; count: number; tone: 'red' | 'amber' | 'neutral'; hint: string; icon: React.ComponentType<{ className?: string }>;
  link?: { href: string; label: string }; children: React.ReactNode;
}) {
  return (
    <Card id={id} className="min-w-0 scroll-mt-20 overflow-hidden">
      <CardHeader className="items-start">
        <div className="flex min-w-0 items-start gap-3">
          <span className={cn('mt-0.5 grid size-8 shrink-0 place-items-center rounded-input', count ? (tone === 'red' ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-700') : 'bg-steel-100 text-ink-500')}><Icon className="size-4" aria-hidden="true" /></span>
          <div className="min-w-0">
            <CardTitle className="flex flex-wrap items-center gap-2">{title} <Badge tone={count ? tone : 'neutral'}><span className="font-mono tabular-nums">{count}</span></Badge></CardTitle>
            <CardDescription>{hint}</CardDescription>
          </div>
        </div>
        {link && <a href={link.href} className="hidden shrink-0 items-center gap-1 text-sm font-medium text-petrol-700 hover:underline sm:inline-flex">{link.label}<ArrowRight className="size-3.5" aria-hidden="true" /></a>}
      </CardHeader>
      {children}
    </Card>
  );
}

function Positive({ title, children }: { title: string; children: string }) {
  return (
    <div className="flex items-center gap-3 px-5 py-6">
      <span className="grid size-9 shrink-0 place-items-center rounded-full bg-emerald-50 text-emerald-700"><CircleCheck className="size-5" aria-hidden="true" /></span>
      <div className="min-w-0">
        <p className="text-sm font-semibold text-ink-900">{title}</p>
        <p className="text-sm text-ink-500">{children}</p>
      </div>
    </div>
  );
}

/** Tiempo sin ofertas contra el SLA: ámbar al pasarlo, rojo si duplica el SLA. */
function SlaClock({ h, sla }: { h: number; sla: number }) {
  const over = sla ? Math.max(0, h - sla) : 0;
  const tone = sla && h >= sla * 2 ? 'text-red-700' : 'text-amber-700';
  return (
    <span className="inline-flex flex-col items-end gap-0.5 text-right">
      <span className={cn('inline-flex items-center gap-1 font-mono text-sm font-semibold tabular-nums', tone)}><Clock className="size-3.5" aria-hidden="true" />{formatOpenTime(h)}</span>
      {over > 0 && <span className="text-xs text-ink-500">+{formatOpenTime(over)} sobre el SLA</span>}
    </span>
  );
}

export function SlaAlerts({ alerts, onAssign }: { alerts: Alerts; onAssign: (r: SlaRequest) => void }) {
  const noBids = useMemo(() => [...(alerts.requestsWithoutBids ?? [])].sort((a, b) => (b.hoursWithoutBids ?? 0) - (a.hoursWithoutBids ?? 0)), [alerts.requestsWithoutBids]);
  const expiring = useMemo(() => [...(alerts.expiringAuctions ?? [])].sort((a, b) => (a.auctionClosesAt ?? '').localeCompare(b.auctionClosesAt ?? '')), [alerts.expiringAuctions]);
  const incidents = useMemo(() => [...(alerts.shipmentsWithIncidents ?? [])].sort((a, b) => (b.hoursOpen ?? 0) - (a.hoursOpen ?? 0)), [alerts.shipmentsWithIncidents]);
  const sla = alerts.slaHours ?? 24;

  return (
    <div className="space-y-6">
      <Section id="sin-ofertas" title="Solicitudes sin ofertas" count={noBids.length} tone="red" icon={Gavel} link={{ href: '/operador/solicitudes?estado=PUBLISHED', label: 'Ver solicitudes publicadas' }}
        hint={sla ? `Más de ${sla} h sin ninguna oferta (RN10). Asigna un transportista para no perder la carga; las más atrasadas van primero.` : 'Publicadas sin ninguna oferta. Asigna un transportista para no perder la carga.'}>
        {noBids.length === 0 ? (
          <Positive title="Sin alertas de SLA">{`Todo en orden: no hay solicitudes con SLA vencido.`}</Positive>
        ) : (
          <Table label="Solicitudes sin ofertas" stack>
            <thead><tr><Th>Ruta y solicitante</Th><Th>Carga</Th><Th className="text-right">Precio sugerido</Th><Th className="text-right">Sin ofertas</Th><Th>Subasta</Th><Th className="relative"><span className="sr-only">Acción</span></Th></tr></thead>
            <tbody>
              {noBids.map((r) => {
                const [label, tone] = AUCTION[r.auctionStatus ?? 'OPEN'] ?? ['—', 'neutral'];
                return (
                  <Tr key={r.id}>
                    <Td primary className="min-w-64">
                      <a href={`/operador/solicitudes/${r.id}`} className="block rounded hover:opacity-80"><RouteLine size="sm" origin={r.originCity!} destination={r.destinationCity!} /></a>
                      <span className="mt-1 block max-w-[22rem] truncate text-xs text-ink-500">{r.requesterName}</span>
                    </Td>
                    <Td label="Carga" className="whitespace-nowrap"><div>{CARGO_LABELS[r.cargoType!] ?? r.cargoType}<div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)} · recojo {formatDate(r.pickupDate)}</div></div></Td>
                    <Td label="Sugerido" className="text-right"><div><span className="money block whitespace-nowrap">{formatMoney(r.suggestedPrice)}</span><span className="block whitespace-nowrap font-mono text-xs text-ink-500">Piso {formatMoney(r.priceFloor)}</span></div></Td>
                    <Td label="Sin ofertas" className="whitespace-nowrap"><SlaClock h={r.hoursWithoutBids ?? 0} sla={sla} /></Td>
                    <Td label="Subasta" className="whitespace-nowrap">{r.auctionStatus === 'OPEN' && r.auctionClosesAt ? <div className="flex flex-col items-start gap-0.5"><Badge tone={tone}>{label}</Badge><Countdown closesAt={r.auctionClosesAt} className="whitespace-nowrap text-xs" /></div> : <Badge tone={tone}>{label}</Badge>}</Td>
                    <Td actions className="relative text-right"><Button size="sm" onClick={() => onAssign(r)}>Asignar transportista<span className="sr-only"> para {r.requesterName}, ruta {CITY_LABELS[r.originCity!] ?? r.originCity} a {CITY_LABELS[r.destinationCity!] ?? r.destinationCity}</span></Button></Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </Section>

      <Section id="incidencias" title="Envíos con incidencias" count={incidents.length} tone="red" icon={ShieldAlert} link={{ href: '/operador/incidencias', label: 'Abrir bandeja' }}
        hint="Incidencias abiertas o en revisión (RN11): congelan el avance de las paradas hasta que las resuelvas. Ámbar pasadas 12 h, rojo pasadas 24 h.">
        {incidents.length === 0 ? (
          <Positive title="Sin incidencias activas">Los envíos avanzan con normalidad.</Positive>
        ) : (
          <Table label="Envíos con incidencias" stack>
            <thead><tr><Th>Incidencia</Th><Th>Estado</Th><Th className="text-right">Abierta hace</Th><Th>Ruta</Th><Th>PYME y transportista</Th><Th className="relative"><span className="sr-only">Acciones</span></Th></tr></thead>
            <tbody>
              {incidents.map((i: IncidentAlert) => (
                <Tr key={i.incidentId}>
                  <Td primary className="whitespace-nowrap font-medium text-ink-900"><a href={`/incidencias/${i.incidentId}`} className="hover:underline">{INCIDENT_CATEGORY_LABELS[i.category!] ?? i.category}</a></Td>
                  <Td label="Estado"><StatusPill status={i.status!} /></Td>
                  <Td label="Abierta hace" className={cn('whitespace-nowrap text-right font-mono tabular-nums', ageTone(i.hoursOpen ?? 0, true))}>{formatOpenTime(i.hoursOpen ?? 0)}</Td>
                  <Td label="Ruta" className="min-w-56"><RouteLine size="sm" origin={i.originCity!} destination={i.destinationCity!} /></Td>
                  <Td label="Partes"><div className="min-w-0"><span className="block max-w-[15rem] truncate font-medium text-ink-900">{i.requesterName}</span><span className="block max-w-[15rem] truncate text-xs text-ink-500">{i.carrierName}</span></div></Td>
                  <Td actions className="relative whitespace-nowrap text-right">
                    <Button size="sm" variant="outline" asChild><a href={`/incidencias/${i.incidentId}`}>Ver incidencia<span className="sr-only"> de {i.requesterName}</span></a></Button>
                    <Button size="sm" variant="ghost" asChild className="ml-1"><a href={`/operador/envios/${i.shipmentId}`}>Ver envío<span className="sr-only"> de {i.requesterName}</span></a></Button>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Section>

      <Section id="por-vencer" title="Subastas por vencer" count={expiring.length} tone="amber" icon={Clock}
        hint={`Cierran en ${alerts.expiringWindowMinutes ?? 10} min o menos. Revisa si recibieron ofertas.`}>
        {expiring.length === 0 ? (
          <Positive title="Sin subastas por vencer">No hay subastas que cierren en los próximos minutos.</Positive>
        ) : (
          <Table label="Subastas por vencer" stack>
            <thead><tr><Th>Ruta</Th><Th>Solicitante</Th><Th>Cierra en</Th><Th className="text-right">Ofertas</Th><Th className="text-right">Sugerido</Th><Th className="relative"><span className="sr-only">Acción</span></Th></tr></thead>
            <tbody>
              {expiring.map((r) => (
                <Tr key={r.id}>
                  <Td primary className="min-w-64"><RouteLine size="sm" origin={r.originCity!} destination={r.destinationCity!} /></Td>
                  <Td label="Solicitante"><span className="block max-w-[14rem] truncate font-medium text-ink-900">{r.requesterName}</span></Td>
                  <Td label="Cierra en"><Countdown closesAt={r.auctionClosesAt} /></Td>
                  <Td label="Ofertas" className={cn('text-right font-mono tabular-nums', r.bidCount ? 'text-ink-900' : 'font-semibold text-amber-700')}>{r.bidCount ?? 0}</Td>
                  <Td label="Sugerido" className="money whitespace-nowrap text-right">{formatMoney(r.suggestedPrice)}</Td>
                  <Td actions className="relative text-right"><Button size="sm" variant="outline" asChild><a href={`/operador/solicitudes/${r.id}`}>Ver solicitud<span className="sr-only"> de {r.requesterName}</span></a></Button></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Section>
    </div>
  );
}
