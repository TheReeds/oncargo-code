import { CheckCircle2 } from 'lucide-react';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Table, Th, Td, Tr } from '@/components/ui/table';
import { RouteLine } from '@/components/domain/RouteLine';
import { Countdown } from '@/components/domain/Countdown';
import { StatusPill } from '@/components/domain/StatusPill';
import { CARGO_LABELS, INCIDENT_CATEGORY_LABELS, formatDate, formatMoney, formatTons } from '@/lib/format';
import type { IncidentAlert, SlaAlerts as Alerts, SlaRequest } from '../server';

const AUCTION: Record<string, [string, 'petrol' | 'neutral' | 'amber']> = { OPEN: ['Abierta', 'petrol'], CLOSED: ['Cerrada', 'neutral'], CANCELLED: ['Cancelada', 'neutral'] };

function Section({ id, title, count, tone, hint, children }: { id: string; title: string; count: number; tone: 'red' | 'amber' | 'neutral'; hint: string; children: React.ReactNode }) {
  return (
    <Card id={id} className="scroll-mt-20">
      <CardHeader>
        <div className="min-w-0">
          <CardTitle className="flex items-center gap-2">{title} <Badge tone={count ? tone : 'neutral'}>{count}</Badge></CardTitle>
          <p className="mt-0.5 text-xs text-ink-500">{hint}</p>
        </div>
      </CardHeader>
      {children}
    </Card>
  );
}

function Positive({ children }: { children: string }) {
  return (
    <div className="flex items-center gap-3 px-5 py-6 text-sm text-ink-700">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-700"><CheckCircle2 className="size-5" aria-hidden="true" /></span>
      {children}
    </div>
  );
}

const hoursLabel = (h: number) => (h >= 48 ? `${Math.floor(h / 24)} d ${h % 24} h` : `${h} h`);

function Hours({ h }: { h: number }) {
  const tone = h >= 48 ? 'text-red-600' : 'text-amber-700';
  return <span className={`font-mono font-semibold tabular-nums ${tone}`}>{hoursLabel(h)}</span>;
}

function Requester({ r }: { r: SlaRequest }) {
  return <div className="min-w-0"><div className="max-w-[16rem] truncate font-medium text-ink-900">{r.requesterName}</div></div>;
}

