import { useState } from 'react';
import { actions } from 'astro:actions';
import { Eye, MessageSquareText } from 'lucide-react';
import { Card, CardBody, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Avatar } from '@/components/ui/avatar';
import { Sheet } from '@/components/ui/sheet';
import { Alert } from '@/components/ui/alert';
import { ReasonDialog } from '@/components/ui/reason-dialog';
import { StatusPill } from '@/components/domain/StatusPill';
import { EmptyState } from '@/components/domain/EmptyState';
import { splitActionError } from '@/lib/action-error';
import { formatDateTime, formatMoney, formatRelative } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { OperatorMessage, OperatorThread } from '../server';

/**
 * Conversaciones de una solicitud en solo lectura, para mediar una disputa (ítem 1.13). Verlas queda en la auditoría
 * (`OPERATOR_VIEWED_CHAT`) con el motivo que el operador indique (opcional), por eso no se cargan hasta que lo pide.
 * El operador no puede escribir: el chat es entre la PYME y el transportista.
 */
export default function OperatorChat({ requestId }: { requestId: string }) {
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState('');
  const [threads, setThreads] = useState<OperatorThread[] | null>(null);
  const [open, setOpen] = useState<OperatorThread | null>(null);
  const [messages, setMessages] = useState<OperatorMessage[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function load(r: string) {
    const { data, error: err } = await actions.operator.threads({ requestId, reason: r || undefined });
    if (err) return splitActionError(err);
    setReason(r);
    setThreads(data);
    return null;
  }

  async function read(t: OperatorThread, before?: string) {
    setOpen(t);
    setLoading(true);
    setError(null);
    const { data, error: err } = await actions.operator.threadMessages({ requestId, carrierOrgId: t.carrierOrgId!, before, reason: reason || undefined });
    setLoading(false);
    if (err || !data) { setError(err ? splitActionError(err).message : 'No pudimos cargar la conversación.'); return; }
    setMessages((prev) => (before ? [...data.items, ...prev] : data.items));
    setCursor(data.nextCursor);
  }

  return (
    <Card data-testid="operator-chat">
      <CardHeader>
        <div><CardTitle>Conversaciones</CardTitle><CardDescription>Solo consulta. Cada vez que las abres queda registrado en la auditoría.</CardDescription></div>
        {threads === null && <Button variant="outline" size="sm" onClick={() => setAsking(true)}><Eye aria-hidden="true" /> Ver conversaciones</Button>}
      </CardHeader>
      {threads === null ? (
        <CardBody><p className="text-sm text-ink-500">Para resolver una disputa puedes leer lo que hablaron la PYME y cada transportista. Se te pedirá un motivo opcional.</p></CardBody>
      ) : threads.length === 0 ? (
        <EmptyState compact icon={MessageSquareText} title="Sin conversaciones" description="Ningún transportista ha escrito ni ofertado en esta solicitud." />
      ) : (
        <ul className="divide-y divide-steel-200" aria-label="Conversaciones por transportista">
          {threads.map((t) => (
            <li key={t.carrierOrgId} className="flex flex-wrap items-center gap-3 px-5 py-3" data-testid="operator-thread">
              <Avatar name={t.carrierName ?? '?'} size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-ink-900">{t.carrierName}</p>
                <p className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-ink-500">
                  {t.bidAmount != null && <><span className="font-mono font-semibold tabular-nums text-emerald-700">{formatMoney(t.bidAmount)}</span>{t.bidStatus && <StatusPill status={t.bidStatus} kind="bid" />}</>}
                  <span>{t.messageCount} {t.messageCount === 1 ? 'mensaje' : 'mensajes'}{t.lastMessageAt ? ` · último ${formatRelative(t.lastMessageAt)}` : ''}</span>
                </p>
                {t.lastMessage && <p className="mt-1 truncate text-xs text-ink-700">{t.lastMessage}</p>}
              </div>
              <Button variant="outline" size="sm" onClick={() => read(t)}>Leer<span className="sr-only"> la conversación con {t.carrierName}</span></Button>
            </li>
          ))}
        </ul>
      )}

      <ReasonDialog
        open={asking} onOpenChange={setAsking}
        title="Ver conversaciones de la solicitud" description="Es una consulta de solo lectura que queda registrada en la auditoría con tu nombre."
        label="Motivo de la consulta" hint="Opcional. Ej. «Disputa por el monto acordado, llamada del cliente»." maxLength={200} rows={3}
        confirmLabel="Ver conversaciones" onSubmit={load}
      />

      <Sheet open={!!open} onOpenChange={(o) => { if (!o) { setOpen(null); setMessages([]); setCursor(null); } }} title={open ? `Conversación con ${open.carrierName}` : 'Conversación'} description="Solo lectura">
        <div className="space-y-3 p-5">
          {error && <Alert tone="error" title="No pudimos cargar la conversación">{error}</Alert>}
          {cursor && <Button variant="ghost" size="sm" loading={loading} onClick={() => open && read(open, cursor)}>Cargar mensajes anteriores</Button>}
          {!loading && !error && messages.length === 0 && <p className="text-sm text-ink-500">Esta conversación no tiene mensajes.</p>}
          <ol className="space-y-3" aria-label="Mensajes">
            {messages.map((m) => {
              const carrier = m.senderOrgId === open?.carrierOrgId;
              return (
                <li key={m.id} className={cn('max-w-[92%] rounded-card border px-3 py-2 text-sm', m.system ? 'mx-auto border-dashed border-steel-200 bg-steel-100 text-ink-700' : carrier ? 'mr-auto border-steel-200 bg-surface' : 'ml-auto border-petrol-700/30 bg-petrol-50')}>
                  <p className="flex items-baseline justify-between gap-3 text-xs"><span className="font-semibold text-ink-900">{m.system ? 'Aviso automático' : m.senderName}</span><span className="shrink-0 text-ink-500">{formatDateTime(m.createdAt)}</span></p>
                  <p className="mt-1 whitespace-pre-wrap break-words text-ink-900">{m.body}</p>
                  {m.proposedPrice != null && <p className="mt-1 font-mono text-xs font-semibold tabular-nums text-emerald-700">{formatMoney(m.proposedPrice)}</p>}
                </li>
              );
            })}
          </ol>
          {loading && messages.length === 0 && <p className="text-sm text-ink-500" role="status">Cargando…</p>}
        </div>
      </Sheet>
    </Card>
  );
}
