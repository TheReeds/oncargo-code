import { Gavel, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/domain/EmptyState';
import { RouteLine } from '@/components/domain/RouteLine';
import { Countdown } from '@/components/domain/Countdown';
import { formatDate, formatMoney } from '@/lib/format';
import { diffPct, formatDiff } from '../diff';
import type { RequestView } from '@/lib/api/server';

export type AttentionItem = {
  request: RequestView;
  closed: boolean;
  best: { amount: number; carrierName: string; carrierTrustScore: number | null } | null;
};

/** "Requiere tu atención" del panel: subastas con ofertas sin elegir, con cuenta regresiva, mejor oferta y CTA directo. */
export default function AttentionList({ items, total }: { items: AttentionItem[]; total: number }) {
  if (items.length === 0)
    return (
      <EmptyState
        compact
        title="Todo al día"
        description="Aquí verás las subastas con ofertas que aún no has elegido. Publica una carga para recibir ofertas."
        action={<Button asChild variant="outline" size="sm"><a href="/pyme/solicitudes/nueva"><Plus />Publicar solicitud</a></Button>}
      />
    );
  return (
    <>
      <ul className="divide-y divide-steel-200" aria-label="Solicitudes con ofertas sin elegir">
        {items.map(({ request: r, closed, best }) => {
          const d = best ? diffPct(best.amount, r.suggestedPrice) : null;
          const n = r.auction?.bidCount ?? 0;
          return (
            <li key={r.id} className="space-y-3 px-4 py-4 sm:px-5">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1"><RouteLine size="sm" origin={r.originCity!} destination={r.destinationCity!} originSub={`Recojo ${formatDate(r.pickupDate)}`} /></div>
                {closed
                  ? <Badge tone="amber">Subasta cerrada</Badge>
                  : <span className="flex shrink-0 flex-col items-end text-xs text-ink-500"><span>Cierra en</span><Countdown closesAt={r.auction?.closesAt} /></span>}
              </div>
              <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-2">
                <div className="min-w-0 text-sm">
                  <div className="text-xs text-ink-500"><span className="font-mono font-semibold text-amber-700">{n}/{r.auction?.maxBids ?? 5}</span> oferta{n === 1 ? '' : 's'} sin elegir{best?.carrierName ? <> · mejor de <span className="text-ink-700">{best.carrierName}</span></> : null}</div>
                  {best ? (
                    <div className="mt-0.5 flex flex-wrap items-baseline gap-x-2">
                      <span className="money text-lg">{formatMoney(best.amount)}</span>
                      {d != null && <span className={`font-mono text-xs tabular-nums ${d <= 0 ? 'text-emerald-700' : 'text-amber-700'}`}>{formatDiff(d)} vs. sugerido</span>}
                    </div>
                  ) : <div className="mt-0.5 text-xs text-ink-500">Sugerido <span className="font-mono">{formatMoney(r.suggestedPrice)}</span></div>}
                </div>
                <Button asChild variant="outline" size="sm"><a href={`/pyme/solicitudes/${r.id}`}><Gavel />Comparar ofertas<span className="sr-only"> de {r.originCity} a {r.destinationCity}</span></a></Button>
              </div>
            </li>
          );
        })}
      </ul>
      {total > items.length && <div className="border-t border-steel-200 px-5 py-3 text-sm"><a href="/pyme/solicitudes" className="font-medium text-petrol-700 hover:underline">Ver las {total} solicitudes con ofertas</a></div>}
    </>
  );
}
