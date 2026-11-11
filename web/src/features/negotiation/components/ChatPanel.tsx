import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import { actions } from 'astro:actions';
import { Handshake, Lock, SendHorizontal } from 'lucide-react';
import { splitActionError } from '@/lib/action-error';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Alert } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@/lib/utils';
import { formatDateTime, formatMoney } from '@/lib/format';
import type { ChatMessage } from '../server';
import { RN07_COPY, isContactError, notifyNegotiationChanged, usePolling } from '../usePolling';

export const MAX_MESSAGE = 1000;

export interface ChatPanelProps {
  requestId: string;
  /** Organización del transportista dueña del hilo. */
  carrierOrgId: string;
  /** Sin caja de texto (operador o auditoría). */
  readOnly?: boolean;
  /** Hilo cerrado (solicitud cancelada o adjudicada a otro): solo lectura con aviso. */
  closed?: boolean;
  /** Intervalo de refresco en ms (pausado con la pestaña oculta). 0 = sin polling. */
  pollMs?: number;
  /** Texto de ayuda cuando todavía no hay mensajes. */
  emptyHint?: string;
  className?: string;
}

const byTime = (a: ChatMessage, b: ChatMessage) => (a.createdAt ?? '').localeCompare(b.createdAt ?? '');
function merge(prev: ChatMessage[], next: ChatMessage[]) {
  const map = new Map(prev.map((m) => [m.id, m]));
  next.forEach((m) => map.set(m.id, m));
  return [...map.values()].sort(byTime);
}

function Bubble({ m }: { m: ChatMessage }) {
  if (m.system)
    return (
      <li className="flex justify-center py-1">
        <div className="flex max-w-[92%] items-start gap-2 rounded-input border border-amber-500/40 bg-amber-50 px-3 py-2 text-sm text-amber-700">
          <Handshake className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <div>
            <p>{m.body}</p>
            {m.proposedPrice != null && <p className="mt-0.5 font-mono text-sm font-semibold tabular-nums">{formatMoney(m.proposedPrice)}</p>}
            <p className="mt-0.5 text-xs">{formatDateTime(m.createdAt)}</p>
          </div>
        </div>
      </li>
    );
  return (
    <li className={cn('flex flex-col', m.mine ? 'items-end' : 'items-start')}>
      {!m.mine && <span className="mb-0.5 px-1 text-xs font-medium text-ink-500">{m.senderName}</span>}
      <div className={cn('max-w-[85%] whitespace-pre-wrap break-words rounded-card px-3 py-2 text-sm', m.mine ? 'rounded-br-sm bg-petrol-700 text-white' : 'rounded-bl-sm bg-steel-100 text-ink-900')}>{m.body}</div>
      <span className="mt-0.5 px-1 text-xs text-ink-500">{formatDateTime(m.createdAt)}</span>
    </li>
  );
}

