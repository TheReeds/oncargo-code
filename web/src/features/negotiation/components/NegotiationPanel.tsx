import { useCallback, useEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { splitActionError } from '@/lib/action-error';
import { Card, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { formatMoney } from '@/lib/format';
import type { ChatThread } from '../server';
import { OPEN_THREAD_EVENT, useNegotiationLive } from '../usePolling';
import { ThreadList } from './ThreadList';
import { ChatPanel } from './ChatPanel';

/** PYME: panel "Conversación" de una solicitud (hilos por transportista + chat). */
export default function NegotiationPanel({ requestId, requestStatus, initialThreads, initialCarrierOrgId, readOnly, pollMs = 30000 }: { requestId: string; requestStatus: string; initialThreads: ChatThread[]; initialCarrierOrgId?: string | null; readOnly?: boolean; pollMs?: number }) {
  const [threads, setThreads] = useState<ChatThread[]>(initialThreads);
  const [selected, setSelected] = useState<string | null>(initialCarrierOrgId ?? initialThreads[0]?.carrierOrgId ?? null);
  const [error, setError] = useState<string | null>(null);
  const root = useRef<HTMLDivElement>(null);

  const refresh = useCallback(async () => {
    const { data, error } = await actions.negotiation.threads({ requestId });
    if (error) { setError(splitActionError(error).message); return; }
    setError(null);
    setThreads(data);
  }, [requestId]);
  useNegotiationLive(() => { refresh(); }, {
    types: ['message.posted', 'counter_offer.updated', 'bid.placed', 'request.matched'],
    match: (e) => e.data.requestId === requestId,
    fallbackMs: pollMs,
  });

  const select = useCallback((id: string) => {
    setSelected(id);
    const u = new URL(window.location.href);
    u.searchParams.set('hilo', id);
    window.history.replaceState(null, '', u);
  }, []);

  // BidComparison pide abrir el hilo de un transportista.
  useEffect(() => {
    const on = (e: Event) => {
      const id = (e as CustomEvent<{ carrierOrgId: string }>).detail?.carrierOrgId;
      if (!id) return;
      select(id);
      root.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    };
    window.addEventListener(OPEN_THREAD_EVENT, on);
    return () => window.removeEventListener(OPEN_THREAD_EVENT, on);
  }, [select]);

  const current = threads.find((t) => t.carrierOrgId === selected) ?? null;
  // Adjudicada: solo sigue abierto el hilo con el ganador. Cancelada: todo en lectura.
  const closed = requestStatus === 'CANCELLED' || (requestStatus === 'MATCHED' && current?.bidStatus !== 'ACCEPTED');

  return (
    <Card>
      <div ref={root} id="conversacion" className="scroll-mt-20">
        <CardHeader>
          <CardTitle>Conversación <span className="ml-1 font-mono text-ink-500">({threads.length})</span></CardTitle>
          <span className="text-xs text-ink-500">Un hilo por transportista. Se actualiza solo.</span>
        </CardHeader>
      </div>
      {error && <div className="px-5 pt-3"><Alert tone="error" title="No pudimos actualizar las conversaciones" action={<Button size="sm" variant="outline" onClick={refresh}>Reintentar</Button>}>{error}</Alert></div>}
      {threads.length === 0 ? (
        <EmptyState title="Aún no hay conversaciones" description="Cuando un transportista te escriba o envíe una oferta, podrás conversar con él aquí." />
      ) : (
        <div className="grid md:grid-cols-[260px_1fr]">
          <ThreadList threads={threads} selected={selected} onSelect={select} className="max-h-48 overflow-y-auto border-b border-steel-200 md:max-h-[34rem] md:border-b-0 md:border-r" />
          {current ? (
            <div className="flex h-[34rem] min-w-0 flex-col">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-steel-200 px-4 py-2.5">
                <a href={`/organizaciones/${current.carrierOrgId}`} className="rounded-sm text-sm font-semibold text-petrol-700 hover:underline">{current.carrierName}</a>
                {current.bidAmount != null && <span className="flex items-center gap-2 text-sm"><span className="text-ink-500">Su oferta</span><span className="font-mono font-semibold tabular-nums text-emerald-700">{formatMoney(current.bidAmount)}</span>{current.bidStatus && <StatusPill status={current.bidStatus} kind="bid" />}</span>}
              </div>
              <ChatPanel requestId={requestId} carrierOrgId={current.carrierOrgId!} closed={closed} readOnly={readOnly} pollMs={pollMs} className="flex-1" />
            </div>
          ) : (
            <div className="flex h-72 items-center justify-center text-sm text-ink-500">Selecciona una conversación para ver los mensajes.</div>
          )}
        </div>
      )}
    </Card>
  );
}
