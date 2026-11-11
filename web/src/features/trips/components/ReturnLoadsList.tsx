import { useState } from 'react';
import { CheckCircle2, MapPin, Undo2 } from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import BidSheet from '@/features/marketplace/components/BidSheet';
import { CARGO_LABELS, CITY_LABELS, formatDate, formatKm, formatMoney, formatTons } from '@/lib/format';
import type { RequestView, VehicleView } from '@/lib/api/server';
import type { ReturnLoadView } from '../server';

/** Adapta una carga de retorno a la forma que espera el flujo de oferta existente (`BidSheet`). */
function asRequest(r: ReturnLoadView): RequestView {
  return {
    id: r.requestId, originCity: r.originCity, originAddress: r.originAddress, destinationCity: r.destinationCity, destinationAddress: r.destinationAddress,
    cargoType: r.cargoType, cargoDescription: r.cargoDescription, weightKg: r.weightKg, pickupDate: r.pickupDate, suggestedPrice: r.suggestedPrice, priceFloor: r.priceFloor,
    requesterName: r.requesterName,
  } as RequestView;
}

export default function ReturnLoadsList({ loads, vehicles }: { loads: ReturnLoadView[]; vehicles: VehicleView[] }) {
  const [active, setActive] = useState<ReturnLoadView | null>(null);

  if (loads.length === 0) {
    return (
      <Card>
        <EmptyState title="Aún no hay cargas de retorno" description="Cuando tengas un envío en tránsito o entregado, te sugeriremos cargas que salen desde su destino para que no regreses vacío." action={<Button asChild variant="outline"><a href="/transportista/marketplace">Ver cargas disponibles</a></Button>} />
      </Card>
    );
  }

  return (
    <>
      <ul className="grid gap-4 lg:grid-cols-2" aria-label="Cargas de retorno sugeridas">
        {loads.map((r) => (
          <li key={r.requestId}>
            <Card className="flex h-full flex-col gap-4 p-5" data-testid="return-load">
              <div className="flex items-start justify-between gap-3">
                <RouteLine origin={r.originCity!} destination={r.destinationCity!} originSub={`Recojo ${formatDate(r.pickupDate)}`} />
              </div>
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <Badge tone="emerald">Compatibilidad <span className="font-mono">{Math.round(r.matchScore ?? 0)}</span></Badge>
                <Badge tone="neutral"><MapPin className="size-3" aria-hidden="true" />{r.distanceKm ? <><span className="font-mono">{formatKm(r.distanceKm)}</span> de {CITY_LABELS[r.fromCity!] ?? r.fromCity}</> : `Sale de ${CITY_LABELS[r.fromCity!] ?? r.fromCity}`}</Badge>
                {(r.waitDays ?? 0) > 0 && <Badge tone="neutral">Esperas <span className="font-mono">{r.waitDays}</span> día{r.waitDays === 1 ? '' : 's'}</Badge>}
              </div>
              <div className="text-sm text-ink-700">
                {CARGO_LABELS[r.cargoType!] ?? r.cargoType} · <span className="font-mono">{formatTons(r.weightKg)}</span> · {r.cargoDescription}
                <div className="mt-0.5 text-xs text-ink-500">{r.requesterName} · llegas con <span className="font-mono">{r.vehiclePlate}</span>{r.shipmentDelivered ? ' (envío entregado)' : ' (envío en tránsito)'}</div>
              </div>
              <div className="mt-auto flex flex-wrap items-end justify-between gap-3 border-t border-steel-200 pt-4">
                <div className="grid grid-cols-2 gap-5">
                  <div><div className="text-xs text-ink-500">Precio sugerido</div><div className="money text-lg">{formatMoney(r.suggestedPrice)}</div></div>
                  <div><div className="text-xs text-ink-500">Piso</div><div className="font-mono text-lg font-semibold tabular-nums">{formatMoney(r.priceFloor)}</div></div>
                </div>
                {r.alreadyBid ? (
                  <Badge tone="petrol"><CheckCircle2 className="size-3" aria-hidden="true" />Ya ofertaste</Badge>
                ) : (
                  <Button variant="money" onClick={() => setActive(r)}><Undo2 />Ofertar<span className="sr-only"> por la carga de {CITY_LABELS[r.originCity!]} a {CITY_LABELS[r.destinationCity!]}</span></Button>
                )}
              </div>
            </Card>
          </li>
        ))}
      </ul>
      <BidSheet request={active ? asRequest(active) : null} vehicles={vehicles} defaultVehicleId={active?.vehicleId} onClose={() => setActive(null)} />
    </>
  );
}