function ChatPanelInner({ requestId, carrierOrgId, readOnly, closed, pollMs = 8000, emptyHint, className }: ChatPanelProps) {
  const uid = useId();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [older, setOlder] = useState(false);
  const [draft, setDraft] = useState('');
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [sendError, setSendError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [lockedByApi, setLockedByApi] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  const stick = useRef(true);
  const restore = useRef<number | null>(null);

  const refresh = useCallback(async (initial = false) => {
    const { data, error } = await actions.negotiation.messages({ requestId, carrierOrgId });
    if (error) {
      if (initial) setLoadError(splitActionError(error).message);
      return;
    }
    setLoadError(null);
    setMessages((prev) => merge(prev, data.items));
    if (initial) setCursor(data.nextCursor);
    setLoading(false);
  }, [requestId, carrierOrgId]);

  useEffect(() => {
    setLoading(true);
    refresh(true).finally(() => setLoading(false));
  }, [refresh]);
  usePolling(() => { refresh(); }, pollMs);

  useLayoutEffect(() => {
    const el = listRef.current;
    if (!el) return;
    if (restore.current != null) {
      el.scrollTop = el.scrollHeight - restore.current;
      restore.current = null;
    } else if (stick.current) el.scrollTop = el.scrollHeight;
  }, [messages, loading]);

  async function loadOlder() {
    if (!cursor || !listRef.current) return;
    setOlder(true);
    const { data, error } = await actions.negotiation.messages({ requestId, carrierOrgId, before: cursor });
    setOlder(false);
    if (error) { setSendError(splitActionError(error).message); return; }
    restore.current = listRef.current.scrollHeight;
    stick.current = false;
    setMessages((prev) => merge(prev, data.items));
    setCursor(data.nextCursor);
  }

  async function send() {
    const body = draft.trim();
    if (!body || sending) return;
    if (body.length > MAX_MESSAGE) { setFieldError(`El mensaje supera los ${MAX_MESSAGE} caracteres. Acórtalo para enviarlo.`); return; }
    setSending(true);
    setFieldError(null);
    setSendError(null);
    const { data, error } = await actions.negotiation.postMessage({ requestId, carrierOrgId, body });
    setSending(false);
    if (error) {
      const { message, field } = splitActionError(error);
      if (isContactError(field, message)) setFieldError(RN07_COPY);
      else if (field === 'body') setFieldError(message);
      else {
        setSendError(message);
        if (/cerrad/i.test(message)) setLockedByApi(true);
      }
      return; // no se borra lo que escribió la persona
    }
    setDraft('');
    stick.current = true;
    setMessages((prev) => merge(prev, [data]));
    notifyNegotiationChanged();
  }

  const locked = readOnly || closed || lockedByApi;
  const inputId = `${uid}-msg`;

  return (
    <div className={cn('flex min-h-0 flex-col', className)}>
      <div
        ref={listRef}
        role="log"
        aria-label="Mensajes de la conversación"
        tabIndex={0}
        onScroll={(e) => { const el = e.currentTarget; stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < 60; }}
        className="min-h-48 flex-1 overflow-y-auto bg-surface p-4"
      >
        <ul className="space-y-3">
        {loading ? (
          <li className="space-y-3" aria-label="Cargando mensajes"><Skeleton className="h-10 w-2/3" /><Skeleton className="ml-auto h-10 w-1/2" /><Skeleton className="h-10 w-3/5" /></li>
        ) : loadError ? (
          <li><Alert tone="error" title="No pudimos cargar la conversación" action={<Button size="sm" variant="outline" onClick={() => { setLoadError(null); setLoading(true); refresh(true).finally(() => setLoading(false)); }}>Reintentar</Button>}>{loadError}</Alert></li>
        ) : (
          <>
            {cursor && (
              <li className="flex justify-center"><Button size="sm" variant="ghost" loading={older} onClick={loadOlder}>Cargar mensajes anteriores</Button></li>
            )}
            {messages.length === 0 && (
              <li className="py-8 text-center text-sm text-ink-500">
                <p className="font-medium text-ink-700">Aún no hay mensajes</p>
                <p className="mt-0.5">{emptyHint ?? (locked ? 'Esta conversación no tiene mensajes.' : 'Escribe el primer mensaje para coordinar los detalles de la carga.')}</p>
              </li>
            )}
            {messages.map((m) => <Bubble key={m.id} m={m} />)}
          </>
        )}
        </ul>
      </div>

      {locked ? (
        <div className="border-t border-steel-200 bg-steel-100 px-4 py-3 text-sm text-ink-700">
          <p className="flex items-center gap-2"><Lock className="size-4 shrink-0 text-ink-500" aria-hidden="true" />{readOnly && !closed ? 'Estás viendo esta conversación en modo lectura.' : 'Esta conversación está cerrada. Puedes leer los mensajes, pero ya no se pueden enviar nuevos.'}</p>
        </div>
      ) : (
        <form
          method="post"
          onSubmit={(e) => { e.preventDefault(); send(); }}
          className="space-y-2 border-t border-steel-200 bg-surface p-3"
        >
          {sendError && <Alert tone="error" title="No pudimos enviar tu mensaje">{sendError}</Alert>}
          <Field label="Mensaje" htmlFor={inputId} error={fieldError} hint="Enter envía · Shift + Enter agrega una línea">
            <Textarea
              id={inputId}
              value={draft}
              rows={2}
              maxLength={MAX_MESSAGE + 200}
              aria-invalid={!!fieldError || undefined}
              onChange={(e) => { setDraft(e.target.value); if (fieldError) setFieldError(null); }}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); send(); } }}
              placeholder="Escribe tu mensaje…"
              className="max-h-32 min-h-12 resize-none"
            />
          </Field>
          <div className="flex items-center justify-between gap-3">
            <span className={cn('font-mono text-xs tabular-nums', draft.length > MAX_MESSAGE ? 'text-red-600' : 'text-ink-500')} aria-live="off">{draft.length}/{MAX_MESSAGE}</span>
            <Button type="submit" size="sm" loading={sending} disabled={!draft.trim()}><SendHorizontal />{sending ? 'Enviando…' : 'Enviar mensaje'}</Button>
          </div>
        </form>
      )}
    </div>
  );
}

/** Chat de un hilo (solicitud + transportista). Reutilizable por PYME, transportista y operador (`readOnly`). */
export function ChatPanel(props: ChatPanelProps) {
  return <ChatPanelInner key={`${props.requestId}:${props.carrierOrgId}`} {...props} />;
}
export default ChatPanel;