export function SlaAlerts({ alerts, onAssign }: { alerts: Alerts; onAssign: (r: SlaRequest) => void }) {
  const noBids = alerts.requestsWithoutBids ?? [];
  const expiring = alerts.expiringAuctions ?? [];
  const incidents = alerts.shipmentsWithIncidents ?? [];
  const sla = alerts.slaHours ?? 24;

  return (
    <div className="space-y-6">
      <Section id="sin-ofertas" title="Solicitudes sin ofertas" count={noBids.length} tone="red" hint={sla ? `Publicadas hace más de ${sla} h sin ninguna oferta. Asigna un transportista para no perder la carga.` : 'Publicadas sin ninguna oferta. Asigna un transportista para no perder la carga.'}>
        {noBids.length === 0 ? (
          <Positive>{`Todo en orden: no hay solicitudes con SLA vencido.`}</Positive>
        ) : (
          <Table>
            <thead><tr><Th>Ruta</Th><Th>Solicitante</Th><Th>Carga</Th><Th>Recojo</Th><Th className="text-right">Sugerido</Th><Th className="text-right">Piso</Th><Th>Sin ofertas</Th><Th>Subasta</Th><Th><span className="sr-only">Acción</span></Th></tr></thead>
            <tbody>
              {noBids.map((r) => {
                const [label, tone] = AUCTION[r.auctionStatus ?? 'OPEN'] ?? ['—', 'neutral'];
                return (
                  <Tr key={r.id}>
                    <Td className="min-w-64"><a href={`/operador/solicitudes/${r.id}`} className="block rounded hover:opacity-80"><RouteLine size="sm" origin={r.originCity!} destination={r.destinationCity!} /></a></Td>
                    <Td><Requester r={r} /></Td>
                    <Td className="whitespace-nowrap">{CARGO_LABELS[r.cargoType!] ?? r.cargoType}<div className="font-mono text-xs text-ink-500">{formatTons(r.weightKg)}</div></Td>
                    <Td className="whitespace-nowrap text-ink-700">{formatDate(r.pickupDate)}</Td>
                    <Td className="money whitespace-nowrap text-right">{formatMoney(r.suggestedPrice)}</Td>
                    <Td className="whitespace-nowrap text-right font-mono tabular-nums text-ink-700">{formatMoney(r.priceFloor)}</Td>
                    <Td className="whitespace-nowrap"><Hours h={r.hoursWithoutBids ?? 0} /></Td>
                    <Td><Badge tone={tone}>{label}</Badge></Td>
                    <Td className="text-right"><Button size="sm" onClick={() => onAssign(r)}>Asignar transportista<span className="sr-only"> para {r.requesterName}, ruta {r.originCity} a {r.destinationCity}</span></Button></Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </Section>

      <Section id="por-vencer" title="Subastas por vencer" count={expiring.length} tone="amber" hint={`Cierran en ${alerts.expiringWindowMinutes ?? 10} min o menos. Revisa si recibieron ofertas.`}>
        {expiring.length === 0 ? (
          <Positive>No hay subastas por vencer en los próximos minutos.</Positive>
        ) : (
          <Table>
            <thead><tr><Th>Ruta</Th><Th>Solicitante</Th><Th>Cierra en</Th><Th className="text-right">Ofertas</Th><Th className="text-right">Sugerido</Th><Th><span className="sr-only">Acción</span></Th></tr></thead>
            <tbody>
              {expiring.map((r) => (
                <Tr key={r.id}>
                  <Td className="min-w-64"><RouteLine size="sm" origin={r.originCity!} destination={r.destinationCity!} /></Td>
                  <Td><Requester r={r} /></Td>
                  <Td><Countdown closesAt={r.auctionClosesAt} /></Td>
                  <Td className={`text-right font-mono tabular-nums ${r.bidCount ? 'text-ink-900' : 'font-semibold text-amber-700'}`}>{r.bidCount ?? 0}</Td>
                  <Td className="money whitespace-nowrap text-right">{formatMoney(r.suggestedPrice)}</Td>
                  <Td className="text-right"><Button size="sm" variant="outline" asChild><a href={`/operador/solicitudes/${r.id}`}>Ver solicitud<span className="sr-only"> de {r.requesterName}</span></a></Button></Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Section>

      <Section id="incidencias" title="Envíos con incidencias" count={incidents.length} tone="red" hint="Incidencias abiertas o en revisión que congelan el avance del envío.">
        {incidents.length === 0 ? (
          <Positive>Sin incidencias activas. Los envíos avanzan con normalidad.</Positive>
        ) : (
          <Table>
            <thead><tr><Th>Incidencia</Th><Th>Estado</Th><Th>Abierta hace</Th><Th>Ruta</Th><Th>PYME y transportista</Th><Th><span className="sr-only">Acciones</span></Th></tr></thead>
            <tbody>
              {incidents.map((i: IncidentAlert) => (
                <Tr key={i.incidentId}>
                  <Td className="whitespace-nowrap font-medium text-ink-900">{INCIDENT_CATEGORY_LABELS[i.category!] ?? i.category}</Td>
                  <Td><StatusPill status={i.status!} /></Td>
                  <Td><Hours h={i.hoursOpen ?? 0} /></Td>
                  <Td className="min-w-56"><RouteLine size="sm" origin={i.originCity!} destination={i.destinationCity!} /></Td>
                  <Td><span className="block max-w-[15rem] truncate font-medium text-ink-900">{i.requesterName}</span><span className="block max-w-[15rem] truncate text-xs text-ink-500">{i.carrierName}</span></Td>
                  <Td className="whitespace-nowrap text-right">
                    <Button size="sm" variant="outline" asChild><a href={`/incidencias/${i.incidentId}`}>Ver incidencia<span className="sr-only"> de {i.requesterName}</span></a></Button>
                    <Button size="sm" variant="ghost" asChild className="ml-1"><a href={`/operador/envios/${i.shipmentId}`}>Ver envío<span className="sr-only"> de {i.requesterName}</span></a></Button>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Section>
    </div>
  );
}
